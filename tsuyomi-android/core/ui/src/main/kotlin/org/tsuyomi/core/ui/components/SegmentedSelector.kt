/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.R
import org.tsuyomi.core.ui.theme.TsuyomiEInkPalette
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.core.ui.theme.tsuyomiAnimateColorAsState
import org.tsuyomi.core.ui.theme.tsuyomiFocusRing

/** One option of a [SegmentedSelector]. */
data class TsuyomiSegment<T>(
    val value: T,
    val label: String,
    val enabled: Boolean = true,
)

/**
 * Single-choice segmented selector with radio semantics: the container exposes collection info,
 * every segment exposes its role, selected flag, position, and a state description, and disabled
 * or error states are announced as text, never color alone.
 *
 * Selection changes use a short ease-out tonal transition in the standard profile and an
 * immediate opaque inversion in the E-ink profile.
 */
@Composable
fun <T> SegmentedSelector(
    options: List<TsuyomiSegment<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
    errorMessage: String? = null,
) {
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    val shape: Shape = RoundedCornerShape(if (eInk) 4.dp else 12.dp)
    val borderColor = when {
        !enabled && eInk -> TsuyomiEInkPalette.N50
        !enabled -> MaterialTheme.colorScheme.outlineVariant
        eInk -> TsuyomiEInkPalette.Ink
        else -> MaterialTheme.colorScheme.outline
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = TsuyomiSpacing.Sm),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    collectionInfo = CollectionInfo(1, options.size)
                    if (errorMessage != null) {
                        error(errorMessage)
                    }
                }
                .border(1.dp, borderColor, shape)
                .clip(shape),
        ) {
            options.forEachIndexed { index, option ->
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(borderColor),
                    )
                }
                SegmentView(
                    option = option,
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                    enabled = enabled && option.enabled,
                    eInk = eInk,
                    index = index,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (!enabled && disabledReason != null) {
            Text(
                text = disabledReason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
            )
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
            )
        }
    }
}

@Composable
private fun <T> SegmentView(
    option: TsuyomiSegment<T>,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    eInk: Boolean,
    index: Int,
    modifier: Modifier = Modifier,
) {
    val instant = LocalDisplayEnvironment.current.instantMotion
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val selectedDescription = stringResource(R.string.coreui_state_selected)
    val unselectedDescription = stringResource(R.string.coreui_state_not_selected)
    val disabledDescription = stringResource(R.string.coreui_state_disabled)

    val containerTarget = when {
        !selected -> Color.Transparent
        eInk -> TsuyomiEInkPalette.Ink
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val containerColor = tsuyomiAnimateColorAsState(containerTarget, instant, "segmentContainer")
    val textColor = when {
        !enabled && eInk -> TsuyomiEInkPalette.N50
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        selected && eInk -> TsuyomiEInkPalette.Paper
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = RoundedCornerShape(if (eInk) 4.dp else 12.dp)

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .tsuyomiFocusRing(shape, focused, MaterialTheme.colorScheme.primary)
            .background(containerColor)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                stateDescription = when {
                    !enabled -> disabledDescription
                    selected -> selectedDescription
                    else -> unselectedDescription
                }
                collectionItemInfo = CollectionItemInfo(0, 1, index, 1)
            }
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = TsuyomiSpacing.Sm, vertical = TsuyomiSpacing.Sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}
