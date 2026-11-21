// SPDX-License-Identifier: Apache-2.0
package com.ashik.volumedot

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashik.volumedot.ui.*

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: PrefsManager
    private var appearance by mutableStateOf<Appearance?>(null)
    private var permissions by mutableStateOf(PermissionStatus())
    private var volumes by mutableStateOf<List<StreamVolume>>(emptyList())
    private var selectedTab by mutableIntStateOf(0)
    private var pendingStart = false
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = Runnable { sendService(FloatingVolumeService.ACTION_REFRESH_PREFS) }
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> appearance = Appearance.from(prefs) }
    private val notificationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { startController() }
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { volumes = readVolumes(this@MainActivity) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        prefs.observe(prefsListener)
        appearance = Appearance.from(prefs)
        selectedTab = (savedInstanceState?.getInt("selected_tab") ?: intent.getIntExtra("open_tab", 0)).coerceIn(0, 2)
        pendingStart = savedInstanceState?.getBoolean("pending_start") ?: false
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val actions = UiActions(
            change = { block -> prefs.block(); handler.removeCallbacks(refresh); handler.postDelayed(refresh, 80) },
            toggleService = ::toggleController, permission = ::openPermission,
            volume = ::setVolume, mute = ::toggleMute, nativeVolume = ::nativeVolume,
            position = ::setPosition, resetPosition = {
                val screen = getSystemService(android.view.WindowManager::class.java)
                val bounds = if (Build.VERSION.SDK_INT >= 30) screen.maximumWindowMetrics.bounds else android.graphics.Rect(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
                prefs.savedX = (bounds.width() - (prefs.buttonSizeDp * resources.displayMetrics.density).toInt()).coerceAtLeast(0)
                prefs.savedY = bounds.height() / 2
                sendService(FloatingVolumeService.ACTION_RESET_POSITION)
            })
        setContent {
            val running by FloatingVolumeService.runningState.collectAsStateWithLifecycle()
            appearance?.let {
                val dark = DesignTokens.isDark(this, it.mode)
                LaunchedEffect(dark) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                    )
                }
                VolumeDotApp(prefs, it, permissions, running, volumes, selectedTab, { tab -> selectedTab = tab }, actions)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("open_tab")) selectedTab = intent.getIntExtra("open_tab", 0).coerceIn(0, 2)
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, volumeReceiver,
            IntentFilter().apply { addAction("android.media.VOLUME_CHANGED_ACTION"); addAction(AudioManager.RINGER_MODE_CHANGED_ACTION) },
            ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        permissions = PermissionStatus(Settings.canDrawOverlays(this), VolumeDotAccessibilityService.instance != null,
            getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
            Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        volumes = readVolumes(this)
        appearance = Appearance.from(prefs)
        if (pendingStart) { pendingStart = false; if (permissions.overlay) requestStart() }
        else if (prefs.isServiceEnabled && permissions.overlay) sendService(FloatingVolumeService.ACTION_REFRESH_PREFS)
    }

    override fun onStop() {
        unregisterReceiver(volumeReceiver)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selected_tab", selectedTab)
        outState.putBoolean("pending_start", pendingStart)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        prefs.removeObserver(prefsListener)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun toggleController() {
        if (FloatingVolumeService.isRunning) {
            pendingStart = false
            prefs.isServiceEnabled = false
            stopService(Intent(this, FloatingVolumeService::class.java))
        } else if (!Settings.canDrawOverlays(this)) {
            pendingStart = true
            openPermission("overlay")
        } else requestStart()
    }

    private fun requestStart() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startController()
    }

    private fun startController() {
        if (!Settings.canDrawOverlays(this)) return
        prefs.isServiceEnabled = true
        sendService(FloatingVolumeService.ACTION_REFRESH_PREFS)
    }

    private fun sendService(action: String) {
        if (!prefs.isServiceEnabled || !Settings.canDrawOverlays(this)) return
        try { ContextCompat.startForegroundService(this, Intent(this, FloatingVolumeService::class.java).setAction(action)) }
        catch (_: RuntimeException) { prefs.isServiceEnabled = false; message("Could not start the controller. Please try again.") }
    }

    private fun setVolume(stream: Int, value: Int) {
        try {
            val audio = getSystemService(AudioManager::class.java)
            audio.setStreamVolume(stream, value.coerceIn(0, audio.getStreamMaxVolume(stream)), 0)
        } catch (_: SecurityException) { message("Do Not Disturb is preventing this volume change.") }
        volumes = readVolumes(this)
    }

    private fun toggleMute() {
        getSystemService(AudioManager::class.java).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
        volumes = readVolumes(this)
    }

    private fun nativeVolume() {
        getSystemService(AudioManager::class.java).adjustSuggestedStreamVolume(AudioManager.ADJUST_SAME, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
    }

    private fun setPosition(side: String, fraction: Float?) {
        val wm = getSystemService(android.view.WindowManager::class.java)
        val width = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.width() else resources.displayMetrics.widthPixels
        val height = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.height() else resources.displayMetrics.heightPixels
        val (w, h) = BubbleRenderer.getBubbleDimensionsPx(this, prefs)
        prefs.savedX = if (side == "left") 0 else (width - maxOf(w, (48 * resources.displayMetrics.density).toInt())).coerceAtLeast(0)
        if (fraction != null) prefs.savedY = (height * fraction).toInt().coerceIn(0, (height - h).coerceAtLeast(0))
        sendService(FloatingVolumeService.ACTION_APPLY_POSITION)
    }

    @SuppressLint("BatteryLife") // Explicit user action on the background-access row.
    private fun openPermission(which: String) {
        val intent = when (which) {
            "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "battery" -> if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName))
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri())
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
            else -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        try { startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())) }
    }

    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
