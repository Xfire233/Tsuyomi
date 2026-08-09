/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.reader.engine.ReaderDocumentSession
import org.tsuyomi.reader.engine.ReaderPresentation
import org.tsuyomi.reader.engine.defaultReaderPresentation
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument

@Composable
fun ReaderSurface(
    document: ReaderDocument,
    restoredLocator: ReaderLocator?,
    onLocatorChanged: (ReaderLocator, LocatorPrecision) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    val session = remember(document.sourceId, document.remoteBookId, document.contentId, document.revision) {
        ReaderDocumentSession(document, restoredLocator, defaultReaderPresentation(isEInk))
    }
    var presentation by remember(session) { mutableStateOf(session.presentation) }
    var position by remember(session) { mutableStateOf(session.position) }

    fun navigate(index: Int) {
        position = session.navigateToBlock(index)
        onLocatorChanged(session.capture(), position.precision)
    }

    Column(modifier.fillMaxSize()) {
        Text(
            text = document.title,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReaderPresentation.entries.forEach { target ->
                TsuyomiButton(
                    text = stringResource(target.label()),
                    onClick = {
                        session.switchPresentation(target)
                        presentation = target
                    },
                    style = if (presentation == target) TsuyomiButtonStyle.PRIMARY else TsuyomiButtonStyle.SECONDARY,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (position.precision == LocatorPrecision.DEGRADED) {
            Text(
                text = stringResource(R.string.reader_degraded_restore),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        when (presentation) {
            ReaderPresentation.SCROLL -> ScrollReader(document, position.blockIndex, ::navigate, Modifier.weight(1f))
            ReaderPresentation.PAGED -> PagedReader(document, position.blockIndex, ::navigate, false, Modifier.weight(1f))
            ReaderPresentation.DUAL_PAGE -> PagedReader(document, position.blockIndex, ::navigate, true, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScrollReader(
    document: ReaderDocument,
    initialIndex: Int,
    onPosition: (Int) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .drop(1)
            .collect(onPosition)
    }
    LazyColumn(state = listState, modifier = modifier.fillMaxWidth()) {
        itemsIndexed(document.blocks, key = { _, block -> block.blockId }) { index, block ->
            ReaderBlockText(
                block = block,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPosition(index) }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun PagedReader(
    document: ReaderDocument,
    index: Int,
    onPosition: (Int) -> Unit,
    dual: Boolean,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ReaderBlockText(document.blocks[index], Modifier.weight(1f))
            if (dual) {
                val second = document.blocks.getOrNull(index + 1)
                if (second != null) ReaderBlockText(second, Modifier.weight(1f))
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TsuyomiButton(
                text = stringResource(R.string.reader_previous),
                onClick = { onPosition((index - if (dual) 2 else 1).coerceAtLeast(0)) },
                enabled = index > 0,
                style = TsuyomiButtonStyle.SECONDARY,
            )
            Text(stringResource(R.string.reader_position, index + 1, document.blocks.size))
            TsuyomiButton(
                text = stringResource(R.string.reader_next),
                onClick = { onPosition((index + if (dual) 2 else 1).coerceAtMost(document.blocks.lastIndex)) },
                enabled = index < document.blocks.lastIndex,
            )
        }
    }
}

@Composable
private fun ReaderBlockText(block: ReaderBlock, modifier: Modifier) {
    when (block) {
        is ReaderBlock.Paragraph -> Text(block.text, modifier, style = MaterialTheme.typography.bodyLarge)
        is ReaderBlock.Heading -> Text(block.text, modifier, style = MaterialTheme.typography.titleMedium)
        is ReaderBlock.Image -> Text(block.altText ?: stringResource(R.string.reader_image), modifier)
    }
}

private fun ReaderPresentation.label(): Int = when (this) {
    ReaderPresentation.SCROLL -> R.string.reader_mode_scroll
    ReaderPresentation.PAGED -> R.string.reader_mode_paged
    ReaderPresentation.DUAL_PAGE -> R.string.reader_mode_dual
}
