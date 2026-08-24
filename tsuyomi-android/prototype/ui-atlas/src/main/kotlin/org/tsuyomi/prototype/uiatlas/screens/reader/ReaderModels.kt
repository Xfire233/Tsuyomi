/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens.reader

import androidx.compose.runtime.Immutable
import org.tsuyomi.prototype.uiatlas.fixtures.SourceAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView

@Immutable
enum class ReaderFlow(val label: String) {
    SCROLL("连续滚动"),
    PAGED("左右分页"),
    DUAL("双页"),
}

@Immutable
data class ReaderPosition(
    val progress: Int,
    val page: Int,
    val pageCount: Int,
) {
    companion object {
        val START = ReaderPosition(progress = 0, page = 1, pageCount = 1)

        fun fromProgress(progress: Int, pageCount: Int): ReaderPosition {
            val safeCount = pageCount.coerceAtLeast(1)
            val safeProgress = progress.coerceIn(0, 100)
            val pageIndex = if (safeCount == 1) 0 else {
                ((safeProgress / 100f) * (safeCount - 1)).toInt().coerceIn(0, safeCount - 1)
            }
            return ReaderPosition(safeProgress, pageIndex + 1, safeCount)
        }
    }
}

@Immutable
enum class ReaderAuxiliaryTab(val label: String) {
    CONTENTS("目录"),
    BOOKMARKS("书签"),
    SEARCH("搜索"),
}

@Immutable
enum class ReaderDocumentKind {
    PROSE,
    MIXED_MEDIA,
    REPLY_STREAM,
}

@Immutable
data class ReaderDocument(
    val id: String,
    val title: String,
    val kind: ReaderDocumentKind,
    val blocks: List<ReaderBlock>,
)

@Immutable
sealed interface ReaderBlock {
    val id: String
}

@Immutable
data class ReaderHeading(
    override val id: String,
    val text: String,
    val level: Int,
) : ReaderBlock

@Immutable
data class ReaderParagraph(
    override val id: String,
    val content: List<ReaderInline>,
) : ReaderBlock

@Immutable
data class ReaderImage(
    override val id: String,
    val title: String,
    val alternative: String,
    val caption: String,
    val aspectRatio: Float,
) : ReaderBlock

@Immutable
data class ReaderQuote(
    override val id: String,
    val content: List<ReaderInline>,
    val attribution: String? = null,
) : ReaderBlock

@Immutable
data class ReaderDivider(override val id: String) : ReaderBlock

@Immutable
data class ReaderListBlock(
    override val id: String,
    val ordered: Boolean,
    val items: List<List<ReaderInline>>,
) : ReaderBlock

@Immutable
data class ReaderCodeBlock(
    override val id: String,
    val language: String?,
    val code: String,
) : ReaderBlock

@Immutable
data class ReaderTableBlock(
    override val id: String,
    val headers: List<String>,
    val rows: List<List<String>>,
) : ReaderBlock

@Immutable
data class ReaderReplyReference(
    override val id: String,
    val floor: String,
    val author: String,
    val excerpt: String,
) : ReaderBlock

@Immutable
data class ReaderAttachment(
    override val id: String,
    val name: String,
    val meta: String,
) : ReaderBlock

@Immutable
data class ReaderPost(
    override val id: String,
    val floor: String,
    val author: String,
    val time: String,
    val isOriginalPoster: Boolean,
    val blocks: List<ReaderBlock>,
) : ReaderBlock

@Immutable
sealed interface ReaderInline {
    val text: String

    @Immutable
    data class Plain(override val text: String) : ReaderInline

    @Immutable
    data class Strong(override val text: String) : ReaderInline

    @Immutable
    data class Emphasis(override val text: String) : ReaderInline

    @Immutable
    data class Strike(override val text: String) : ReaderInline

    @Immutable
    data class Code(override val text: String) : ReaderInline

    @Immutable
    data class Link(override val text: String, val destination: String) : ReaderInline

    @Immutable
    data class Ruby(override val text: String, val reading: String) : ReaderInline
}

internal object ReaderAtlasFixtures {
    fun documentFor(view: AtlasLibraryView, chapterNumber: Int): ReaderDocument = when (view) {
        AtlasLibraryView.CONTINUE -> mixedMediaDocument(chapterNumber)
        AtlasLibraryView.RECENT -> replyStreamDocument(chapterNumber)
        else -> proseDocument(chapterNumber)
    }

    fun previewSnippet(chapterNumber: Int): String =
        SourceAtlasFixtures.readerPageText(pageForChapter(chapterNumber))
            .lineSequence()
            .first()
            .take(56)

    private fun proseDocument(chapterNumber: Int): ReaderDocument {
        val chapter = SourceAtlasFixtures.chapters[(chapterNumber - 1).coerceIn(SourceAtlasFixtures.chapters.indices)]
        val paragraphs = SourceAtlasFixtures.readerPageText(pageForChapter(chapterNumber))
            .split("\n\n")
        return ReaderDocument(
            id = "prose-$chapterNumber",
            title = chapter.title,
            kind = ReaderDocumentKind.PROSE,
            blocks = buildList {
                paragraphs.forEachIndexed { index, paragraph ->
                    add(
                        ReaderParagraph(
                            id = "paragraph-$index",
                            content = if (index == 1) {
                                listOf(
                                    ReaderInline.Plain(paragraph.take(paragraph.length / 2)),
                                    ReaderInline.Emphasis(paragraph.drop(paragraph.length / 2)),
                                )
                            } else {
                                listOf(ReaderInline.Plain(paragraph))
                            },
                        ),
                    )
                    if (index == 2) {
                        add(
                            ReaderQuote(
                                id = "quote",
                                content = listOf(ReaderInline.Plain("灯火不会替人指路，但会让归来的人知道还有一扇门没有关。")),
                                attribution = "《守夜手记》",
                            ),
                        )
                    }
                }
            },
        )
    }

    private fun mixedMediaDocument(chapterNumber: Int): ReaderDocument = ReaderDocument(
        id = "mixed-$chapterNumber",
        title = "第${chapterNumber}章 · 河图残卷",
        kind = ReaderDocumentKind.MIXED_MEDIA,
        blocks = listOf(
            ReaderHeading("mixed-title", "河图残卷", level = 1),
            ReaderParagraph(
                "mixed-intro",
                listOf(
                    ReaderInline.Plain("雨停后，木匣里的旧图重新显出颜色。守夜人先辨认出"),
                    ReaderInline.Strong("三处渡口"),
                    ReaderInline.Plain("，随后看见角落写着"),
                    ReaderInline.Ruby("归舟", "guī zhōu"),
                    ReaderInline.Plain("二字。"),
                ),
            ),
            ReaderImage(
                id = "river-map",
                title = "河图残卷",
                alternative = "泛黄纸面上绘有三条河道、山脊和一盏红色纸灯",
                caption = "图一 · 木匣中的河道手绘图",
                aspectRatio = 1.45f,
            ),
            ReaderQuote(
                id = "map-note",
                content = listOf(
                    ReaderInline.Plain("潮生时看山，潮退时看灯。"),
                    ReaderInline.Strike("切勿独行"),
                ),
                attribution = "残卷背面题记",
            ),
            ReaderListBlock(
                id = "clues",
                ordered = true,
                items = listOf(
                    listOf(ReaderInline.Plain("确认北侧渡口的石阶数量。")),
                    listOf(ReaderInline.Plain("比对旧信中的灯号："), ReaderInline.Code("短、短、长")),
                    listOf(ReaderInline.Plain("在天亮前返回纸灯巷。")),
                ),
            ),
            ReaderImage(
                id = "lantern-detail",
                title = "纸灯纹样细节",
                alternative = "纸灯剪影中隐藏着类似水鸟的纹样",
                caption = "图二 · 放大后的剪纸纹样",
                aspectRatio = 0.78f,
            ),
            ReaderTableBlock(
                id = "tide-table",
                headers = listOf("时刻", "河面记号"),
                rows = listOf(
                    listOf("子时", "石阶露出三层"),
                    listOf("寅时", "旧桥影指向东南"),
                    listOf("卯时", "纸灯熄灭"),
                ),
            ),
            ReaderCodeBlock(
                id = "signal",
                language = "灯号",
                code = "··—  /  ·—·  /  —··",
            ),
            ReaderAttachment(
                id = "attachment",
                name = "河图残卷题记.txt",
                meta = "UTF-8 · 2.4 KB · 已下载",
            ),
            ReaderParagraph(
                "mixed-outro",
                listOf(
                    ReaderInline.Plain("关于图中旧桥的考据可查看"),
                    ReaderInline.Link("《南河渡口沿革》", "tsuyomi://note/south-river"),
                    ReaderInline.Plain("。链接由宿主处理，不交给来源渲染。"),
                ),
            ),
        ),
    )

    private fun replyStreamDocument(chapterNumber: Int): ReaderDocument = ReaderDocument(
        id = "thread-$chapterNumber",
        title = "第${chapterNumber}话讨论 · 灯影从哪里来",
        kind = ReaderDocumentKind.REPLY_STREAM,
        blocks = listOf(
            ReaderHeading("thread-title", "灯影从哪里来", level = 1),
            ReaderParagraph(
                "thread-summary",
                listOf(ReaderInline.Plain("回复流保持稳定 postId；楼层折叠、字体变化或图片重排都不会改变语义阅读位置。")),
            ),
            ReaderPost(
                id = "post-1082",
                floor = "楼主",
                author = "河岸听雨",
                time = "08-22 21:14",
                isOriginalPoster = true,
                blocks = listOf(
                    ReaderParagraph(
                        "post-1082-body",
                        listOf(
                            ReaderInline.Plain("我把第十二章提到的三处灯影画在一起，发现它们并不是同一盏灯的倒影。"),
                        ),
                    ),
                    ReaderImage(
                        id = "post-1082-image",
                        title = "三处灯影对照",
                        alternative = "三张河面灯影的并排对照图",
                        caption = "楼主上传 · 原图可查看",
                        aspectRatio = 1.8f,
                    ),
                ),
            ),
            ReaderPost(
                id = "post-1087",
                floor = "2楼",
                author = "雾都棋士",
                time = "08-22 21:37",
                isOriginalPoster = false,
                blocks = listOf(
                    ReaderReplyReference(
                        id = "reply-1082",
                        floor = "楼主",
                        author = "河岸听雨",
                        excerpt = "它们并不是同一盏灯的倒影……",
                    ),
                    ReaderParagraph(
                        "post-1087-body",
                        listOf(
                            ReaderInline.Plain("第二张图右下角的水纹方向相反，可能是另一条支流。"),
                            ReaderInline.Strong("建议对照第九章的图卷。"),
                        ),
                    ),
                ),
            ),
            ReaderPost(
                id = "post-1095",
                floor = "3楼",
                author = "山中邮差",
                time = "08-22 22:03 · 已编辑",
                isOriginalPoster = false,
                blocks = listOf(
                    ReaderParagraph(
                        "post-1095-body",
                        listOf(ReaderInline.Plain("旧版 TXT 的换行和新版网页不同，但宿主标准化后段落与引用关系一致。")),
                    ),
                    ReaderAttachment(
                        id = "post-1095-attachment",
                        name = "第九章旧版校对记录.txt",
                        meta = "UTF-8 · 6.1 KB",
                    ),
                ),
            ),
        ),
    )

    private fun pageForChapter(chapterNumber: Int): Int =
        ((chapterNumber - 1) % SourceAtlasFixtures.READER_PAGE_COUNT) + 1
}
