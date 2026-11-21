// SPDX-License-Identifier: Apache-2.0
package com.ashik.volumedot.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ashik.volumedot.R

@Composable
fun Symbol(@DrawableRes icon: Int, modifier: Modifier = Modifier, description: String? = null) {
    Icon(painterResource(icon), description, modifier.size(DesignTokens.glyph))
}

@Composable
fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = DesignTokens.card, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(DesignTokens.gutter), verticalArrangement = Arrangement.spacedBy(DesignTokens.inset)) {
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.compact)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            content()
        }
    }
}

@Composable
fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(vertical = DesignTokens.gap),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.inset)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.compact)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun ValueSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String,
    onChange: (Float) -> Unit, enabled: Boolean = true, steps: Int = 0) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(when (unit) {
                "dp" -> stringResource(R.string.ui_size_dp, value.toInt())
                "%" -> stringResource(R.string.ui_percent, value.toInt())
                else -> stringResource(R.string.ui_value_unit, value.toInt(), unit)
            },
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        ExpressiveSlider(value, range, onChange, Modifier.fillMaxWidth(), enabled, steps, label)
    }
}

@Composable
fun ExpressiveSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, steps: Int = 0, label: String = "Volume",
    vertical: Boolean = false, onFinished: () -> Unit = {}) {
    val state = rememberSliderState(value.coerceIn(range), steps, range)
    LaunchedEffect(value, range) { state.value = value.coerceIn(range) }
    val update: (Float) -> Unit = { next -> state.value = next; onChange(next) }
    val semantics = modifier.semantics { contentDescription = label }
    if (vertical) VerticalSlider(state = state, onValueChange = update, modifier = semantics,
        enabled = enabled, topToBottom = false, onValueChangeFinished = onFinished)
    else Slider(state = state, onValueChange = update, modifier = semantics, enabled = enabled, onValueChangeFinished = onFinished)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(DesignTokens.gap), verticalArrangement = Arrangement.spacedBy(DesignTokens.compact)) {
        options.forEach { (id, label) -> FilterChip(selected = selected == id, onClick = { onSelect(id) }, label = { Text(label) }) }
    }
}
