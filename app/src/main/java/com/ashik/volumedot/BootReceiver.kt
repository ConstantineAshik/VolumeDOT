// SPDX-License-Identifier: Apache-2.0
package com.ashik.volumedot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            try {
                val prefs = PrefsManager(context)
                if (prefs.isServiceEnabled && Settings.canDrawOverlays(context)) {
                    val serviceIntent = Intent(context, FloatingVolumeService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to start FloatingVolumeService on boot: ${e.message}", e)
            }
        }
    }
}
