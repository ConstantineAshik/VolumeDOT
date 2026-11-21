// SPDX-License-Identifier: Apache-2.0
package com.ashik.volumedot

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.ashik.volumedot.ui.DesignTokens
import androidx.core.content.ContextCompat
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

data class BubbleTheme(val id: String, val label: String)

private data class BubbleColors(val accentColor: Int, val surfaceColor: Int, val softColor: Int, val iconTint: Int)

object BubbleRenderer {

    val THEMES: List<BubbleTheme> = listOf(
        BubbleTheme(PrefsManager.THEME_CYAN, "Cyan"),
        BubbleTheme(PrefsManager.THEME_VIOLET, "Violet"),
        BubbleTheme(PrefsManager.THEME_EMERALD, "Emerald"),
        BubbleTheme(PrefsManager.THEME_CORAL, "Coral"),
        BubbleTheme(PrefsManager.THEME_ROSE, "Rose"),
        BubbleTheme(PrefsManager.THEME_AMBER, "Gold"),
        BubbleTheme(PrefsManager.THEME_STEALTH, "Stealth"),
        BubbleTheme(PrefsManager.THEME_FROSTED, "Light")
    )

    val PRESET_EMOJIS: List<String> = listOf(
        "🎧", "🔊", "🎵", "🎚️", "🎛️", "🎙️",
        "⚡", "🔥", "✨", "💎", "🚀", "🎯",
        "🎮", "🌙", "🐾", "🔔", "💫", "🫧"
    )

    fun getTheme(id: String): BubbleTheme =
        THEMES.firstOrNull { it.id == id } ?: THEMES.first()

    /**
     * Returns width and height in pixels for the configured size and shape.
     */
    fun getBubbleDimensionsPx(context: Context, prefs: PrefsManager): Pair<Int, Int> {
        val basePx = dpToPx(context, prefs.buttonSizeDp)
        return when (prefs.buttonShape) {
            PrefsManager.SHAPE_PILL -> Pair((basePx * 1.22f).toInt(), (basePx * 0.84f).toInt())
            else -> Pair(basePx, basePx)
        }
    }

    /**
     * Renders the complete floating button into [host] matching all user preferences.
     * [volumeLevel]: 0 = muted, 1 = low/mid, 2 = high.
     */
    fun renderBubble(
        context: Context,
        host: FrameLayout,
        prefs: PrefsManager,
        volumeLevel: Int,
        isMoving: Boolean = false
    ) {
        val colors = DesignTokens.colors(context, prefs)
        val theme = BubbleColors(colors.primary.toArgb(), colors.primaryContainer.toArgb(),
            colors.surfaceContainerHighest.toArgb(), colors.onPrimaryContainer.toArgb())
        val (widthPx, heightPx) = getBubbleDimensionsPx(context, prefs)
        val minDim = min(widthPx, heightPx)
        val container = host.findViewWithTag<FrameLayout>("bubble_visual") ?: FrameLayout(context).apply {
            tag = "bubble_visual"
            host.removeAllViews()
            host.addView(this, FrameLayout.LayoutParams(widthPx, heightPx, Gravity.CENTER))
        }
        container.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx, Gravity.CENTER)
        host.background = null
        host.elevation = 0f
        host.clipChildren = false
        host.clipToPadding = false

        // 1. Background & Shape
        container.background = createBackgroundDrawable(context, prefs, theme, minDim, isMoving)
        container.elevation = if (prefs.bgStyle == PrefsManager.BG_TRANSPARENT && !isMoving) {
            0f
        } else {
            dpToPx(context, 2).toFloat()
        }

        // 2. Foreground Content (Vector or Emoji)
        val useText = prefs.iconMode == PrefsManager.ICON_DYNAMIC_EMOJI ||
                prefs.iconMode == PrefsManager.ICON_CUSTOM_EMOJI

        if (useText) {
            val textView = (container.findViewWithTag<TextView>("bubble_text")) ?: TextView(context).apply {
                tag = "bubble_text"
                gravity = Gravity.CENTER
                includeFontPadding = false
                container.removeAllViews()
                container.addView(
                    this,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
            val displayStr = when (prefs.iconMode) {
                PrefsManager.ICON_DYNAMIC_EMOJI -> when (volumeLevel) {
                    0 -> "🔇"
                    1 -> "🔉"
                    else -> "🔊"
                }
                else -> prefs.customEmoji
            }
            textView.text = displayStr
            val scaleFactor = if (prefs.buttonShape == PrefsManager.SHAPE_PILL) 0.36f else 0.40f
            val spSize = (prefs.buttonSizeDp * scaleFactor).coerceIn(13f, 38f)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize)
            textView.setTextColor(theme.iconTint)
        } else {
            val imageView = (container.findViewWithTag<ImageView>("bubble_vector")) ?: ImageView(context).apply {
                tag = "bubble_vector"
                scaleType = ImageView.ScaleType.FIT_CENTER
                container.removeAllViews()
                container.addView(
                    this,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
            val pad = (minDim * 0.22f).toInt().coerceAtLeast(dpToPx(context, 6))
            imageView.setPadding(pad, pad, pad, pad)
            val tint = if (prefs.bgStyle == PrefsManager.BG_TRANSPARENT || prefs.bgStyle == PrefsManager.BG_OUTLINE) {
                theme.accentColor
            } else {
                theme.iconTint
            }
            imageView.setImageDrawable(
                if (prefs.iconMode == PrefsManager.ICON_DYNAMIC_VECTOR)
                    ContextCompat.getDrawable(context, when (volumeLevel) { 0 -> R.drawable.sym_volume_off; 1 -> R.drawable.sym_volume_down; else -> R.drawable.sym_volume_up })?.mutate()?.apply { setTint(tint) }
                else VectorGlyphDrawable(
                    mode = prefs.iconMode,
                    volumeLevel = volumeLevel,
                    primaryColor = tint,
                    errorColor = colors.error.toArgb(),
                    neutralColor = colors.onSurfaceVariant.toArgb(),
                    accentColor = theme.accentColor
                )
            )
        }
    }

    private fun createBackgroundDrawable(
        context: Context,
        prefs: PrefsManager,
        theme: BubbleColors,
        minDimPx: Int,
        isMoving: Boolean
    ): Drawable? {
        if (prefs.bgStyle == PrefsManager.BG_TRANSPARENT && !isMoving) {
            return null
        }

        val gd = GradientDrawable()
        when (prefs.buttonShape) {
            PrefsManager.SHAPE_CIRCLE -> {
                gd.shape = GradientDrawable.OVAL
            }
            PrefsManager.SHAPE_SQUIRCLE -> {
                gd.shape = GradientDrawable.RECTANGLE
                gd.cornerRadius = minDimPx * 0.30f
            }
            PrefsManager.SHAPE_ROUNDED -> {
                gd.shape = GradientDrawable.RECTANGLE
                gd.cornerRadius = minDimPx * 0.16f
            }
            PrefsManager.SHAPE_PILL -> {
                gd.shape = GradientDrawable.RECTANGLE
                gd.cornerRadius = minDimPx * 0.50f
            }
            else -> {
                gd.shape = GradientDrawable.OVAL
            }
        }

        val strokePx = max(dpToPx(context, 1), (minDimPx / 26))

        if (isMoving) {
            gd.setColor(theme.surfaceColor)
            gd.setStroke(dpToPx(context, 3), theme.accentColor)
            return gd
        }

        when (prefs.bgStyle) {
            PrefsManager.BG_GLASS -> {
                gd.setColor(theme.surfaceColor)
                gd.setStroke(strokePx, theme.accentColor)
            }
            PrefsManager.BG_FROST -> {
                gd.setColor(theme.softColor)
                gd.setStroke(strokePx, Color.argb(180, Color.red(theme.accentColor), Color.green(theme.accentColor), Color.blue(theme.accentColor)))
            }
            PrefsManager.BG_OUTLINE -> {
                gd.setColor(Color.TRANSPARENT)
                gd.setStroke(max(dpToPx(context, 2), strokePx + 1), theme.accentColor)
            }
            else -> {
                gd.setColor(theme.surfaceColor)
                gd.setStroke(strokePx, theme.accentColor)
            }
        }
        return gd
    }

    private fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    /**
     * Crisp anti-aliased Canvas vector renderer for professional UI icons inside the floating button.
     */
    private class VectorGlyphDrawable(
        private val mode: String,
        private val volumeLevel: Int,
        private val primaryColor: Int,
        private val errorColor: Int,
        private val neutralColor: Int,
        private val accentColor: Int
    ) : Drawable() {

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = primaryColor
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = accentColor
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val size = min(w, h)
            if (size <= 0f) return

            val cx = b.centerX().toFloat()
            val cy = b.centerY().toFloat()
            val left = cx - size / 2f
            val top = cy - size / 2f

            when (mode) {
                PrefsManager.ICON_EQUALIZER -> drawEqualizer(canvas, left, top, size)
                PrefsManager.ICON_MINIMAL_DOT -> drawMinimalDot(canvas, cx, cy, size)
                PrefsManager.ICON_PLUS_MINUS -> drawPlusMinus(canvas, cx, cy, size)
                else -> drawDynamicSpeaker(canvas, left, top, size)
            }
        }

        private fun drawDynamicSpeaker(canvas: Canvas, left: Float, top: Float, s: Float) {
            fillPaint.color = primaryColor
            val path = Path().apply {
                // Speaker body & cone
                moveTo(left + s * 0.14f, top + s * 0.38f)
                lineTo(left + s * 0.32f, top + s * 0.38f)
                lineTo(left + s * 0.54f, top + s * 0.18f)
                lineTo(left + s * 0.54f, top + s * 0.82f)
                lineTo(left + s * 0.32f, top + s * 0.62f)
                lineTo(left + s * 0.14f, top + s * 0.62f)
                close()
            }
            canvas.drawPath(path, fillPaint)

            strokePaint.strokeWidth = max(2f, s * 0.09f)

            if (volumeLevel == 0) {
                // Mute 'X' in coral/red
                strokePaint.color = errorColor
                canvas.drawLine(left + s * 0.66f, top + s * 0.36f, left + s * 0.88f, top + s * 0.64f, strokePaint)
                canvas.drawLine(left + s * 0.88f, top + s * 0.36f, left + s * 0.66f, top + s * 0.64f, strokePaint)
            } else {
                // Inner sound wave
                strokePaint.color = accentColor
                val innerArc = RectF(left + s * 0.42f, top + s * 0.30f, left + s * 0.74f, top + s * 0.70f)
                canvas.drawArc(innerArc, -48f, 96f, false, strokePaint)

                if (volumeLevel >= 2) {
                    // Outer sound wave
                    val outerArc = RectF(left + s * 0.46f, top + s * 0.16f, left + s * 0.90f, top + s * 0.84f)
                    canvas.drawArc(outerArc, -48f, 96f, false, strokePaint)
                }
            }
        }

        private fun drawEqualizer(canvas: Canvas, left: Float, top: Float, s: Float) {
            val barW = s * 0.16f
            val r = barW / 2f
            val heights = when (volumeLevel) {
                0 -> floatArrayOf(0.18f, 0.18f, 0.18f)
                1 -> floatArrayOf(0.36f, 0.58f, 0.30f)
                else -> floatArrayOf(0.54f, 0.84f, 0.66f)
            }
            val xs = floatArrayOf(0.20f, 0.42f, 0.64f)
            val cy = top + s * 0.5f

            for (i in 0..2) {
                fillPaint.color = if (i == 1) accentColor else primaryColor
                val bh = s * heights[i]
                val rect = RectF(left + s * xs[i], cy - bh / 2f, left + s * xs[i] + barW, cy + bh / 2f)
                canvas.drawRoundRect(rect, r, r, fillPaint)
            }
        }

        private fun drawMinimalDot(canvas: Canvas, cx: Float, cy: Float, s: Float) {
            strokePaint.color = accentColor
            strokePaint.strokeWidth = max(2f, s * 0.08f)
            canvas.drawCircle(cx, cy, s * 0.38f, strokePaint)

            fillPaint.color = if (volumeLevel == 0) neutralColor else primaryColor
            val innerR = when (volumeLevel) {
                0 -> s * 0.12f
                1 -> s * 0.18f
                else -> s * 0.23f
            }
            canvas.drawCircle(cx, cy, innerR, fillPaint)
        }

        private fun drawPlusMinus(canvas: Canvas, cx: Float, cy: Float, s: Float) {
            strokePaint.color = primaryColor
            strokePaint.strokeWidth = max(2.2f, s * 0.10f)
            val halfSpan = s * 0.24f
            // Plus (top)
            val plusY = cy - s * 0.15f
            canvas.drawLine(cx - halfSpan, plusY, cx + halfSpan, plusY, strokePaint)
            canvas.drawLine(cx, plusY - halfSpan * 0.85f, cx, plusY + halfSpan * 0.85f, strokePaint)
            // Minus (bottom)
            strokePaint.color = accentColor
            val minusY = cy + s * 0.24f
            canvas.drawLine(cx - halfSpan, minusY, cx + halfSpan, minusY, strokePaint)
        }

        override fun setAlpha(alpha: Int) {
            fillPaint.alpha = alpha
            strokePaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fillPaint.colorFilter = colorFilter
            strokePaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
