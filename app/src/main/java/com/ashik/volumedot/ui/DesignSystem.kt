// SPDX-License-Identifier: Apache-2.0
package com.ashik.volumedot.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashik.volumedot.PrefsManager
import com.google.android.material.color.MaterialColors

/** Shared by Compose screens, the panel, and the existing WindowManager bubble renderer. */
object DesignTokens {
    val compact = 4.dp
    val gap = 8.dp
    val spacing = 12.dp
    val inset = 16.dp
    val gutter = 20.dp
    val section = 28.dp
    val glyph = 24.dp
    val largeGlyph = 40.dp
    val swatch = 18.dp
    val buttonHeight = 56.dp
    val bubblePreviewHeight = 132.dp
    val studioPreviewHeight = 148.dp
    val verticalTrackHeight = 180.dp
    val verticalTrackWidth = 64.dp
    val wideBreakpoint = 840.dp
    val pairedCardsBreakpoint = 700.dp
    val compactBreakpoint = 360.dp
    val maxContent = 1080.dp
    val card = RoundedCornerShape(28.dp)
    val hero = RoundedCornerShape(36.dp)
    val pill = RoundedCornerShape(50)
    val minTouch = 48.dp
    val accents = linkedMapOf(
        PrefsManager.THEME_CYAN to Color(0xFF006A6A),
        PrefsManager.THEME_VIOLET to Color(0xFF65558F),
        PrefsManager.THEME_EMERALD to Color(0xFF386A20),
        PrefsManager.THEME_CORAL to Color(0xFF8C4A2F),
        PrefsManager.THEME_ROSE to Color(0xFF904A62),
        PrefsManager.THEME_AMBER to Color(0xFF795900),
        PrefsManager.THEME_STEALTH to Color(0xFF5D5E62),
        PrefsManager.THEME_FROSTED to Color(0xFF38618C)
    )
    val typography = Typography(
        displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-1).sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 38.sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
    )

    fun isDark(context: Context, mode: String): Boolean = when (mode) {
        "dark" -> true
        "light" -> false
        else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    fun colors(context: Context, mode: String, dynamic: Boolean, accent: String): ColorScheme {
        val dark = isDark(context, mode)
        if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        val roles = MaterialColors.getColorRoles((accents[accent] ?: accents.getValue(PrefsManager.THEME_CYAN)).toArgb(), !dark)
        val base = if (dark) darkColorScheme() else lightColorScheme()
        return base.copy(primary = Color(roles.accent), onPrimary = Color(roles.onAccent),
            primaryContainer = Color(roles.accentContainer), onPrimaryContainer = Color(roles.onAccentContainer))
    }

    fun colors(context: Context, prefs: PrefsManager): ColorScheme =
        colors(context, prefs.themeMode, prefs.dynamicColors, prefs.colorTheme)

    fun motionAllowed(prefs: PrefsManager): Boolean = prefs.animationsEnabled && ValueAnimator.areAnimatorsEnabled()

    fun <T> springSpec(enabled: Boolean): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) else snap()
}

@Immutable
data class Appearance(
    val mode: String, val dynamic: Boolean, val accent: String, val size: Int,
    val activeOpacity: Int, val idleOpacity: Int, val panelOpacity: Int,
    val radius: Int, val animations: Boolean, val orientation: String, val autoHide: Int,
    val icon: String, val emoji: String, val shape: String, val finish: String,
    val locked: Boolean, val snap: Boolean, val dim: Boolean, val swipe: Boolean,
    val haptic: Boolean, val screenshot: Boolean, val holdOnly: Boolean, val tap: String,
    val positionX: Int, val positionY: Int
) {
    companion object {
        fun from(p: PrefsManager) = Appearance(p.themeMode, p.dynamicColors, p.colorTheme, p.buttonSizeDp,
            p.activeOpacityPercent, p.idleOpacityPercent, p.panelOpacityPercent, p.cornerRadiusDp,
            p.animationsEnabled, p.sliderOrientation, p.panelAutoHideSeconds, p.iconMode, p.customEmoji,
            p.buttonShape, p.bgStyle, p.isPositionLocked, p.isEdgeSnapEnabled, p.isAutoDimEnabled,
            p.isSwipeVolumeEnabled, p.isHapticEnabled, p.isDoubleTapScreenshot, p.isHoldToMoveOnly, p.tapAction,
            p.savedX, p.savedY)
    }
}

val LocalMotionEnabled = staticCompositionLocalOf { true }

private object StillMotion : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

@Composable
fun VolumeDotTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val context = LocalContext.current
    LocalConfiguration.current // React to wallpaper/night-mode configuration updates.
    val colors = DesignTokens.colors(context, appearance.mode, appearance.dynamic, appearance.accent)
    val motion = appearance.animations && ValueAnimator.areAnimatorsEnabled()
    CompositionLocalProvider(LocalMotionEnabled provides motion) {
        MaterialExpressiveTheme(colorScheme = colors, typography = DesignTokens.typography,
            motionScheme = if (motion) MotionScheme.expressive() else StillMotion,
            shapes = Shapes(large = DesignTokens.card, extraLarge = DesignTokens.hero), content = content)
    }
}
