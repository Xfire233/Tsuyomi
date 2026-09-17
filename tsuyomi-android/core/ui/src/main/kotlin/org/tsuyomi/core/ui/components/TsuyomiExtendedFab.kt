/* SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** Core-owned extended floating action button for a persistent contextual action. */
@Composable
fun TsuyomiExtendedFab(
    text: String,
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        text = { Text(text) },
        icon = { Icon(imageVector, contentDescription = contentDescription) },
        onClick = onClick,
        modifier = modifier,
        expanded = true,
    )
}
