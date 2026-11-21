// SPDX-License-Identifier: Apache-2.0
package com.ashik.volumedot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ComposeRedesignTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun exportStoreIcon() {
        val drawable = requireNotNull(compose.activity.getDrawable(R.mipmap.ic_launcher))
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 512, 512)
        drawable.draw(Canvas(bitmap))
        assertTrue(bitmap.getPixel(256, 256) != 0)
        val output = File("build/outputs/fdroid/icon.png")
        requireNotNull(output.parentFile).mkdirs()
        output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun navigationThemesAndPreviewWork() {
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        capture("phone-home-light")
        compose.onNodeWithText("Appearance", useUnmergedTree = false).performClick()
        compose.onNodeWithTag("appearance_screen").assertIsDisplayed()
        capture("phone-appearance-light")
        compose.onNodeWithTag("appearance_screen").performScrollToNode(hasText("Dark"))
        compose.onNodeWithText("Dark").performClick()
        compose.runOnIdle { assertEquals("dark", PrefsManager(compose.activity).themeMode) }
        compose.runOnIdle {
            val bars = androidx.core.view.WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
            assertFalse("Manual dark theme uses light status icons", bars.isAppearanceLightStatusBars)
            assertFalse("Manual dark theme uses light navigation icons", bars.isAppearanceLightNavigationBars)
        }
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("Your sound.\nWithin reach.").assertIsDisplayed()
        capture("phone-home-dark")
        compose.onNodeWithContentDescription("Toggle floating controller preview").performClick()
        compose.onNodeWithTag("home_screen").performScrollToNode(hasTestTag("volume_panel"))
        compose.onNodeWithTag("volume_panel").assertIsDisplayed()
        capture("phone-panel-dark")
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
        compose.onNodeWithText("Lock position").performClick()
        compose.runOnIdle { assertTrue(PrefsManager(compose.activity).isPositionLocked) }
        capture("phone-settings-dark")
    }

    @Test @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun narrowPhoneStillReachesSettings() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("settings_screen").performScrollToNode(hasText("Ready for everyday use"))
        compose.onNodeWithText("Ready for everyday use").assertIsDisplayed()
        capture("compact-settings")
    }

    @Test @Config(qualifiers = "w1024dp-h900dp-mdpi")
    fun tabletUsesRailAndSideBySideCards() {
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        compose.onNodeWithText("Meet your controller").assertIsDisplayed()
        compose.onNodeWithText("Your sound.\nWithin reach.").assertIsDisplayed()
        capture("tablet-home")
    }

    @Test fun appearanceControlsPersistAndCanBeReset() {
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithTag("appearance_screen").performScrollToNode(hasText("Violet"))
        compose.onNodeWithText("Violet").performClick()
        compose.runOnIdle {
            assertEquals(PrefsManager.THEME_VIOLET, PrefsManager(compose.activity).colorTheme)
            assertFalse(PrefsManager(compose.activity).dynamicColors)
        }
        compose.onNodeWithTag("appearance_screen").performScrollToNode(hasText("Reset appearance"))
        compose.onNodeWithText("Reset appearance").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(PrefsManager.THEME_VIOLET, PrefsManager(compose.activity).colorTheme) }
    }

    @Test fun keyboardAndSystemBarInsetsRemainSafe() {
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        compose.runOnIdle {
            val root = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            val insets = androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(), androidx.core.graphics.Insets.of(0, 24, 0, 30))
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.of(0, 0, 0, 240))
                .build()
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root, insets)
        }
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "ar-rEG-ldrtl-w393dp-h851dp-mdpi") fun rtlLayoutKeepsNavigationReachable() {
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithTag("appearance_screen").assertIsDisplayed()
        assertTrue("RTL mirrors navigation", compose.onNodeWithText("Home").fetchSemanticsNode().boundsInRoot.left >
            compose.onNodeWithText("Settings").fetchSemanticsNode().boundsInRoot.left)
        capture("phone-rtl")
    }

    @Test fun verticalPreviewAndReducedMotionAreFunctional() {
        compose.runOnIdle {
            PrefsManager(compose.activity).apply { sliderOrientation = "vertical"; animationsEnabled = false }
        }
        compose.onNodeWithContentDescription("Toggle floating controller preview").performClick()
        compose.onNodeWithTag("home_screen").performScrollToNode(hasTestTag("volume_panel"))
        compose.onNodeWithContentDescription("Media volume").assertExists()
        capture("phone-vertical-panel")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val decor = compose.activity.window.decorView
            assertTrue("Rendered width", decor.width > 0)
            assertTrue("Rendered height", decor.height > 0)
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            val output = File("build/outputs/ui-previews/$name.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
