# VolumeDot 3.0.2 F-Droid preparation QA

Validated 21 November 2026.

- `testDebugUnitTest lintDebug lintRelease assembleRelease assembleDebug -Pfdroid=true`: passed.
- 64 automated tests passed: 48 reliability, 8 Compose/render (including launcher-icon export), 2 overlay interaction and 6 rename compatibility cases.
- Debug and release lint: zero reported issues.
- Unsigned release built successfully while local signing.properties and the private keystore were present. apksigner rejected it as unsigned, as expected.
- Optional local signed release: passed; apksigner verified the existing certificate SHA-256 0c70733887102d384d0bc655ea8add52d51e2ab1fd07d4c9bf82728cb43450e9.
- Portable metadata check passed: description/changelog limits, static version 3.0.2 / code 10, recipe fields, 512px PNG icon and four real UI renders.
- `git check-ignore` confirmed private signing.properties, release keystore, local SDK configuration and generated APKs are excluded.
- Repository remote configured to https://github.com/ConstantineAshik/VolumeDOT.git. No push, tag or submission performed.
- Actual fdroid lint/build and cross-machine reproducibility remain unverified; the candidate build is explicitly disabled pending those checks and source publication. See FDROID.md.

The report below records the preceding app redesign and signed-release baseline.

---
# VolumeDot 3.0.1 QA report

Validated 21 November 2026. Supersedes the 2.6.1 report.

## Final results

| Check | Result |
| --- | --- |
| Debug APK | Passed |
| Signed, minified release APK | Passed |
| Debug lint | 0 errors, 0 warnings |
| Release lint | 0 errors, 0 warnings |
| Automated tests | 63 passed; 0 failures, errors or skips |
| Existing/extended framework regressions | 48 cases on APIs 28, 34, 36 and 37 |
| Compose screen/render tests | 7 passed |
| Overlay interaction tests | 2 passed |
| Rename compatibility tests | 6 passed on APIs 28 and 34 |
| Release signature | Verified, APK Signature Scheme v3 |
| Update signing compatibility | Certificate matches the previous 2.6.1 signed release |
| Package/version | com.ashik.floatvol / 3.0.1 / versionCode 9 |
| Minimum/target Android SDK | 28 / 37 |
| Compile Android SDK | 37.1 |

Final command:

```shell
gradlew.bat testDebugUnitTest assembleDebug assembleRelease lintDebug lintRelease --continue --console=plain
```

The complete final run succeeded in 30s. Log: `qa-volumedot-rename.log`. No compiler/dependency deprecation warnings were observed in that final log. Lint warnings remain configured as build failures, with no baseline or blanket suppressions added.

## Functional and architectural verification

The existing service, AudioManager integration, foreground notification, screenshot accessibility service, boot receiver, gesture recognizer and preference file/keys remain in use. Tests verify enabled/disabled sticky restart, refresh, explicit stop, preference bounds/reset, cancelled holds, position lock including release-time snapping, tab restoration and screen construction across the supported test APIs.

New tests verify theme/appearance persistence, panel-opacity/radius/timeout bounds, minimum 48dp bubble touch windows, fallback text contrast, panel lifecycle ownership and automatic timeout disposal. Each fallback accent was checked in light/dark mode for primary/on-primary and surface/on-surface contrast of at least 4.5:1.

Actual Compose input tests verify that slider taps and thumb dragging change the supplied volume value without invoking the header move callback, while header dragging invokes movement. They also verify stream selection and persistent quick-size/finish controls. Sliders use the current stateful Material 3 APIs and update local slider state synchronously during interaction.

Screen tests exercise navigation, manual dark mode and readable system-bar icon styles, accent selection, reset cancellation, a vertical slider preview, disabled motion, compact large-text settings, keyboard/system-bar inset dispatch, adaptive tablet navigation and mirrored Arabic-locale RTL navigation.

## Visual review

Native-graphics Robolectric tests rendered the real Compose screens, not HTML mockups. The following PNGs are preserved in `design-previews/`:

- `phone-home-light.png`
- `phone-home-dark.png`
- `phone-appearance-light.png`
- `phone-settings-dark.png`
- `phone-panel-dark.png`
- `phone-vertical-panel.png`
- `compact-settings.png`
- `tablet-home.png`
- `phone-rtl.png`

Configurations include a 393Ã—851dp phone, 320Ã—640dp compact phone at 1.3Ã— font scale, and 1024Ã—900dp tablet. RTL review uses an Arabic locale and an explicit navigation-order assertion. Copy remains English; no language translations were added.

The review identified and corrected the narrow-screen status-chip/branding wrap. The default panel was reduced to the main slider row; secondary/legacy quick controls remain available through the controls menu. Both themes, the appearance studio, compact header, tablet pairing, vertical panel and mirrored layout were visually inspected.

## Important behavior and migration notes

Single tap now opens the compact VolumeDot panel by default. Users can choose the previous **System controls** behavior in Settings; native controls also remain available from the panel. Double tap, hold/release, drag, locked vertical swipe, size presets, all six icon modes, emoji presets/input, four shapes, finishes and screenshot functionality remain available.

Existing Glass/Frost preference identifiers are retained for compatibility but now render as flat Material **Tonal/Soft** surfaces. No glass blur or gradient effect was introduced. Theme, wallpaper colors, panel opacity, corner radius, timeout, orientation and motion preferences are added to the existing SharedPreferences store rather than migrating user data.

The app and panel share design tokens. Android 12+ wallpaper colors are optional; older phones use tonal fallback palettes. User-selected bubble fading deliberately reduces visibility. The panel uses a separate lifecycle/saved-state owner and disposes its Compose composition on close, timeout, attach failure and service destruction. Only the header drags the window; the panel is not a full-screen touch blocker.

## Remaining device-only checks

No device is connected through ADB. A fresh accelerator check reports that the Android Emulator hypervisor driver is not installed. Therefore no physical-device/emulator end-to-end verification is claimed.

Still verify on the affected phone:

1. Install as an update and confirm existing appearance, gestures and position are retained.
2. Overlay permission, notification permission, accessibility setup and battery-setting hand-offs on the actual OS/OEM.
3. Native media/ring/alarm changes, Bluetooth volume events and Do Not Disturb restrictions.
4. Repeated screenshots, clean overlay hiding/restoration, and navigation back from accessibility settings.
5. Actual WindowManager drag/edge snap, outside-touch pass-through, expanded menu bounds, rotation, cutouts, keyboard and system bars.
6. Panel timeout during and after touch; native reduced-motion settings; switching themes and wallpaper while running.
7. Overnight idle, battery saver, OS process termination, reboot and update behavior. Android force-stop and manufacturer battery policies still apply.
8. Screen-reader interaction and device-specific font/emoji rendering.

Android 9, 14, 16 and 17 were tested as framework sandboxes, not as physical devices. The test suite establishes regression coverage and native UI rendering, not guaranteed background survival across every phone.

## Delivered artifacts

- `VolumeDot-v3.0.1-signed-release.apk`
- `VolumeDot-v3.0.1-debug.apk`
- `DESIGN.md`: architecture analysis, redesign plan, design tokens and modified-file map.
- `design-previews/`: nine rendered previews.
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/lint-results-release.html`

Release SHA-256: `E9D9023B07152CC52635D73A5A8B44911C1E1A1D0E86D71C4CBAA4D787950A47`.

Signing certificate SHA-256: `0c70733887102d384d0bc655ea8add52d51e2ab1fd07d4c9bf82728cb43450e9`.

## Rename verification

The packaged application label is **VolumeDot**. The project, Kotlin namespace, Compose names, resource/theme names, notification labels, accessibility label, documentation, CI artifacts and new APK filenames use the new brand. Native UI renders were regenerated and copied to `design-previews/`.

Six additional compatibility cases verify the displayed application/launcher labels, old launcher alias targeting `com.ashik.volumedot.MainActivity`, stable registered accessibility component using the new implementation, and readability of existing preference values. The application ID, signing identity, notification channel and preference-file ID remain unchanged to support updates. The full final suite has 63 passing cases and both lint variants have zero findings.

Physical update/install and accessibility-grant retention on a phone remain device-only checks; no physical-device run is claimed.
