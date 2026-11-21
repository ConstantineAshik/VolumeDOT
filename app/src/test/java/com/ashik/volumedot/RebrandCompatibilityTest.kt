// SPDX-License-Identifier: Apache-2.0
package com.ashik.volumedot

import android.content.ComponentName
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class RebrandCompatibilityTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun appAndLauncherUseNewBrandWithStableInstallationIdentity() {
        val pm = context.packageManager
        assertEquals("com.ashik.floatvol", context.packageName)
        assertEquals("VolumeDot", pm.getApplicationLabel(context.applicationInfo).toString())
        assertEquals("VolumeDot", context.getString(R.string.app_name))
        val launch = pm.getLaunchIntentForPackage(context.packageName)
        assertNotNull("Existing launcher component remains available", launch)
        val alias = pm.getActivityInfo(ComponentName(context, "com.ashik.floatvol.MainActivity"), 0)
        assertEquals("com.ashik.volumedot.MainActivity", alias.targetActivity)
        assertEquals("VolumeDot", alias.loadLabel(pm).toString())
    }

    @Test fun screenshotPermissionKeepsTheRegisteredComponent() {
        val service = context.packageManager.getServiceInfo(
            ComponentName(context, "com.ashik.floatvol.FloatVolAccessibilityService"), 0)
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", service.permission)
        assertEquals("VolumeDot Stealth Screenshot", service.loadLabel(context.packageManager).toString())
        assertTrue(VolumeDotAccessibilityService::class.java.isAssignableFrom(Class.forName(service.name)))
    }

    @Test fun existingPreferencesRemainReadable() {
        context.getSharedPreferences("floatvol_prefs", Context.MODE_PRIVATE).edit()
            .putInt("button_size_dp", 72)
            .putString("color_theme", "violet")
            .putBoolean("position_locked", true)
            .commit()
        val prefs = PrefsManager(context)
        assertEquals(72, prefs.buttonSizeDp)
        assertEquals(PrefsManager.THEME_VIOLET, prefs.colorTheme)
        assertTrue(prefs.isPositionLocked)
    }
}
