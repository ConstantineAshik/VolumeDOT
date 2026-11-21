// SPDX-License-Identifier: Apache-2.0
package com.ashik.volumedot

import android.content.Context
import androidx.core.content.edit
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences =
        // Keep the existing on-device store so the rebrand preserves installed users' settings.
        context.getSharedPreferences("floatvol_prefs", Context.MODE_PRIVATE)

    companion object {
        const val ICON_DYNAMIC_VECTOR = "dynamic_vector"
        const val ICON_DYNAMIC_EMOJI = "dynamic_emoji"
        const val ICON_CUSTOM_EMOJI = "custom_emoji"
        const val ICON_EQUALIZER = "equalizer"
        const val ICON_MINIMAL_DOT = "minimal_dot"
        const val ICON_PLUS_MINUS = "plus_minus"

        const val SHAPE_CIRCLE = "circle"
        const val SHAPE_SQUIRCLE = "squircle"
        const val SHAPE_ROUNDED = "rounded"
        const val SHAPE_PILL = "pill"

        const val BG_GLASS = "glass"
        const val BG_FROST = "frost"
        const val BG_OUTLINE = "outline"
        const val BG_TRANSPARENT = "transparent"

        const val THEME_CYAN = "cyan"
        const val THEME_VIOLET = "violet"
        const val THEME_EMERALD = "emerald"
        const val THEME_CORAL = "coral"
        const val THEME_ROSE = "rose"
        const val THEME_AMBER = "amber"
        const val THEME_STEALTH = "stealth"
        const val THEME_FROSTED = "frosted"
    }

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean("service_enabled", false)
        set(value) = prefs.edit { putBoolean("service_enabled", value) }

    var isPositionLocked: Boolean
        get() = prefs.getBoolean("position_locked", false)
        set(value) = prefs.edit { putBoolean("position_locked", value) }

    var isEdgeSnapEnabled: Boolean
        get() = prefs.getBoolean("edge_snap", true)
        set(value) = prefs.edit { putBoolean("edge_snap", value) }

    var isAutoDimEnabled: Boolean
        get() = prefs.getBoolean("auto_dim", true)
        set(value) = prefs.edit { putBoolean("auto_dim", value) }

    var isSwipeVolumeEnabled: Boolean
        get() = prefs.getBoolean("swipe_volume", true)
        set(value) = prefs.edit { putBoolean("swipe_volume", value) }

    var isHapticEnabled: Boolean
        get() = prefs.getBoolean("haptic_enabled", true)
        set(value) = prefs.edit { putBoolean("haptic_enabled", value) }

    var isDoubleTapScreenshot: Boolean
        get() = prefs.getBoolean("double_tap_screenshot", true)
        set(value) = prefs.edit { putBoolean("double_tap_screenshot", value) }

    var isHoldToMoveOnly: Boolean
        get() = prefs.getBoolean("hold_to_move_only", false)
        set(value) = prefs.edit { putBoolean("hold_to_move_only", value) }

    var buttonSizeDp: Int
        get() = prefs.getInt("button_size_dp", 56).coerceIn(32, 96)
        set(value) = prefs.edit { putInt("button_size_dp", value.coerceIn(32, 96)) }

    var activeOpacityPercent: Int
        get() = prefs.getInt("active_opacity", 92).coerceIn(15, 100)
        set(value) = prefs.edit { putInt("active_opacity", value.coerceIn(15, 100)) }

    var idleOpacityPercent: Int
        get() = prefs.getInt("idle_opacity", 35).coerceIn(10, 100)
        set(value) = prefs.edit { putInt("idle_opacity", value.coerceIn(10, 100)) }

    var iconMode: String
        get() = prefs.getString("icon_mode", ICON_DYNAMIC_VECTOR) ?: ICON_DYNAMIC_VECTOR
        set(value) = prefs.edit { putString("icon_mode", value) }

    var customEmoji: String
        get() = prefs.getString("custom_emoji", "🎧")?.takeIf { it.isNotBlank() } ?: "🎧"
        set(value) = prefs.edit { putString("custom_emoji", value.trim().ifEmpty { "🎧" }) }

    var buttonShape: String
        get() = prefs.getString("button_shape", SHAPE_CIRCLE) ?: SHAPE_CIRCLE
        set(value) = prefs.edit { putString("button_shape", value) }

    var colorTheme: String
        get() = prefs.getString("color_theme", THEME_CYAN) ?: THEME_CYAN
        set(value) = prefs.edit { putString("color_theme", value) }

    var bgStyle: String
        get() {
            val stored = prefs.getString("bg_style", null)
            if (stored != null) return stored
            return if (prefs.getBoolean("transparent_bg", false)) BG_TRANSPARENT else BG_GLASS
        }
        set(value) {
            prefs.edit {
                putString("bg_style", value)
                putBoolean("transparent_bg", value == BG_TRANSPARENT)
            }
        }

    var isTransparentBg: Boolean
        get() = bgStyle == BG_TRANSPARENT
        set(value) {
            bgStyle = if (value) BG_TRANSPARENT else BG_GLASS
        }

    var savedX: Int
        get() = prefs.getInt("saved_x", 0)
        set(value) = prefs.edit { putInt("saved_x", value) }

    var savedY: Int
        get() = prefs.getInt("saved_y", 300)
        set(value) = prefs.edit { putInt("saved_y", value) }

    var themeMode: String
        get() = prefs.getString("theme_mode", "system") ?: "system"
        set(value) = prefs.edit { putString("theme_mode", value) }

    var dynamicColors: Boolean
        get() = prefs.getBoolean("dynamic_colors", true)
        set(value) = prefs.edit { putBoolean("dynamic_colors", value) }

    var panelOpacityPercent: Int
        get() = prefs.getInt("panel_opacity", 98).coerceIn(70, 100)
        set(value) = prefs.edit { putInt("panel_opacity", value.coerceIn(70, 100)) }

    var cornerRadiusDp: Int
        get() = prefs.getInt("corner_radius", 28).coerceIn(16, 48)
        set(value) = prefs.edit { putInt("corner_radius", value.coerceIn(16, 48)) }

    var animationsEnabled: Boolean
        get() = prefs.getBoolean("animations_enabled", true)
        set(value) = prefs.edit { putBoolean("animations_enabled", value) }

    var panelAutoHideSeconds: Int
        get() = prefs.getInt("panel_auto_hide", 8).coerceIn(0, 30)
        set(value) = prefs.edit { putInt("panel_auto_hide", value.coerceIn(0, 30)) }

    var sliderOrientation: String
        get() = prefs.getString("slider_orientation", "horizontal") ?: "horizontal"
        set(value) = prefs.edit { putString("slider_orientation", value) }

    var tapAction: String
        get() = prefs.getString("tap_action", "panel") ?: "panel"
        set(value) = prefs.edit { putString("tap_action", value) }

    fun observe(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun removeObserver(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    fun resetAppearanceDefaults() {
        buttonSizeDp = 56
        activeOpacityPercent = 92
        idleOpacityPercent = 35
        iconMode = ICON_DYNAMIC_VECTOR
        customEmoji = "🎧"
        buttonShape = SHAPE_CIRCLE
        colorTheme = THEME_CYAN
        bgStyle = BG_GLASS
        panelOpacityPercent = 98
        cornerRadiusDp = 28
        sliderOrientation = "horizontal"
    }
}
