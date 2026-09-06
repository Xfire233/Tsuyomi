/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.feature.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import org.tsuyomi.core.display.ColorSchemePreference
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.display.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.RemoteTarget
import org.tsuyomi.shared.sourcecontract.SourceBookSummary

@PreviewTest
@Preview(name = "remote-mirror", device = "spec:width=360dp,height=800dp,dpi=420", locale = "zh-rCN")
@Composable
fun RemoteMirrorStandardScreenshot() {
    val environment = DisplayEnvironment(
        preferences = DisplayPreferences(DisplayPreference.STANDARD, ColorSchemePreference.LIGHT),
        effectiveProfile = DisplayProfile.STANDARD,
        decisionReason = DisplayDecisionReason.MANUAL_STANDARD,
        detectedDeviceLabel = null,
        dynamicColorEligible = false,
        dynamicColorEffective = false,
        effectiveDarkTheme = false,
        motionPolicy = MotionPolicy.INSTANT,
        redrawEpoch = 0,
    )
    val books = listOf(
        SourceBookSummary(BookIdentity(SourceId, "1"), "文学少女", "野村美月", null, "https://example.com/1"),
        SourceBookSummary(BookIdentity(SourceId, "2"), "狼与香辛料", "支仓冻砂", null, "https://example.com/2"),
        SourceBookSummary(BookIdentity(SourceId, "3"), "奇诺之旅", "时雨泽惠一", null, "https://example.com/3"),
    )
    DisplayEnvironmentProvider(environment) {
        TsuyomiTheme(environment) {
            Surface(Modifier.fillMaxSize()) {
                RemoteLibraryScreen(
                    sourceId = SourceId,
                    sourceName = "文库8",
                    books = books,
                    selectedIds = emptySet(),
                    state = RemoteLibraryViewState.CONTENT,
                    message = null,
                    copyConfirmationVisible = false,
                    onNavigateUp = {},
                    onRefresh = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onRequestCopy = {},
                    onDismissCopy = {},
                    onConfirmCopy = {},
                    onOpenVerification = {},
                    onOpenBook = {},
                    targets = listOf(
                        RemoteTarget("0", "默认书架", kind = "default"),
                        RemoteTarget("1", "特别收藏"),
                    ),
                    mirrorPinned = true,
                )
            }
        }
    }
}

private const val SourceId = "org.tsuyomi.wenku8"
