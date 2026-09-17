/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

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
) {
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = label
            valueDescription?.let { stateDescription = it }
        },
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        steps = steps,
        enabled = enabled,
    )
}
