/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.model

/**
 * The three review families of the atlas. They mirror the product's three roots
 * (书架 / 浏览 / 更多); canonical book surfaces travel with the Library family by default because
 * every library context opens them, while source-owned browsing surfaces form the Source family.
 */
enum class AtlasFamily {
    LIBRARY,
    SOURCE,
    MORE,
}

/** Screen archetypes from constitution §20; they drive state coverage and window sampling. */
enum class AtlasArchetype {
    ROOT_LIST,
    COLLECTION_LIST,
    DETAIL,
    FORM,
    FLOW,
    READER,
    REPORT,
    INFO,
}

/**
 * Fixture routes retained by the standalone Atlas host. Removed Phase 4 destinations are resolved
 * by [parse] directly to their canonical successor and therefore have no enum entry or renderable
 * standalone surface.
 *
 * [title] is the stable zh-CN screen noun used by the app bar.
 * [listGrid] marks book-bearing contexts where LIST/COMPACT/GRID remain available.
 * [fontScale2Pass] marks surfaces requiring the 2.0 no-clip pass.
 */
enum class AtlasRoute(
    val path: String,
    val family: AtlasFamily,
    val archetype: AtlasArchetype,
    val title: String,
    val listGrid: Boolean = false,
    val fontScale2Pass: Boolean = false,
) {
    LIBRARY("library", AtlasFamily.LIBRARY, AtlasArchetype.ROOT_LIST, "书架", listGrid = true, fontScale2Pass = true),
    LIBRARY_SYSTEM(
        "library/system/{viewId}",
        AtlasFamily.LIBRARY,
        AtlasArchetype.COLLECTION_LIST,
        "书架视图",
        listGrid = true,
    ),
    LIBRARY_HISTORY("library/history", AtlasFamily.LIBRARY, AtlasArchetype.COLLECTION_LIST, "历史"),
    LIBRARY_UPDATES("library/updates", AtlasFamily.LIBRARY, AtlasArchetype.COLLECTION_LIST, "追更", listGrid = true),
    LIBRARY_COLLECTION(
        "library/collections/{collectionId}",
        AtlasFamily.LIBRARY,
        AtlasArchetype.COLLECTION_LIST,
        "收藏夹",
        listGrid = true,
    ),
    LIBRARY_COLLECTION_CHILD(
        "library/collections/{collectionId}/children/{childId}",
        AtlasFamily.LIBRARY,
        AtlasArchetype.COLLECTION_LIST,
        "子收藏夹",
        listGrid = true,
    ),
    LIBRARY_COLLECTION_GRANDCHILD(
        "library/collections/{collectionId}/children/{childId}/children/{grandchildId}",
        AtlasFamily.LIBRARY,
        AtlasArchetype.COLLECTION_LIST,
        "子收藏夹",
        listGrid = true,
    ),
    LIBRARY_COLLECTION_RULE(
        "library/collections/{collectionId}/rule",
        AtlasFamily.LIBRARY,
        AtlasArchetype.FORM,
        "规则",
        fontScale2Pass = true,
    ),
    LIBRARY_TAGS("library/tags", AtlasFamily.LIBRARY, AtlasArchetype.COLLECTION_LIST, "标签", listGrid = true),
    LIBRARY_MIRROR(
        "library/mirror/{bindingId}",
        AtlasFamily.LIBRARY,
        AtlasArchetype.COLLECTION_LIST,
        "网站镜像",
        listGrid = true,
    ),
    LIBRARY_MIRROR_FOLDER(
        "library/mirror/{bindingId}/folders/{folderId}",
        AtlasFamily.LIBRARY,
        AtlasArchetype.COLLECTION_LIST,
        "镜像收藏夹",
        listGrid = true,
    ),
    LIBRARY_MIRROR_SUBFOLDER(
        "library/mirror/{bindingId}/folders/{folderId}/folders/{subfolderId}",
        AtlasFamily.LIBRARY,
        AtlasArchetype.COLLECTION_LIST,
        "镜像收藏夹",
        listGrid = true,
    ),
    BOOK_DETAIL(
        "book/{sourceId}/{remoteBookId}",
        AtlasFamily.LIBRARY,
        AtlasArchetype.DETAIL,
        "书籍详情",
        fontScale2Pass = true,
    ),
    BOOK_READER(
        "book/{sourceId}/{remoteBookId}/reader/{chapterId}",
        AtlasFamily.LIBRARY,
        AtlasArchetype.READER,
        "阅读",
    ),
    BROWSE("browse", AtlasFamily.SOURCE, AtlasArchetype.ROOT_LIST, "浏览"),
    SEARCH(
        "search",
        AtlasFamily.SOURCE,
        AtlasArchetype.COLLECTION_LIST,
        "聚合搜索",
        listGrid = true,
        fontScale2Pass = true,
    ),
    BROWSE_SOURCE_REMOTE_LIBRARY(
        "browse/source/{sourceId}/remote-library",
        AtlasFamily.SOURCE,
        AtlasArchetype.COLLECTION_LIST,
        "网站收藏",
        listGrid = true,
    ),
    SOURCE_VERIFICATION("source/verification", AtlasFamily.SOURCE, AtlasArchetype.FLOW, "登录验证"),
    MORE("more", AtlasFamily.MORE, AtlasArchetype.ROOT_LIST, "更多"),
    MORE_DISPLAY("more/display", AtlasFamily.MORE, AtlasArchetype.FORM, "显示"),
    MORE_READER("more/reader", AtlasFamily.MORE, AtlasArchetype.FORM, "阅读"),
    MORE_DATA("more/data", AtlasFamily.MORE, AtlasArchetype.FORM, "数据"),
    MORE_DATA_REPORT("more/data/report/{sessionId}", AtlasFamily.MORE, AtlasArchetype.REPORT, "导入报告"),
    MORE_HELP("more/help", AtlasFamily.MORE, AtlasArchetype.INFO, "帮助"),
    MORE_ABOUT("more/about", AtlasFamily.MORE, AtlasArchetype.INFO, "关于"),
    ;

    /** True for the single root destination of each family (书架 / 浏览 / 更多). */
    val isRoot: Boolean
        get() = this == LIBRARY || this == BROWSE || this == MORE

    companion object {
        /** Root route of a family; used when the reviewer switches family without a route pick. */
        fun rootOf(family: AtlasFamily): AtlasRoute = when (family) {
            AtlasFamily.LIBRARY -> LIBRARY
            AtlasFamily.SOURCE -> BROWSE
            AtlasFamily.MORE -> MORE
        }

        /**
         * Resolves an intent extra or menu value to a route. Accepts the enum name
         * (`LIBRARY_HISTORY`), the pattern (`library/history`), or a concrete path whose segments
         * match a pattern with `{param}` placeholders (`library/collections/3`). Returns null for
         * unrecognized input; callers fall back to the default route.
         */
        fun parse(raw: String?): AtlasRoute? {
            val value = raw?.trim()?.removePrefix("/").orEmpty()
            if (value.isEmpty()) return null
            if (value == "library/collections" || value == "library/collections/templates") return LIBRARY
            if (Regex("book/[^/]+/[^/]+/directory").matches(value)) return BOOK_DETAIL
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }?.let { return it }
            entries.firstOrNull { it.path == value }?.let { return it }
            val segments = value.split('/')
            return entries
                .filter { route ->
                    val pattern = route.path.split('/')
                    pattern.size == segments.size &&
                        pattern.indices.all { i ->
                            pattern[i].startsWith("{") || pattern[i] == segments[i]
                        }
                }
                .minByOrNull { route -> route.path.count { it == '{' } }
        }
    }
}
