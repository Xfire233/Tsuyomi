/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * Labeled text input owned by core UI. The visible label is also retained as the accessible name;
 * [supportingText] remains visible rather than being silently folded into an error icon.
 */
@Composable
fun TsuyomiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        label = { Text(label) },
        trailingIcon = trailingIcon,
        supportingText = if (supportingText.isNullOrBlank()) null else ({ Text(supportingText) }),
    )
}

/**
 * Selection-aware labeled text input for callers that preserve cursor and composition state.
 * Visual styling and accessibility semantics match the String overload.
 */
@Composable
fun TsuyomiTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        label = { Text(label) },
        trailingIcon = trailingIcon,
        supportingText = if (supportingText.isNullOrBlank()) null else ({ Text(supportingText) }),
    )
}

/**
 * Range input with its name, value description and caller modifiers on the native control.
 * The owning layout renders its visible label without introducing another focus boundary.
 */
@Composable
fun TsuyomiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    steps: Int = 0,
    enabled: Boolean = true,
    valueDescription: String? = null,
    onValueChangeCancelled: (() -> Unit)? = null,
) {
    val interactionSource = remember(steps, valueRange) { SliderInteractionSource() }
    interactionSource.onCancel = onValueChangeCancelled
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = { target ->
            interactionSource.beginValueInput()
            onValueChange(target)
        },
        // Keep accessibility bounds on the reserved target, outside Material's inner thumb bounds.
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {
            contentDescription = label
            valueDescription?.let { stateDescription = it }
        },
        valueRange = valueRange,
        onValueChangeFinished = {
            if (!interactionSource.dragCancelled) onValueChangeFinished?.invoke()
        },
        steps = steps,
        enabled = enabled,
        interactionSource = interactionSource,
    )
}

/** Material calls its finish callback for a cancelled drag too. Observe cancellation before that
 * callback, rather than collecting the interaction flow asynchronously after a possible commit. */
private class SliderInteractionSource(
    private val delegate: MutableInteractionSource = MutableInteractionSource(),
) : MutableInteractionSource by delegate {
    var dragCancelled = false
        private set
    var onCancel: (() -> Unit)? = null

    fun beginValueInput() {
        dragCancelled = false
    }

    override suspend fun emit(interaction: Interaction) {
        observe(interaction)
        delegate.emit(interaction)
    }

    override fun tryEmit(interaction: Interaction): Boolean {
        observe(interaction)
        return delegate.tryEmit(interaction)
    }

    private fun observe(interaction: Interaction) {
        when (interaction) {
            is DragInteraction.Start -> dragCancelled = false
            is DragInteraction.Stop -> dragCancelled = false
            is DragInteraction.Cancel -> {
                dragCancelled = true
                onCancel?.invoke()
            }
            is androidx.compose.foundation.interaction.PressInteraction.Press -> dragCancelled = false
        }
    }
}
