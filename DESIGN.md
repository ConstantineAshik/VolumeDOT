# VolumeDot 3.0.1 — Material 3 Expressive redesign

## Existing application analysis

The starting application was a single-module Kotlin Android app, minimum API 28, target API 37. Its six production Kotlin files were MainActivity, FloatingVolumeService, BubbleRenderer, PrefsManager, BootReceiver and VolumeDotAccessibilityService. There were no Compose dependencies or composables. MainActivity built Home, Studio and Settings entirely with Android Views in Kotlin; XML provided the application theme, colors, launcher assets, manifest and accessibility configuration, not screen layouts.

FloatingVolumeService owns AudioManager, the foreground notification, broadcast receiver, handler callbacks and WindowManager windows. A draggable FrameLayout was the collapsed bubble; a separate widget-based window was the quick hub. Its gestures included delayed single tap, double-tap screenshot or media mute, hold-and-drag, hold-release quick controls, locked vertical swipes, edge snap, and cancellation handling. Screenshots use Android's GLOBAL_ACTION_TAKE_SCREENSHOT through the accessibility service, hiding/restoring the overlay around the capture.

PrefsManager stores settings in the existing `floatvol_prefs` SharedPreferences file. Existing keys include service intent, position lock, edge snap, idle dimming, swipe volume, haptics, double-tap behavior, hold-to-move, bubble dimensions, active/idle opacity, icon mode, emoji, shape, color preset, background finish and saved X/Y coordinates. BootReceiver restores an enabled controller after boot or package replacement if Android permits it.

The app requests overlay access; Android 13+ notification permission supports notification actions; accessibility is optional and used for screenshots. Optional unrestricted battery access helps background availability. The foreground service uses the declared specialUse type. No network backend, account, database, image assets, or DataStore migration is needed.

## Redesign plan and decisions

1. Establish shared color, shape, spacing, type, motion and breakpoint tokens before composing screens.
2. Replace the View-built Activity screens with Compose, retaining the service, AudioManager calls, permission flows, old preference file/keys and screenshot implementation.
3. Use stable Compose Material 3 1.5.0 and its MaterialExpressiveTheme. Use official Material Symbols Rounded vector assets, bundled offline with their license.
4. Build a dashboard with an explicit start/stop action, live controller preview, actual device-volume sliders, permission health and gesture guidance.
5. Provide a dedicated appearance studio and behavior/settings screen. Persist every available control; use a confirmation dialog for appearance reset.
6. Keep the existing native collapsed bubble and gesture recognizer for compatibility. Replace its rendered colors with shared Material roles and place its visual inside a minimum 48dp touch window.
7. Replace only the quick-hub presentation with a bounded Compose window. Give each panel an explicit lifecycle, saved-state owner, ViewModel store and composition-disposal path.
8. Verify source/lint/build checks, existing regressions, preference migration, framework behavior, Compose interactions, responsive renders and default color contrast. Record device-only limits honestly.

The provided ui-skill guidance informed the design-system-first, responsive and accessibility checks. Its web-specific JSX/Tailwind rules were translated to Compose conventions; no unrelated web tooling or external skill installation was needed.

## Design system

`ui/DesignSystem.kt` is the source of truth for fallback accent seeds, color generation, spacing, shapes, typography, breakpoints and motion policy. `MaterialTheme.colorScheme` supplies all screen and component colors. There are no screen-local color literals. The old bubble palette metadata no longer supplies separate glass-style colors.

- Android 12+: dynamic light/dark schemes from wallpaper, independently switchable.
- Older Android: Material tonal color roles from the selected fallback accent, with shared light/dark neutral surfaces.
- Theme choices: System, Light, Dark. Choosing a manual accent turns off dynamic color so the selection has a visible result.
- Type: Material 3 hierarchy using the platform sans-serif family, normal/medium weights, 36sp display, 32sp headline, 22sp title and readable 14/16sp body styles.
- Geometry: 28dp standard cards, 36dp hero surface, pill buttons/chips, user-adjustable panel corners; a consistent shared spacing scale.
- Accessibility: Material semantics/ripples/focus/disabled states, visible labels, named icon actions, 48dp minimum interactive sizing, and token contrast checks. User-selected bubble fading intentionally lowers its visibility.
- Motion: Material Expressive motion scheme plus spring expansion/collapse and edge snapping. Turning motion off switches theme motion specs to snap; native window motion also checks system animator settings.

The legacy preset identifiers remain intact. Glass/Frost choices are presented as **Tonal/Soft**, rendered as flat Material tonal surfaces. Outline/Clear remain available. Emoji, vector, equalizer, minimal-dot and plus/minus choices are preserved.

## Screens and responsive behavior

Home establishes the hierarchy: brand/status, controller action, interactive preview, device volume, permission health and gesture guide. Appearance contains preview, theme, dynamic colors, accents, shape, size, corners, finish, icons/emojis and opacity. Settings contains gestures, touch feedback, lock, snap, dimming, motion, orientation, timeout, position and permission actions.

Compact screens use bottom navigation and a single scrollable column. At 700dp of content width the dashboard hero and preview become paired cards. At 840dp the app uses a navigation rail. Content is capped at 1080dp. Choice rows wrap; settings are scrollable; text sizing follows the system. A short status chip prevents branding from wrapping on narrow/large-text screens. Scaffold handles system bars and cutouts; the emoji editor retains keyboard access. RTL navigation is tested, while physical WindowManager coordinates remain independent of text direction.

## Floating controller

The default horizontal panel is approximately 340dp wide and 136dp tall, bounded further by the display width. Its initial presentation contains the stream title, header drag handle, controls menu, close action, media mute and a volume slider. More controls reveals stream selection, lock, reset, system controls, the original quick-size presets, finish choices, screenshot and Appearance actions. This preserves functionality without keeping a large window open by default.

Vertical mode uses Material 3's native vertical slider in a narrower window. Expanded content is height-bounded and scrollable. The service repositions the panel when its measured height changes so it stays on-screen. Only the header owns panel-drag pointer input; the slider owns its own gestures. Both remain ordinary small TYPE_APPLICATION_OVERLAY windows with NOT_TOUCH_MODAL behavior; there is no full-screen invisible touch interceptor.

Auto-hide is configurable from off to 30 seconds, pauses during touch, and restarts after interaction. Outside touch closes the panel. Closing, screenshot capture, rotation, attach failure and service destruction dispose its composition and owner. The saved-state registry serves transient Compose state while each panel exists; long-lived preferences remain in PrefsManager. A fresh panel intentionally starts on Media with its extra controls collapsed.

## Persistence and compatibility

The existing SharedPreferences architecture and all old preference keys remain. New keys add theme mode, wallpaper colors, panel opacity, panel radius, animation preference, panel timeout, slider orientation and tap action. No user-data migration or service rewrite is introduced. The new default single tap opens VolumeDot's panel; System controls remains a selectable tap behavior and a panel action.

Build setup uses the stable November 2026 Compose BOM, stable Material 3 1.5.0, stable Activity/Lifecycle libraries and a matching stable Kotlin Compose compiler. Compile SDK 37.1 is required by the stable dependencies; target remains API 37 and minimum remains API 28. App bytecode stays Java 17; modern Android framework tests use Java 25.

## Main modified files

| File | Responsibility |
| --- | --- |
| MainActivity.kt | Compose entry, permission and volume actions, lifecycle observers, tab state |
| PrefsManager.kt | Existing persistence plus new bounded appearance/behavior settings |
| FloatingVolumeService.kt | Existing overlay logic plus bounded Compose panel, timeout, springs and live status |
| BubbleRenderer.kt | Preserved visual modes using shared Material colors and accessible touch/visual separation |
| ui/DesignSystem.kt | Shared tokens, color schemes, typography, motion and immutable appearance snapshot |
| ui/Components.kt | Reusable cards, switches, chips, icons and stateful sliders |
| ui/VolumeDotApp.kt | Dashboard, appearance studio, settings, adaptive navigation and preview |
| ui/VolumePanel.kt | Compact/expanded panel, stream selection, quick settings and header-only drag |
| ui/OverlayPanelHost.kt | Explicit lifecycle/saved-state/ViewModel ownership and composition disposal |
| res/drawable/sym_*.xml | Official Material Symbols Rounded vectors |
| assets/MaterialSymbols-LICENSE.txt | Bundled icon license |
| ReliabilityTest.kt | Existing regressions plus persistence, contrast, touch bounds and panel disposal/timeout |
| ComposeRedesignTest.kt | Screen/navigation/theme tests and actual layout renders |
| OverlayInteractionTest.kt | Slider versus drag ownership and quick-control interactions |
| Gradle files and CI workflow | Stable Compose setup and SDK/build/test validation |

See QA_REPORT.md for the final validation results, screenshots and remaining limitations.

## Primary references

- https://m3.material.io/develop
- https://developer.android.com/blog/posts/jetpack-compose-november-2026-release
- https://developer.android.com/jetpack/androidx/releases/compose-material3
- https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme
- https://github.com/google/material-design-icons

## 3.0.1 naming and update compatibility

The visible app name, project name, notification/accessibility labels, UI functions, theme resource, documentation, CI artifact names and Kotlin namespace now use VolumeDot. Production and test code use `com.ashik.volumedot`; the Compose entry points are `VolumeDotApp` and `VolumeDotTheme`.

A small set of technical identifiers intentionally remains stable: installed application ID `com.ashik.floatvol`, preference store `floatvol_prefs`, notification channel ID, and the original signing configuration. An Android activity alias retains the old launcher component and redirects to the renamed Activity. A minimal accessibility-service adapter retains the registered component name while inheriting the renamed implementation, preserving the user's existing accessibility selection. These are compatibility contracts, not displayed branding.

Historical APKs/logs and the workspace directory are retained as original artifacts. New installable artifacts are named `VolumeDot-v3.0.1-*.apk`.