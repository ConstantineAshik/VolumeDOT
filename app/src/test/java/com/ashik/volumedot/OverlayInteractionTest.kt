// SPDX-License-Identifier: Apache-2.0
package com.ashik.volumedot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.ashik.volumedot.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OverlayInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun content(content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.setContent(content = content) }
    }

    @Test fun sliderChangesVolumeWithoutMovingWindowAndHeaderDrags() {
        val prefs = PrefsManager(RuntimeEnvironment.getApplication()).apply { animationsEnabled = false }
        val appearance = Appearance.from(prefs)
        var value by mutableStateOf(5)
        var drags = 0
        var changes = 0
        var closes = 0
        var tappedValue = 0
        content {
            VolumeDotTheme(appearance) {
                Box(Modifier.width(340.dp)) {
                    VolumePanel(appearance, listOf(StreamVolume(android.media.AudioManager.STREAM_MUSIC, "Media", R.drawable.sym_volume_up, value, 15)),
                        onVolume = { _, volume -> changes++; value = volume }, onMute = {}, onClose = { closes++ }, onStudio = {},
                        onNative = {}, onScreenshot = {}, onLock = {}, onReset = {},
                        onDrag = { _, _ -> drags++ }, onDragEnd = {}, onSize = {}, onFinish = {})
                }
            }
        }
        compose.onNodeWithContentDescription("Close volume panel").performTouchInput { click() }
        compose.runOnIdle { assertEquals("Touch input reaches buttons", 1, closes) }
        compose.onNodeWithContentDescription("Media volume").performTouchInput { click(center) }
        compose.runOnIdle { assertTrue("Slider tap callback count: $changes", changes > 0); tappedValue = value }
        compose.onNodeWithContentDescription("Media volume").performTouchInput {
            down(center)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Media volume").performTouchInput {
            moveTo(centerRight - androidx.compose.ui.geometry.Offset(24f, 0f), 300)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Media volume").performTouchInput {
            up()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertTrue("Dragging increases volume: $tappedValue -> $value", value > tappedValue); assertEquals("Slider must not drag window", 0, drags) }
        compose.onNodeWithContentDescription("Drag controller").performTouchInput { swipeRight() }
        compose.runOnIdle { assertTrue("Header drags window", drags > 0) }
    }

    @Test fun quickSettingsAndStreamSelectionAreReachable() {
        val prefs = PrefsManager(RuntimeEnvironment.getApplication()).apply { animationsEnabled = false }
        var appearance by mutableStateOf(Appearance.from(prefs))
        content {
            VolumeDotTheme(appearance) {
                Box(Modifier.width(340.dp)) {
                    VolumePanel(appearance, readVolumes(RuntimeEnvironment.getApplication()),
                        onVolume = { _, _ -> }, onMute = {}, onClose = {}, onStudio = {}, onNative = {},
                        onScreenshot = {}, onLock = {}, onReset = {}, onDrag = { _, _ -> }, onDragEnd = {},
                        onSize = { prefs.buttonSizeDp = it; appearance = Appearance.from(prefs) },
                        onFinish = { prefs.bgStyle = it; appearance = Appearance.from(prefs) })
                }
            }
        }
        compose.onNodeWithContentDescription("More volume controls").performClick()
        compose.onNodeWithText("Ring").performClick()
        compose.onNodeWithContentDescription("Ring volume").assertExists()
        compose.onNodeWithText("L").performClick()
        compose.runOnIdle { assertEquals(70, prefs.buttonSizeDp) }
        compose.onNodeWithText("Outline").performClick()
        compose.runOnIdle { assertEquals(PrefsManager.BG_OUTLINE, prefs.bgStyle) }
    }
}
