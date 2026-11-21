# VolumeDot 3.0.2

A floating Android volume controller redesigned with Jetpack Compose and stable Material 3 Expressive components.

## Features

- Adaptive Home, Appearance and Settings screens; bottom navigation on phones and a navigation rail on wider displays.
- Wallpaper-based colors on Android 12+, eight fallback accents, and System/Light/Dark themes.
- Interactive controller preview and real media, ring and alarm volume adjustment.
- Compact floating volume panel with native horizontal/vertical Material sliders, mute, quick settings and header dragging.
- Six icon modes, preset/custom emojis, four shapes, flat tonal/soft/outline/clear finishes, size and opacity settings.
- Configurable corner radius, panel auto-hide, motion, edge snap, position lock, haptics and gestures.
- Optional double-tap screenshots through the existing accessibility global action.
- Existing preference keys and foreground-service recovery retained.

## Build and test

Requires Android SDK Platform 37.1 and JDK 25 for the complete framework test suite. Minimum supported Android version remains Android 9 (API 28); app bytecode remains Java 17. The checksum-verified wrapper supplies Gradle 9.9.1.

```shell
./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug lintRelease -Pfdroid=true
```

On Windows, use `gradlew.bat`. Lint warnings fail builds. CI installs the matching SDK and runs the same checks before packaging.

## Install

Install `VolumeDot-v3.0.2-signed-release.apk` as an update, or use:

```shell
adb install -r VolumeDot-v3.0.2-signed-release.apk
```

Grant **Appear on top** to enable the controller. Screenshot accessibility is optional. Notifications provide Stop and Reset actions. Unrestricted battery access may help background availability on some phones; Android and manufacturer restrictions still apply.

Single tap now opens the compact VolumeDot panel by default. Select **System controls** in Settings to retain the previous tap behavior. All original gesture, icon, size, finish, volume and screenshot options remain available.

## Documentation and rendered previews

- `DESIGN.md`: original architecture analysis, redesign plan, design-system decisions, compatibility and modified-file map.
- `QA_REPORT.md`: final test/build results and remaining device checks.
- `app/build/outputs/ui-previews/`: actual Compose renders from automated native-graphics tests, including light/dark phone views, compact large-text settings, tablet, vertical panel and RTL.
- `app/build/reports/`: test and lint reports.

Physical-device validation of screenshots, system permission hand-offs and overnight background operation is still required.

## Source, license and F-Droid

Source: https://github.com/ConstantineAshik/VolumeDOT. Licensed under Apache-2.0; see LICENSE and NOTICE. See [FDROID.md](FDROID.md) for unsigned builds, optional private signing, listing metadata and outstanding submission steps. Public-clone release builds are unsigned by default; existing local signed builds use ignored signing.properties.

