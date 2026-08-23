/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas

/**
 * Shared zh-CN vocabulary for atlas chrome and state surfaces (fixed primary locale, Atlas Spec
 * §1.5). Strings live in code — not resources — so captures never depend on the host locale.
 * Family screens reuse these constants instead of inventing parallel wording.
 */
object AtlasStrings {
    const val ROOT_LIBRARY = "书架"
    const val ROOT_BROWSE = "浏览"
    const val ROOT_MORE = "更多"

    const val LOADING = "正在加载…"
    const val RETRY = "重试"
    const val CANCEL = "取消"
    const val CLOSE = "关闭"
    const val CONFIRM = "确认"

    const val NAVIGATE_UP = "返回上级"
    const val OVERFLOW = "更多操作"
    const val SELECT_ALL = "全选"
    const val CLEAR_SELECTION = "清除所有选择"
    const val SELECT = "选择"
    const val SEARCH = "搜索"
    const val HISTORY = "历史"

    const val EMPTY_LIBRARY_ACTION = "浏览并添加书籍"
    const val OFFLINE_TITLE = "当前离线"
    const val REFRESHING_TITLE = "正在刷新…"
    const val UNRESOLVED_TITLE = "远程操作未确认"
    const val MUTATION_WORKING = "正在处理…"

    const val READ_LATER = "稍后再读"
    const val DORMANT_SOURCE = "来源休眠"
    const val FROZEN_MIRROR = "已冻结"

    fun selectedCount(count: Int): String = "已选 $count"

    fun subtitleCount(view: String, count: Int): String = "$view · $count"

    fun unreadUpdates(count: Int): String = "$count 章更新"

    fun pageOf(page: Int, total: Int): String = "第 $page 页，共 $total 页"

    fun ratingLabel(value: Int): String = "★ $value"

    fun extraTags(count: Int): String = "+$count"
}
