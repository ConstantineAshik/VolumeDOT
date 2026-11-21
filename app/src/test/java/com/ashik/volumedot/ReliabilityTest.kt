// SPDX-License-Identifier: Apache-2.0
package com.ashik.volumedot

import android.content.Context
import android.content.Intent
import android.app.Service
import android.os.Looper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34, 36, 37])
class ReliabilityTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun clearPreferences() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(false)
        context.getSharedPreferences("floatvol_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        // API 26–32 use Core's merged signature permission to emulate non-exported receivers.
        shadowOf(context).grantPermissions("${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    }

    @Test fun appearanceValuesAreClampedAndResetKeepsServiceIntent() {
        val prefs = PrefsManager(context)
        prefs.isServiceEnabled = true
        prefs.buttonSizeDp = 200
        prefs.activeOpacityPercent = -10
        prefs.idleOpacityPercent = 200
        assertEquals(96, prefs.buttonSizeDp)
        assertEquals(15, prefs.activeOpacityPercent)
        assertEquals(100, prefs.idleOpacityPercent)
        prefs.resetAppearanceDefaults()
        assertEquals(56, prefs.buttonSizeDp)
        assertTrue(prefs.isServiceEnabled)
    }

    @Test fun disabledStickyRestartDoesNotCreateOverlay() {

        val controller = Robolectric.buildService(FloatingVolumeService::class.java).create()
        val service = controller.get()
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 1))
        assertFalse(FloatingVolumeService.isRunning)
        controller.destroy()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(FloatingVolumeService.isRunning)
    }

    @Test fun enabledServiceSurvivesStickyRestartAndStopsExplicitly() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        PrefsManager(context).isServiceEnabled = true
        val controller = Robolectric.buildService(FloatingVolumeService::class.java).create()
        val service = controller.get()
        assertEquals(Service.START_STICKY, service.onStartCommand(null, 0, 1))
        assertTrue(FloatingVolumeService.isRunning)
        assertEquals(Service.START_STICKY, service.onStartCommand(
            Intent(context, FloatingVolumeService::class.java).setAction(FloatingVolumeService.ACTION_REFRESH_PREFS), 0, 2))
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(
            Intent(context, FloatingVolumeService::class.java).setAction(FloatingVolumeService.ACTION_STOP_SERVICE), 0, 3))
        assertFalse(PrefsManager(context).isServiceEnabled)
        controller.destroy()
        assertFalse(FloatingVolumeService.isRunning)
    }

    @Test @Config(qualifiers = "+night") fun screensAlsoBuildInDarkMode() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get().window.decorView)
        controller.pause().stop().destroy()
    }

    @Test fun formattedDisplayValuesAreValid() {
        assertEquals("56dp", context.getString(R.string.ui_size_dp, 56))
        assertEquals("92%", context.getString(R.string.ui_percent, 92))
        assertEquals("56dp", context.getString(R.string.ui_value_unit, 56, "dp"))
        assertEquals("Live Preview • 56dp • Violet • Glass",
            context.getString(R.string.ui_preview_format, 56, "Violet", "Glass"))
    }

    @Test fun newAppearanceSettingsPersistWithSafeBounds() {
        val prefs = PrefsManager(context)
        prefs.themeMode = "dark"
        prefs.dynamicColors = false
        prefs.sliderOrientation = "vertical"
        prefs.panelOpacityPercent = 0
        prefs.cornerRadiusDp = 200
        prefs.panelAutoHideSeconds = 90
        val restored = PrefsManager(context)
        assertEquals("dark", restored.themeMode)
        assertFalse(restored.dynamicColors)
        assertEquals("vertical", restored.sliderOrientation)
        assertEquals(70, restored.panelOpacityPercent)
        assertEquals(48, restored.cornerRadiusDp)
        assertEquals(30, restored.panelAutoHideSeconds)
    }

    @Test fun fallbackPalettesKeepReadableContrast() {
        val p = PrefsManager(context)
        p.dynamicColors = false
        for (mode in listOf("light", "dark")) {
            p.themeMode = mode
            for (accent in com.ashik.volumedot.ui.DesignTokens.accents.keys) {
                p.colorTheme = accent
                val colors = com.ashik.volumedot.ui.DesignTokens.colors(context, p)
                fun rgb(color: androidx.compose.ui.graphics.Color) = (color.alpha * 255).toInt().shl(24) or
                    (color.red * 255).toInt().shl(16) or (color.green * 255).toInt().shl(8) or (color.blue * 255).toInt()
                assertTrue("$mode $accent primary contrast", androidx.core.graphics.ColorUtils.calculateContrast(rgb(colors.onPrimary), rgb(colors.primary)) >= 4.5)
                assertTrue("$mode surface contrast", androidx.core.graphics.ColorUtils.calculateContrast(rgb(colors.onSurface), rgb(colors.surface)) >= 4.5)
            }
        }
    }

    @Test fun panelOwnsItsLifecycleAndAutoHides() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        PrefsManager(context).apply { isServiceEnabled = true; animationsEnabled = false; panelAutoHideSeconds = 1 }
        val controller = Robolectric.buildService(FloatingVolumeService::class.java).create()
        try {
            val service = controller.get()
            service.onStartCommand(null, 0, 1)
            FloatingVolumeService::class.java.getDeclaredMethod("toggleExpandedQuickPanel").apply { isAccessible = true }.invoke(service)
            val panel = field(service, "expandedPanelView") as com.ashik.volumedot.ui.OverlayPanelHost
            assertEquals(androidx.lifecycle.Lifecycle.State.RESUMED, panel.lifecycle.currentState)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1200))
            assertNull(field(service, "expandedPanelView"))
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, panel.lifecycle.currentState)
        } finally { controller.destroy() }
    }

    @Test fun smallBubbleKeepsAccessibleTouchWindow() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        PrefsManager(context).apply { isServiceEnabled = true; buttonSizeDp = 32 }
        val controller = Robolectric.buildService(FloatingVolumeService::class.java).create()
        try {
            controller.get().onStartCommand(null, 0, 1)
            val params = field(controller.get(), "bubbleParams") as android.view.WindowManager.LayoutParams
            val minTouch = (48 * context.resources.displayMetrics.density).toInt()
            assertTrue(params.width >= minTouch)
            assertTrue(params.height >= minTouch)
        } finally { controller.destroy() }
    }

    @Test fun cancelledHoldDoesNotOpenQuickPanel() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        PrefsManager(context).isServiceEnabled = true
        val controller = Robolectric.buildService(FloatingVolumeService::class.java).create()
        try {
            val service = controller.get()
            service.onStartCommand(null, 0, 1)
            val bubble = field(service, "floatingBubbleView") as android.view.View
            touch(bubble, android.view.MotionEvent.ACTION_DOWN, 100f, 200f)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
            touch(bubble, android.view.MotionEvent.ACTION_CANCEL, 100f, 200f)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
            assertNull(field(service, "expandedPanelView"))
        } finally { controller.destroy() }
    }

    @Test fun positionLockAlsoPreventsSnappingAfterHold() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        PrefsManager(context).apply {
            isServiceEnabled = true
            isPositionLocked = true
            savedX = 100
        }
        val controller = Robolectric.buildService(FloatingVolumeService::class.java).create()
        try {
            val service = controller.get()
            service.onStartCommand(null, 0, 1)
            val bubble = field(service, "floatingBubbleView") as android.view.View
            val params = field(service, "bubbleParams") as android.view.WindowManager.LayoutParams
            val originalX = params.x
            val originalY = params.y
            touch(bubble, android.view.MotionEvent.ACTION_DOWN, 100f, 200f)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
            touch(bubble, android.view.MotionEvent.ACTION_MOVE, 200f, 300f)
            touch(bubble, android.view.MotionEvent.ACTION_UP, 200f, 300f)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
            assertEquals(originalX, params.x)
            assertEquals(originalY, params.y)
        } finally { controller.destroy() }
    }

    private fun field(service: FloatingVolumeService, name: String): Any? =
        FloatingVolumeService::class.java.getDeclaredField(name).apply { isAccessible = true }.get(service)

    private fun touch(view: android.view.View, action: Int, x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        val event = android.view.MotionEvent.obtain(now, now, action, x, y, 0)
        try { view.dispatchTouchEvent(event) } finally { event.recycle() }
    }

    @Test fun allScreensBuildAndRestoreSelectedTab() {
        val controller = Robolectric.buildActivity(MainActivity::class.java,
            Intent(context, MainActivity::class.java).putExtra("open_tab", 1)).setup()
        val state = android.os.Bundle()
        controller.saveInstanceState(state)
        assertEquals(1, state.getInt("selected_tab"))
        controller.pause().stop().destroy()
    }
}
