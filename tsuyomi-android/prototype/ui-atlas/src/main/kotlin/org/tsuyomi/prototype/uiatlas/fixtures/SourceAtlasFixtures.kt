/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.fixtures

import kotlin.random.Random
import org.tsuyomi.prototype.uiatlas.ATLAS_SEED
import org.tsuyomi.prototype.uiatlas.model.AtlasBook
import org.tsuyomi.prototype.uiatlas.model.AtlasCover
import org.tsuyomi.prototype.uiatlas.model.AtlasBookIdentity
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView
import org.tsuyomi.prototype.uiatlas.model.AtlasSource

/**
 * Synthetic zh-CN fixtures for the Source route family (atlas routes #12–18: canonical book,
 * detail, directory, reader, Browse root, aggregated search, remote library, verification).
 *
 * Everything here is deterministic, code-defined data (Atlas Spec §4): no network, no Room, no
 * real source packages, no branding assets. Sub-scenarios that the capture extras cannot address
 * directly (caller context, typed search failure, remote gate, reader chapter kind) are derived
 * from the launch [AtlasLibraryView] extra by the explicit, documented mappings below so every
 * scenario stays reachable in both capture and interactive review.
 */
object SourceAtlasFixtures {

    // ---------- Canonical book detail (#12) --------------------------------------------------

    /**
     * The caller a canonical detail was opened from (constitution §13 `BookCallerContext`;
     * Atlas Spec §2.2 row 12: Library list, Browse search, History). The app-bar subtitle echoes
     * it so the Up target stays reviewable.
     */
    enum class DetailCaller(val label: String) {
        LIBRARY_LIST("书架 · 全部书籍"),
        BROWSE_SEARCH("浏览 · 聚合搜索"),
        HISTORY("书架 · 历史"),
    }

    /**
     * Deterministic capture mapping: the launch `view` extra picks the caller.
     * `ALL` → Library list, `RECENT` → History (recency surface), everything else → Browse
     * search. `DORMANT` additionally selects the dormant-source book fixture.
     */
    fun detailCallerFor(view: AtlasLibraryView): DetailCaller = when (view) {
        AtlasLibraryView.ALL -> DetailCaller.LIBRARY_LIST
        AtlasLibraryView.RECENT -> DetailCaller.HISTORY
        else -> DetailCaller.BROWSE_SEARCH
    }

    /**
     * The canonical detail book. When [dormant] the owning source is 源·竹 (dormant) and every
     * source-owned section degrades with provenance instead of disappearing.
     */
    fun detailBook(dormant: Boolean): AtlasBook {
        val source: AtlasSource =
            if (dormant) AtlasFixtures.sourceBamboo else AtlasFixtures.sourcePine
        return AtlasBook(
            id = "detail-book-${source.id}",
            title = "纸灯巷的守夜人",
            authors = "林晚照",
            cover = AtlasCover.Generated(seed = ATLAS_SEED + 1201L),
            source = source,
            progressLabel = "读至 第12章 · 43%",
            unreadUpdates = 3,
            readLater = false,
            dormantSource = dormant,
            rating = 4,
            tags = listOf("志怪", "治愈", "悬疑", "民俗"),
        )
    }

    /** Local (Room) truth rows for the detail's Room-first section; rendered before any source data. */
    val detailLocalRows: List<Pair<String, String>> = listOf(
        "阅读进度" to "第12章 · 43%",
        "评分" to "★ 4",
        "本地标签" to "志怪、治愈、悬疑、民俗",
        "所在收藏" to "全部书籍 · 夜读",
        "历史" to "最近阅读 2026-08-11 09:12",
    )

    /** Source-owned rows for the detail's source section (normal capability). */
    val detailSourceRows: List<Pair<String, String>> = listOf(
        "来源状态" to "正常 · v1.4",
        "最新章节" to "第 200 章 · 灯下归人",
        "数据更新于" to "2026-08-11 09:30（固定时钟）",
    )

    /** Degraded source rows when the owning source is dormant (never hidden, labelled stale). */
    val detailDormantRows: List<Pair<String, String>> = listOf(
        "来源状态" to "休眠 · 远程功能暂停",
        "最新章节" to "第 173 章（快照 · 可能过期）",
        "数据更新于" to "2026-07-28 21:04（最后成功同步）",
    )

    // ---------- Chapter directory (#13) ------------------------------------------------------

    /** One chapter row in the directory. */
    data class AtlasChapter(
        val number: Int,
        val title: String,
        val read: Boolean,
        val updated: Boolean,
        val downloaded: Boolean,
    )

    const val DIRECTORY_TOTAL = 200
    const val CURRENT_CHAPTER = 12

    /** E-ink directory page size (constitution §5.6: directory/chapter list = 12). */
    const val DIRECTORY_PAGE_SIZE = 12

    private val chapterMotifs = listOf(
        "灯影", "雨巷", "更声", "旧信", "渡口", "山雾", "棋局", "夜航",
    )

    /** The 200-chapter deterministic fixture (Atlas Spec §2.2 row 13). */
    val chapters: List<AtlasChapter> = List(DIRECTORY_TOTAL) { index ->
        val number = index + 1
        AtlasChapter(
            number = number,
            title = "第${number}章 · ${chapterMotifs[index % chapterMotifs.size]}",
            read = number < CURRENT_CHAPTER,
            updated = number > DIRECTORY_TOTAL - 3,
            downloaded = number in 1..24,
        )
    }

    // ---------- Reader (#14, Atlas Spec F11) --------------------------------------------------

    /** Fixture chapter kinds (F11): paged text, image chapter, offline chapter, gated chapter. */
    enum class ReaderChapterKind(val title: String) {
        TEXT("第12章 · 灯下归人"),
        IMAGE("第9章 · 图卷（图片章）"),
        OFFLINE("第10章 · 旧信（已下载）"),
        VERIFICATION_REQUIRED("第13章 · 雾门（需验证）"),
    }

    /**
     * Deterministic capture mapping: launch `view` picks the reader chapter kind.
     * `ALL` → text, `CONTINUE` → image, `RECENT` → offline, `READ_LATER` → verification-required.
     */
    fun readerChapterFor(view: AtlasLibraryView): ReaderChapterKind = when (view) {
        AtlasLibraryView.CONTINUE -> ReaderChapterKind.IMAGE
        AtlasLibraryView.RECENT -> ReaderChapterKind.OFFLINE
        AtlasLibraryView.READ_LATER -> ReaderChapterKind.VERIFICATION_REQUIRED
        else -> ReaderChapterKind.TEXT
    }

    const val READER_PAGE_COUNT = 40

    /** The page rendered in stills and as the interactive default (1-based). */
    const val READER_DEFAULT_PAGE = 12

    private val readerSentences = listOf(
        "巷口的纸灯在雨里晃了一下，灯芯缩成一粒红豆。",
        "守夜人把更梆子揣回怀里，数着青石板上碎开的水光。",
        "他记得每一盏灯的位置，就像记得每一位夜归人的脚步声。",
        "风从河埠头上来，带着潮湿的木腥气和远处渡船的号角。",
        "灯罩上的剪纸投下细碎的影，像一封被拆开的旧信。",
        "三更过后，巷子安静得能听见灯油缓缓下降的声音。",
    )

    /**
     * Synthetic text for one page of the text chapter: deterministic from [page] so repeat
     * captures are pixel-identical. Roughly a screenful of zh-CN paragraphs.
     */
    fun readerPageText(page: Int): String {
        val random = Random(ATLAS_SEED + 1400L + page)
        return buildString {
            repeat(6) { paragraph ->
                val sentences = 3 + random.nextInt(3)
                repeat(sentences) {
                    append(readerSentences[random.nextInt(readerSentences.size)])
                }
                if (paragraph < 5) append("\n\n")
            }
        }
    }

    /**
     * Complete, fit-safe text page for the 1264×1680@240 E-ink AVD. Three compact paragraphs
     * keep every sentence and the trailing page status above the persistent reader footer, even
     * when reader chrome is visible. This is a discrete page fixture, not a truncation of
     * [readerPageText], so changing pages never skips a hidden remainder.
     */
    fun readerEInkPageText(page: Int): String {
        val random = Random(ATLAS_SEED + 1410L + page)
        return buildString {
            repeat(3) { paragraph ->
                repeat(3) {
                    append(readerSentences[random.nextInt(readerSentences.size)])
                }
                if (paragraph < 2) append("\n\n")
            }
        }
    }

    /** Titles of the three fixture chapters listed in the reader drawer (F11). */
    val drawerChapters: List<String> = listOf(
        ReaderChapterKind.IMAGE.title,
        ReaderChapterKind.OFFLINE.title,
        ReaderChapterKind.TEXT.title,
    )

    // ---------- Reader seek preview (RC2.1 fixture-only) --------------------------------------

    /**
     * Chapter the progress-rail seek preview targets in deterministic direct renders. Chosen
     * away from [CURRENT_CHAPTER] so origin and target are unambiguous in one frame.
     */
    const val SEEK_TARGET_CHAPTER = 87

    /** Page inside the seek target chapter shown by the static WYSIWYG preview strip. */
    const val SEEK_TARGET_PAGE = 3

    /** Current-chapter percentage used by the Standard Reader scrub-preview evidence. */
    const val SEEK_TARGET_PROGRESS = 68

    /** Title of the seek target chapter, derived from the same motif table as [chapters]. */
    val seekTargetTitle: String
        get() = "第${SEEK_TARGET_CHAPTER}章 · ${chapterMotifs[(SEEK_TARGET_CHAPTER - 1) % chapterMotifs.size]}"

    /**
     * First line of the seek target page: the static WYSIWYG preview strip content. The seek
     * preview never re-paginates the real chapter; it shows this deterministic snippet exactly
     * as the committed page would begin.
     */
    val seekPreviewSnippet: String
        get() = readerPageText(SEEK_TARGET_PAGE).lineSequence().first()

    // ---------- Browse root (#15, Atlas Spec F8) ----------------------------------------------

    /** The one installable source package fixture with a staged approval diff (F8). */
    data class InstallableSource(
        val name: String,
        val version: String,
        val summary: String,
        val diffAdded: List<String>,
        val diffRemoved: List<String>,
    )

    val installableSource = InstallableSource(
        name = "源·苇",
        version = "v0.3",
        summary = "待安装 · 签名校验通过 · 哈希与包摘要绑定",
        diffAdded = listOf("网络搜索（声明）", "网站收藏读取（声明）"),
        diffRemoved = listOf("后台同步（上一版声明，已移除）"),
    )

    // ---------- Aggregated search -------------------------------------------------------------

    /** Local query cap (constitution §5.7: 100 chars, surfaced honestly). */
    const val SEARCH_QUERY_CAP = 100

    /** Host-renderable, extension-declared search filter types; data only, never extension UI. */
    enum class SearchFilterKind(val label: String) {
        TEXT("文本"),
        INTEGER("整数"),
        BOOLEAN("开关"),
        SINGLE_CHOICE("单选"),
        MULTI_CHOICE("多选"),
        RANGE("范围"),
        DATE_RANGE("日期范围"),
        SORT("排序"),
        GROUP("分组"),
    }

    data class SearchFilterFixture(
        val id: String,
        val label: String,
        val kind: SearchFilterKind,
        val valueSummary: String,
        val semanticRole: String? = null,
        val remoteOptions: Boolean = false,
    )

    data class SourceSearchDescriptorFixture(
        val source: AtlasSource,
        val queryKinds: String,
        val queryHint: String,
        val filters: List<SearchFilterFixture>,
        val laneState: String,
    )

    /** D33 fixtures: equivalent to Wenku/ESJ/forum capabilities without using real branding. */
    val searchDescriptors: List<SourceSearchDescriptorFixture> = listOf(
        SourceSearchDescriptorFixture(
            source = AtlasFixtures.sourcePine,
            queryKinds = "KEYWORD",
            queryHint = "书名或作者",
            filters = listOf(
                SearchFilterFixture("field", "搜索字段", SearchFilterKind.SINGLE_CHOICE, "标题", "TITLE|AUTHOR"),
                SearchFilterFixture("sort", "排序", SearchFilterKind.SORT, "相关性 · 静态选项已就绪"),
            ),
            laneState = "搜索中 · 已返回 2 条 · 可单独取消",
        ),
        SourceSearchDescriptorFixture(
            source = AtlasFixtures.sourceCypress,
            queryKinds = "KEYWORD|URL",
            queryHint = "关键词、标签或详情链接",
            filters = listOf(
                SearchFilterFixture("category", "分区", SearchFilterKind.SINGLE_CHOICE, "全部"),
                SearchFilterFixture("tags", "标签", SearchFilterKind.MULTI_CHOICE, "选项未加载；打开筛选不会联网", "TAG", remoteOptions = true),
                SearchFilterFixture("sort", "排序", SearchFilterKind.SORT, "最近更新"),
            ),
            laneState = "凭据过期 · 可单独处理后重试",
        ),
        SourceSearchDescriptorFixture(
            source = AtlasFixtures.sourceBamboo,
            queryKinds = "KEYWORD|URL|OPAQUE_ID",
            queryHint = "关键词、主题链接、tid/pid",
            filters = listOf(
                SearchFilterFixture("forumIds", "论坛分区", SearchFilterKind.MULTI_CHOICE, "文学区及子区"),
                SearchFilterFixture("scope", "搜索范围", SearchFilterKind.SINGLE_CHOICE, "仅标题", "TITLE"),
                SearchFilterFixture("postId", "帖子 ID", SearchFilterKind.INTEGER, "留空（也可直接在主搜索栏输入 pid）"),
                SearchFilterFixture("timeRange", "时间范围", SearchFilterKind.SINGLE_CHOICE, "全部时间"),
                SearchFilterFixture("order", "排序", SearchFilterKind.SORT, "发布时间 · 降序"),
            ),
            laneState = "来源休眠 · 本次不发请求",
        ),
    )

    /** Local-first fixture with one exact duplicate and one same-title/distinct-identity pair. */
    val aggregatedSearchResults: List<AtlasBook> = run {
        val seed = AtlasFixtures.books(count = 6, salt = 33)
        val pineIdentity = AtlasBookIdentity(AtlasFixtures.sourcePine.id, "pine-fog-harbor")
        val localBound = seed[0].copy(title = "雾港书简", identity = pineIdentity)
        val localOther = seed[1].copy(title = "雾港书简")
        listOf(
            localBound,
            localOther,
            seed[2].copy(id = "pine-fog-harbor", title = "雾港书简", source = AtlasFixtures.sourcePine, identity = pineIdentity),
            seed[3].copy(source = AtlasFixtures.sourcePine),
            seed[4].copy(title = "雾港书简", source = AtlasFixtures.sourceCypress, identity = AtlasBookIdentity(AtlasFixtures.sourceCypress.id, "cypress-fog-harbor")),
            seed[5].copy(source = AtlasFixtures.sourceBamboo, dormantSource = true),
        )
    }

    // ---------- Remote library (#18) ------------------------------------------------------------


    /**
     * Gates that can stand in front of the remote library (Atlas Spec §2.2 row 18: capability /
     * grant / credential), plus the dormant degradation.
     */
    enum class RemoteGate(val title: String, val message: String, val actionLabel: String?) {
        NONE("", "", null),
        CAPABILITY(
            title = "该源不支持网站收藏",
            message = "源·苇 的已验签清单没有声明网站收藏能力。此入口仅对声明了该能力的源开放。",
            actionLabel = null,
        ),
        GRANT(
            title = "需要授权",
            message = "读取网站收藏需要先完成一次授权。授权在源的页面内完成，宿主不接触你的密码。",
            actionLabel = "去授权",
        ),
        CREDENTIAL(
            title = "凭据已过期",
            message = "源·柏 的登录凭据已过期。重新登录后可重新读取网站收藏列表。",
            actionLabel = "重新登录",
        ),
        DORMANT(
            title = "来源休眠",
            message = "源·竹处于休眠状态，网站收藏列表当前不可读取；本地书架与最后完整镜像快照不受影响。",
            actionLabel = null,
        ),
    }

    /**
     * Deterministic capture mapping: `CONTINUE` → capability gate, `RECENT` → grant gate,
     * `READ_LATER` → credential gate, `DORMANT` → dormant degradation, `COLLECTION` → partial
     * result panel, anything else → no gate.
     */
    fun remoteGateFor(view: AtlasLibraryView): RemoteGate = when (view) {
        AtlasLibraryView.CONTINUE -> RemoteGate.CAPABILITY
        AtlasLibraryView.RECENT -> RemoteGate.GRANT
        AtlasLibraryView.READ_LATER -> RemoteGate.CREDENTIAL
        AtlasLibraryView.DORMANT -> RemoteGate.DORMANT
        else -> RemoteGate.NONE
    }

    /** True when the partial local-import result panel renders over the list (view = COLLECTION). */
    fun remoteShowsPartial(view: AtlasLibraryView): Boolean = view == AtlasLibraryView.COLLECTION

    /** Counted cancellable local-pin import fixture numbers. */
    const val IMPORT_TOTAL = 128
    const val IMPORT_DONE = 42
    const val IMPORT_PARTIAL_DONE = 87
    const val IMPORT_PARTIAL_FAILED = 3

    /** Local-import failure taxonomy rows (typed causes, no secrets, no raw pages). */
    val importFailureRows: List<Pair<String, String>> = listOf(
        "星海拾荒者" to "网络中断 · 已保留已复制的本地 pin",
        "雾都棋士" to "凭据失效 · 需重新登录",
        "山中邮差" to "条目格式无法解析 · 已跳过",
    )

    /** One remote-library entry: the book plus whether it is already on the shelf. */
    data class RemoteEntry(val book: AtlasBook, val onShelf: Boolean)

    /** E-ink remote list page size (constitution §5.6: remote/mirror list = 8). */
    const val REMOTE_PAGE_SIZE = 8

    val remoteEntries: List<RemoteEntry> =
        AtlasFixtures.books(count = 16, salt = 17).mapIndexed { index, book ->
            RemoteEntry(
                book = book.copy(source = AtlasFixtures.sourcePine),
                onShelf = index % 4 == 0,
            )
        }

    // ---------- Verification (#18) --------------------------------------------------------------

    const val VERIFICATION_HOST_NOTICE =
        "此验证由宿主应用发起。页面内容由 源·松 提供；Tsuyomi 不记录你在页面中的任何输入。"
    const val VERIFICATION_STATUS_WAITING = "状态：等待你在页面中完成验证"
    const val VERIFICATION_STUB_CAPTION =
        "图册占位：真实实现中此处为受控网页视图；本画面为合成内容，不发起任何网络请求。"
}
