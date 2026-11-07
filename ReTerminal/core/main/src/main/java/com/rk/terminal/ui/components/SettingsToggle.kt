package com.rk.terminal.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.components.compose.preferences.switch.PreferenceSwitch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsToggle(
    modifier: Modifier = Modifier,
    label: String,
    description: String? = null,
    @DrawableRes iconRes: Int? = null,
    default: Boolean,
    reactiveSideEffect: ((checked: Boolean) -> Boolean)? = null,
    sideEffect: ((checked: Boolean) -> Unit)? = null,
    showSwitch: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    isEnabled: Boolean = true,
    isSwitchLocked: Boolean = false,
    endWidget: (@Composable () -> Unit)? = null,
) {
    // `default` is the owner-provided value: when the caller re-renders with a new
    // one (settings restored, another screen changed it) the row must follow it.
    var state by remember(default) {
        mutableStateOf(default)
    }

    // Both entry points must commit the requested value, not a negation of whatever
    // `state` happens to hold: a drag and a row tap can land in the same frame.
    fun applyToggle(target: Boolean) {
        if (isSwitchLocked) return
        val reactive = reactiveSideEffect
        state =
            if (reactive != null) {
                reactive(target)
            } else {
                sideEffect?.invoke(target)
                target
            }
    }

    if (showSwitch && endWidget != null){
        throw IllegalStateException("endWidget with show switch")
    }

    if (showSwitch) {
        PreferenceSwitch(checked = state,
            onLongClick = onLongClick,
            onCheckedChange = { target -> applyToggle(target) },
            label = label,
            modifier = modifier,
            description = description,
            enabled = isEnabled,
            onClick = { applyToggle(!state) })
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        PreferenceTemplate(
            modifier = modifier.combinedClickable(
                enabled = isEnabled,
                indication = ripple(),
                interactionSource = interactionSource,
                onLongClick = onLongClick,
                onClick = { sideEffect?.invoke(false) }
            ),
            title = { Text(text = label) },
            description = { description?.let { Text(text = it) } },
            enabled = isEnabled,
            endWidget = endWidget
        )
    }


}