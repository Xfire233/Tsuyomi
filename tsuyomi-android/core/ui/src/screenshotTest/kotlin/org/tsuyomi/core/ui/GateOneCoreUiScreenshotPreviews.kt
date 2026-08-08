/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import org.tsuyomi.core.display.ColorSchemePreference
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.display.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.ui.components.AppScaffold
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiNavigation
import org.tsuyomi.core.ui.components.TsuyomiNavigationItem
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.layout.TsuyomiWindowSize
import org.tsuyomi.core.ui.theme.TsuyomiTheme

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Preview(name = "phone-portrait", device = "spec:width=360dp,height=800dp,dpi=420", locale = "zh-rCN")
@Preview(name = "phone-landscape", device = "spec:width=800dp,height=360dp,dpi=420", locale = "zh-rCN")
@Preview(name = "double-compact", device = "spec:width=360dp,height=320dp,dpi=420", locale = "zh-rCN")
@Preview(name = "breakpoint-below", device = "spec:width=599dp,height=800dp,dpi=320", locale = "zh-rCN")
@Preview(name = "breakpoint-at", device = "spec:width=600dp,height=800dp,dpi=320", locale = "zh-rCN")
@Preview(name = "expanded", device = "spec:width=840dp,height=900dp,dpi=240", locale = "zh-rCN")
private annotation class GateOneDevices

@PreviewTest
@GateOneDevices
@Composable
fun StandardEmptyStateScreenshots() {
    GateOneCoreShell(
        environment = standardEnvironment(),
        selectedRoute = "library",
        title = "书架",
    ) {
        StateView(
            kind = TsuyomiStateKind.EMPTY,
            title = "书架是空的",
            message = "收藏的书籍会显示在这里。当前还没有可添加书籍的内容源。",
            actionLabel = "前往浏览",
            onAction = {},
        )
    }
}


@Composable
private fun GateOneCoreShell(
    environment: DisplayEnvironment,
    selectedRoute: String,
    title: String,
    navigateUp: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    DisplayEnvironmentProvider(environment) {
        TsuyomiTheme(environment) {
            Surface(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val items = listOf(
                        TsuyomiNavigationItem("library", "书架", TsuyomiIcons.Shelf),
                        TsuyomiNavigationItem("browse", "浏览", TsuyomiIcons.Compass),
                        TsuyomiNavigationItem("more", "更多", TsuyomiIcons.More),
                    )
                    AppScaffold(
                        windowSize = TsuyomiWindowSize(
                            widthDp = maxWidth.value.toInt(),
                            heightDp = maxHeight.value.toInt(),
                        ),
                        topBar = { TsuyomiTopBar(title = title, onNavigateUp = navigateUp) },
                        navigation = { layout ->
                            TsuyomiNavigation(
                                layout = layout,
                                items = items,
                                selectedRoute = selectedRoute,
                                onSelect = {},
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                        content = content,
                    )
                }
            }
        }
    }
}

private fun standardEnvironment() = DisplayEnvironment(
    preferences = DisplayPreferences(
        displayPreference = DisplayPreference.STANDARD,
        colorSchemePreference = ColorSchemePreference.LIGHT,
    ),
    effectiveProfile = DisplayProfile.STANDARD,
    decisionReason = DisplayDecisionReason.MANUAL_STANDARD,
    detectedDeviceLabel = null,
    dynamicColorEligible = false,
    dynamicColorEffective = false,
    effectiveDarkTheme = false,
    motionPolicy = MotionPolicy.INSTANT,
    redrawEpoch = 0,
)
