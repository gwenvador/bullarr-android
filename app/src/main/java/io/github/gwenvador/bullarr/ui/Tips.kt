package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ChipColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// Infobulles automatiques : IconButton et FilterChip (versions de ce package, qui masquent celles
// de Material) affichent au long appui la description de la première Icon qu'ils contiennent.

private class TipHolder { var text by mutableStateOf<String?>(null) }

private val LocalTip = staticCompositionLocalOf<TipHolder?> { null }

/** Même Icon que Material ; sa description sert aussi d'infobulle au bouton qui la contient. */
@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val holder = LocalTip.current
    if (holder != null && contentDescription != null && holder.text != contentDescription) holder.text = contentDescription
    androidx.compose.material3.Icon(imageVector, contentDescription, modifier, tint)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val holder = remember { TipHolder() }
    @Suppress("DEPRECATION")
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(holder.text ?: "") } },
        state = rememberTooltipState(),
        enableUserInput = holder.text != null,
    ) {
        androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            CompositionLocalProvider(LocalTip provides holder) { content() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val holder = remember { TipHolder() }
    @Suppress("DEPRECATION")
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(holder.text ?: "") } },
        state = rememberTooltipState(),
        modifier = modifier,
        enableUserInput = holder.text != null,
    ) {
        androidx.compose.material3.FilterChip(selected = selected, onClick = onClick, enabled = enabled, label = {
            CompositionLocalProvider(LocalTip provides holder) { label() }
        })
    }
}
