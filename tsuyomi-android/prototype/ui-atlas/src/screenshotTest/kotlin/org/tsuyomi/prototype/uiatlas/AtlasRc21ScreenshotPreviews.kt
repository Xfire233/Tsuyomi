/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasLayout
import org.tsuyomi.prototype.uiatlas.model.AtlasProfile
import org.tsuyomi.prototype.uiatlas.model.AtlasReaderSeekPreview
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.AtlasReviewSpec
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.model.AtlasThemeKind

private const val Rc21Standard = "spec:width=1080px,height=2400px,dpi=420"
private const val Rc21EInk = "spec:width=1080px,height=1920px,dpi=320"

@Composable
private fun Rc21(
    id: String,
    route: AtlasRoute,
    profile: AtlasProfile,
    scenario: String,
    currentDefault: String,
    verifies: String,
    layout: AtlasLayout? = null,
    state: AtlasPageState = AtlasPageState.CONTENT,
    seek: AtlasReaderSeekPreview? = null,
) = AtlasApp(
    initial = AtlasContext(
        route = route,
        profile = profile,
        state = state,
        theme = AtlasThemeKind.LIGHT,
        layout = layout,
        reducedMotion = profile == AtlasProfile.EINK,
        capture = true,
        inlineModalPreview = seek != null || state == AtlasPageState.MODAL,
        simulateSystemUi = true,
        review = AtlasReviewSpec(id, scenario, currentDefault, verifies),
        readerSeekPreview = seek,
    ),
    runtime = rememberScreenshotRuntime(),
)

@PreviewTest @Preview(name = "rc21-a-library-standard", device = Rc21Standard, locale = "zh-rCN")
@Composable fun Rc21ALibraryStandard() = Rc21("rc21-a-library", AtlasRoute.LIBRARY, AtlasProfile.STANDARD, "书架同时容纳系统节点、收藏夹、镜像和书籍", "常规手机固定三列；系统节点与默认收藏夹置顶", "混合内容流、双创建入口、严格等高与长标题完整入口")
@PreviewTest @Preview(name = "rc21-a-library-eink", device = Rc21EInk, locale = "zh-rCN")
@Composable fun Rc21ALibraryEInk() = Rc21("rc21-a-library-eink", AtlasRoute.LIBRARY, AtlasProfile.EINK, "电子墨水书架混合内容流", "常规手机固定三列、按钮分页与移动", "真实紧凑宽度、明确边界、固定分页与等高卡片")

@PreviewTest @Preview(name = "rc21-b-detail-standard", device = Rc21Standard, locale = "zh-rCN")
@Composable fun Rc21BDetail() = Rc21("rc21-b-detail", AtlasRoute.BOOK_DETAIL, AtlasProfile.STANDARD, "从书架打开一本已加入本地的来源书", "详情页直接拥有完整章节", "header、简介、紧凑元数据和章节列表顺序")
@PreviewTest @Preview(name = "rc21-b-detail-eink", device = Rc21EInk, locale = "zh-rCN")
@Composable fun Rc21BDetailEInk() = Rc21("rc21-b-detail-eink", AtlasRoute.BOOK_DETAIL, AtlasProfile.EINK, "从电子墨水书架打开来源书", "详情页直接拥有完整章节", "高对比 header、元数据与显式分页章节")


@PreviewTest @Preview(name = "rc21-c-reader-seek-standard", device = Rc21Standard, locale = "zh-rCN")
@Composable fun Rc21CReaderSeek() = Rc21("rc21-c-reader-seek", AtlasRoute.BOOK_READER, AtlasProfile.STANDARD, "阅读中拖动到另一位置", "正文 viewport 原位显示目标排版", "无独立预览窗；取消保持原位置", seek = AtlasReaderSeekPreview.CANCEL)
@PreviewTest @Preview(name = "rc21-c-reader-commit-standard", device = Rc21Standard, locale = "zh-rCN")
@Composable fun Rc21CReaderCommit() = Rc21("rc21-c-reader-commit", AtlasRoute.BOOK_READER, AtlasProfile.STANDARD, "释放后跳到预览位置", "只提交一次 semantic locator", "commit 与 preview 分离，并保留返回原位置", seek = AtlasReaderSeekPreview.COMMIT)
@PreviewTest @Preview(name = "rc21-c-reader-return-eink", device = Rc21EInk, locale = "zh-rCN")
@Composable fun Rc21CReaderReturnEInk() = Rc21("rc21-c-reader-return", AtlasRoute.BOOK_READER, AtlasProfile.EINK, "电子墨水离散选择并确认跳转", "整页预览、显式确认、保留返回原位置", "无连续拖动、无滚动、一次 semantic commit", seek = AtlasReaderSeekPreview.RETURN_ORIGIN)

@PreviewTest @Preview(name = "rc21-d-search-standard", device = Rc21Standard, locale = "zh-rCN")
@Composable fun Rc21DSearch() = Rc21("rc21-d-search", AtlasRoute.SEARCH, AtlasProfile.STANDARD, "一次提交同时搜索本地与所选来源", "draft inert；统一增量结果流", "一个总进度、exact identity 合并、无高级筛选或来源状态条")
@PreviewTest @Preview(name = "rc21-d-search-eink", device = Rc21EInk, locale = "zh-rCN")
@Composable fun Rc21DSearchEInk() = Rc21("rc21-d-search-eink", AtlasRoute.SEARCH, AtlasProfile.EINK, "电子墨水一次提交统一搜索", "draft inert；统一结果流", "固定分页、exact identity、无教学或来源 lane")


@PreviewTest @Preview(name = "rc21-e-updates-standard", device = Rc21Standard, locale = "zh-rCN")
@Composable fun Rc21EUpdates() = Rc21("rc21-e-updates", AtlasRoute.LIBRARY_UPDATES, AtlasProfile.STANDARD, "检查追更并逐项确认已看过", "密集列表默认；短时 M3 progress", "标题、章节状态、主动作和三布局")
@PreviewTest @Preview(name = "rc21-e-updates-eink", device = Rc21EInk, locale = "zh-rCN")
@Composable fun Rc21EUpdatesEInk() = Rc21("rc21-e-updates-eink", AtlasRoute.LIBRARY_UPDATES, AtlasProfile.EINK, "电子墨水检查追更并确认已看过", "密集列表默认；静态工作 glyph", "标题、状态、主动作完整可读")


@PreviewTest @Preview(name = "rc21-f-remote-standard", device = Rc21Standard, locale = "zh-rCN")
@Composable fun Rc21FRemoteStandard() = Rc21("rc21-f-remote-standard", AtlasRoute.BROWSE_SOURCE_REMOTE_LIBRARY, AtlasProfile.STANDARD, "审阅网站收藏并复制到本地", "密集列表；刷新列表与全部复制一级可见", "选择、目标操作与三布局")

@PreviewTest @Preview(name = "rc21-f-remote-eink", device = Rc21EInk, locale = "zh-rCN")
@Composable fun Rc21FRemote() = Rc21("rc21-f-remote", AtlasRoute.BROWSE_SOURCE_REMOTE_LIBRARY, AtlasProfile.EINK, "审阅网站收藏并复制到本地", "密集列表；刷新列表与全部复制一级可见", "可读标题、纵向间距、选择与 E-ink 分页")

@PreviewTest @Preview(name = "rc21-g-tags-standard", device = Rc21Standard, locale = "zh-rCN")
@Composable fun Rc21GTags() = Rc21("rc21-g-tags", AtlasRoute.LIBRARY_TAGS, AtlasProfile.STANDARD, "统一查看本地标签和网站标签", "按所有权分为本地 / 来源", "本地可管理；来源只读且不混淆")
@PreviewTest @Preview(name = "rc21-g-tags-eink", device = Rc21EInk, locale = "zh-rCN")
@Composable fun Rc21GTagsEInk() = Rc21("rc21-g-tags-eink", AtlasRoute.LIBRARY_TAGS, AtlasProfile.EINK, "电子墨水查看本地标签和网站标签", "按所有权分为本地 / 来源", "高对比所有权分区、本地可管理与来源只读")


@PreviewTest @Preview(name = "rc21-h-reader-settings-standard", device = Rc21Standard, locale = "zh-rCN")
@Composable fun Rc21HReaderSettingsStandard() = Rc21("rc21-h-reader-settings-standard", AtlasRoute.BOOK_READER, AtlasProfile.STANDARD, "阅读时打开部分高度快速设置", "同一真实 M3 sheet 可上拉到全高", "首屏完整显示字号、行距、边距、段距且无标题关闭行", state = AtlasPageState.MODAL)

@PreviewTest @Preview(name = "rc21-h-reader-settings-eink", device = Rc21EInk, locale = "zh-rCN")
@Composable fun Rc21HReaderSettings() = Rc21("rc21-h-reader-settings", AtlasRoute.BOOK_READER, AtlasProfile.EINK, "阅读时打开设置", "全窗口不透明容器", "与 Standard 共享四项首屏和分组顺序；无 sheet 手势", state = AtlasPageState.MODAL)
