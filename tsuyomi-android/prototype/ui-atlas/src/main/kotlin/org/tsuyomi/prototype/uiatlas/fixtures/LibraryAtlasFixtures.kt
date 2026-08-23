/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.fixtures

import androidx.compose.runtime.Immutable
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.model.AtlasBook
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView
import org.tsuyomi.prototype.uiatlas.model.AtlasSource

/**
 * Library-family fixtures (Atlas Spec §4: F1 views, F4 collections/tags, F5 mirror, F6 updates).
 *
 * Everything is synthetic zh-CN fixture data derived from the seeded [AtlasFixtures] pools; no
 * real source, brand, or user text appears. Parameterized library routes carry no id in
 * `AtlasContext`, so the family screen resolves the fixture subject deterministically from the
 * review extras (documented here, identical input → identical frame):
 *
 * - `library/collections/{id}`: manual `夜航船` by default; smart `科幻·未读` when
 *   `libraryView == COLLECTION`.
 * - `library/mirror/{bindingId}`: enabled `源·松` binding by default; frozen `源·竹` binding when
 *   `libraryView == MIRROR`; deep-link-from-Browse entry provenance when
 *   `libraryView == RECENT`.
 */
object LibraryAtlasFixtures {

    // --- Library views (#1/#2) ---------------------------------------------------------------

    /** One switchable library view: representative fixture books plus the guided empty copy. */
    @Immutable
    data class LibraryViewFixture(
        val view: AtlasLibraryView,
        val books: List<AtlasBook>,
        val emptyTitle: String,
        val emptyMessage: String?,
        val emptyActionLabel: String? = null,
    )

    /** Representative 24-book fixture shared by variant E anatomies (Atlas Spec §3 E). */
    val variantEBooks: List<AtlasBook> = AtlasFixtures.books(24, salt = 24)

    fun viewFixture(view: AtlasLibraryView): LibraryViewFixture = when (view) {
        AtlasLibraryView.ALL -> LibraryViewFixture(
            view = view,
            books = AtlasFixtures.books(24, salt = 1),
            emptyTitle = "书架还是空的",
            emptyMessage = "从「浏览」添加第一本书，它会出现在这里。",
            emptyActionLabel = AtlasStrings.EMPTY_LIBRARY_ACTION,
        )

        AtlasLibraryView.CONTINUE -> LibraryViewFixture(
            view = view,
            books = AtlasFixtures.books(14, salt = 2).map { book ->
                book.copy(
                    progressLabel = book.progressLabel ?: "读至 第6章 · 21%",
                    unreadUpdates = 0,
                    readLater = false,
                )
            },
            emptyTitle = "没有正在阅读的书",
            emptyMessage = "打开任意一本书开始阅读后，可以从这里快速继续。",
            emptyActionLabel = "查看全部书籍",
        )

        AtlasLibraryView.RECENT -> LibraryViewFixture(
            view = view,
            books = AtlasFixtures.books(20, salt = 3),
            emptyTitle = "最近还没有记录",
            emptyMessage = "最近打开或更新的书会出现在这里。",
            emptyActionLabel = "查看全部书籍",
        )

        AtlasLibraryView.READ_LATER -> LibraryViewFixture(
            view = view,
            books = AtlasFixtures.books(6, salt = 4).map { it.copy(readLater = true) },
            emptyTitle = "稍后再读是空的",
            emptyMessage = "在书籍详情里点「稍后再读」，把书留到以后。",
            emptyActionLabel = "查看全部书籍",
        )

        AtlasLibraryView.DORMANT -> LibraryViewFixture(
            view = view,
            books = AtlasFixtures.books(4, salt = 5).map {
                it.copy(source = AtlasFixtures.sourceBamboo, dormantSource = true)
            },
            emptyTitle = "没有休眠来源的书",
            emptyMessage = "来源休眠时，它的书会归入这里；已缓存的章节仍可阅读。",
        )

        AtlasLibraryView.COLLECTION -> LibraryViewFixture(
            view = view,
            books = AtlasFixtures.books(23, salt = 6),
            emptyTitle = "收藏夹为空",
            emptyMessage = null,
            emptyActionLabel = "管理收藏夹",
        )

        AtlasLibraryView.MIRROR -> LibraryViewFixture(
            view = view,
            books = AtlasFixtures.books(31, salt = 7).map {
                it.copy(source = AtlasFixtures.sourcePine, dormantSource = false)
            },
            emptyTitle = "镜像里还没有书",
            emptyMessage = "从源网站同步收藏后，书籍会显示在这里。",
            emptyActionLabel = "打开网站镜像",
        )
    }


    // --- Collections tree (F4: 8 folders, 2 smart leaves, 3 levels) ----------------------------

    @Immutable
    data class CollectionFixture(
        val id: String,
        val name: String,
        val bookCount: Int,
        val smart: Boolean = false,
        val ruleSummary: String? = null,
        val children: List<CollectionFixture> = emptyList(),
    )

    val smartSciFi = CollectionFixture(
        id = "col-sci-fi",
        name = "科幻·未读",
        bookCount = 9,
        smart = true,
        ruleSummary = "来源 属于 任意 · 标签 包含 科幻 · 阅读进度 等于 未读",
    )

    val smartReread = CollectionFixture(
        id = "col-reread",
        name = "雨天重读",
        bookCount = 4,
        smart = true,
        ruleSummary = "评分 大于等于 4 · 标签 包含 治愈",
    )

    val collectionZhiguai = CollectionFixture(
        id = "col-zhiguai",
        name = "志怪选",
        bookCount = 12,
        children = listOf(
            CollectionFixture(id = "col-archive", name = "已读归档", bookCount = 7),
        ),
    )

    val manualNightBoat = CollectionFixture(
        id = "col-night-boat",
        name = "夜航船",
        bookCount = 23,
        children = listOf(collectionZhiguai, smartSciFi),
    )

    val collectionTree: List<CollectionFixture> = listOf(
        manualNightBoat,
        CollectionFixture(
            id = "col-bedside",
            name = "枕边书",
            bookCount = 15,
            children = listOf(
                CollectionFixture(id = "col-shorts", name = "短篇集", bookCount = 6),
                smartReread,
            ),
        ),
        CollectionFixture(id = "col-unsorted", name = "待整理", bookCount = 3),
        CollectionFixture(
            id = "col-serials",
            name = "长篇连载",
            bookCount = 41,
            children = listOf(
                CollectionFixture(id = "col-weekly", name = "周更追读", bookCount = 11),
            ),
        ),
    )

    /** Manual collection detail (#7): non-recursive direct books plus child folders. */
    val manualDetailBooks: List<AtlasBook> = AtlasFixtures.books(8, salt = 61)

    /** Smart collection detail (#8): rule explanation plus matching books. */
    val smartDetailBooks: List<AtlasBook> = AtlasFixtures.books(9, salt = 62)

    fun booksForCollection(collection: CollectionFixture): List<AtlasBook> = when (collection.id) {
        manualNightBoat.id -> manualDetailBooks
        smartSciFi.id -> smartDetailBooks
        else -> AtlasFixtures.books(
            count = collection.bookCount.coerceIn(1, 12),
            salt = 70 + collection.id.sumOf(Char::code),
        )
    }

    /** Membership sheet fixture (#7 Mod): checkable collections for one book. */
    @Immutable
    data class MembershipRow(
        val id: String,
        val name: String,
        val depth: Int,
        val member: Boolean,
        val smartLocked: Boolean = false,
    )

    val membershipRows: List<MembershipRow> = listOf(
        MembershipRow("col-night-boat", "夜航船", depth = 0, member = true),
        MembershipRow("col-zhiguai", "志怪选", depth = 1, member = true),
        MembershipRow("col-archive", "已读归档", depth = 2, member = false),
        MembershipRow("col-sci-fi", "科幻·未读", depth = 1, member = false, smartLocked = true),
        MembershipRow("col-bedside", "枕边书", depth = 0, member = false),
        MembershipRow("col-unsorted", "待整理", depth = 0, member = false),
    )

    // --- Rule editor (#9): 3 groups / 12 conditions, caps visible, one inline AST error --------

    @Immutable
    data class RuleConditionFixture(
        val field: String,
        val operator: String,
        val value: String,
        val error: String? = null,
    )

    @Immutable
    data class RuleGroupFixture(val id: String, val conditions: List<RuleConditionFixture>)

    val ruleGroups: List<RuleGroupFixture> = listOf(
        RuleGroupFixture(
            id = "group-1",
            conditions = listOf(
                RuleConditionFixture("来源", "属于", "任意来源"),
                RuleConditionFixture("标签", "包含", "科幻"),
                RuleConditionFixture("阅读进度", "等于", "未读"),
                RuleConditionFixture("评分", "大于等于", "3"),
            ),
        ),
        RuleGroupFixture(
            id = "group-2",
            conditions = listOf(
                RuleConditionFixture("作者", "包含", "林晚照"),
                RuleConditionFixture("书名", "包含", "星海"),
                RuleConditionFixture("章节数", "大于", "100"),
                RuleConditionFixture("加入时间", "晚于", "2026-01-01"),
            ),
        ),
        RuleGroupFixture(
            id = "group-3",
            conditions = listOf(
                RuleConditionFixture("本地标签", "不包含", "已弃"),
                RuleConditionFixture("来源", "不属于", "源·竹"),
                RuleConditionFixture("章节数", "小于", "", error = "需要一个数值"),
                RuleConditionFixture(
                    "简介",
                    "包含",
                    "深夜食堂与旧书店之间往返的漫长雨季，以及被反复提及的那一杯手冲咖啡",
                ),
            ),
        ),
    )

    const val RULE_CONDITION_CAP = 64
    const val RULE_VALUE_CAP = 1024
    const val RULE_CONDITION_COUNT = 12
    const val RULE_LONGEST_VALUE = 36

    // --- View templates (#6) -------------------------------------------------------------------

    @Immutable
    data class ViewTemplateFixture(
        val id: String,
        val name: String,
        val description: String,
        val visible: Boolean,
        val system: Boolean,
    )

    val viewTemplates: List<ViewTemplateFixture> = listOf(
        ViewTemplateFixture("tpl-continue", "继续阅读", "按最近阅读进度排序的在读书籍", visible = true, system = true),
        ViewTemplateFixture("tpl-recent", "最近", "最近打开或更新的书籍", visible = true, system = true),
        ViewTemplateFixture("tpl-updates", "追更", "有未读章节更新的书籍", visible = true, system = true),
        ViewTemplateFixture("tpl-read-later", "稍后再读", "标记为稍后再读的书籍", visible = true, system = false),
        ViewTemplateFixture("tpl-dormant", "休眠来源", "来源已休眠、内容仍可阅读的书籍", visible = false, system = false),
    )

    const val TEMPLATE_VERSION_NOTE = "模板数据版本 v3 · 重新生成将按当前书架重建全部视图（约 2 秒）"

    // --- History (#3): recency groups ------------------------------------------------------------

    @Immutable
    data class HistoryEntryFixture(val book: AtlasBook, val timeLabel: String)

    @Immutable
    data class HistoryGroupFixture(val label: String, val entries: List<HistoryEntryFixture>)

    val historyGroups: List<HistoryGroupFixture> = listOf(
        HistoryGroupFixture(
            label = "今天",
            entries = listOf(
                HistoryEntryFixture(AtlasFixtures.book(1), "今天 09:12"),
                HistoryEntryFixture(AtlasFixtures.book(4), "今天 08:47"),
                HistoryEntryFixture(AtlasFixtures.book(7), "今天 07:30"),
            ),
        ),
        HistoryGroupFixture(
            label = "昨天",
            entries = listOf(
                HistoryEntryFixture(AtlasFixtures.book(2), "昨天 22:05"),
                HistoryEntryFixture(AtlasFixtures.book(5), "昨天 21:18"),
                HistoryEntryFixture(AtlasFixtures.book(8), "昨天 13:42"),
                HistoryEntryFixture(AtlasFixtures.book(10), "昨天 09:26"),
            ),
        ),
        HistoryGroupFixture(
            label = "本周更早",
            entries = listOf(
                HistoryEntryFixture(AtlasFixtures.book(3), "8月9日 23:11"),
                HistoryEntryFixture(AtlasFixtures.book(6), "8月9日 20:03"),
                HistoryEntryFixture(AtlasFixtures.book(9), "8月8日 22:47"),
                HistoryEntryFixture(AtlasFixtures.book(11), "8月7日 19:35"),
                HistoryEntryFixture(AtlasFixtures.book(12), "8月6日 21:52"),
            ),
        ),
    )

    // --- Updates (#4/F6): session rows, per-book anchors, exclusions, dormant report -------------

    @Immutable
    data class UpdateEntryFixture(
        val book: AtlasBook,
        val newChapters: Int,
        val anchorLabel: String,
        val updatedAtLabel: String,
    )

    /** Running session (42/128) rendered atop the inbox while a check is in flight. */
    const val UPDATE_RUNNING_CHECKED = 42
    const val UPDATE_RUNNING_TOTAL = 128
    const val UPDATE_RUNNING_FOUND = 3

    /** Previous session summary: partial failure is a first-class distinct state. */
    const val UPDATE_PARTIAL_FAILED = 3
    const val UPDATE_PARTIAL_DETAIL = "源·柏 凭据过期 ×2 · 源·松 连接超时 ×1"

    /** Failed source line reported inside the session block. */
    const val UPDATE_FAILED_SOURCE_LINE = "源·柏：凭据过期，本次未能检查（2 本书）"

    /** Dormant source is reported, never silently skipped. */
    const val UPDATE_DORMANT_LINE = "源·竹 已休眠：4 本书本次未检查"

    val updateEntries: List<UpdateEntryFixture> =
        AtlasFixtures.books(9, salt = 41).mapIndexed { index, book ->
            val chapters = (index % 7) + 1
            UpdateEntryFixture(
                book = book.copy(unreadUpdates = chapters),
                newChapters = chapters,
                anchorLabel = "更新至 第${40 + index * 3} 章",
                updatedAtLabel = "8 月 ${14 - index.coerceAtMost(8)} 日 ${9 + index}:20",
            )
        }

    @Immutable
    data class ExcludedBookFixture(val book: AtlasBook, val reason: String)

    val updateExclusions: List<ExcludedBookFixture> =
        AtlasFixtures.books(2, salt = 42).map {
            ExcludedBookFixture(it.copy(unreadUpdates = 0), reason = "手动排除 · 不检查更新")
        }

    // --- Tags (#10/F4): local rename/merge/delete, NFKC collision pair, source groups -----------

    @Immutable
    data class TagFixture(val id: String, val name: String, val bookCount: Int)

    val localTags: List<TagFixture> = listOf(
        TagFixture("tag-zhiguai", "志怪", 18),
        TagFixture("tag-zhiyu", "治愈", 9),
        TagFixture("tag-keihuan", "科幻", 23),
        TagFixture("tag-richang", "日常", 11),
        TagFixture("tag-meishi", "美食", 5),
        TagFixture("tag-lvxing", "旅行", 7),
        TagFixture("tag-gudian", "古典", 13),
        TagFixture("tag-cafe-zh", "咖啡厅", 3),
        TagFixture("tag-cafe-jp", "カフェ", 2),
    )

    /**
     * Normalization collision pair (F4): `咖啡厅` vs `カフェ` fold to the same key under NFKC +
     * case-fold, so merging/renaming surfaces an explicit collision dialog instead of silently
     * combining.
     */
    const val TAG_COLLISION_SOURCE = "咖啡厅"
    const val TAG_COLLISION_TARGET = "カフェ"
    const val TAG_COLLISION_MERGED_COUNT = 5

    @Immutable
    data class SourceTagGroupFixture(val source: AtlasSource, val tags: List<TagFixture>)

    val sourceTagGroups: List<SourceTagGroupFixture> = listOf(
        SourceTagGroupFixture(
            AtlasFixtures.sourcePine,
            listOf(TagFixture("st-pine-1", "奇幻", 42), TagFixture("st-pine-2", "武侠", 17), TagFixture("st-pine-3", "轻小说", 26)),
        ),
        SourceTagGroupFixture(
            AtlasFixtures.sourceCypress,
            listOf(TagFixture("st-cypress-1", "出版", 12), TagFixture("st-cypress-2", "推理", 9)),
        ),
        SourceTagGroupFixture(
            AtlasFixtures.sourceBamboo,
            listOf(TagFixture("st-bamboo-1", "日轻", 31), TagFixture("st-bamboo-2", "百合", 8)),
        ),
    )

    // --- Mirror (#11/F5): enabled + frozen bindings, ≤2-level tree, calibration sessions --------

    enum class MirrorNodeKind { FOLDER, BOOK }

    @Immutable
    data class MirrorNodeFixture(
        val id: String,
        val name: String,
        val kind: MirrorNodeKind,
        val childCount: Int = 0,
        val children: List<MirrorNodeFixture> = emptyList(),
        /** Deeper levels beyond the §8 ≤2-level expansion cap, surfaced honestly. */
        val hiddenLevels: Int = 0,
        val hiddenNodes: Int = 0,
    )

    @Immutable
    data class MirrorBindingFixture(
        val id: String,
        val source: AtlasSource,
        val frozen: Boolean,
        val nodeCount: Int,
        val lastSyncLabel: String,
        val roots: List<MirrorNodeFixture>,
    )

    private fun bookLeaf(id: String, title: String) =
        MirrorNodeFixture(id = id, name = title, kind = MirrorNodeKind.BOOK)

    val mirrorPine = MirrorBindingFixture(
        id = "mirror-pine",
        source = AtlasFixtures.sourcePine,
        frozen = false,
        nodeCount = 300,
        lastSyncLabel = "上次同步 2026-08-11 06:00",
        roots = listOf(
            MirrorNodeFixture(
                id = "mn-default",
                name = "默认收藏夹",
                kind = MirrorNodeKind.FOLDER,
                childCount = 96,
                children = listOf(
                    MirrorNodeFixture(
                        id = "mn-wuxia",
                        name = "武侠",
                        kind = MirrorNodeKind.FOLDER,
                        childCount = 24,
                        hiddenLevels = 2,
                        hiddenNodes = 18,
                        children = listOf(
                            bookLeaf("mn-b1", "纸灯巷的守夜人"),
                            bookLeaf("mn-b2", "雾都棋士"),
                            bookLeaf("mn-b3", "山中邮差"),
                        ),
                    ),
                    MirrorNodeFixture(
                        id = "mn-qihuan",
                        name = "奇幻",
                        kind = MirrorNodeKind.FOLDER,
                        childCount = 31,
                    ),
                    bookLeaf("mn-b4", "星海拾荒者"),
                ),
            ),
            MirrorNodeFixture(
                id = "mn-ongoing",
                name = "追更中",
                kind = MirrorNodeKind.FOLDER,
                childCount = 58,
            ),
            MirrorNodeFixture(
                id = "mn-finished",
                name = "已完结",
                kind = MirrorNodeKind.FOLDER,
                childCount = 146,
            ),
        ),
    )

    val mirrorBamboo = MirrorBindingFixture(
        id = "mirror-bamboo",
        source = AtlasFixtures.sourceBamboo,
        frozen = true,
        nodeCount = 87,
        lastSyncLabel = "上次成功 2026-08-04 21:12",
        roots = listOf(
            MirrorNodeFixture(
                id = "mn-fav",
                name = "お気に入り",
                kind = MirrorNodeKind.FOLDER,
                childCount = 52,
                children = listOf(
                    bookLeaf("mn-bb1", "青石镇异闻录"),
                    bookLeaf("mn-bb2", "猫、雨与旧书店"),
                ),
            ),
            MirrorNodeFixture(
                id = "mn-history",
                name = "履歴",
                kind = MirrorNodeKind.FOLDER,
                childCount = 35,
            ),
        ),
    )

    /** The one UNRESOLVED remote add attempt (F5): never auto-retried, local truth untouched. */
    const val MIRROR_UNRESOLVED_LINE = "向「源·松」添加收藏《雾都棋士》的请求未收到确认"

    enum class CalibrationPhase { WORKING, SUCCESS, FAILED }

    fun calibrationMessage(phase: CalibrationPhase): String = when (phase) {
        CalibrationPhase.WORKING -> "正在校准「源·松」镜像… 已比对 118 / 300"
        CalibrationPhase.SUCCESS -> "校准完成：300 个节点全部匹配"
        CalibrationPhase.FAILED -> "校准失败：网站结构已变更，已保留原快照"
    }
}
