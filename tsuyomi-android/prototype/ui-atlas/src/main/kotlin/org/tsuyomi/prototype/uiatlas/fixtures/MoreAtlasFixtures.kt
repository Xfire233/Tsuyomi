/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.fixtures

/** One grouped destination row on the More root. */
data class MoreDestinationFixture(
    val title: String,
    val summary: String,
)

data class MoreDestinationGroupFixture(
    val title: String,
    val destinations: List<MoreDestinationFixture>,
)

/** Fixed F12 preference payload used by the Display route. */
data class MoreDisplayFixture(
    val profileOptions: List<String>,
    val themeOptions: List<String>,
    val storedProfile: String,
    val storedTheme: String,
    val dynamicColorStored: Boolean,
    val dynamicColorCapability: String,
    val eInkRedrawStored: Boolean,
    val unknownSchemaVersion: Int,
    val resetScope: String,
)

/** Fixed reader preferences; persisted values intentionally differ from E-ink effective values. */
data class MoreReaderFixture(
    val fontSizeOptions: List<String>,
    val lineSpacingOptions: List<String>,
    val layoutOptions: List<String>,
    val storedFontSize: String,
    val storedLineSpacing: String,
    val storedLayout: String,
    val eInkEffectiveLayout: String,
    val pageAnimationStored: Boolean,
    val volumePagingStored: Boolean,
    val volumeMediaStored: Boolean,
    val progressStored: Boolean,
)

data class MoreDataEntryFixture(
    val title: String,
    val summary: String,
)

/** One redacted, deterministic import-review issue. */
data class MoreTransferIssueFixture(
    val code: String,
    val title: String,
    val detail: String,
)

/** F7: 87 warnings, 23 conflicts, three formats, and a pending recovery gate. */
data class MoreTransferReportFixture(
    val sessionId: String,
    val startedAt: String,
    val sourceFormat: String,
    val supportedFormats: List<String>,
    val importedBooks: Int,
    val importedCollections: Int,
    val importedAnnotations: Int,
    val warnings: List<MoreTransferIssueFixture>,
    val conflicts: List<MoreTransferIssueFixture>,
    val warningCap: Int,
    val recoveryPending: Boolean,
)

/** One replayable feature-introduction surface. */
data class MoreFeatureIntroductionFixture(
    val id: String,
    val title: String,
    val version: String,
    val summary: String,
    val points: List<String>,
)

data class MoreAboutFixture(
    val appName: String,
    val version: String,
    val build: String,
    val licenseName: String,
    val licenseNotice: String,
    val fixtureNotice: String,
)

/**
 * Deterministic synthetic zh-CN fixtures for routes 19–23. This object has no production,
 * persistence, file, network, or platform dependency; every value is fixed review data.
 */
object MoreAtlasFixtures {
    val destinationGroups = listOf(
        MoreDestinationGroupFixture(
            title = "界面与阅读",
            destinations = listOf(
                MoreDestinationFixture(
                    title = "显示",
                    summary = "显示配置、主题、动态配色与界面重置",
                ),
                MoreDestinationFixture(
                    title = "阅读",
                    summary = "排版、布局、导航与进度显示",
                ),
            ),
        ),
        MoreDestinationGroupFixture(
            title = "数据与支持",
            destinations = listOf(
                MoreDestinationFixture(
                    title = "数据",
                    summary = "分开导入、导出并查看最近报告",
                ),
                MoreDestinationFixture(
                    title = "帮助",
                    summary = "重播功能说明、支持指南与界面重置入口",
                ),
                MoreDestinationFixture(
                    title = "关于",
                    summary = "版本、许可与图册说明",
                ),
            ),
        ),
    )

    val display = MoreDisplayFixture(
        profileOptions = listOf("标准", "电子墨水"),
        themeOptions = listOf("跟随系统", "浅色", "深色"),
        storedProfile = "标准",
        storedTheme = "跟随系统",
        dynamicColorStored = true,
        dynamicColorCapability = "固定能力样例：支持动态配色；仅标准显示配置可生效",
        eInkRedrawStored = true,
        unknownSchemaVersion = 99,
        resetScope = "显示配置、主题、动态配色、每个书架视图的布局覆盖、排序方式与功能说明已读版本；书籍、收藏夹、标签、评分、阅读进度、历史、来源与凭据均不受影响。",
    )

    val reader = MoreReaderFixture(
        fontSizeOptions = listOf("小", "标准", "大"),
        lineSpacingOptions = listOf("紧凑", "舒适", "宽松"),
        layoutOptions = listOf("连续滚动", "分页"),
        storedFontSize = "标准",
        storedLineSpacing = "舒适",
        storedLayout = "连续滚动",
        eInkEffectiveLayout = "分页",
        pageAnimationStored = true,
        volumePagingStored = true,
        volumeMediaStored = false,
        progressStored = true,
    )

    val dataEntries = listOf(
        MoreDataEntryFixture(
            title = "导入 Tsuyomi 数据",
            summary = "先审阅内容与警告，再明确确认导入",
        ),
        MoreDataEntryFixture(
            title = "从 Hikari Novel 导入",
            summary = "独立迁移入口；不会与 Tsuyomi 数据包混用",
        ),
        MoreDataEntryFixture(
            title = "导出",
            summary = "预览范围与格式；取消不会改变任何状态",
        ),
        MoreDataEntryFixture(
            title = "查看最近导入报告",
            summary = "会话 IMP-20260811-093000 · 部分完成",
        ),
    )

    private val warningTitles = listOf(
        "书名缺少作者",
        "进度位置已降级",
        "来源标识无法映射",
        "标签名称已规范化",
        "封面引用未迁移",
        "重复历史记录已合并",
    )

    private val warningDetails = listOf(
        "保留书籍并将作者显示为未知；可在详情页补充。",
        "保留章节与百分比；旧版像素偏移不会成为持久进度。",
        "保留为本地条目，不推断或伪造来源身份。",
        "使用规范化后的本地标签；来源标签仍保持只读分组。",
        "封面缓存不属于导入范围；首次显示时使用标题回退。",
        "相同书籍与时间点只保留一条本地历史记录。",
    )

    private val conflictTitles = listOf(
        "同名收藏夹内容不同",
        "同一本书的进度不一致",
        "本地标签规范化后重名",
        "稍后再读标记不一致",
    )

    private val conflictDetails = listOf(
        "保留现有收藏夹，并将导入内容放入带日期后缀的新收藏夹。",
        "默认保留进度较新的语义位置；选择前不会写入。",
        "冲突标签分别显示连接数；必须明确选择合并或保留两项。",
        "保留本机明确选择；导入值列在报告中供审阅。",
    )

    val transferReport = MoreTransferReportFixture(
        sessionId = "IMP-20260811-093000",
        startedAt = "2026-08-11 09:30 +08:00",
        sourceFormat = "Tsuyomi 数据包 v3",
        supportedFormats = listOf(
            "Tsuyomi 数据包 v3",
            "Hikari Novel 书架清单",
            "阅读进度 JSON",
        ),
        importedBooks = 412,
        importedCollections = 18,
        importedAnnotations = 1296,
        warnings = List(87) { index ->
            MoreTransferIssueFixture(
                code = "W${(index + 1).toString().padStart(3, '0')}",
                title = warningTitles[index % warningTitles.size],
                detail = warningDetails[index % warningDetails.size],
            )
        },
        conflicts = List(23) { index ->
            MoreTransferIssueFixture(
                code = "C${(index + 1).toString().padStart(3, '0')}",
                title = conflictTitles[index % conflictTitles.size],
                detail = conflictDetails[index % conflictDetails.size],
            )
        },
        warningCap = 50,
        recoveryPending = true,
    )

    val featureIntroductions = listOf(
        MoreFeatureIntroductionFixture(
            id = "mirror-setup",
            title = "功能说明：网站镜像",
            version = "说明版本 1",
            summary = "镜像是手动校准的本地只读快照，不是持续同步。",
            points = listOf(
                "启用镜像本身不会联网。",
                "只有明确选择“校准网站镜像”才读取完整网站结构。",
                "校准完成前不会替换最后完整快照，也不会创建本地 pin。",
                "停用、冻结或删除本地快照都不会向网站写入。",
            ),
        ),
        MoreFeatureIntroductionFixture(
            id = "updates",
            title = "功能说明：追更",
            version = "说明版本 1",
            summary = "更新检查默认关闭，视图显示与后台计划相互独立。",
            points = listOf(
                "打开追更不会自动检查或标记已处理。",
                "手动检查与可选计划任务共享可取消的来源队列。",
                "标记已处理只保存本地 exact anchor，不向来源写入。",
                "通知权限被拒绝时，应用内会话状态仍可查看和取消。",
            ),
        ),
        MoreFeatureIntroductionFixture(
            id = "smart-rule-editor",
            title = "功能说明：智能收藏规则",
            version = "说明版本 1",
            summary = "规则只组合受限字段，不执行 SQL、脚本或来源代码。",
            points = listOf(
                "本地标签与指定来源标签必须明确区分。",
                "保存前显示可读摘要与逐项错误。",
                "离开未保存编辑时会明确确认。",
                "规则变化只改变派生结果，不删除标注。",
            ),
        ),
        MoreFeatureIntroductionFixture(
            id = "website-writeback",
            title = "功能说明：网站写入",
            version = "说明版本 1",
            summary = "加入、移除和移动网站收藏是分开的显式远程操作。",
            points = listOf(
                "每次写入都要求签名能力、权限、凭据和最终确认。",
                "移动目标必须来自刚读取的签名列表。",
                "不确定结果不会显示成功，也不会自动重试。",
                "本地移出书架永远不会触发网站操作。",
            ),
        ),
        MoreFeatureIntroductionFixture(
            id = "data-transfer",
            title = "功能说明：数据导入与导出",
            version = "说明版本 2",
            summary = "导入先审阅再确认；导出历史默认关闭。",
            points = listOf(
                "语义进度默认可移植；浏览/搜索历史需单独明确选择。",
                "凭据、镜像、更新运行态、远程回执、缓存和界面偏好永不传输。",
                "导入不会启用调度、镜像或网站写入。",
                "取消选择器或确认不会改变任何数据。",
            ),
        ),
    )

    val about = MoreAboutFixture(
        appName = "Tsuyomi",
        version = "0.0.0-ui-atlas",
        build = "图册构建 20260811 · 固定时钟 2026-08-11 09:30 +08:00",
        licenseName = "Apache License 2.0",
        licenseNotice = "Copyright 2026 Tsuyomi Contributors\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this work except in compliance with the License. You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.",
        fixtureNotice = "此临时图册仅使用固定中文样例、自绘几何图形与程序生成内容；不连接生产模块，不读取文件，不访问网络，也不包含第三方品牌资源。",
    )
}
