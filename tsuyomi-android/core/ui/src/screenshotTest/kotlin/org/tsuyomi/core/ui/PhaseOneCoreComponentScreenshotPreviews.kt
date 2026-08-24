/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import org.tsuyomi.core.ui.components.InfoBanner
import org.tsuyomi.core.ui.components.PaginationBar
import org.tsuyomi.core.ui.components.SegmentedSelector
import org.tsuyomi.core.ui.components.SettingsSwitchRow
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiDialogPane
import org.tsuyomi.core.ui.components.TsuyomiSegment
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.theme.TsuyomiTheme

private const val ComponentPhone = "spec:width=360dp,height=800dp,dpi=420"
private const val ComponentEInk = "spec:width=843dp,height=1120dp,dpi=240"

@PreviewTest
@Preview(name = "loading", device = ComponentPhone, locale = "zh-rCN")
@Composable
fun LoadingStateScreenshot() {
    CorePreview(standardEnvironment()) {
        StateView(TsuyomiStateKind.LOADING, "正在加载", message = "正在读取本地状态…")
    }
}

@PreviewTest
@Preview(name = "error", device = ComponentPhone, locale = "zh-rCN")
@Composable
fun ErrorStateScreenshot() {
    CorePreview(standardEnvironment()) {
        StateView(
            TsuyomiStateKind.ERROR,
            "无法加载",
            message = "请检查后重试。",
            actionLabel = "重试",
            onAction = {},
        )
    }
}

@PreviewTest
@Preview(name = "offline", device = ComponentPhone, locale = "zh-rCN")
@Composable
fun OfflineStateScreenshot() {
    CorePreview(standardEnvironment()) {
        Column {
            InfoBanner(title = "当前离线", message = "继续显示已保存内容。")
            StateView(TsuyomiStateKind.EMPTY, "暂无缓存", message = "联网后可加载内容。")
        }
    }
}

@PreviewTest
@Preview(name = "pagination-first-middle-last", device = ComponentPhone, locale = "zh-rCN")
@Composable
fun PaginationStatesScreenshot() {
    CorePreview(standardEnvironment()) {
        Column(Modifier.padding(TsuyomiSpacing.Md)) {
            PaginationBar(1, 5, onPrevious = {}, onNext = {})
            PaginationBar(3, 5, onPrevious = {}, onNext = {})
            PaginationBar(5, 5, onPrevious = {}, onNext = {})
        }
    }
}

@PreviewTest
@Preview(name = "controls-focus-disabled-error", device = ComponentPhone, locale = "zh-rCN")
@Composable
fun ControlStatesScreenshot() {
    val focusRequester = FocusRequester()
    CorePreview(standardEnvironment()) {
        Column(Modifier.padding(TsuyomiSpacing.Md)) {
            TsuyomiButton(
                text = "已聚焦操作",
                onClick = {},
                modifier = Modifier.focusRequester(focusRequester),
            )
            SettingsSwitchRow(
                title = "动态颜色",
                summary = "保留当前偏好",
                checked = true,
                enabled = false,
                disabledReason = "当前环境不可用",
                onCheckedChange = {},
            )
            SegmentedSelector(
                options = listOf(TsuyomiSegment("a", "选项一"), TsuyomiSegment("b", "选项二")),
                selected = "a",
                onSelect = {},
                errorMessage = "无法保存选择",
            )
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@PreviewTest
@Preview(name = "eink-full-window-no-scrim", device = ComponentEInk, locale = "zh-rCN")
@Composable
fun EInkDialogScreenshot() {
    CorePreview(eInkEnvironment()) {
        TsuyomiDialogPane(
            title = "确认操作",
            text = "墨水屏对话框使用完全不透明的整窗纸面。",
            confirmLabel = "确认",
            onConfirm = {},
            dismissLabel = "取消",
            onDismiss = {},
            fullWindow = true,
        )
    }
}

@Composable
private fun CorePreview(
    environment: DisplayEnvironment,
    content: @Composable () -> Unit,
) {
    DisplayEnvironmentProvider(environment) {
        TsuyomiTheme(environment) {
            Surface(Modifier.fillMaxSize(), content = content)
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

private fun eInkEnvironment() = DisplayEnvironment(
    preferences = DisplayPreferences(
        displayPreference = DisplayPreference.EINK,
        colorSchemePreference = ColorSchemePreference.DARK,
        dynamicColorEnabled = true,
    ),
    effectiveProfile = DisplayProfile.EINK,
    decisionReason = DisplayDecisionReason.MANUAL_EINK,
    detectedDeviceLabel = null,
    dynamicColorEligible = false,
    dynamicColorEffective = false,
    effectiveDarkTheme = false,
    motionPolicy = MotionPolicy.INSTANT,
    redrawEpoch = 0,
)
