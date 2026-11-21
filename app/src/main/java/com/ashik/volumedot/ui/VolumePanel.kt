// SPDX-License-Identifier: Apache-2.0
package com.ashik.volumedot.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ashik.volumedot.R

data class StreamVolume(val stream: Int, val label: String, val icon: Int, val value: Int, val maximum: Int)

fun readVolumes(context: Context): List<StreamVolume> {
    val audio = context.getSystemService(AudioManager::class.java)
    return listOf(Triple(AudioManager.STREAM_MUSIC, "Media", R.drawable.sym_volume_up),
        Triple(AudioManager.STREAM_RING, "Ring", R.drawable.sym_notifications),
        Triple(AudioManager.STREAM_ALARM, "Alarm", R.drawable.sym_notifications)).map { (stream, label, icon) ->
        val maximum = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
        StreamVolume(stream, label, icon, audio.getStreamVolume(stream).coerceIn(0, maximum), maximum)
    }
}

/** Only the header drags. Slider pointer input never moves the WindowManager window. */
@Composable
fun VolumePanel(p: Appearance, volumes: List<StreamVolume>, onVolume: (Int, Int) -> Unit, onMute: () -> Unit,
    onClose: () -> Unit, onStudio: () -> Unit, onNative: () -> Unit, onScreenshot: () -> Unit,
    onLock: () -> Unit, onReset: () -> Unit, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit,
    onSize: (Int) -> Unit, onFinish: (String) -> Unit,
    preview: Boolean = false) {
    var selectedStream by rememberSaveable { mutableIntStateOf(AudioManager.STREAM_MUSIC) }
    var more by rememberSaveable { mutableStateOf(false) }
    val selected = volumes.firstOrNull { it.stream == selectedStream } ?: volumes.firstOrNull() ?: return
    val motion = LocalMotionEnabled.current
    val drag = if (preview) Modifier else Modifier.pointerInput(p.locked) {
        detectDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, amount ->
            if (!p.locked) { change.consume(); onDrag(amount.x, amount.y) }
        }
    }
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(p.radius.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = p.panelOpacity / 100f),
        tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().testTag("volume_panel")) {
        Column(Modifier.padding(DesignTokens.inset).animateContentSize(DesignTokens.springSpec(motion)),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.gap), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).heightIn(min = DesignTokens.minTouch).then(drag),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.gap), verticalAlignment = Alignment.CenterVertically) {
                    Symbol(if (preview) selected.icon else R.drawable.sym_drag_indicator,
                        description = if (preview) null else if (p.locked) "Position locked" else "Drag controller")
                    Text(selected.label, style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = { more = !more }) { Symbol(R.drawable.sym_tune, description = "More volume controls") }
                IconButton(onClick = onClose) { Symbol(R.drawable.sym_close, description = "Close volume panel") }
            }
            if (p.orientation == "vertical") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${selected.value * 100 / selected.maximum}%", style = MaterialTheme.typography.headlineSmall)
                    ExpressiveSlider(selected.value.toFloat(), 0f..selected.maximum.toFloat(),
                        { onVolume(selected.stream, it.toInt()) }, Modifier.height(DesignTokens.verticalTrackHeight).width(DesignTokens.verticalTrackWidth),
                        steps = (selected.maximum - 1).coerceAtLeast(0), label = "${selected.label} volume", vertical = true)
                }
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.gap)) {
                FilledTonalIconButton(onClick = onMute) { Symbol(if (volumes.firstOrNull()?.value == 0) R.drawable.sym_volume_up else R.drawable.sym_volume_off, description = "Mute or unmute media") }
                ExpressiveSlider(selected.value.toFloat(), 0f..selected.maximum.toFloat(), { onVolume(selected.stream, it.toInt()) },
                    Modifier.weight(1f), steps = (selected.maximum - 1).coerceAtLeast(0), label = "${selected.label} volume")
                Text("${selected.value * 100 / selected.maximum}%", style = MaterialTheme.typography.labelLarge)
            }
            if (p.orientation == "vertical") FilledTonalIconButton(onClick = onMute) {
                Symbol(R.drawable.sym_volume_off, description = "Mute or unmute media")
            }
            if (more) {
                ChoiceRow(volumes.map { it.stream to it.label }, selected.stream) { selectedStream = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = onLock) { Symbol(if (p.locked) R.drawable.sym_lock else R.drawable.sym_lock_open, description = "Toggle position lock") }
                    IconButton(onClick = onReset) { Symbol(R.drawable.sym_restart_alt, description = "Reset floating position") }
                    IconButton(onClick = onNative) { Symbol(R.drawable.sym_open_in_new, description = "System volume controls") }
                }
                Text("Bubble size", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(listOf(38 to "S", 54 to "M", 70 to "L", 86 to "XL"), p.size, onSize)
                ChoiceRow(listOf(com.ashik.volumedot.PrefsManager.BG_GLASS to "Tonal", com.ashik.volumedot.PrefsManager.BG_FROST to "Soft",
                    com.ashik.volumedot.PrefsManager.BG_OUTLINE to "Outline", com.ashik.volumedot.PrefsManager.BG_TRANSPARENT to "Clear"), p.finish, onFinish)
                if (!preview) {
                    TextButton(onClick = onScreenshot, modifier = Modifier.fillMaxWidth()) { Symbol(R.drawable.sym_screenshot); Spacer(Modifier.width(6.dp)); Text("Screenshot") }
                    TextButton(onClick = onStudio, modifier = Modifier.fillMaxWidth()) { Symbol(R.drawable.sym_palette); Spacer(Modifier.width(6.dp)); Text("Appearance") }
                }
            }
        }
    }
}
