// SPDX-License-Identifier: Apache-2.0
package com.ashik.volumedot

import android.animation.ValueAnimator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.ashik.volumedot.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout

import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlin.math.abs
import kotlin.math.max

class FloatingVolumeService : Service() {

    companion object {
        private val running = MutableStateFlow(false)
        val runningState = running.asStateFlow()
        @Volatile var isRunning = false
            private set(value) { field = value; running.value = value }
        private const val TAG = "VolumeDotService"
        const val ACTION_REFRESH_PREFS = "com.ashik.volumedot.ACTION_REFRESH_PREFS"
        const val ACTION_STOP_SERVICE = "com.ashik.volumedot.ACTION_STOP_SERVICE"
        const val ACTION_APPLY_POSITION = "com.ashik.volumedot.ACTION_APPLY_POSITION"
        const val ACTION_RESET_POSITION = "com.ashik.volumedot.ACTION_RESET_POSITION"

        // Stable Android channel ID preserves the user's notification preferences across the rename.
        private const val CHANNEL_ID = "floatvol_overlay_channel"
        private const val NOTIFICATION_ID = 1001
        private const val AUTO_DIM_DELAY_MS = 3000L
        private const val DOUBLE_TAP_WINDOW_MS = 250L
        private const val HOLD_TO_DRAG_MS = 280L
        private const val STEALTH_RESTORE_DELAY_MS = 650L
    }

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var prefs: PrefsManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var floatingBubbleView: FrameLayout? = null
    private var expandedPanelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelClosing = false
    private var panelTouched = false
    private var edgeSpring: SpringAnimation? = null
    private val panelSprings = mutableListOf<SpringAnimation>()
    private val panelAppearance = mutableStateOf<Appearance?>(null)
    private val panelVolumes = mutableStateOf<List<StreamVolume>>(emptyList())
    private val hidePanel = Runnable { closeExpandedQuickPanel(animate = true) }
    private lateinit var bubbleParams: WindowManager.LayoutParams

    private var isDestroyed = false
    private var foregroundReady = false
    private var positionAnimator: ValueAnimator? = null
    private var isTakingScreenshot = false
    private var isCurrentlyMoving = false
    private var volumeChangeReceiver: BroadcastReceiver? = null

    private val autoDimRunnable = Runnable {
        if (prefs.isAutoDimEnabled && expandedPanelView == null && !isTakingScreenshot && !isCurrentlyMoving) {
            floatingBubbleView?.animate()
                ?.alpha(prefs.idleOpacityPercent / 100f)
                ?.setDuration(if (DesignTokens.motionAllowed(prefs)) 350 else 0)
                ?.start()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        prefs = PrefsManager(this)

        foregroundReady = startForegroundNotification()
        if (!foregroundReady) {
            stopSelf()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission not granted. Terminating service.")
            Toast.makeText(this, "VolumeDot: Overlay permission is required", Toast.LENGTH_LONG).show()
            prefs.isServiceEnabled = false
            stopSelf()
            return
        }

        registerVolumeChangeReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundReady || !Settings.canDrawOverlays(this) ||
            (!prefs.isServiceEnabled && intent?.action != ACTION_STOP_SERVICE)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_STOP_SERVICE) {
            if (floatingBubbleView?.isAttachedToWindow != true) createFloatingBubble()
            if (floatingBubbleView == null) return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                prefs.isServiceEnabled = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_PREFS -> {
                applyDynamicPreferences()
            }
            ACTION_APPLY_POSITION -> {
                edgeSpring?.cancel()
                bubbleParams.x = prefs.savedX
                bubbleParams.y = prefs.savedY
                clampAndSavePosition()
                updatePanelPosition()
            }
            ACTION_RESET_POSITION -> {
                resetBubbleToDefaultPosition()
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        closeExpandedQuickPanel()
        mainHandler.postDelayed({
            clampAndRealignToScreen(animate = DesignTokens.motionAllowed(prefs))
            applyDynamicPreferences()
        }, 150L)
    }

    private fun startForegroundNotification(): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VolumeDot Overlay Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the floating volume controller active and accessible"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingVolumeService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_IMMUTABLE
        )

        val resetIntent = PendingIntent.getService(
            this, 2,
            Intent(this, FloatingVolumeService::class.java).apply { action = ACTION_RESET_POSITION },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("VolumeDot Active")
            .setContentText("Tap: Volume UI • Hold: Move • Double-Tap: Screenshot")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_revert, "Center", resetIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    } else {
                        0
                    }
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Cannot enter foreground", e)
            return false
        }
    }
    private fun registerVolumeChangeReceiver() {
        if (volumeChangeReceiver != null) return
        volumeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateBubbleVisuals()
                refreshQuickHubSliders()
            }
        }
        val filter = IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(volumeChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(volumeChangeReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register volume receiver: ${e.message}")
        }
    }

    private fun createFloatingBubble() {
        val dimensions = BubbleRenderer.getBubbleDimensionsPx(this, prefs)
        val widthPx = max(dpToPx(48), dimensions.first)
        val heightPx = max(dpToPx(48), dimensions.second)

        val container = FloatingBubbleView(this).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR }
        BubbleRenderer.renderBubble(this, container, prefs, getVolumeLevel(), isMoving = false)

        val overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val screenSize = getScreenDimensions()
        val initialX = prefs.savedX.coerceIn(0, max(0, screenSize.x - widthPx))
        val initialY = prefs.savedY.coerceIn(dpToPx(40), max(dpToPx(40), screenSize.y - heightPx - dpToPx(40)))

        bubbleParams = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        setupTouchInteractions(container)

        try {
            windowManager.addView(container, bubbleParams)
            floatingBubbleView = container
            isRunning = true
            container.contentDescription = "Floating volume controller. Tap for volume, hold for controls."
            floatingBubbleView?.alpha = prefs.activeOpacityPercent / 100f
            scheduleAutoDim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating bubble to window manager: ${e.message}", e)
            Toast.makeText(this, "Could not display overlay: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun setupTouchInteractions(view: FloatingBubbleView) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var tapCount = 0

        var isHoldToMoveActive = false
        var hasSignificantMove = false
        var isSwipingVolume = false
        var swipeAccumulatedDy = 0f

        var clickAction: () -> Unit = {}
        val singleTapRunnable = Runnable {
            if (tapCount == 1) {
                clickAction()
            }
            tapCount = 0
        }

        val holdToMoveRunnable = Runnable {
            isHoldToMoveActive = true
            isCurrentlyMoving = true
            tapCount = 0
            mainHandler.removeCallbacks(singleTapRunnable)
            triggerHaptic()

            view.animate()
                .scaleX(1.16f)
                .scaleY(1.16f)
                .setDuration(if (DesignTokens.motionAllowed(prefs)) 150 else 0)
                .setInterpolator(OvershootInterpolator())
                .start()
            BubbleRenderer.renderBubble(this, view, prefs, getVolumeLevel(), isMoving = true)
        }

        view.setOnClickListener { if (prefs.tapAction == "native") openNativeSystemVolumeUI() else toggleExpandedQuickPanel() }
        view.setOnLongClickListener { toggleExpandedQuickPanel(); true }
        view.gestureHandler = touch@{ event, onClick ->
            clickAction = onClick
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    edgeSpring?.cancel()
                    positionAnimator?.cancel()
                    wakeUpBubbleOpacity()
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isHoldToMoveActive = false
                    isCurrentlyMoving = false
                    hasSignificantMove = false
                    isSwipingVolume = false
                    swipeAccumulatedDy = 0f

                    mainHandler.postDelayed(holdToMoveRunnable, HOLD_TO_DRAG_MS)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    val moveDist = abs(dx) + abs(dy)

                    if (moveDist > dpToPx(9)) {
                        hasSignificantMove = true
                    }

                    if (!isHoldToMoveActive && hasSignificantMove) {
                        if (prefs.isPositionLocked && prefs.isSwipeVolumeEnabled) {
                            mainHandler.removeCallbacks(holdToMoveRunnable)
                            val stepThreshold = dpToPx(24)
                            val deltaY = event.rawY - initialTouchY - swipeAccumulatedDy
                            if (abs(deltaY) >= stepThreshold) {
                                isSwipingVolume = true
                                tapCount = 0
                                mainHandler.removeCallbacks(singleTapRunnable)
                                val direction = if (deltaY < 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                                try {
                                    audioManager.adjustSuggestedStreamVolume(
                                        direction,
                                        AudioManager.USE_DEFAULT_STREAM_TYPE,
                                        AudioManager.FLAG_SHOW_UI
                                    )
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to adjust volume: ${e.message}")
                                }
                                triggerHaptic()
                                updateBubbleVisuals()
                                swipeAccumulatedDy += deltaY
                            }
                            return@touch true
                        } else if (!prefs.isPositionLocked && !prefs.isHoldToMoveOnly) {
                            mainHandler.removeCallbacks(holdToMoveRunnable)
                            isHoldToMoveActive = true
                            isCurrentlyMoving = true
                            BubbleRenderer.renderBubble(this, view, prefs, getVolumeLevel(), isMoving = true)
                        }
                    }

                    if (isHoldToMoveActive && !prefs.isPositionLocked) {
                        val screen = getScreenDimensions()
                        val newX = (initialX + dx.toInt()).coerceIn(0, max(0, screen.x - bubbleParams.width))
                        val safeTop = dpToPx(36)
                        val safeBottom = max(safeTop, screen.y - bubbleParams.height - dpToPx(36))
                        val newY = (initialY + dy.toInt()).coerceIn(safeTop, safeBottom)

                        bubbleParams.x = newX
                        bubbleParams.y = newY
                        try {
                            floatingBubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) }
                        } catch (e: Exception) {
                            Log.w(TAG, "updateViewLayout failed: ${e.message}")
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(holdToMoveRunnable)
                    isCurrentlyMoving = false

                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(if (DesignTokens.motionAllowed(prefs)) 160 else 0)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                    BubbleRenderer.renderBubble(this, view, prefs, getVolumeLevel(), isMoving = false)

                    if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        mainHandler.removeCallbacks(singleTapRunnable)
                        tapCount = 0
                        clampAndSavePosition()
                        scheduleAutoDim()
                        return@touch true
                    }
                    val totalMove = abs(event.rawX - initialTouchX) + abs(event.rawY - initialTouchY)

                    if (isHoldToMoveActive && hasSignificantMove && !prefs.isPositionLocked) {
                        triggerHaptic()
                        if (prefs.isEdgeSnapEnabled) {
                            snapToNearestHorizontalEdge()
                        } else {
                            clampAndSavePosition()
                        }
                    } else if (isHoldToMoveActive && !hasSignificantMove) {
                        toggleExpandedQuickPanel()
                    } else if (!isSwipingVolume && totalMove < dpToPx(10)) {
                        tapCount++
                        if (tapCount == 1) {
                            mainHandler.postDelayed(singleTapRunnable, DOUBLE_TAP_WINDOW_MS)
                        } else if (tapCount >= 2) {
                            mainHandler.removeCallbacks(singleTapRunnable)
                            tapCount = 0
                            if (prefs.isDoubleTapScreenshot) {
                                performStealthScreenshot()
                            } else {
                                toggleMediaMute()
                            }
                        }
                    } else if (!prefs.isPositionLocked && hasSignificantMove) {
                        if (prefs.isEdgeSnapEnabled) {
                            snapToNearestHorizontalEdge()
                        } else {
                            clampAndSavePosition()
                        }
                    }

                    scheduleAutoDim()
                    true
                }
                else -> false
            }
        }
    }

    private fun clampAndSavePosition() {
        val screen = getScreenDimensions()
        bubbleParams.x = bubbleParams.x.coerceIn(0, max(0, screen.x - bubbleParams.width))
        val safeTop = dpToPx(36)
        val safeBottom = max(safeTop, screen.y - bubbleParams.height - dpToPx(36))
        bubbleParams.y = bubbleParams.y.coerceIn(safeTop, safeBottom)

        try {
            floatingBubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) }
        } catch (e: Exception) {
            Log.w(TAG, "clampAndSavePosition layout update failed: ${e.message}")
        }
        prefs.savedX = bubbleParams.x
        prefs.savedY = bubbleParams.y
    }

    private fun clampAndRealignToScreen(animate: Boolean) {
        val view = floatingBubbleView ?: return
        val screen = getScreenDimensions()

        var targetX = bubbleParams.x.coerceIn(0, max(0, screen.x - bubbleParams.width))
        val safeTop = dpToPx(36)
        val safeBottom = max(safeTop, screen.y - bubbleParams.height - dpToPx(36))
        val targetY = bubbleParams.y.coerceIn(safeTop, safeBottom)

        if (prefs.isEdgeSnapEnabled) {
            targetX = if (targetX + (bubbleParams.width / 2) < screen.x / 2) {
                0
            } else {
                max(0, screen.x - bubbleParams.width)
            }
        }

        if (animate) {
            val startX = bubbleParams.x
            val startY = bubbleParams.y
            positionAnimator?.cancel()
            positionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    val frac = anim.animatedValue as Float
                    bubbleParams.x = (startX + (targetX - startX) * frac).toInt()
                    bubbleParams.y = (startY + (targetY - startY) * frac).toInt()
                    try {
                        windowManager.updateViewLayout(view, bubbleParams)
                    } catch (e: Exception) {
                        Log.w(TAG, "Rotation realignment update failed: ${e.message}")
                    }
                }
                start()
            }
        } else {
            bubbleParams.x = targetX
            bubbleParams.y = targetY
            try {
                windowManager.updateViewLayout(view, bubbleParams)
            } catch (e: Exception) {
                Log.w(TAG, "Realign update failed: ${e.message}")
            }
        }
        prefs.savedX = targetX
        prefs.savedY = targetY
    }

    private fun resetBubbleToDefaultPosition() {
        edgeSpring?.cancel()
        positionAnimator?.cancel()
        val screen = getScreenDimensions()
        val defaultX = max(0, screen.x - bubbleParams.width)
        val defaultY = (screen.y / 2) - (bubbleParams.height / 2)

        bubbleParams.x = defaultX
        bubbleParams.y = defaultY
        floatingBubbleView?.let {
            try {
                windowManager.updateViewLayout(it, bubbleParams)
            } catch (e: Exception) {
                Log.w(TAG, "Reset position update failed: ${e.message}")
            }
        }
        prefs.savedX = defaultX
        prefs.savedY = defaultY
        wakeUpBubbleOpacity()
        scheduleAutoDim()
        triggerHaptic()
        Toast.makeText(this, "VolumeDot centered on edge", Toast.LENGTH_SHORT).show()
    }

    private fun openNativeSystemVolumeUI() {
        triggerHaptic()
        try {
            audioManager.adjustSuggestedStreamVolume(
                AudioManager.ADJUST_SAME,
                AudioManager.USE_DEFAULT_STREAM_TYPE,
                AudioManager.FLAG_SHOW_UI
            )
        } catch (e: Exception) {
            Log.w(TAG, "openNativeSystemVolumeUI error: ${e.message}")
        }
        updateBubbleVisuals()
    }

    private fun performStealthScreenshot() {
        if (isTakingScreenshot) return
        isTakingScreenshot = true
        triggerHaptic()

        if (VolumeDotAccessibilityService.instance == null) {
            isTakingScreenshot = false
            Toast.makeText(
                this,
                "Enable 'VolumeDot Stealth Screenshot' in Accessibility Settings first",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            closeExpandedQuickPanel()
            return
        }

        closeExpandedQuickPanel()
        val bubble = floatingBubbleView
        if (bubble == null) {
            isTakingScreenshot = false
            return
        }

        bubble.visibility = View.GONE

        Choreographer.getInstance().postFrameCallback {
            mainHandler.postDelayed({
                val screenshotTaken = VolumeDotAccessibilityService.triggerSystemScreenshot()
                val restoreDelay = if (screenshotTaken) STEALTH_RESTORE_DELAY_MS else 100L

                mainHandler.postDelayed(restore@{
                    if (isDestroyed || floatingBubbleView !== bubble) return@restore
                    isTakingScreenshot = false
                    bubble.alpha = 0f
                    bubble.visibility = View.VISIBLE
                    bubble.animate()
                        .alpha(prefs.activeOpacityPercent / 100f)
                        .setDuration(if (DesignTokens.motionAllowed(prefs)) 180 else 0)
                        .withEndAction {
                            isTakingScreenshot = false
                            scheduleAutoDim()
                        }.start()
                }, restoreDelay)
            }, 45L)
        }
    }

    private fun toggleMediaMute() {
        triggerHaptic()
        try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_TOGGLE_MUTE,
                AudioManager.FLAG_SHOW_UI
            )
        } catch (e: Exception) {
            Log.w(TAG, "toggleMediaMute failed: ${e.message}")
        }
        updateBubbleVisuals()
        refreshQuickHubSliders()
    }


    private fun toggleExpandedQuickPanel() {
        if (expandedPanelView != null) { closeExpandedQuickPanel(animate = true); return }
        panelClosing = false
        panelTouched = false
        panelAppearance.value = Appearance.from(prefs)
        panelVolumes.value = readVolumes(this)
        val screen = getScreenDimensions()
        val host = OverlayPanelHost(this, { closeExpandedQuickPanel(animate = true) }) { touching ->
            panelTouched = touching
            schedulePanelHide()
        }
        val width = minOf(dpToPx(if (prefs.sliderOrientation == "vertical") 240 else 340), screen.x - dpToPx(24)).coerceAtLeast(1)
        panelParams = WindowManager.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        host.layoutDirection = View.LAYOUT_DIRECTION_LTR
        expandedPanelView = host
        updatePanelPosition(attach = false)
        host.show {
            panelAppearance.value?.let { appearance ->
                VolumeDotTheme(appearance) {
                    Box(Modifier.heightIn(max = ((getScreenDimensions().y - dpToPx(88)) /
                        resources.displayMetrics.density).coerceAtLeast(120f).dp).verticalScroll(rememberScrollState())) {
                        VolumePanel(appearance, panelVolumes.value, ::setPanelVolume, ::toggleMediaMute,
                            onClose = { closeExpandedQuickPanel(animate = true) },
                            onStudio = {
                                // Keep the visible user-interaction window alive through the launch.
                                // Dispose it after the foreground hand-off has been requested.
                                startActivity(Intent(this@FloatingVolumeService, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("open_tab", 1))
                                closeExpandedQuickPanel()
                            }, onNative = { closeExpandedQuickPanel(); openNativeSystemVolumeUI() },
                            onScreenshot = ::performStealthScreenshot,
                            onLock = { prefs.isPositionLocked = !prefs.isPositionLocked; panelAppearance.value = Appearance.from(prefs) },
                            onReset = { resetBubbleToDefaultPosition(); updatePanelPosition() },
                            onSize = { prefs.buttonSizeDp = it; applyDynamicPreferences() },
                            onFinish = { prefs.bgStyle = it; applyDynamicPreferences() },
                            onDrag = ::movePanel, onDragEnd = {
                                clampAndSavePosition()
                                if (prefs.isEdgeSnapEnabled && !prefs.isPositionLocked) snapToNearestHorizontalEdge()
                                schedulePanelHide()
                            })
                    }
                }
            }
        }
        try {
            windowManager.addView(host, panelParams)
            host.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
                if (bottom != oldBottom) updatePanelPosition()
            }
            floatingBubbleView?.visibility = View.INVISIBLE
            if (DesignTokens.motionAllowed(prefs)) {
                host.scaleX = 0.9f; host.scaleY = 0.9f; host.alpha = 0f
                panelSprings += SpringAnimation(host, SpringAnimation.SCALE_X, 1f).apply {
                    spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY; start()
                }
                panelSprings += SpringAnimation(host, SpringAnimation.SCALE_Y, 1f).apply {
                    spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY; start()
                }
                host.animate().alpha(1f).setDuration(if (DesignTokens.motionAllowed(prefs)) 160 else 0).start()
            }
            schedulePanelHide()
        } catch (e: Exception) {
            host.dispose(); expandedPanelView = null; panelParams = null
            Log.e(TAG, "Cannot attach volume panel", e)
        }
    }

    private fun setPanelVolume(stream: Int, value: Int) {
        try { audioManager.setStreamVolume(stream, value.coerceIn(0, audioManager.getStreamMaxVolume(stream)), 0) }
        catch (_: SecurityException) { Toast.makeText(this, "Do Not Disturb prevents this volume change", Toast.LENGTH_SHORT).show() }
        panelVolumes.value = readVolumes(this)
        updateBubbleVisuals()
        schedulePanelHide()
    }

    private fun refreshQuickHubSliders() {
        if (expandedPanelView != null) panelVolumes.value = readVolumes(this)
    }

    private fun movePanel(dx: Float, dy: Float) {
        if (prefs.isPositionLocked || panelClosing) return
        edgeSpring?.cancel()
        val screen = getScreenDimensions()
        bubbleParams.x = (bubbleParams.x + dx.toInt()).coerceIn(0, max(0, screen.x - bubbleParams.width))
        bubbleParams.y = (bubbleParams.y + dy.toInt()).coerceIn(dpToPx(36), max(dpToPx(36), screen.y - bubbleParams.height - dpToPx(36)))
        val params = panelParams ?: return
        params.x = (params.x + dx.toInt()).coerceIn(dpToPx(12), max(dpToPx(12), screen.x - params.width - dpToPx(12)))
        params.y = (params.y + dy.toInt()).coerceIn(dpToPx(36), max(dpToPx(36), screen.y - (expandedPanelView?.height ?: 0) - dpToPx(36)))
        try {
            expandedPanelView?.let { windowManager.updateViewLayout(it, params) }
            floatingBubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) }
        } catch (e: IllegalArgumentException) { Log.w(TAG, "Panel detached during drag", e) }
    }

    private fun updatePanelPosition(attach: Boolean = true) {
        val params = panelParams ?: return
        val screen = getScreenDimensions()
        params.x = if (bubbleParams.x + bubbleParams.width / 2 < screen.x / 2) dpToPx(12)
            else max(dpToPx(12), screen.x - params.width - dpToPx(12))
        val panelHeight = (expandedPanelView?.height ?: 0).takeIf { it > 0 } ?: dpToPx(340)
        params.y = (bubbleParams.y - dpToPx(72)).coerceIn(dpToPx(36), max(dpToPx(36), screen.y - panelHeight - dpToPx(36)))
        if (attach) try { expandedPanelView?.let { windowManager.updateViewLayout(it, params) } }
        catch (e: IllegalArgumentException) { Log.w(TAG, "Panel detached while aligning", e) }
    }

    private fun schedulePanelHide() {
        mainHandler.removeCallbacks(hidePanel)
        if (!isDestroyed && !panelTouched && expandedPanelView != null && prefs.panelAutoHideSeconds > 0)
            mainHandler.postDelayed(hidePanel, prefs.panelAutoHideSeconds * 1000L)
    }

    private fun closeExpandedQuickPanel(animate: Boolean = false) {
        mainHandler.removeCallbacks(hidePanel)
        val panel = expandedPanelView as? OverlayPanelHost ?: return
        if (panelClosing && animate) return
        panelClosing = true
        panelSprings.forEach { it.cancel() }
        panelSprings.clear()
        panel.animate().cancel()
        val remove = {
            panelSprings.forEach { it.cancel() }
            panelSprings.clear()
            panel.dispose()
            try { if (panel.isAttachedToWindow) windowManager.removeView(panel) }
            catch (e: IllegalArgumentException) { Log.w(TAG, "Panel already removed", e) }
            if (expandedPanelView === panel) { expandedPanelView = null; panelParams = null }
            panelClosing = false
            if (!isDestroyed && !isTakingScreenshot) {
                floatingBubbleView?.visibility = View.VISIBLE
                wakeUpBubbleOpacity()
                scheduleAutoDim()
            }
        }
        if (animate && !isDestroyed && DesignTokens.motionAllowed(prefs)) {
            panelSprings += SpringAnimation(panel, SpringAnimation.SCALE_X, 0.92f).apply {
                spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY; start()
            }
            panelSprings += SpringAnimation(panel, SpringAnimation.SCALE_Y, 0.92f).apply {
                spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY; start()
            }
            panel.animate().alpha(0f).setDuration(160).withEndAction { remove() }.start()
        } else remove()
    }

    private fun snapToNearestHorizontalEdge() {
        val screen = getScreenDimensions()
        val screenWidth = screen.x
        val targetX = if (bubbleParams.x + (bubbleParams.width / 2) < screenWidth / 2) {
            0
        } else {
            max(0, screenWidth - bubbleParams.width)
        }

        edgeSpring?.cancel()
        positionAnimator?.cancel()
        val update: (Float) -> Unit = { position ->
            bubbleParams.x = position.toInt()
            try { floatingBubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) } }
            catch (e: IllegalArgumentException) { Log.w(TAG, "Bubble detached while snapping", e) }
            updatePanelPosition()
        }
        if (DesignTokens.motionAllowed(prefs)) {
            edgeSpring = SpringAnimation(FloatValueHolder(bubbleParams.x.toFloat())).apply {
                spring = SpringForce(targetX.toFloat()).apply {
                    dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                    stiffness = SpringForce.STIFFNESS_MEDIUM
                }
                addUpdateListener { _, value, _ -> update(value) }
                start()
            }
        } else update(targetX.toFloat())
        prefs.savedX = targetX
        prefs.savedY = bubbleParams.y
    }

    private fun applyDynamicPreferences() {
        panelAppearance.value = Appearance.from(prefs)
        if (!DesignTokens.motionAllowed(prefs)) {
            edgeSpring?.cancel()
            positionAnimator?.cancel()
        }
        panelParams?.let {
            it.width = minOf(dpToPx(if (prefs.sliderOrientation == "vertical") 240 else 340), getScreenDimensions().x - dpToPx(24)).coerceAtLeast(1)
            updatePanelPosition()
        }
        schedulePanelHide()
        val view = floatingBubbleView ?: return
        val dimensions = BubbleRenderer.getBubbleDimensionsPx(this, prefs)
        val newW = max(dpToPx(48), dimensions.first)
        val newH = max(dpToPx(48), dimensions.second)
        bubbleParams.width = newW
        bubbleParams.height = newH

        BubbleRenderer.renderBubble(this, view, prefs, getVolumeLevel(), isMoving = isCurrentlyMoving)

        val screen = getScreenDimensions()
        bubbleParams.x = bubbleParams.x.coerceIn(0, max(0, screen.x - newW))
        val safeTop = dpToPx(36)
        val safeBottom = max(safeTop, screen.y - newH - dpToPx(36))
        bubbleParams.y = bubbleParams.y.coerceIn(safeTop, safeBottom)

        try {
            windowManager.updateViewLayout(view, bubbleParams)
        } catch (e: Exception) {
            Log.w(TAG, "applyDynamicPreferences updateViewLayout failed: ${e.message}")
        }

        wakeUpBubbleOpacity()
        scheduleAutoDim()
    }

    private fun updateBubbleVisuals() {
        val view = floatingBubbleView ?: return
        BubbleRenderer.renderBubble(this, view, prefs, getVolumeLevel(), isMoving = isCurrentlyMoving)
    }

    private fun wakeUpBubbleOpacity() {
        mainHandler.removeCallbacks(autoDimRunnable)
        floatingBubbleView?.animate()
            ?.alpha(prefs.activeOpacityPercent / 100f)
            ?.setDuration(if (DesignTokens.motionAllowed(prefs)) 120 else 0)
            ?.start()
    }

    private fun scheduleAutoDim() {
        mainHandler.removeCallbacks(autoDimRunnable)
        if (!isDestroyed && prefs.isAutoDimEnabled) {
            mainHandler.postDelayed(autoDimRunnable, AUTO_DIM_DELAY_MS)
        }
    }

    private fun getVolumeLevel(): Int {
        return try {
            val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            when {
                vol == 0 -> 0
                vol <= maxVol / 2 -> 1
                else -> 2
            }
        } catch (e: Exception) {
            2
        }
    }

    private fun getScreenDimensions(): Point {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            Point(metrics.bounds.width(), metrics.bounds.height())
        } else {
            val dm = resources.displayMetrics
            Point(dm.widthPixels, dm.heightPixels)
        }
    }

    private fun triggerHaptic() {
        if (!prefs.isHapticEnabled) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.w(TAG, "Haptic trigger failed: ${e.message}")
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        edgeSpring?.cancel()
        isDestroyed = true
        isRunning = false
        positionAnimator?.cancel()
        floatingBubbleView?.animate()?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        volumeChangeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Unregister volume receiver failed: ${e.message}")
            }
            volumeChangeReceiver = null
        }
        closeExpandedQuickPanel()
        floatingBubbleView?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager.removeView(view)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error removing floatingBubbleView: ${e.message}")
            }
            floatingBubbleView = null
        }
        super.onDestroy()
    }
}

/** Keeps gesture handling and accessibility clicks on the same volume action. */
private class FloatingBubbleView(context: Context) : FrameLayout(context) {
    var gestureHandler: ((MotionEvent, () -> Unit) -> Boolean)? = null
    override fun onTouchEvent(event: MotionEvent): Boolean = gestureHandler?.invoke(event) { performClick() } ?: super.onTouchEvent(event)
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
