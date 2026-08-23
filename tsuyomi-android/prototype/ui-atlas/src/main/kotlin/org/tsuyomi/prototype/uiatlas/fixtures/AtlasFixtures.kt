/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.fixtures

import androidx.compose.ui.graphics.Color
import kotlin.random.Random
import org.tsuyomi.prototype.uiatlas.ATLAS_FIXED_CLOCK
import org.tsuyomi.prototype.uiatlas.ATLAS_SEED
import org.tsuyomi.prototype.uiatlas.model.AtlasBook
import org.tsuyomi.prototype.uiatlas.model.AtlasBrandInvalidity
import org.tsuyomi.prototype.uiatlas.model.AtlasBranding
import org.tsuyomi.prototype.uiatlas.model.AtlasCover
import org.tsuyomi.prototype.uiatlas.model.AtlasSource
import org.tsuyomi.prototype.uiatlas.model.AtlasSourceMark

/**
 * Deterministic synthetic fixtures shared by every atlas family (Atlas Spec §4 base pools).
 * Family-specific matrices (F1–F12 detail) extend these pools in their own fixture files; this
 * file owns the seeded primitives so identical input always yields identical frames.
 */
object AtlasFixtures {

    /** Fixed review clock shown wherever a timestamp renders (never the wall clock). */
    const val CLOCK: String = ATLAS_FIXED_CLOCK

    // --- Sources (F8/F9): one full-capability, one dormant, one credential-expired. ----------

    /** 源·松 — installed, full capability, valid signed branding. */
    val sourcePine = AtlasSource(
        id = "atlas.pine",
        name = "源·松",
        mark = AtlasSourceMark.PINE,
        branding = AtlasBranding.Valid(Color(0xFF3E5C4B)),
        version = "v1.4",
    )

    /** 源·柏 — installed, credential expired, valid branding of a different hue. */
    val sourceCypress = AtlasSource(
        id = "atlas.cypress",
        name = "源·柏",
        mark = AtlasSourceMark.CYPRESS,
        branding = AtlasBranding.Valid(Color(0xFF4A5E6E)),
        version = "v2.1",
        credentialExpired = true,
    )

    /** 源·竹 — installed, dormant; branding valid but the source is degraded. */
    val sourceBamboo = AtlasSource(
        id = "atlas.bamboo",
        name = "源·竹",
        mark = AtlasSourceMark.BAMBOO,
        branding = AtlasBranding.Valid(Color(0xFF5E6247)),
        version = "v0.9",
        dormant = true,
    )

    /** Branding variant fixtures (F9): the same source rendered through each pipeline outcome. */
    val brandingInvalidScript = AtlasBranding.Invalid(AtlasBrandInvalidity.SCRIPT)
    val brandingInvalidRemoteRef = AtlasBranding.Invalid(AtlasBrandInvalidity.REMOTE_REF)
    val brandingInvalidOversize = AtlasBranding.Invalid(AtlasBrandInvalidity.OVERSIZE)
    val brandingMissing = AtlasBranding.Missing

    val installedSources: List<AtlasSource> = listOf(sourcePine, sourceCypress, sourceBamboo)

    // --- Book text pools (F1/F3): synthetic titles incl. long-text stress entries. ------------

    private val titles = listOf(
        "纸灯巷的守夜人",
        "半亩方塘一鉴开",
        "星海拾荒者",
        "青石镇异闻录",
        "凌晨四点的面包房",
        "雾都棋士",
        "沙丘译丛：失落航线",
        "猫、雨与旧书店",
        "山中邮差",
        "无名氏的植物图鉴",
        "霓虹深渊漫游指南 Neon Abyss Guide 2049",
        "风之谷的第三封信 ✉",
        // F3 long-text stress: 60+ hanzi single-line breaker.
        "关于我在异世界经营深夜食堂却意外卷入魔王讨伐战并不得不一边炖汤一边拯救世界这件小事",
        // F3 RTL stress title.
        "حكاية المقهى القديم 老咖啡馆的故事",
    )

    private val authors = listOf(
        "林晚照",
        "陈栖迟",
        "顾青崖",
        "苏折柳",
        "沈照萤",
        "叶扶疏 · 闻人棠",
        "白鹭洲",
        // F3 stress: 40+ character author list.
        "林晚照 · 陈栖迟 · 顾青崖 · 苏折柳 · 沈照萤 · 叶扶疏 · 闻人棠 · 白鹭洲",
    )

    private val tagPool = listOf("志怪", "治愈", "悬疑", "科幻", "日常", "美食", "旅行", "古典")

    private val progressPool = listOf(
        null,
        "读至 第3章 · 12%",
        "读至 第12章 · 43%",
        "读至 第27章 · 68%",
        "读至 第41章 · 91%",
        "已读完",
    )

    /**
     * Generates [count] deterministic books (Atlas Spec F1/F10 shape). Seeded by
     * [ATLAS_SEED] + [salt]; repeated calls with the same arguments return identical lists.
     */
    fun books(count: Int, salt: Int = 0): List<AtlasBook> {
        val random = Random(ATLAS_SEED + salt)
        return List(count) { index ->
            val source = installedSources[random.nextInt(installedSources.size)]
            val tagCount = random.nextInt(0, 4)
            val tags = List(tagCount) { tagPool[random.nextInt(tagPool.size)] }.distinct()
            val title = titles[random.nextInt(titles.size)]
            val authors = authors[random.nextInt(authors.size)]
            val cover = when (random.nextInt(10)) {
                0 -> AtlasCover.Absent
                1 -> AtlasCover.Failed
                2 -> AtlasCover.Stale(seed = ATLAS_SEED + index)
                else -> AtlasCover.Generated(seed = ATLAS_SEED + salt * 997L + index)
            }
            val owningSource = if (random.nextInt(12) == 0) null else source
            val progressLabel = progressPool[random.nextInt(progressPool.size)]
            val unreadUpdates = if (random.nextInt(4) == 0) random.nextInt(1, 8) else 0
            val readLater = random.nextInt(8) == 0
            val rating = if (random.nextInt(3) == 0) random.nextInt(1, 6) else null
            AtlasBook(
                id = "book-$salt-$index",
                title = title,
                authors = authors,
                cover = cover,
                source = owningSource,
                progressLabel = progressLabel,
                unreadUpdates = unreadUpdates,
                readLater = readLater,
                dormantSource = source.dormant,
                lastReadAtEpochMillis = progressLabel?.let {
                    1_775_000_000_000L - (salt * 97L + index) * 3_600_000L
                },
                rating = rating,
                tags = tags,
            )
        }
    }

    /** One deterministic book by index; stable across runs and call sites. */
    fun book(index: Int): AtlasBook = books(count = index + 1).last()
}
