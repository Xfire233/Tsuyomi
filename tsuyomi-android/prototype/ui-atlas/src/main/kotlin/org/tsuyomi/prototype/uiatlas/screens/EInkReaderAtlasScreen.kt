/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import androidx.compose.ui.text.font.FontFamily
import org.tsuyomi.prototype.uiatlas.components.AtlasBanner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.tsuyomi.prototype.uiatlas.components.AtlasButton
import org.tsuyomi.prototype.uiatlas.components.AtlasButtonStyle
import org.tsuyomi.prototype.uiatlas.components.AtlasInfoBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasOverflowItem
import org.tsuyomi.prototype.uiatlas.components.AtlasScaffold
import org.tsuyomi.prototype.uiatlas.components.AtlasStateKind
import org.tsuyomi.prototype.uiatlas.components.AtlasStateView
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBar
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBarAction
import org.tsuyomi.prototype.uiatlas.components.AtlasIconButton
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.fixtures.SourceAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasFamily
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasNavigation
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasReaderPresentation
import org.tsuyomi.prototype.uiatlas.runtime.LocalPrototypeRuntime
import org.tsuyomi.prototype.uiatlas.runtime.prototypeRepository
import org.tsuyomi.prototype.uiatlas.screens.reader.StandardReaderAtlasScreen
import org.tsuyomi.prototype.uiatlas.theme.AtlasEInkPalette
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment
// -- #10 Reader -----------------------------------------------------------------------------

@Composable
internal fun BookReader(context: AtlasContext, modifier: Modifier) {
    if (!LocalAtlasEnvironment.current.eInk) {
        StandardReaderAtlasScreen(context, modifier)
        return
    }
    // The E-ink Reader remains frozen in this Standard-only review pass.
    val navigation = LocalAtlasNavigation.current
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val scope = rememberCoroutineScope()
    val eInk = LocalAtlasEnvironment.current.eInk
    val chapter = SourceAtlasFixtures.readerChapterFor(context.libraryView)
    var page by rememberSaveable(runtime.persistent) {
        mutableIntStateOf(if (runtime.persistent) repository.int("reader.page", SourceAtlasFixtures.READER_DEFAULT_PAGE) else SourceAtlasFixtures.READER_DEFAULT_PAGE)
    }
    var originPage by rememberSaveable { mutableIntStateOf(page) }
    var scrollMode by rememberSaveable(runtime.persistent) { mutableStateOf(if (runtime.persistent) repository.boolean("reader.scrollMode") else false) }
    var immersiveMode by rememberSaveable(runtime.persistent) { mutableStateOf(if (runtime.persistent) repository.boolean("reader.immersive") else context.readerImmersive) }
    var chrome by remember(context.capture, context.readerSeekPreview) {
        // Interactive launches expose a usable Up/settings path first; a page tap still hides chrome.
        mutableStateOf(!context.capture || context.review != null || context.readerSeekPreview != null)
    }
    val commitPage: (Int, String) -> Unit = { target, event ->
        page = target.coerceIn(1, SourceAtlasFixtures.READER_PAGE_COUNT)
        repository.putInt("reader.page", page, event, chapter.title)
    }
    var layer by rememberSaveable(context.showModal, context.readerSeekPreview) {
        mutableStateOf(if (context.showModal) "settings" else if (context.readerSeekPreview != null) "seek" else null)
    }
    val activeLayer = layer
    val reading = context.primaryState == AtlasPageState.CONTENT
    val guardedContent = chapter == SourceAtlasFixtures.ReaderChapterKind.VERIFICATION_REQUIRED
    val showChrome = !reading || context.showOfflineBanner || guardedContent || chrome || activeLayer != null
    val readerPresentation = LocalAtlasReaderPresentation.current
    SideEffect { readerPresentation.setChromeVisible(showChrome) }
    BackHandler(enabled = activeLayer in setOf("typography", "navigation", "page", "device")) { layer = "settings" }
    BackHandler(enabled = activeLayer != null) { layer = null }
    BackHandler(enabled = activeLayer == null && chrome) { chrome = false }

    AtlasScaffold(
        modifier = modifier,
        topBar = {
            if (showChrome) {
                AtlasTopBar(
                    title = chapter.title,
                    onUp = navigation.up,
                    actions = listOf(
                        AtlasTopBarAction(AtlasIcons.Chapters, "章节目录", { layer = "drawer" }),
                        AtlasTopBarAction(AtlasIcons.Search, "页内搜索") { repository.record("ReaderSearchOpened", chapter.title, "success") },
                    ),
                    overflow = listOf(
                        AtlasOverflowItem("添加书签") { repository.putBoolean("reader.bookmark.$page", true, "ReaderBookmarkAdded", page.toString()) },
                        AtlasOverflowItem("浏览书签") { repository.record("ReaderBookmarksOpened", chapter.title, "success") },
                    ),
                )
            }
        },
        footer = if (reading && (eInk || showChrome)) {
            {
                ReaderPosition(
                    page = page,
                    chapter = chapter.title,
                    onPrev = { if (page > 1) commitPage(page - 1, "ReaderPagePrevious") },
                    onNext = { if (page < SourceAtlasFixtures.READER_PAGE_COUNT) commitPage(page + 1, "ReaderPageNext") },
                    onSeek = { target ->
                        if (target == null) {
                            originPage = page
                            layer = "seek"
                        } else {
                            commitPage(target, "LocatorCommit")
                        }
                    },
                    onSettings = { layer = "settings" },
                )
            }
        } else null,
    ) {
        when (context.primaryState) {
            AtlasPageState.LOADING -> AtlasStateView(AtlasStateKind.LOADING, "正在加载章节…")
            AtlasPageState.ERROR -> AtlasStateView(AtlasStateKind.ERROR, "章节加载失败", message = "该章节尚未下载；已下载章节仍可离线阅读。", actionLabel = AtlasStrings.RETRY, onAction = {
                scope.launch { runtime.scenarios.run("reader-load", chapter.title) }
            })
            else -> Column(Modifier.fillMaxSize()) {
                if (context.showOfflineBanner) AtlasInfoBanner(AtlasBanner(AtlasStrings.OFFLINE_TITLE, "本地章节可正常阅读；来源操作已停用。"))
                Box(Modifier.weight(1f).fillMaxWidth().clickable(role = Role.Button, onClickLabel = "显示或隐藏阅读工具栏") { chrome = !chrome }) {
                    ReaderPage(chapter, page, scrollMode = scrollMode && !eInk, immersive = immersiveMode && !showChrome, chromeVisible = showChrome)
                }
            }
        }
    }

    when (activeLayer) {
        "settings", "typography", "navigation", "page", "device" -> ReaderSettingsContainer(
            inlinePreview = context.inlineModalPreview,
            eInk = eInk,
            page = activeLayer,
            scrollMode = scrollMode && !eInk,
            immersiveMode = immersiveMode,
            onScrollMode = {
                scrollMode = it && !eInk
                repository.putBoolean("reader.scrollMode", scrollMode, "ReaderModeChanged")
            },
            onImmersiveMode = {
                immersiveMode = it
                repository.putBoolean("reader.immersive", it, "ReaderImmersiveChanged")
                readerPresentation.setImmersive(it)
            },
            onNavigate = { layer = it },
            onDismiss = { layer = null },
        )
        "drawer" -> ReviewDialog("章节目录", onDismiss = { layer = null }) {
            Column {
                SourceAtlasFixtures.drawerChapters.forEachIndexed { index, title ->
                    AtlasButton(title, {
                        commitPage(index + 1, "ReaderChapterSelected")
                        layer = null
                    }, modifier = Modifier.fillMaxWidth(), style = AtlasButtonStyle.TEXT)
                }
            }
        }
        "seek" -> ReaderSeekPreview(
            openingPage = originPage,
            eInk = eInk,
            showReturnOrigin = context.readerSeekPreview == org.tsuyomi.prototype.uiatlas.model.AtlasReaderSeekPreview.RETURN_ORIGIN,
            onCancel = { layer = null },
            onCommit = { commitPage(it, "LocatorCommit"); layer = null },
        )
    }
}

@Composable
private fun ReaderSeekPreview(
    openingPage: Int,
    eInk: Boolean,
    showReturnOrigin: Boolean,
    onCancel: () -> Unit,
    onCommit: (Int) -> Unit,
) {
    var target by rememberSaveable { mutableFloatStateOf((openingPage + 7).coerceAtMost(SourceAtlasFixtures.READER_PAGE_COUNT).toFloat()) }
    val targetPage = target.toInt().coerceIn(1, SourceAtlasFixtures.READER_PAGE_COUNT)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        border = if (eInk) BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline) else null,
    ) {
        Column(Modifier.fillMaxSize()) {
            AtlasInfoBanner(
                AtlasBanner(
                    title = "EXPERIMENTAL / NOT APPROVED",
                    message = "阅读位置预览尚未获得实体设备视觉批准；确认只提交一次 LocatorCommit。",
                ),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ReaderPage(SourceAtlasFixtures.ReaderChapterKind.TEXT, targetPage, scrollMode = false, immersive = false, chromeVisible = true)
            }
            Text("第 $targetPage 页", modifier = Modifier.padding(horizontal = AtlasSpacing.Md), style = MaterialTheme.typography.labelLarge)
            if (eInk) {
                Row(Modifier.fillMaxWidth().padding(AtlasSpacing.Md), horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                    AtlasButton("上一页", { target = (target - 1).coerceAtLeast(1f) }, modifier = Modifier.weight(1f), style = AtlasButtonStyle.SECONDARY)
                    AtlasButton("下一页", { target = (target + 1).coerceAtMost(SourceAtlasFixtures.READER_PAGE_COUNT.toFloat()) }, modifier = Modifier.weight(1f), style = AtlasButtonStyle.SECONDARY)
                }
            } else {
                Slider(
                    value = target,
                    onValueChange = { target = it },
                    valueRange = 1f..SourceAtlasFixtures.READER_PAGE_COUNT.toFloat(),
                    steps = SourceAtlasFixtures.READER_PAGE_COUNT - 2,
                    modifier = Modifier.padding(horizontal = AtlasSpacing.Md),
                )
            }
            Row(Modifier.fillMaxWidth().padding(AtlasSpacing.Md), horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                AtlasButton("取消", onCancel, modifier = Modifier.weight(1f), style = AtlasButtonStyle.TEXT)
                AtlasButton(if (eInk) "确认跳转" else "跳到此处", { onCommit(targetPage) }, modifier = Modifier.weight(1f))
            }
            if (showReturnOrigin) AtlasButton("返回原位置 · 第 $openingPage 页", { onCommit(openingPage) }, modifier = Modifier.fillMaxWidth().padding(horizontal = AtlasSpacing.Md), style = AtlasButtonStyle.SECONDARY)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsContainer(
    inlinePreview: Boolean,
    eInk: Boolean,
    page: String,
    scrollMode: Boolean,
    immersiveMode: Boolean,
    onScrollMode: (Boolean) -> Unit,
    onImmersiveMode: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        ReaderSettingsContent(page, eInk, scrollMode, immersiveMode, onScrollMode, onImmersiveMode, onNavigate)
    }
    when {
        eInk -> EInkReaderSettingsPage(scrollMode, immersiveMode, onScrollMode, onImmersiveMode, onDismiss)
        inlinePreview -> BottomSheetScaffold(sheetContent = { content() }, sheetPeekHeight = 720.dp) {}
        else -> ModalBottomSheet(onDismissRequest = onDismiss) { content() }
    }
}

@Composable
private fun EInkReaderSettingsPage(
    scrollMode: Boolean,
    immersiveMode: Boolean,
    onScrollMode: (Boolean) -> Unit,
    onImmersiveMode: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val fontSize = if (runtime.persistent) repository.int("reader.fontSize", 18).toFloat() else 18f
    val lineHeight = if (runtime.persistent) repository.string("reader.lineSpacing", "1.7").toFloatOrNull() ?: 1.7f else 1.7f
    val margin = if (runtime.persistent) repository.int("reader.margin", 24).toFloat() else 24f
    val paragraphSpacing = if (runtime.persistent) repository.string("reader.paragraphSpacing", ".8").toFloatOrNull() ?: .8f else .8f
    val pageMargin = if (runtime.persistent) repository.int("reader.pageMargin", 20).toFloat() else 20f
    val volumePaging = if (runtime.persistent) repository.boolean("reader.volumePaging", true) else true
    val keepAwake = if (runtime.persistent) repository.boolean("reader.keepAwake", true) else true
    val lockPortrait = if (runtime.persistent) repository.boolean("reader.lockPortrait") else false
    val wide = containerWidth() >= 600.dp

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.fillMaxSize()) {
            AtlasTopBar(title = "阅读设置", onUp = onDismiss)
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(AtlasSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Md),
            ) {
                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Md), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Md)) {
                            EInkSettingsSection("排版") {
                                EInkSliderSetting("字号", "${fontSize.toInt()}sp", fontSize, { repository.putInt("reader.fontSize", it.toInt(), "ReaderFontSizeChanged") }, 12f..32f, 19)
                                EInkSliderSetting("行距", "${"%.1f".format(lineHeight)}", lineHeight, { repository.putString("reader.lineSpacing", "%.1f".format(it), "ReaderLineSpacingChanged") }, 1.2f..2.2f, 9)
                                EInkSliderSetting("边距", "${margin.toInt()}dp", margin, { repository.putInt("reader.margin", it.toInt(), "ReaderMarginChanged") }, 12f..40f, 6)
                                EInkSliderSetting("段距", "${"%.1f".format(paragraphSpacing)}em", paragraphSpacing, { repository.putString("reader.paragraphSpacing", "%.1f".format(it), "ReaderParagraphSpacingChanged") }, 0f..1.6f, 7)
                                AtlasButton("系统 CJK 无衬线 · 常规", { repository.record("ReaderFontPickerOpened", "reader", "success") }, modifier = Modifier.fillMaxWidth(), style = AtlasButtonStyle.SECONDARY)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Md)) {
                            EInkPageSettings(pageMargin, { repository.putInt("reader.pageMargin", it.toInt(), "ReaderPageMarginChanged") }, scrollMode, onScrollMode)
                            EInkNavigationSettings(volumePaging, { repository.putBoolean("reader.volumePaging", it, "ReaderVolumePagingChanged") })
                            EInkDeviceSettings(
                                keepAwake,
                                { repository.putBoolean("reader.keepAwake", it, "ReaderKeepAwakeChanged") },
                                lockPortrait,
                                { repository.putBoolean("reader.lockPortrait", it, "ReaderLockPortraitChanged") },
                                immersiveMode,
                                onImmersiveMode,
                            )
                        }
                    }
                } else {
                    EInkSettingsSection("排版") {
                        EInkSliderSetting("字号", "${fontSize.toInt()}sp", fontSize, { repository.putInt("reader.fontSize", it.toInt(), "ReaderFontSizeChanged") }, 12f..32f, 19)
                        EInkSliderSetting("行距", "${"%.1f".format(lineHeight)}", lineHeight, { repository.putString("reader.lineSpacing", "%.1f".format(it), "ReaderLineSpacingChanged") }, 1.2f..2.2f, 9)
                        EInkSliderSetting("边距", "${margin.toInt()}dp", margin, { repository.putInt("reader.margin", it.toInt(), "ReaderMarginChanged") }, 12f..40f, 6)
                        EInkSliderSetting("段距", "${"%.1f".format(paragraphSpacing)}em", paragraphSpacing, { repository.putString("reader.paragraphSpacing", "%.1f".format(it), "ReaderParagraphSpacingChanged") }, 0f..1.6f, 7)
                        AtlasButton("系统 CJK 无衬线 · 常规", { repository.record("ReaderFontPickerOpened", "reader", "success") }, modifier = Modifier.fillMaxWidth(), style = AtlasButtonStyle.SECONDARY)
                    }
                    EInkPageSettings(pageMargin, { repository.putInt("reader.pageMargin", it.toInt(), "ReaderPageMarginChanged") }, scrollMode, onScrollMode)
                    EInkNavigationSettings(volumePaging, { repository.putBoolean("reader.volumePaging", it, "ReaderVolumePagingChanged") })
                    EInkDeviceSettings(
                        keepAwake,
                        { repository.putBoolean("reader.keepAwake", it, "ReaderKeepAwakeChanged") },
                        lockPortrait,
                        { repository.putBoolean("reader.lockPortrait", it, "ReaderLockPortraitChanged") },
                        immersiveMode,
                        onImmersiveMode,
                    )
                }
            }
        }
    }
}

@Composable
private fun EInkSettingsSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(AtlasSpacing.Md), verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun EInkSliderSetting(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(44.dp))
        Text(valueLabel, Modifier.width(64.dp), style = MaterialTheme.typography.labelLarge)
        Slider(value, onValueChange, Modifier.weight(1f), valueRange = valueRange, steps = steps)
    }
}

@Composable
private fun EInkSwitchSetting(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f))
        Switch(checked, null)
    }
}

@Composable
private fun EInkPageSettings(pageMargin: Float, onPageMargin: (Float) -> Unit, scrollMode: Boolean, onScrollMode: (Boolean) -> Unit) {
    EInkSettingsSection("页面") {
        EInkSliderSetting("页边", "${pageMargin.toInt()}dp", pageMargin, onPageMargin, 12f..40f, 6)
        Row(horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
            FilterChip(selected = true, onClick = {}, label = { Text("黑白") })
            FilterChip(selected = !scrollMode, onClick = { onScrollMode(false) }, label = { Text("分页") })
            FilterChip(selected = scrollMode, onClick = {}, enabled = false, label = { Text("滚动") })
        }
    }
}

@Composable
private fun EInkNavigationSettings(volumePaging: Boolean, onVolumePaging: (Boolean) -> Unit) {
    EInkSettingsSection("导航") {
        Text("点击区域：左侧上一页 · 中间工具栏 · 右侧下一页")
        EInkSwitchSetting("音量键翻页", volumePaging, onVolumePaging)
        Text("进度轨：整书进度 · 显示章节刻度", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EInkDeviceSettings(
    keepAwake: Boolean,
    onKeepAwake: (Boolean) -> Unit,
    lockPortrait: Boolean,
    onLockPortrait: (Boolean) -> Unit,
    immersiveMode: Boolean,
    onImmersiveMode: (Boolean) -> Unit,
) {
    EInkSettingsSection("设备") {
        EInkSwitchSetting("保持屏幕常亮", keepAwake, onKeepAwake)
        EInkSwitchSetting("锁定竖屏", lockPortrait, onLockPortrait)
        EInkSwitchSetting("全屏沉浸", immersiveMode, onImmersiveMode)
    }
}

@Composable
private fun ReaderSettingsContent(
    page: String,
    eInk: Boolean,
    scrollMode: Boolean,
    immersiveMode: Boolean,
    onScrollMode: (Boolean) -> Unit,
    onImmersiveMode: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val fontSize = if (runtime.persistent) repository.int("reader.fontSize", 18).toFloat() else 18f
    val lineHeight = if (runtime.persistent) repository.string("reader.lineSpacing", "1.7").toFloatOrNull() ?: 1.7f else 1.7f
    val margin = if (runtime.persistent) repository.int("reader.margin", 24).toFloat() else 24f
    val paragraphSpacing = if (runtime.persistent) repository.string("reader.paragraphSpacing", ".8").toFloatOrNull() ?: .8f else .8f
    val pageMarginFraction = if (runtime.persistent) repository.string("reader.pageMarginFraction", ".35").toFloatOrNull() ?: .35f else .35f
    val volumePaging = if (runtime.persistent) repository.boolean("reader.volumePaging", true) else true
    val keepAwake = if (runtime.persistent) repository.boolean("reader.keepAwake", true) else true
    val lockPortrait = if (runtime.persistent) repository.boolean("reader.lockPortrait") else false
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AtlasSpacing.Lg)
            .padding(
                top = if (eInk) AtlasSpacing.Xxl + AtlasSpacing.Md else AtlasSpacing.Sm,
                bottom = if (eInk) AtlasSpacing.Lg else AtlasSpacing.Sm,
            ),
        verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
    ) {
        if (page != "settings") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AtlasIconButton(AtlasIcons.Back, "返回快速设置", { onNavigate("settings") })
                Text(
                    when (page) { "typography" -> "排版"; "page" -> "页面"; "navigation" -> "导航"; else -> "设备" },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        when (page) {
            "typography" -> {
                Text("字体与字重", style = MaterialTheme.typography.titleMedium)
                AtlasButton("系统 CJK 无衬线 · 常规", { repository.record("ReaderFontPickerOpened", "reader", "success") }, modifier = Modifier.fillMaxWidth(), style = AtlasButtonStyle.SECONDARY)
                Text("字距 0 · 首行缩进 2 字 · 两端对齐")
            }
            "page" -> {
                Text("上下边距 ${(pageMarginFraction * 57).toInt()}dp", style = MaterialTheme.typography.titleMedium)
                Slider(value = pageMarginFraction, onValueChange = { repository.putString("reader.pageMarginFraction", it.toString(), "ReaderPageMarginChanged") })
                Row(horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                    FilterChip(selected = true, onClick = { repository.record("ReaderPaperStyleSelected", if (eInk) "monochrome" else "paper", "success") }, label = { Text(if (eInk) "黑白" else "纸张") })
                    FilterChip(selected = !scrollMode, onClick = { onScrollMode(false) }, label = { Text("分页") })
                    FilterChip(selected = scrollMode, onClick = { onScrollMode(true) }, label = { Text("滚动") }, enabled = !eInk)
                }
            }
            "navigation" -> {
                Text("点击区域：左侧上一页 · 中间工具栏 · 右侧下一页")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("音量键翻页", Modifier.weight(1f))
                    Switch(volumePaging, { repository.putBoolean("reader.volumePaging", it, "ReaderVolumePagingChanged") })
                }
                Text("进度轨：整书进度 · 显示章节刻度")
            }
            "device" -> {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("保持屏幕常亮", Modifier.weight(1f)); Switch(keepAwake, { repository.putBoolean("reader.keepAwake", it, "ReaderKeepAwakeChanged") }) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("锁定竖屏", Modifier.weight(1f)); Switch(lockPortrait, { repository.putBoolean("reader.lockPortrait", it, "ReaderLockPortraitChanged") }) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("全屏沉浸", Modifier.weight(1f)); Switch(immersiveMode, onImmersiveMode) }
            }
            else -> {
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("字号", Modifier.width(44.dp)); Text("${fontSize.toInt()}sp", Modifier.width(58.dp)); Slider(fontSize, { repository.putInt("reader.fontSize", it.toInt(), "ReaderFontSizeChanged") }, Modifier.weight(1f), valueRange = 12f..32f, steps = 19)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("行距", Modifier.width(44.dp)); Text("${"%.1f".format(lineHeight)}", Modifier.width(58.dp)); Slider(lineHeight, { repository.putString("reader.lineSpacing", "%.1f".format(it), "ReaderLineSpacingChanged") }, Modifier.weight(1f), valueRange = 1.2f..2.2f, steps = 9)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("边距", Modifier.width(44.dp)); Text("${margin.toInt()}dp", Modifier.width(58.dp)); Slider(margin, { repository.putInt("reader.margin", it.toInt(), "ReaderMarginChanged") }, Modifier.weight(1f), valueRange = 12f..40f, steps = 6)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("段距", Modifier.width(44.dp)); Text("${"%.1f".format(paragraphSpacing)}em", Modifier.width(58.dp)); Slider(paragraphSpacing, { repository.putString("reader.paragraphSpacing", "%.1f".format(it), "ReaderParagraphSpacingChanged") }, Modifier.weight(1f), valueRange = 0f..1.6f, steps = 7)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Xs)) {
                    AtlasButton("排版", { onNavigate("typography") }, modifier = Modifier.weight(1f), style = AtlasButtonStyle.TEXT)
                    AtlasButton("页面", { onNavigate("page") }, modifier = Modifier.weight(1f), style = AtlasButtonStyle.TEXT)
                    AtlasButton("导航", { onNavigate("navigation") }, modifier = Modifier.weight(1f), style = AtlasButtonStyle.TEXT)
                    AtlasButton("设备", { onNavigate("device") }, modifier = Modifier.weight(1f), style = AtlasButtonStyle.TEXT)
                }
            }
        }
    }
}

@Composable
private fun ReaderPage(
    kind: SourceAtlasFixtures.ReaderChapterKind,
    page: Int,
    scrollMode: Boolean,
    immersive: Boolean,
    chromeVisible: Boolean,
) {
    val navigation = LocalAtlasNavigation.current
    when (kind) {
        SourceAtlasFixtures.ReaderChapterKind.VERIFICATION_REQUIRED -> Column(
            Modifier.fillMaxSize().padding(AtlasSpacing.Lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(AtlasIcons.Verify, contentDescription = null, modifier = Modifier.size(48.dp))
            Text("该章节需要完成验证", style = MaterialTheme.typography.titleMedium)
            AtlasButton("前往验证", { navigation.navigateInRoot(AtlasFamily.SOURCE, AtlasRoute.SOURCE_VERIFICATION) }, modifier = Modifier.padding(top = AtlasSpacing.Lg))
        }
        SourceAtlasFixtures.ReaderChapterKind.IMAGE -> ImagePage(page)
        else -> {
            val eInk = LocalAtlasEnvironment.current.eInk
            val pageModifier = if (eInk || !scrollMode) Modifier.fillMaxSize() else Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            val topInset = when {
                immersive -> Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                !chromeVisible -> Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                else -> Modifier
            }
            Column(topInset.then(pageModifier).padding(AtlasSpacing.Md)) {
                Text(
                    if (eInk) SourceAtlasFixtures.readerEInkPageText(page) else SourceAtlasFixtures.readerPageText(page),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Default),
                )
            }
        }
    }
}

@Composable
private fun ImagePage(page: Int) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(Modifier.fillMaxSize().padding(AtlasSpacing.Md), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(color, Offset(size.width * .1f, size.height * .8f), Offset(size.width * .5f, size.height * .3f), 4f)
                drawLine(color, Offset(size.width * .5f, size.height * .3f), Offset(size.width * .9f, size.height * .7f), 4f)
            }
            Text("图片页 $page", modifier = Modifier.align(Alignment.BottomCenter).padding(AtlasSpacing.Md))
        }
    }
}

@Composable
private fun ReaderPosition(
    page: Int,
    chapter: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int?) -> Unit,
    onSettings: () -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    Column {
        Canvas(
            Modifier.fillMaxWidth().height(48.dp).clickable(role = Role.Button, onClickLabel = "跳到下一刻度") {
                onSeek((page + 8).coerceAtMost(SourceAtlasFixtures.READER_PAGE_COUNT))
            },
        ) {
            val y = size.height / 2f
            drawLine(outline, Offset(24f, y), Offset(size.width - 24f, y), 3f)
            repeat(7) { tick ->
                val x = 24f + (size.width - 48f) * tick / 6f
                drawLine(ink, Offset(x, y - 8f), Offset(x, y + 8f), if (tick == 2) 5f else 2f)
            }
            val position = 24f + (size.width - 48f) * (page - 1f) / (SourceAtlasFixtures.READER_PAGE_COUNT - 1f)
            drawCircle(accent, 8f, Offset(position, y))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            AtlasIconButton(AtlasIcons.Prev, "上一章", onPrev, enabled = page > 1)
            Column(
                Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "拖动预览阅读位置") { onSeek(null) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(chapter, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("全书 $page / ${SourceAtlasFixtures.READER_PAGE_COUNT}", style = MaterialTheme.typography.labelSmall)
            }
            AtlasIconButton(AtlasIcons.Next, "下一章", onNext, enabled = page < SourceAtlasFixtures.READER_PAGE_COUNT)
            AtlasIconButton(AtlasIcons.Settings, "阅读设置", onSettings)
        }
    }
}
