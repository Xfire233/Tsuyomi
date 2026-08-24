@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import org.tsuyomi.prototype.uiatlas.components.AtlasFeatureIntroduction
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.tsuyomi.prototype.uiatlas.components.AtlasOverflowItem
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.components.AtlasBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasButton
import org.tsuyomi.prototype.uiatlas.components.AtlasButtonStyle
import org.tsuyomi.prototype.uiatlas.components.AtlasChip
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.AtlasInfoBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationPhase
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationStatus
import org.tsuyomi.prototype.uiatlas.components.AtlasScaffold
import org.tsuyomi.prototype.uiatlas.components.AtlasStateKind
import org.tsuyomi.prototype.uiatlas.components.AtlasStateView
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBar
import org.tsuyomi.prototype.uiatlas.fixtures.MoreAtlasFixtures
import org.tsuyomi.prototype.uiatlas.fixtures.MoreFeatureIntroductionFixture
import org.tsuyomi.prototype.uiatlas.fixtures.MoreTransferIssueFixture
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.AtlasProfile
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasNavigation
import org.tsuyomi.prototype.uiatlas.runtime.LocalPrototypeRuntime
import org.tsuyomi.prototype.uiatlas.runtime.prototypeRepository
import org.tsuyomi.prototype.uiatlas.theme.AtlasEInkPalette
import org.tsuyomi.prototype.uiatlas.theme.AtlasMotion
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment
import org.tsuyomi.prototype.uiatlas.theme.atlasFocusRing

private enum class MoreModal {
    DISPLAY_UNKNOWN_SCHEMA,
    DISPLAY_RESET,
    DATA_IMPORT_TSUYOMI,
    DATA_IMPORT_HIKARI,
    DATA_EXPORT,
    HELP_FEATURE_INTRODUCTION,
    ABOUT_LICENSE,
    REPORT_RECOVERY,
}

/** Full-screen, fixture-only route family for atlas routes 19–23. */
@Composable
fun MoreAtlasScreen(context: AtlasContext, modifier: Modifier = Modifier) {
    val navigation = LocalAtlasNavigation.current
    var stateName by rememberSaveable(context.route.name, context.state.name) { mutableStateOf(context.state.name) }
    var modalName by rememberSaveable(context.route.name, context.state.name) { mutableStateOf(initialModal(context.route, context.state)?.name.orEmpty()) }
    var selectedIntroduction by rememberSaveable(context.route.name, context.state.name) { mutableIntStateOf(0) }
    var reportExpanded by rememberSaveable(context.route.name, context.state.name) { mutableStateOf(context.route == AtlasRoute.MORE_DATA_REPORT && context.showModal) }

    val route = context.route
    val pageContext = context.copy(state = AtlasPageState.valueOf(stateName))
    val activeModal = modalName.takeIf { it.isNotEmpty() }?.let(MoreModal::valueOf)

    fun navigate(destination: AtlasRoute, modal: MoreModal? = null) {
        stateName = AtlasPageState.CONTENT.name
        modalName = modal?.name.orEmpty()
        if (destination == AtlasRoute.MORE_DATA_REPORT) reportExpanded = false
        navigation.navigate(destination)
    }

    fun closeModal() { modalName = "" }
    fun resolvePrimaryState() { stateName = AtlasPageState.CONTENT.name }

    when (route) {
        AtlasRoute.MORE -> MoreRootPage(
            context = pageContext,
            modifier = modifier,
            onResolvePrimary = ::resolvePrimaryState,
            onDisplay = { navigate(AtlasRoute.MORE_DISPLAY) },
            onReader = { navigate(AtlasRoute.MORE_READER) },
            onData = { navigate(AtlasRoute.MORE_DATA) },
            onHelp = { navigate(AtlasRoute.MORE_HELP) },
            onAbout = { navigate(AtlasRoute.MORE_ABOUT) },
        )
        AtlasRoute.MORE_DISPLAY -> MoreDisplayPage(
            context = pageContext,
            modifier = modifier,
            activeModal = activeModal,
            onOpenModal = { modalName = it.name },
            onCloseModal = ::closeModal,
            onUp = navigation.up,
            onResolvePrimary = ::resolvePrimaryState,
        )
        AtlasRoute.MORE_READER -> MoreReaderPage(
            context = pageContext,
            modifier = modifier,
            onUp = navigation.up,
            onResolvePrimary = ::resolvePrimaryState,
        )
        AtlasRoute.MORE_DATA -> MoreDataPage(
            context = pageContext,
            modifier = modifier,
            activeModal = activeModal,
            onOpenModal = { modalName = it.name },
            onCloseModal = ::closeModal,
            onUp = navigation.up,
            onOpenReport = { navigate(AtlasRoute.MORE_DATA_REPORT) },
            onResolvePrimary = ::resolvePrimaryState,
        )
        AtlasRoute.MORE_DATA_REPORT -> MoreDataReportPage(
            context = pageContext,
            modifier = modifier,
            expanded = reportExpanded,
            activeModal = activeModal,
            onExpandedChange = { reportExpanded = it },
            onOpenRecovery = { modalName = MoreModal.REPORT_RECOVERY.name },
            onCloseModal = ::closeModal,
            onUpToData = navigation.up,
            onResolvePrimary = ::resolvePrimaryState,
        )
        AtlasRoute.MORE_HELP -> MoreHelpPage(
            context = pageContext,
            modifier = modifier,
            activeModal = activeModal,
            selectedIntroduction = selectedIntroduction,
            onSelectIntroduction = { index ->
                selectedIntroduction = index
                modalName = MoreModal.HELP_FEATURE_INTRODUCTION.name
            },
            onCloseModal = ::closeModal,
            onOpenDisplayReset = { navigate(AtlasRoute.MORE_DISPLAY, MoreModal.DISPLAY_RESET) },
            onUp = navigation.up,
            onResolvePrimary = ::resolvePrimaryState,
        )
        AtlasRoute.MORE_ABOUT -> MoreAboutPage(
            context = pageContext,
            modifier = modifier,
            activeModal = activeModal,
            onOpenLicense = { modalName = MoreModal.ABOUT_LICENSE.name },
            onCloseModal = ::closeModal,
            onUp = navigation.up,
            onResolvePrimary = ::resolvePrimaryState,
        )
        else -> MoreRootPage(
            context = pageContext.copy(route = AtlasRoute.MORE),
            modifier = modifier,
            onResolvePrimary = ::resolvePrimaryState,
            onDisplay = { navigate(AtlasRoute.MORE_DISPLAY) },
            onReader = { navigate(AtlasRoute.MORE_READER) },
            onData = { navigate(AtlasRoute.MORE_DATA) },
            onHelp = { navigate(AtlasRoute.MORE_HELP) },
            onAbout = { navigate(AtlasRoute.MORE_ABOUT) },
        )
    }
}

private fun initialModal(route: AtlasRoute, state: AtlasPageState): MoreModal? {
    if (state != AtlasPageState.MODAL) return null
    return when (route) {
        AtlasRoute.MORE_DISPLAY -> MoreModal.DISPLAY_UNKNOWN_SCHEMA
        AtlasRoute.MORE_DATA -> MoreModal.DATA_IMPORT_TSUYOMI
        AtlasRoute.MORE_HELP -> MoreModal.HELP_FEATURE_INTRODUCTION
        AtlasRoute.MORE_ABOUT -> MoreModal.ABOUT_LICENSE
        else -> null
    }
}

@Composable
private fun MoreRootPage(
    context: AtlasContext,
    modifier: Modifier,
    onResolvePrimary: () -> Unit,
    onDisplay: () -> Unit,
    onReader: () -> Unit,
    onData: () -> Unit,
    onHelp: () -> Unit,
    onAbout: () -> Unit,
) {
    MorePage(context, AtlasStrings.ROOT_MORE, modifier, null, onResolvePrimary) {
        MoreScrollableContent {
            val handlers = listOf(onDisplay, onReader, onData, onHelp, onAbout)
            var handlerIndex = 0
            MoreAtlasFixtures.destinationGroups.forEach { group ->
                MoreSectionHeader(group.title)
                MoreRowGroup {
                    group.destinations.forEachIndexed { index, destination ->
                        MoreActionRow(
                            title = destination.title,
                            summary = destination.summary,
                            onClick = handlers[handlerIndex++],
                            showDivider = index != group.destinations.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreDisplayPage(
    context: AtlasContext,
    modifier: Modifier,
    activeModal: MoreModal?,
    onOpenModal: (MoreModal) -> Unit,
    onCloseModal: () -> Unit,
    onUp: () -> Unit,
    onResolvePrimary: () -> Unit,
) {
    BackHandler(enabled = activeModal == null) { onUp() }
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val fixture = MoreAtlasFixtures.display
    val eInk = context.profile == AtlasProfile.EINK
    var selectedProfile by rememberSaveable(runtime.persistent) {
        mutableStateOf(if (runtime.persistent) repository.string("more.display.profile", profileLabel(context.profile)) else profileLabel(context.profile))
    }
    var selectedTheme by rememberSaveable(runtime.persistent) {
        mutableStateOf(if (runtime.persistent) repository.string("more.display.theme", fixture.storedTheme) else fixture.storedTheme)
    }
    var dynamicColorStored by rememberSaveable(runtime.persistent) {
        mutableStateOf(if (runtime.persistent) repository.boolean("display.dynamic", fixture.dynamicColorStored) else fixture.dynamicColorStored)
    }
    var eInkRedrawStored by rememberSaveable(runtime.persistent) {
        mutableStateOf(if (runtime.persistent) repository.boolean("display.einkRedraw", fixture.eInkRedrawStored) else fixture.eInkRedrawStored)
    }
    var unknownPayloadPresent by rememberSaveable {
        mutableStateOf(activeModal == MoreModal.DISPLAY_UNKNOWN_SCHEMA)
    }
    var mutation by remember { mutableStateOf<AtlasMutationStatus?>(null) }

    fun blockedMutation() {
        mutation = AtlasMutationStatus(
            phase = AtlasMutationPhase.ERROR,
            message = "未写入：架构版本 v${fixture.unknownSchemaVersion} 的界面设置仍按原样只读保留。",
            retryLabel = "查看说明",
            onRetry = { onOpenModal(MoreModal.DISPLAY_UNKNOWN_SCHEMA) },
        )
    }
    fun acceptPreference(message: String, update: () -> Unit) {
        if (unknownPayloadPresent) blockedMutation() else {
            update()
            mutation = AtlasMutationStatus(AtlasMutationPhase.SUCCESS, message)
        }
    }

    MorePage(
        context = context,
        title = "显示",
        modifier = modifier,
        onUp = onUp,
        onResolvePrimary = onResolvePrimary,
        mutation = mutation ?: if (context.showMutationBanner) {
            AtlasMutationStatus(
                AtlasMutationPhase.SUCCESS,
                "已保存显示设置；持久值与当前有效值已重新计算。",
            )
        } else null,
    ) {
        MoreScrollableContent {
            if (unknownPayloadPresent) {
                MoreInfoPanel(
                    title = "只读保留：架构版本 v${fixture.unknownSchemaVersion}",
                    body = "当前使用内存中的安全默认值；所有界面设置写入已阻止，未知载荷不会被迁移、降级或部分覆盖。",
                    errorTone = true,
                    actionLabel = "查看处理方式",
                    onAction = { onOpenModal(MoreModal.DISPLAY_UNKNOWN_SCHEMA) },
                )
            }

            MoreSectionHeader("显示配置")
            MoreSegmentedSelector(
                title = "首选显示配置",
                selected = selectedProfile,
                options = fixture.profileOptions,
                enabled = !unknownPayloadPresent,
                disabledReason = if (unknownPayloadPresent) "架构版本较新；重置前禁止写入。" else null,
                supportingText = "标准与电子墨水共享路由、任务和结果；呈现策略由有效配置决定。",
                onSelect = { choice ->
                    acceptPreference("已保存首选显示配置：$choice。") {
                        selectedProfile = choice
                        repository.putString("more.display.profile", choice, "DisplayProfileChanged")
                    }
                },
            )

            MoreSectionHeader("颜色")
            MoreSegmentedSelector(
                title = "主题",
                selected = selectedTheme,
                options = fixture.themeOptions,
                enabled = !unknownPayloadPresent && !eInk,
                disabledReason = when {
                    unknownPayloadPresent -> "架构版本较新；重置前禁止写入。"
                    eInk -> "当前有效：电子墨水固定使用不透明单色方案；已保存主题会在标准配置恢复。"
                    else -> null
                },
                supportingText = if (eInk) "已保存：$selectedTheme · 当前有效：电子墨水单色" else "已保存并生效：$selectedTheme",
                onSelect = { choice ->
                    acceptPreference("已保存主题：$choice。") {
                        selectedTheme = choice
                        repository.putString("more.display.theme", choice, "DisplayThemeChanged")
                    }
                },
            )
            MoreSwitchRow(
                title = "动态配色",
                summary = if (eInk) "已保存：${onOff(dynamicColorStored)} · 当前有效：关闭" else "已保存并生效：${onOff(dynamicColorStored)}",
                supportingText = fixture.dynamicColorCapability,
                checked = dynamicColorStored,
                enabled = !unknownPayloadPresent && !eInk,
                disabledReason = when {
                    unknownPayloadPresent -> "架构版本较新；重置前禁止写入。"
                    eInk -> "电子墨水不使用动态配色；该偏好仍为标准配置保留。"
                    else -> null
                },
                onCheckedChange = { checked ->
                    acceptPreference("动态配色已${if (checked) "开启" else "关闭"}。") {
                        dynamicColorStored = checked
                        repository.putBoolean("display.dynamic", checked, "DynamicColorChanged")
                    }
                },
            )

            if (eInk) {
                MoreSectionHeader("电子墨水")
                MoreSwitchRow(
                    title = "设置变更后整页重绘",
                    summary = "已保存并生效：${onOff(eInkRedrawStored)}",
                    supportingText = "一次不透明整页提交，避免依赖淡入、阴影或透明层表达结果。",
                    checked = eInkRedrawStored,
                    enabled = !unknownPayloadPresent,
                    disabledReason = if (unknownPayloadPresent) "架构版本较新；重置前禁止写入。" else null,
                    onCheckedChange = { checked ->
                        acceptPreference("整页重绘已${if (checked) "开启" else "关闭"}。") {
                            eInkRedrawStored = checked
                            repository.putBoolean("display.einkRedraw", checked, "EInkRedrawChanged")
                        }
                    },
                )
                MoreInfoPanel(
                    title = "当前有效约束",
                    body = "无渐变、透明度、阴影、涟漪与中间动画帧；设置结果以文字、边框和不透明填充共同表达。",
                )
            }

            MoreSectionHeader("重置")
            MoreActionRow(
                title = "重置界面设置",
                summary = "仅重置界面偏好；书籍与其他领域数据不变",
                onClick = { onOpenModal(MoreModal.DISPLAY_RESET) },
            )
        }
    }

    if (activeModal == MoreModal.DISPLAY_UNKNOWN_SCHEMA) {
        MoreDialog(
            title = "界面设置来自更新版本；保留或重置",
            dismissOnOutside = true,
            onDismiss = onCloseModal,
            safeLabel = "保留并继续",
            onSafe = onCloseModal,
            confirmLabel = "查看重置范围",
            onConfirm = { onOpenModal(MoreModal.DISPLAY_RESET) },
        ) {
            MoreDialogParagraph("检测到界面偏好架构版本 v${fixture.unknownSchemaVersion}。当前版本会逐字节保留该载荷，只在内存使用安全默认值，并阻止所有偏好写入。")
            MoreDialogParagraph("“保留并继续”不会修改载荷；兼容的更新版本仍可继续迁移。唯一的当前写入路径是明确执行界面设置重置。")
        }
    }
    if (activeModal == MoreModal.DISPLAY_RESET) {
        MoreDialog(
            title = "重置界面设置",
            dismissOnOutside = false,
            onDismiss = onCloseModal,
            safeLabel = AtlasStrings.CANCEL,
            onSafe = onCloseModal,
            confirmLabel = "重置界面设置",
            onConfirm = {
                selectedProfile = "标准"
                selectedTheme = "跟随系统"
                dynamicColorStored = false
                eInkRedrawStored = true
                repository.putString("more.display.profile", "标准", "DisplaySettingsReset")
                repository.putString("more.display.theme", "跟随系统", "DisplaySettingsReset")
                repository.putBoolean("display.dynamic", false, "DisplaySettingsReset")
                repository.putBoolean("display.einkRedraw", true, "DisplaySettingsReset")
                unknownPayloadPresent = false
                mutation = AtlasMutationStatus(
                    AtlasMutationPhase.SUCCESS,
                    "界面设置已恢复为宪章默认值；书籍、收藏、进度、历史、来源与凭据均未改变。",
                )
                onCloseModal()
            },
        ) {
            MoreDialogParagraph(fixture.resetScope)
            MoreDialogParagraph("此操作不会恢复为“无偏好”，而是恢复到当前界面宪章的明确默认值。")
        }
    }
}

@Composable
private fun MoreReaderPage(
    context: AtlasContext,
    modifier: Modifier,
    onUp: () -> Unit,
    onResolvePrimary: () -> Unit,
) {
    BackHandler { onUp() }
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val fixture = MoreAtlasFixtures.reader
    val eInk = context.profile == AtlasProfile.EINK
    var fontSize by rememberSaveable(runtime.persistent) { mutableStateOf(if (runtime.persistent) repository.string("more.reader.fontSize", fixture.storedFontSize) else fixture.storedFontSize) }
    var lineSpacing by rememberSaveable(runtime.persistent) { mutableStateOf(if (runtime.persistent) repository.string("more.reader.lineSpacing", fixture.storedLineSpacing) else fixture.storedLineSpacing) }
    var layout by rememberSaveable(runtime.persistent) { mutableStateOf(if (runtime.persistent) repository.string("more.reader.layout", fixture.storedLayout) else fixture.storedLayout) }
    var pageAnimation by rememberSaveable(runtime.persistent) { mutableStateOf(if (runtime.persistent) repository.boolean("reader.pageAnimation", fixture.pageAnimationStored) else fixture.pageAnimationStored) }
    var volumePaging by rememberSaveable(runtime.persistent) { mutableStateOf(if (runtime.persistent) repository.boolean("reader.volumePaging", fixture.volumePagingStored) else fixture.volumePagingStored) }
    var volumeMedia by rememberSaveable(runtime.persistent) { mutableStateOf(if (runtime.persistent) repository.boolean("reader.volumeMedia", fixture.volumeMediaStored) else fixture.volumeMediaStored) }
    var progressVisible by rememberSaveable(runtime.persistent) { mutableStateOf(if (runtime.persistent) repository.boolean("reader.progressVisible", fixture.progressStored) else fixture.progressStored) }
    var mutation by remember { mutableStateOf<AtlasMutationStatus?>(null) }
    fun success(message: String) { mutation = AtlasMutationStatus(AtlasMutationPhase.SUCCESS, message) }

    MorePage(
        context = context,
        title = "阅读",
        modifier = modifier,
        onUp = onUp,
        onResolvePrimary = onResolvePrimary,
        mutation = mutation ?: if (context.showMutationBanner) {
            AtlasMutationStatus(
                AtlasMutationPhase.ERROR,
                "未创建无效组合：音量键翻页与阅读时媒体音量调节不能同时启用。",
            )
        } else null,
    ) {
        MoreScrollableContent {
            MoreSectionHeader("排版")
            MoreSegmentedSelector(
                title = "字号",
                selected = fontSize,
                options = fixture.fontSizeOptions,
                supportingText = "阅读正文使用独立排版令牌，行高始终不少于字号的 1.5 倍。",
                onSelect = { choice ->
                    fontSize = choice
                    repository.putString("more.reader.fontSize", choice, "ReaderDefaultFontSizeChanged")
                    success("阅读字号已设为：$choice。")
                },
            )
            MoreSegmentedSelector(
                title = "行距",
                selected = lineSpacing,
                options = fixture.lineSpacingOptions,
                onSelect = { choice ->
                    lineSpacing = choice
                    repository.putString("more.reader.lineSpacing", choice, "ReaderDefaultLineSpacingChanged")
                    success("阅读行距已设为：$choice。")
                },
            )

            MoreSectionHeader("布局")
            MoreSegmentedSelector(
                title = "首选阅读布局",
                selected = layout,
                options = fixture.layoutOptions,
                disabledOptions = if (eInk) setOf("连续滚动") else emptySet(),
                supportingText = if (eInk) "已保存：$layout · 当前有效：${fixture.eInkEffectiveLayout}。连续滚动在电子墨水下不可选。" else "当前有效：$layout",
                onSelect = { choice ->
                    layout = choice
                    repository.putString("more.reader.layout", choice, "ReaderDefaultLayoutChanged")
                    success("首选阅读布局已设为：$choice。")
                },
            )
            MoreSwitchRow(
                title = "分页动效",
                summary = if (eInk) "已保存：${onOff(pageAnimation)} · 当前有效：关闭" else "已保存并生效：${onOff(pageAnimation)}",
                supportingText = if (eInk) "电子墨水即时提交新页，不生成过渡帧。" else "仅在明确开启时使用克制的分页过渡；新指令会取代旧指令。",
                checked = pageAnimation,
                enabled = !eInk,
                disabledReason = if (eInk) "电子墨水固定为即时分页；已保存值继续保留。" else null,
                onCheckedChange = { checked ->
                    pageAnimation = checked
                    repository.putBoolean("reader.pageAnimation", checked, "ReaderPageAnimationChanged")
                    success("分页动效已${if (checked) "开启" else "关闭"}。")
                },
            )

            MoreSectionHeader("导航")
            MoreSwitchRow(
                title = "音量键翻页",
                summary = onOff(volumePaging),
                supportingText = "屏幕翻页按钮始终保留为可见等价路径。",
                checked = volumePaging,
                onCheckedChange = { checked ->
                    volumePaging = checked
                    repository.putBoolean("reader.volumePaging", checked, "ReaderVolumePagingChanged")
                    if (checked && volumeMedia) {
                        volumeMedia = false
                        repository.putBoolean("reader.volumeMedia", false, "ReaderVolumeMediaChanged")
                        success("已开启音量键翻页，并自动关闭阅读时媒体音量调节，未产生无效组合。")
                    } else success("音量键翻页已${if (checked) "开启" else "关闭"}。")
                },
            )
            MoreSwitchRow(
                title = "阅读时保留媒体音量调节",
                summary = onOff(volumeMedia),
                supportingText = if (volumePaging) "先关闭“音量键翻页”才能启用；两项不会同时生效。" else "启用后，音量键保持系统媒体音量行为。",
                checked = volumeMedia,
                enabled = !volumePaging,
                disabledReason = if (volumePaging) "与“音量键翻页”互斥。" else null,
                onCheckedChange = { checked ->
                    volumeMedia = checked
                    repository.putBoolean("reader.volumeMedia", checked, "ReaderVolumeMediaChanged")
                    success("阅读时媒体音量调节已${if (checked) "开启" else "关闭"}。")
                },
            )

            MoreSectionHeader("进度")
            MoreSwitchRow(
                title = "显示语义进度",
                summary = onOff(progressVisible),
                supportingText = "显示章节与百分比；像素偏移和渲染页码不会成为持久进度。",
                checked = progressVisible,
                onCheckedChange = { checked ->
                    progressVisible = checked
                    repository.putBoolean("reader.progressVisible", checked, "ReaderProgressVisibilityChanged")
                    success("语义进度显示已${if (checked) "开启" else "关闭"}。")
                },
            )
            if (eInk) {
                MoreInfoPanel(
                    title = "电子墨水当前有效约束",
                    body = "阅读表面锁定分页；分页动效关闭；位置状态持续显示；所有设置结果即时、不透明地提交。标准配置中的偏好仍被保留。",
                )
            }
        }
    }
}

@Composable
private fun MoreDataPage(
    context: AtlasContext,
    modifier: Modifier,
    activeModal: MoreModal?,
    onOpenModal: (MoreModal) -> Unit,
    onCloseModal: () -> Unit,
    onUp: () -> Unit,
    onOpenReport: () -> Unit,
    onResolvePrimary: () -> Unit,
) {
    BackHandler(enabled = activeModal == null) { onUp() }
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    var mutation by remember { mutableStateOf<AtlasMutationStatus?>(null) }
    var includeHistory by rememberSaveable(runtime.persistent) { mutableStateOf(if (runtime.persistent) repository.boolean("data.export.includeHistory") else false) }
    var tutorialOpen by remember(context.tutorial) { mutableStateOf(context.tutorial) }
    MorePage(
        context = context,
        title = "数据",
        modifier = modifier,
        onUp = onUp,
        onResolvePrimary = onResolvePrimary,
        mutation = mutation ?: if (context.showMutationBanner) {
            AtlasMutationStatus(
                AtlasMutationPhase.SUCCESS,
                "导出预览已准备；尚未创建文件，也没有改变任何数据。",
            )
        } else null,
        overflow = listOf(AtlasOverflowItem("功能说明") { tutorialOpen = true }),
    ) {
        MoreScrollableContent {
            MoreInfoPanel(
                title = "导入、迁移、导出和报告分开",
                body = "每个任务都有独立入口。选择器取消或对话框取消均保持零状态变化；导入只会在审阅后明确确认。",
            )
            MoreSectionHeader("数据任务")
            MoreRowGroup {
                MoreAtlasFixtures.dataEntries.forEachIndexed { index, entry ->
                    MoreActionRow(
                        title = entry.title,
                        summary = entry.summary,
                        onClick = when (index) {
                            0 -> ({ onOpenModal(MoreModal.DATA_IMPORT_TSUYOMI) })
                            1 -> ({ onOpenModal(MoreModal.DATA_IMPORT_HIKARI) })
                            2 -> ({ onOpenModal(MoreModal.DATA_EXPORT) })
                            else -> onOpenReport
                        },
                        showDivider = index != MoreAtlasFixtures.dataEntries.lastIndex,
                    )
                }
            }
            MoreSectionHeader("支持的固定审阅格式")
            MoreAtlasFixtures.transferReport.supportedFormats.forEach { format ->
                MoreInfoRow(format, "可审阅")
            }
        }
    }
    if (tutorialOpen) {
        AtlasFeatureIntroduction(
            featureId = "data-transfer",
            tutorialVersion = 2,
            title = "功能说明：数据导入与导出",
            summary = "导入先审阅再确认；导出历史默认关闭。",
            points = listOf(
                "语义进度默认可移植，浏览/搜索历史需要单独选择。",
                "凭据、镜像、更新运行态、远程回执、缓存和界面偏好永不传输。",
                "导入不会启用调度、镜像或网站写入。",
                "关闭说明不会打开文件或改变数据。",
            ),
            onDismiss = { tutorialOpen = false },
        )
    }

    when (activeModal) {
        MoreModal.DATA_IMPORT_TSUYOMI, MoreModal.DATA_IMPORT_HIKARI -> {
            val hikari = activeModal == MoreModal.DATA_IMPORT_HIKARI
            MoreDialog(
                title = if (hikari) "从 Hikari Novel 导入" else "导入 Tsuyomi 数据",
                dismissOnOutside = false,
                onDismiss = onCloseModal,
                safeLabel = AtlasStrings.CANCEL,
                onSafe = onCloseModal,
                confirmLabel = "查看固定审阅报告",
                onConfirm = {
                    repository.record("TransferReviewOpened", if (hikari) "hikari" else "tsuyomi", "success")
                    onCloseModal()
                    onOpenReport()
                },
            ) {
                MoreDialogParagraph(
                    if (hikari) "固定迁移样例会把书架条目、收藏夹与语义进度映射到本地审阅报告；不会访问真实文件或推断来源身份。"
                    else "固定 Tsuyomi 数据包样例包含 412 本书、18 个收藏夹与 1296 条本地标注。下一步只打开审阅报告，不执行导入。"
                )
                MoreDialogParagraph("取消此对话框不会改变任何状态。警告与冲突必须在报告中明确审阅。")
            }
        }
        MoreModal.DATA_EXPORT -> MoreDialog(
            title = "导出预览",
            dismissOnOutside = true,
            onDismiss = onCloseModal,
            safeLabel = AtlasStrings.CLOSE,
            onSafe = {
                mutation = AtlasMutationStatus(AtlasMutationPhase.SUCCESS, "导出预览已关闭；未创建文件。")
                onCloseModal()
            },
            confirmLabel = null,
            onConfirm = null,
        ) {
            MoreDialogParagraph("默认数据包：安全书籍元数据、本地 pin、稍后再读、收藏夹/规则、本地标签、评分与语义进度。")
            MoreSwitchRow(
                title = "包含浏览与搜索历史",
                summary = if (includeHistory) "已选择" else "默认不包含",
                supportingText = "历史可能包含从未加入书架的浏览和搜索记录；只有在此处明确开启才导出。",
                checked = includeHistory,
                onCheckedChange = {
                    includeHistory = it
                    repository.putBoolean("data.export.includeHistory", it, "ExportHistoryPreferenceChanged")
                },
            )
            MoreDialogParagraph("始终不包含：网站镜像绑定/节点/快照、更新会话/anchor/调度、远程尝试/target/receipt、凭据、来源状态、CoverRef transport locator、二进制缓存、界面偏好与设备状态。")
            MoreDialogParagraph("关闭预览不会打开文件选择器或写入文件。")
        }
        else -> Unit
    }
}

@Composable
private fun MoreDataReportPage(
    context: AtlasContext,
    modifier: Modifier,
    expanded: Boolean,
    activeModal: MoreModal?,
    onExpandedChange: (Boolean) -> Unit,
    onOpenRecovery: () -> Unit,
    onCloseModal: () -> Unit,
    onUpToData: () -> Unit,
    onResolvePrimary: () -> Unit,
) {
    val report = MoreAtlasFixtures.transferReport
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    var recoveryPending by rememberSaveable(runtime.persistent) { mutableStateOf(if (runtime.persistent) repository.boolean("data.report.recoveryPending", report.recoveryPending) else report.recoveryPending) }
    var mutation by remember { mutableStateOf<AtlasMutationStatus?>(null) }
    BackHandler(enabled = activeModal == null) {
        if (recoveryPending) onOpenRecovery() else onUpToData()
    }
    val gateBanner = if (recoveryPending) AtlasBanner(
        title = "恢复待处理",
        message = "导入在应用阶段中断；离开报告前必须选择恢复到导入前或保留当前结果。",
        actionLabel = "处理",
        onAction = onOpenRecovery,
        errorTone = true,
    ) else null

    MorePage(
        context = context,
        title = "导入报告",
        modifier = modifier,
        subtitle = report.sessionId,
        onUp = { if (recoveryPending) onOpenRecovery() else onUpToData() },
        onResolvePrimary = onResolvePrimary,
        mutation = mutation ?: if (context.showMutationBanner) {
            AtlasMutationStatus(AtlasMutationPhase.SUCCESS, "恢复门已解决；现在可以返回数据页面。")
        } else null,
        pinnedBanner = gateBanner,
    ) {
        MoreScrollableContent {
            MoreInfoPanel(
                title = "部分完成",
                body = "${report.sourceFormat} · ${report.startedAt}\n书籍 ${report.importedBooks} · 收藏夹 ${report.importedCollections} · 标注 ${report.importedAnnotations}",
                errorTone = true,
            )
            MoreInfoPanel(
                title = "恢复门",
                body = if (recoveryPending) "待处理：系统 Back 与顶部返回都会先打开恢复选择；不会在未解决时离开报告。" else "已解决：报告仍可审阅，返回数据页面已恢复。",
                actionLabel = if (recoveryPending) "选择恢复结果" else null,
                onAction = if (recoveryPending) onOpenRecovery else null,
            )

            MoreSectionHeader("警告 ${report.warnings.size}")
            MoreVariantNote(disclosureVariantLabel(context))
            report.warnings.take(report.warningCap).forEach { issue -> MoreIssueRow(issue) }
            val remainingWarnings = report.warnings.drop(report.warningCap)
            if (remainingWarnings.isNotEmpty()) {
                MoreExpanderRow(
                    expanded = expanded,
                    label = if (expanded) "收起到前 ${report.warningCap} 条" else "展开全部 ${report.warnings.size} 条",
                    onClick = { onExpandedChange(!expanded) },
                )
                ExpandableIssueRows(context, expanded, remainingWarnings)
            }

            MoreSectionHeader("冲突 ${report.conflicts.size}")
            MoreInfoPanel(
                title = "冲突必须明确解决",
                body = "报告展示固定建议，但在恢复门解决前不会把任何未确认选择显示为成功。",
            )
            report.conflicts.forEach { issue -> MoreIssueRow(issue) }
        }
    }

    if (activeModal == MoreModal.REPORT_RECOVERY) {
        MoreDialog(
            title = "解决恢复门",
            dismissOnOutside = false,
            onDismiss = onCloseModal,
            safeLabel = AtlasStrings.CANCEL,
            onSafe = onCloseModal,
            confirmLabel = "保留当前结果",
            onConfirm = {
                recoveryPending = false
                repository.putBoolean("data.report.recoveryPending", false, "ImportRecoveryResolved", "keep-current")
                mutation = AtlasMutationStatus(AtlasMutationPhase.SUCCESS, "已保留当前结果；恢复门已解除。")
                onCloseModal()
            },
            secondaryLabel = "恢复到导入前",
            onSecondary = {
                recoveryPending = false
                repository.putBoolean("data.report.recoveryPending", false, "ImportRecoveryResolved", "restore-before")
                mutation = AtlasMutationStatus(AtlasMutationPhase.SUCCESS, "已恢复到导入前；恢复门已解除。")
                onCloseModal()
            },
        ) {
            MoreDialogParagraph("保留当前结果：保留已应用的本地样例，并继续展示 87 条警告与 23 条冲突。")
            MoreDialogParagraph("恢复到导入前：撤销本会话的本地样例变更。两种选择都不会执行远程操作。")
        }
    }
}

@Composable
private fun MoreHelpPage(
    context: AtlasContext,
    modifier: Modifier,
    activeModal: MoreModal?,
    selectedIntroduction: Int,
    onSelectIntroduction: (Int) -> Unit,
    onCloseModal: () -> Unit,
    onOpenDisplayReset: () -> Unit,
    onUp: () -> Unit,
    onResolvePrimary: () -> Unit,
) {
    BackHandler(enabled = activeModal == null) { onUp() }
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    var introductionsEnabled by rememberSaveable(runtime.persistent) { mutableStateOf(if (runtime.persistent) repository.boolean("help.introductionsEnabled", true) else true) }
    var seenVersionsReset by rememberSaveable(runtime.persistent) { mutableStateOf(if (runtime.persistent) repository.boolean("help.seenVersionsReset") else false) }
    var mutation by remember { mutableStateOf<AtlasMutationStatus?>(null) }
    val defaultMutation = AtlasMutationStatus(
        AtlasMutationPhase.SUCCESS,
        "已重置全部功能说明的已读版本；下次进入对应功能时会再次显示。",
    )
    MorePage(
        context = context,
        title = "帮助",
        modifier = modifier,
        onUp = onUp,
        onResolvePrimary = onResolvePrimary,
        mutation = mutation ?: if (context.showMutationBanner) defaultMutation else null,
    ) {
        MoreScrollableContent {
            MoreSectionHeader("功能说明")
            MoreSwitchRow(
                title = "自动显示功能说明",
                summary = onOff(introductionsEnabled),
                supportingText = "关闭后仍可从本页逐项重播；关闭说明不会批准任何来源能力。",
                checked = introductionsEnabled,
                onCheckedChange = { checked ->
                    introductionsEnabled = checked
                    repository.putBoolean("help.introductionsEnabled", checked, "FeatureIntroductionsChanged")
                    mutation = AtlasMutationStatus(
                        AtlasMutationPhase.SUCCESS,
                        "自动显示功能说明已${if (checked) "开启" else "关闭"}；手动重播始终可用。",
                    )
                },
            )
            MoreActionRow(
                title = "重置功能说明已读版本",
                summary = if (seenVersionsReset) "已重置；下次进入时会再次显示" else "让全部新版说明再次出现",
                onClick = {
                    seenVersionsReset = true
                    repository.putBoolean("help.seenVersionsReset", true, "FeatureIntroductionVersionsReset")
                    mutation = defaultMutation
                },
            )
            MoreAtlasFixtures.featureIntroductions.forEachIndexed { index, intro ->
                MoreActionRow(
                    title = intro.title,
                    summary = "${intro.version} · ${intro.summary}",
                    onClick = { onSelectIntroduction(index) },
                )
            }

            MoreSectionHeader("界面设置")
            MoreActionRow(
                title = "重置界面设置",
                summary = "前往“显示”的唯一重置入口；本页不重复执行重置",
                onClick = onOpenDisplayReset,
            )
            MoreSectionHeader("支持与问题")
            MoreInfoPanel(
                title = "报告问题时请提供",
                body = "路由、显示配置、主题、窗口大小、字体缩放、操作步骤与可见错误文字。请勿包含凭据、Cookie、原始页面或个人书架内容。",
            )
            MoreInfoPanel(
                title = "离线也可查看",
                body = "本帮助页与功能说明均来自应用内固定内容；不会打开网页或依赖网络。",
            )
        }
    }

    if (activeModal == MoreModal.HELP_FEATURE_INTRODUCTION) {
        val introduction = MoreAtlasFixtures.featureIntroductions[
            selectedIntroduction.coerceIn(MoreAtlasFixtures.featureIntroductions.indices)
        ]
        FeatureIntroductionOverlay(
            context = context,
            introduction = introduction,
            onDismiss = onCloseModal,
            onAcknowledge = {
                repository.putString("help.lastAcknowledged", introduction.id, "FeatureIntroductionAcknowledged")
                mutation = AtlasMutationStatus(
                    AtlasMutationPhase.SUCCESS,
                    "已记录“${introduction.title}”的固定已读版本；这不代表批准任何能力。",
                )
                onCloseModal()
            },
        )
    }
}

@Composable
private fun MoreAboutPage(
    context: AtlasContext,
    modifier: Modifier,
    activeModal: MoreModal?,
    onOpenLicense: () -> Unit,
    onCloseModal: () -> Unit,
    onUp: () -> Unit,
    onResolvePrimary: () -> Unit,
) {
    BackHandler(enabled = activeModal == null) { onUp() }
    val about = MoreAtlasFixtures.about
    MorePage(context, "关于", modifier, onUp, onResolvePrimary) {
        MoreScrollableContent {
            Text(text = about.appName, style = MaterialTheme.typography.displayMedium)
            Text(
                text = "为阅读与整理而设计",
                modifier = Modifier.padding(top = AtlasSpacing.Xs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoreSectionHeader("版本")
            MoreInfoRow("版本", about.version)
            MoreInfoRow("构建", about.build)
            MoreInfoRow("当前显示", profileLabel(context.profile))
            MoreSectionHeader("许可")
            MoreInfoPanel(
                title = about.licenseName,
                body = "Tsuyomi 原型代码按 Apache License 2.0 提供。完整许可说明可在不联网的对话框中审阅。",
            )
            MoreActionRow("查看完整许可说明", about.licenseName, onOpenLicense)
            MoreSectionHeader("隐私")
            MoreInfoPanel("离线可用", "关于与许可说明均随应用提供，不需要联网。")
        }
    }
    if (activeModal == MoreModal.ABOUT_LICENSE) {
        MoreDialog(
            title = about.licenseName,
            dismissOnOutside = true,
            onDismiss = onCloseModal,
            safeLabel = AtlasStrings.CLOSE,
            onSafe = onCloseModal,
            confirmLabel = null,
            onConfirm = null,
        ) {
            MoreDialogParagraph(about.licenseNotice)
            MoreDialogParagraph(about.fixtureNotice)
        }
    }
}
