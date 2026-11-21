// SPDX-License-Identifier: Apache-2.0
package com.ashik.volumedot

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

open class VolumeDotAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VolumeDotAccService"

        @Volatile
        var instance: VolumeDotAccessibilityService? = null
            private set

        /**
         * Triggers Android's built-in system screenshot action (Android 9.0 / API 28+).
         */
        fun triggerSystemScreenshot(): Boolean {
            val service = instance
            if (service == null) {
                Log.w(TAG, "Accessibility service instance is null")
                return false
            }
            return try {
                service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            } catch (e: Exception) {
                Log.e(TAG, "performGlobalAction failed: ${e.message}", e)
                false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Zero event processing for 0% background CPU and battery impact
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) {
            instance = null
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }
}
