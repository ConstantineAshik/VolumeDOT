// SPDX-License-Identifier: Apache-2.0
package com.ashik.volumedot.ui

import android.os.Build
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ashik.volumedot.BubbleRenderer
import com.ashik.volumedot.PrefsManager
import com.ashik.volumedot.R

data class PermissionStatus(val overlay: Boolean = false, val accessibility: Boolean = false,
    val battery: Boolean = false, val notifications: Boolean = false)

class UiActions(
    val change: (PrefsManager.() -> Unit) -> Unit,
    val toggleService: () -> Unit,
    val permission: (String) -> Unit,
    val volume: (Int, Int) -> Unit,
    val mute: () -> Unit,
    val nativeVolume: () -> Unit,
    val position: (String, Float?) -> Unit,
    val resetPosition: () -> Unit
)

@Composable
fun VolumeDotApp(prefs: PrefsManager, appearance: Appearance, status: PermissionStatus, running: Boolean,
    volumes: List<StreamVolume>, selectedTab: Int, onTab: (Int) -> Unit, actions: UiActions) {
    VolumeDotTheme(appearance) {
        BoxWithConstraints(Modifier.fillMaxSize().testTag("app_root")) {
            val wide = maxWidth >= DesignTokens.wideBreakpoint
            val compactStatus = maxWidth < DesignTokens.compactBreakpoint || LocalDensity.current.fontScale > 1.15f
            val labels = listOf("Home", "Appearance", "Settings")
            val icons = listOf(R.drawable.sym_home, R.drawable.sym_palette, R.drawable.sym_tune)
            Scaffold(containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    @OptIn(ExperimentalMaterial3Api::class)
                    TopAppBar(title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, maxLines = 1) }, actions = {
                        AssistChip(onClick = { onTab(2) }, label = { Text(if (compactStatus) { if (running) "Live" else "Ready" }
                            else if (running) "Controller live" else "Ready when you are") },
                            leadingIcon = { Symbol(if (running) R.drawable.sym_graphic_eq else R.drawable.sym_volume_up) })
                        Spacer(Modifier.width(DesignTokens.inset))
                    })
                }, bottomBar = {
                    if (!wide) NavigationBar {
                        labels.forEachIndexed { index, label -> NavigationBarItem(selectedTab == index,
                            onClick = { onTab(index) }, icon = { Symbol(icons[index]) }, label = { Text(label) }) }
                    }
                }) { padding ->
                Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    if (wide) NavigationRail {
                        Spacer(Modifier.height(DesignTokens.glyph))
                        labels.forEachIndexed { index, label -> NavigationRailItem(selectedTab == index,
                            onClick = { onTab(index) }, icon = { Symbol(icons[index]) }, label = { Text(label) },
                            modifier = Modifier.padding(vertical = 6.dp)) }
                    }
                    val motion = LocalMotionEnabled.current
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        AnimatedContent(selectedTab, modifier = Modifier.widthIn(max = DesignTokens.maxContent).fillMaxSize(),
                            transitionSpec = {
                                fadeIn(DesignTokens.springSpec(motion)) togetherWith fadeOut(DesignTokens.springSpec(motion))
                            }, label = "Screen transition") { tab ->
                            when (tab) {
                                1 -> AppearanceScreen(prefs, appearance, actions)
                                2 -> SettingsScreen(appearance, status, actions)
                                else -> Dashboard(prefs, appearance, status, running, volumes, actions, onTab)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.gap), modifier = Modifier.padding(bottom = DesignTokens.compact)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Dashboard(prefs: PrefsManager, p: Appearance, status: PermissionStatus, running: Boolean,
    volumes: List<StreamVolume>, actions: UiActions, onTab: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("home_screen"), contentPadding = PaddingValues(DesignTokens.gutter),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.gutter)) {
        item {
            BoxWithConstraints {
                val hero: @Composable () -> Unit = {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = DesignTokens.hero) {
                        Column(Modifier.fillMaxWidth().padding(DesignTokens.section), verticalArrangement = Arrangement.spacedBy(DesignTokens.gutter)) {
                            Symbol(R.drawable.sym_graphic_eq, modifier = Modifier.size(DesignTokens.largeGlyph))
                            Text("Your sound.\nWithin reach.", style = MaterialTheme.typography.displaySmall)
                            Text(if (running) "Your floating controller is ready, wherever you go." else
                                "Keep volume control close. A little button, a lot less reaching.", style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = actions.toggleService, modifier = Modifier.fillMaxWidth().heightIn(min = DesignTokens.buttonHeight)) {
                                Symbol(if (running) R.drawable.sym_pause else R.drawable.sym_play_arrow)
                                Spacer(Modifier.width(DesignTokens.gap))
                                Text(if (running) "Turn off VolumeDot" else if (!status.overlay) "Set up VolumeDot" else "Turn on VolumeDot")
                            }
                        }
                    }
                }
                val preview: @Composable () -> Unit = {
                    SectionCard("Meet your controller", "Tap the preview to try the new volume panel.") {
                        InteractivePreview(prefs, p, volumes, actions)
                        FilledTonalButton(onClick = { onTab(1) }, modifier = Modifier.fillMaxWidth()) {
                            Symbol(R.drawable.sym_palette); Spacer(Modifier.width(DesignTokens.gap)); Text("Make it yours")
                        }
                    }
                }
                if (maxWidth >= DesignTokens.pairedCardsBreakpoint) Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.gutter)) {
                    Box(Modifier.weight(1f)) { hero() }; Box(Modifier.weight(1f)) { preview() }
                } else Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.gutter)) { hero(); preview() }
            }
        }
        item {
            SectionCard("Sound, at a glance", "These sliders adjust your phone’s volume.") {
                volumes.forEach { stream ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing)) {
                        Symbol(stream.icon)
                        Box(Modifier.weight(1f)) {
                            ValueSlider(stream.label, stream.value.toFloat(), 0f..stream.maximum.toFloat(), "",
                                { actions.volume(stream.stream, it.toInt()) }, steps = (stream.maximum - 1).coerceAtLeast(0))
                        }
                    }
                }
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(DesignTokens.gap)) {
                    FilledTonalButton(onClick = actions.mute) { Symbol(R.drawable.sym_volume_off); Spacer(Modifier.width(DesignTokens.gap)); Text("Mute / unmute") }
                    IconButton(onClick = actions.nativeVolume) { Symbol(R.drawable.sym_open_in_new, description = "Open system volume controls") }
                }
            }
        }
        item { PermissionsCard(status, actions) }
        item {
            SectionCard("Small gestures. Big control.") {
                Guide(R.drawable.sym_touch_app, "Tap", if (p.tap == "panel") "Expand your floating volume panel." else "Open Android’s volume controls.")
                Guide(R.drawable.sym_drag_indicator, "Hold & drag", if (p.locked) "Position is locked. Unlock it in Settings to move." else "Move the controller. Release without moving for quick controls.")
                Guide(R.drawable.sym_screenshot, "Double tap", if (p.screenshot) "Capture a screenshot without the floating controller." else "Mute or unmute media.")
                TextButton(onClick = { onTab(2) }) { Text("Configure gestures"); Spacer(Modifier.width(DesignTokens.gap)); Symbol(R.drawable.sym_arrow_forward) }
            }
        }
    }
}

@Composable
private fun Guide(icon: Int, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.inset), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(Modifier.size(DesignTokens.minTouch), contentAlignment = Alignment.Center) { Symbol(icon) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BubblePreview(prefs: PrefsManager, p: Appearance, idle: Boolean = false) {
    val dimensions = BubbleRenderer.getBubbleDimensionsPx(LocalContext.current, prefs)
    val density = LocalDensity.current.density
    AndroidView(factory = { FrameLayout(it) }, update = { BubbleRenderer.renderBubble(it.context, it, prefs, 2) },
        modifier = Modifier.size((dimensions.first / density).dp, (dimensions.second / density).dp)
            .alpha((if (idle) p.idleOpacity else p.activeOpacity) / 100f))
}

@Composable
private fun InteractivePreview(prefs: PrefsManager, p: Appearance, volumes: List<StreamVolume>, actions: UiActions) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = DesignTokens.card,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(DesignTokens.gap), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(DesignTokens.bubblePreviewHeight).clickable { expanded = !expanded }
                .semantics { contentDescription = "Toggle floating controller preview" }, contentAlignment = Alignment.Center) {
                BubblePreview(prefs, p)
            }
            AnimatedVisibility(expanded, enter = expandVertically(DesignTokens.springSpec(LocalMotionEnabled.current)) + fadeIn(DesignTokens.springSpec(LocalMotionEnabled.current)),
                exit = shrinkVertically(DesignTokens.springSpec(LocalMotionEnabled.current)) + fadeOut(DesignTokens.springSpec(LocalMotionEnabled.current))) {
                VolumePanel(p, volumes, { stream, value -> actions.volume(stream, value) }, actions.mute,
                    onClose = { expanded = false }, onStudio = {}, onNative = actions.nativeVolume,
                    onScreenshot = { actions.permission("accessibility") }, onLock = { actions.change { isPositionLocked = !isPositionLocked } },
                    onReset = actions.resetPosition, onDrag = { _, _ -> }, onDragEnd = {},
                    onSize = { actions.change { buttonSizeDp = it } }, onFinish = { actions.change { bgStyle = it } }, preview = true)
            }
        }
    }
}

@Composable
private fun AppearanceScreen(prefs: PrefsManager, p: Appearance, actions: UiActions) {
    var idle by rememberSaveable { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("appearance_screen"), contentPadding = PaddingValues(DesignTokens.gutter),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.gutter)) {
        item { ScreenTitle("Make it yours", "A controller that feels at home on your phone.") }
        item {
            SectionCard("Live preview", "Your changes apply to the floating controller instantly.") {
                Box(Modifier.fillMaxWidth().height(DesignTokens.studioPreviewHeight).background(MaterialTheme.colorScheme.surfaceContainerHighest, DesignTokens.card),
                    contentAlignment = Alignment.Center) { BubblePreview(prefs, p, idle) }
                Text(stringResource(R.string.ui_preview_format, p.size, BubbleRenderer.getTheme(p.accent).label,
                    when (p.finish) { PrefsManager.BG_TRANSPARENT -> "Clear"; PrefsManager.BG_OUTLINE -> "Outline"; PrefsManager.BG_FROST -> "Soft"; else -> "Tonal" }),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChoiceRow(listOf(false to "Active", true to "Idle"), idle) { idle = it }
            }
        }
        item {
            SectionCard("Color & theme") {
                ChoiceRow(listOf("system" to "System", "light" to "Light", "dark" to "Dark"), p.mode) { actions.change { themeMode = it } }
                SettingSwitch("Wallpaper colors", if (Build.VERSION.SDK_INT >= 31) "Match your phone’s color palette." else "Available on Android 12 and later.",
                    p.dynamic, { actions.change { dynamicColors = it } }, enabled = Build.VERSION.SDK_INT >= 31)
                Text("Accent color", style = MaterialTheme.typography.titleMedium)
                AccentPicker(p, actions)
                Text("Choosing an accent turns off wallpaper colors.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionCard("Shape & size") {
                ChoiceRow(listOf(PrefsManager.SHAPE_CIRCLE to "Circle", PrefsManager.SHAPE_SQUIRCLE to "Squircle",
                    PrefsManager.SHAPE_ROUNDED to "Soft square", PrefsManager.SHAPE_PILL to "Capsule"), p.shape) { actions.change { buttonShape = it } }
                ValueSlider("Bubble size", p.size.toFloat(), 32f..96f, "dp", { actions.change { buttonSizeDp = it.toInt() } })
                ValueSlider("Panel corners", p.radius.toFloat(), 16f..48f, "dp", { actions.change { cornerRadiusDp = it.toInt() } })
                ChoiceRow(listOf(PrefsManager.BG_GLASS to "Tonal", PrefsManager.BG_FROST to "Soft", PrefsManager.BG_OUTLINE to "Outline",
                    PrefsManager.BG_TRANSPARENT to "Clear"), p.finish) { actions.change { bgStyle = it } }
            }
        }
        item {
            SectionCard("Icon & expression") {
                ChoiceRow(listOf(PrefsManager.ICON_DYNAMIC_VECTOR to "Speaker", PrefsManager.ICON_EQUALIZER to "Equalizer",
                    PrefsManager.ICON_MINIMAL_DOT to "Minimal", PrefsManager.ICON_PLUS_MINUS to "Plus / minus",
                    PrefsManager.ICON_DYNAMIC_EMOJI to "Auto emoji", PrefsManager.ICON_CUSTOM_EMOJI to "Your emoji"), p.icon) {
                    actions.change { iconMode = it }
                }
                val emoji = rememberTextFieldState(p.emoji)
                var appliedEmoji by rememberSaveable { mutableStateOf(p.emoji) }
                LaunchedEffect(p.emoji) {
                    if (appliedEmoji != p.emoji && emoji.text.toString() != p.emoji) emoji.edit { replace(0, length, p.emoji) }
                    appliedEmoji = p.emoji
                }
                ChoiceRow(BubbleRenderer.PRESET_EMOJIS.map { it to it }, p.emoji) {
                    actions.change { customEmoji = it; iconMode = PrefsManager.ICON_CUSTOM_EMOJI }
                }
                OutlinedTextField(state = emoji, inputTransformation = InputTransformation.maxLength(64),
                    label = { Text("Custom emoji or symbol") }, modifier = Modifier.fillMaxWidth())
                FilledTonalButton(onClick = { if (emoji.text.isNotBlank()) actions.change {
                    customEmoji = emoji.text.toString(); iconMode = PrefsManager.ICON_CUSTOM_EMOJI
                } }, enabled = emoji.text.isNotBlank()) { Text("Use this symbol") }
            }
        }
        item {
            SectionCard("Opacity") {
                ValueSlider("Active bubble", p.activeOpacity.toFloat(), 15f..100f, "%", { actions.change { activeOpacityPercent = it.toInt() } })
                ValueSlider("Idle bubble", p.idleOpacity.toFloat(), 10f..100f, "%", { actions.change { idleOpacityPercent = it.toInt() } })
                ValueSlider("Volume panel", p.panelOpacity.toFloat(), 70f..100f, "%", { actions.change { panelOpacityPercent = it.toInt() } })
            }
        }
        item { OutlinedButton(onClick = { reset = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset appearance") } }
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("Reset your look?") },
        text = { Text("Restore the default colors, icon, size and opacity. Your gestures and controller state will stay as they are.") },
        confirmButton = { TextButton(onClick = { actions.change { resetAppearanceDefaults(); themeMode = "system"; dynamicColors = true }; reset = false }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { reset = false }) { Text("Cancel") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(p: Appearance, actions: UiActions) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(DesignTokens.gap), verticalArrangement = Arrangement.spacedBy(DesignTokens.gap)) {
        DesignTokens.accents.forEach { (id, color) ->
            val name = BubbleRenderer.getTheme(id).label
            FilterChip(selected = !p.dynamic && p.accent == id, onClick = { actions.change { colorTheme = id; dynamicColors = false } },
                label = { Text(name) }, leadingIcon = { Box(Modifier.size(DesignTokens.swatch).background(color, CircleShape)) })
        }
    }
}

@Composable
private fun SettingsScreen(p: Appearance, status: PermissionStatus, actions: UiActions) {
    val screen = LocalResources.current.displayMetrics
    LazyColumn(Modifier.fillMaxSize().testTag("settings_screen"), contentPadding = PaddingValues(DesignTokens.gutter),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.gutter)) {
        item { ScreenTitle("Just the way you like it", "Fine-tune how VolumeDot feels and behaves.") }
        item {
            SectionCard("Gestures") {
                Text("Single tap", style = MaterialTheme.typography.titleMedium)
                ChoiceRow(listOf("panel" to "VolumeDot panel", "native" to "System controls"), p.tap) { actions.change { tapAction = it } }
                SettingSwitch("Screenshot on double tap", "Turn off to mute or unmute media instead.", p.screenshot, { actions.change { isDoubleTapScreenshot = it } })
                SettingSwitch("Hold before dragging", "Prevent accidental movement while tapping.", p.holdOnly, { actions.change { isHoldToMoveOnly = it } })
                SettingSwitch("Lock position", "Keep the bubble exactly where you put it.", p.locked, { actions.change { isPositionLocked = it } })
                SettingSwitch("Swipe to adjust volume", "Swipe vertically on the locked bubble.", p.swipe, { actions.change { isSwipeVolumeEnabled = it } })
                SettingSwitch("Touch feedback", "A gentle vibration for controller gestures.", p.haptic, { actions.change { isHapticEnabled = it } })
            }
        }
        item {
            SectionCard("Floating behavior") {
                SettingSwitch("Snap to edges", "Settle neatly against the nearest screen edge.", p.snap, { actions.change { isEdgeSnapEnabled = it } })
                SettingSwitch("Dim when idle", "Lower bubble opacity after three seconds.", p.dim, { actions.change { isAutoDimEnabled = it } })
                SettingSwitch("Expressive motion", "Spring transitions that also respect system animation settings.", p.animations, { actions.change { animationsEnabled = it } })
                Text("Slider orientation", style = MaterialTheme.typography.titleMedium)
                ChoiceRow(listOf("horizontal" to "Horizontal", "vertical" to "Vertical"), p.orientation) { actions.change { sliderOrientation = it } }
                ValueSlider("Close panel after", p.autoHide.toFloat(), 0f..30f, "s", { actions.change { panelAutoHideSeconds = it.toInt() } })
                Text(if (p.autoHide == 0) "Auto-hide is off. Close the panel when you’re done." else "The timer pauses while you’re touching the panel.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionCard("Position", "Move the bubble directly, or choose a starting point.") {
                ChoiceRow(listOf("left" to "Left edge", "right" to "Right edge"), if (p.positionX < screen.widthPixels / 2) "left" else "right") {
                    actions.position(it, null)
                }
                ValueSlider("Height on screen", (p.positionY.toFloat() / screen.heightPixels * 100).coerceIn(5f, 90f), 5f..90f, "%", {
                    actions.position(if (p.positionX < screen.widthPixels / 2) "left" else "right", it / 100f)
                })
                FilledTonalButton(onClick = actions.resetPosition) { Symbol(R.drawable.sym_restart_alt); Spacer(Modifier.width(DesignTokens.gap)); Text("Reset position") }
            }
        }
        item { PermissionsCard(status, actions) }
        item { Text("VolumeDot 3.0 • Sound within reach", modifier = Modifier.padding(DesignTokens.gap), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun PermissionsCard(status: PermissionStatus, actions: UiActions) {
    SectionCard("Ready for everyday use", "Only overlay access is required for volume controls.") {
        PermissionRow("overlay", "Appear on top", "Show your controller over other apps.", status.overlay, R.drawable.sym_touch_app, actions)
        PermissionRow("accessibility", "Screenshots", "Optional accessibility access for double-tap captures.", status.accessibility, R.drawable.sym_screenshot, actions)
        PermissionRow("notifications", "Notifications", "Keep Stop and Reset actions close by.", status.notifications, R.drawable.sym_notifications, actions)
        PermissionRow("battery", "Background access", "Reduce battery restrictions for longer sessions.", status.battery, R.drawable.sym_battery_saver, actions)
    }
}

@Composable
private fun PermissionRow(key: String, title: String, subtitle: String, granted: Boolean, icon: Int, actions: UiActions) {
    Row(Modifier.fillMaxWidth().clickable { actions.permission(key) }.padding(vertical = DesignTokens.gap),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing)) {
        Symbol(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (granted) "Allowed" else "Tap to set up", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Symbol(if (granted) R.drawable.sym_check else R.drawable.sym_arrow_forward)
    }
}
