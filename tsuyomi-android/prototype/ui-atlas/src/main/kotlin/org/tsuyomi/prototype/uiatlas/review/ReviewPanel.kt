/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import kotlinx.coroutines.delay
import org.tsuyomi.prototype.uiatlas.BuildConfig
import org.tsuyomi.prototype.uiatlas.components.AtlasButton
import org.tsuyomi.prototype.uiatlas.components.AtlasDropdownSelector
import org.tsuyomi.prototype.uiatlas.components.AtlasButtonStyle
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBar
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.runtime.PrototypeRuntime
import org.tsuyomi.prototype.uiatlas.runtime.PrototypeScenario
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewPanel(
    context: AtlasContext,
    runtime: PrototypeRuntime,
    onDismiss: () -> Unit,
    onExport: (Boolean) -> Unit,
    onShare: (Boolean) -> Unit,
) {
    val eInk = context.profile.name == "EINK"
    if (eInk) {
        BackHandler(onBack = onDismiss)
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.fillMaxSize()) {
                AtlasTopBar(title = "审阅", subtitle = ReviewNodeCatalog.resolve(context).id, onUp = onDismiss)
                ReviewPanelContent(context, runtime, onExport, onShare, Modifier.weight(1f))
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            ReviewPanelContent(
                context = context,
                runtime = runtime,
                onExport = onExport,
                onShare = onShare,
                modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp),
            )
        }
    }
}

@Composable
private fun ReviewPanelContent(
    context: AtlasContext,
    runtime: PrototypeRuntime,
    onExport: (Boolean) -> Unit,
    onShare: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val reviewSnapshot by runtime.reviews.snapshot.collectAsStateWithLifecycle()
    val productSnapshot by runtime.repository.snapshot.collectAsStateWithLifecycle()
    val active = reviewSnapshot.builds[BuildConfig.PROTOTYPE_BUILD_ID] ?: ReviewBuildState()
    val resolvedNode = ReviewNodeCatalog.resolve(context)
    var selectedNodeId by rememberSaveable(BuildConfig.PROTOTYPE_BUILD_ID) { mutableStateOf(resolvedNode.id) }
    val selectedNode = ReviewNodeCatalog.byId.getValue(selectedNodeId)
    val selectedRoute = selectedNode.route?.path ?: "global"
    val progress = active.progress[selectedNodeId] ?: ReviewNodeProgress()
    val humanControlsEnabled = active.controlMode == ReviewControlMode.HUMAN
    val actualOnlineScenario = selectedNode.evidenceStage == ReviewEvidenceStage.ACTUAL_ONLINE_SCENARIO
    var pageComment by rememberSaveable(BuildConfig.PROTOTYPE_BUILD_ID) { mutableStateOf("") }
    var authorName by rememberSaveable(BuildConfig.PROTOTYPE_BUILD_ID) { mutableStateOf(ReviewCommentAuthor.AI.name) }
    var visualEvidenceHash by rememberSaveable(BuildConfig.PROTOTYPE_BUILD_ID) { mutableStateOf("") }
    var interactionEvidenceHash by rememberSaveable(BuildConfig.PROTOTYPE_BUILD_ID) { mutableStateOf("") }
    var wholeComment by rememberSaveable(BuildConfig.PROTOTYPE_BUILD_ID) { mutableStateOf(active.wholePrototypeComment) }
    var includeStale by rememberSaveable { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<ReviewConfirmation?>(null) }
    var liveSubmissionError by rememberSaveable { mutableStateOf<String?>(null) }
    val lastLiveSubmission by runtime.reviews.liveBridge.lastSubmission.collectAsStateWithLifecycle()
    val recentEvents = productSnapshot.recentEvents.map { it.exportLine() }
    val latestComment by rememberUpdatedState(pageComment)
    val latestAuthor by rememberUpdatedState(ReviewCommentAuthor.valueOf(authorName))
    val latestEvents by rememberUpdatedState(recentEvents)
    val scenarioKey = scenarioKey(context).takeIf { selectedNode.route == context.route }

    fun persistCurrentNode() {
        runtime.reviews.saveNodeComment(
            nodeId = selectedNode.id,
            route = selectedRoute,
            comment = pageComment,
            author = ReviewCommentAuthor.valueOf(authorName),
            context = reviewContext(context, recentEvents),
        )
        runtime.reviews.attachEvidence(selectedNode.id, visualEvidenceHash, interactionEvidenceHash)
    }

    fun submitLive(kind: ReviewSubmissionKind) {
        persistCurrentNode()
        runtime.reviews.saveWholePrototypeComment(wholeComment)
        runCatching {
            runtime.reviews.liveBridge.submit(
                snapshot = runtime.reviews.snapshot.value,
                nodeId = selectedNode.id,
                route = selectedRoute,
                profile = context.profile.name,
                kind = kind,
            )
        }.onSuccess {
            liveSubmissionError = null
        }.onFailure { error ->
            liveSubmissionError = error.message ?: error::class.simpleName ?: "未知错误"
        }
    }

    fun selectNode(node: ReviewNode) {
        persistCurrentNode()
        selectedNodeId = node.id
    }

    LaunchedEffect(resolvedNode.id) {
        if (selectedNodeId != resolvedNode.id) selectedNodeId = resolvedNode.id
    }
    LaunchedEffect(selectedNodeId) {
        val saved = runtime.reviews.comment(selectedNodeId)
        val savedProgress = runtime.reviews.progress(selectedNodeId)
        pageComment = saved?.comment.orEmpty()
        authorName = (saved?.author ?: if (humanControlsEnabled) ReviewCommentAuthor.HUMAN else ReviewCommentAuthor.AI).name
        visualEvidenceHash = savedProgress.visualEvidenceHash.orEmpty()
        interactionEvidenceHash = savedProgress.interactionEvidenceHash.orEmpty()
        runtime.reviews.markVisited(selectedNodeId)
    }
    LaunchedEffect(selectedNodeId, pageComment, authorName) {
        delay(450)
        runtime.reviews.saveNodeComment(
            nodeId = selectedNodeId,
            route = ReviewNodeCatalog.byId.getValue(selectedNodeId).route?.path ?: "global",
            comment = pageComment,
            author = ReviewCommentAuthor.valueOf(authorName),
            context = reviewContext(context, recentEvents),
        )
    }
    LaunchedEffect(selectedNodeId, visualEvidenceHash, interactionEvidenceHash) {
        delay(450)
        runtime.reviews.attachEvidence(selectedNodeId, visualEvidenceHash, interactionEvidenceHash)
    }
    LaunchedEffect(wholeComment) {
        delay(450)
        runtime.reviews.saveWholePrototypeComment(wholeComment)
    }
    DisposableEffect(selectedNodeId) {
        val nodeAtEffect = selectedNode
        onDispose {
            runtime.reviews.saveNodeComment(
                nodeId = nodeAtEffect.id,
                route = nodeAtEffect.route?.path ?: "global",
                comment = latestComment,
                author = latestAuthor,
                context = reviewContext(context, latestEvents),
            )
        }
    }

    val panelScrollState = rememberScrollState()

    Column(
        modifier
            .verticalScroll(panelScrollState)
            .padding(horizontal = AtlasSpacing.Md)
            .padding(bottom = AtlasSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Md),
    ) {
        Text("节点化 debug 审阅器", style = MaterialTheme.typography.titleLarge)
        Text(
            "评论约 450ms 自动保存；AI 只生成草稿，人工结论仅在人工接管模式可用。数据仅保存在 noBackupFilesDir。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("当前审阅节点", style = MaterialTheme.typography.titleMedium)
        AtlasDropdownSelector(
            value = "${selectedNode.id} · ${selectedNode.title}",
            options = ReviewNodeCatalog.nodes.map { node -> "${node.id} · ${node.title}" },
            onSelect = { index -> selectNode(ReviewNodeCatalog.nodes[index]) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "$selectedRoute · ${context.profile.name.lowercase()} · ${context.theme.name.lowercase()} · ${context.state.name.lowercase()}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            if (actualOnlineScenario) {
                "验证阶段：真实线上生产场景。Atlas 只保留评论和 AI 草稿，不能记录人工完成或最终 verdict。"
            } else {
                "验证阶段：Standard Atlas UI 构建。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (actualOnlineScenario) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        if (selectedNode.requiredStates.isNotEmpty()) {
            Text(
                "必审状态：${selectedNode.requiredStates.sortedBy { it.ordinal }.joinToString { it.extraKey }}",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text("控制权", style = MaterialTheme.typography.titleMedium)
        Text("当前：${controlModeLabel(active.controlMode)}", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
            ReviewControlMode.entries.forEach { mode ->
                FilterChip(
                    selected = active.controlMode == mode,
                    onClick = { runtime.reviews.setControlMode(mode) },
                    label = { Text(controlModeLabel(mode)) },
                )
            }
        }
        ReviewChecklist("操作", selectedNode.operations)
        ReviewChecklist("视觉确认", selectedNode.visualChecks)
        ReviewChecklist("人工专判（AI 不得最终裁决）", selectedNode.humanOnlyChecks)

        HorizontalDivider()
        Text("节点意见", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
            ReviewCommentAuthor.entries.forEach { author ->
                FilterChip(
                    selected = authorName == author.name,
                    onClick = { authorName = author.name },
                    label = { Text(authorLabel(author)) },
                    enabled = humanControlsEnabled || author == ReviewCommentAuthor.AI,
                )
            }
        }
        OutlinedTextField(
            value = pageComment,
            onValueChange = { pageComment = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("AI 草稿或人工修订") },
            minLines = 5,
        )
        Text("已自动保存到当前 build", style = MaterialTheme.typography.bodySmall)

        Text("证据哈希（可由 harness 写入）", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = visualEvidenceHash,
            onValueChange = { visualEvidenceHash = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("visual evidence SHA-256") },
            singleLine = true,
        )
        OutlinedTextField(
            value = interactionEvidenceHash,
            onValueChange = { interactionEvidenceHash = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("interaction evidence SHA-256") },
            singleLine = true,
        )

        Text("节点进度", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
        ) {
            AtlasButton(
                "AI 草稿完成",
                { runtime.reviews.markAiTriaged(selectedNode.id) },
                modifier = Modifier.weight(1f),
                style = AtlasButtonStyle.SECONDARY,
            )
            AtlasButton(
                "人工已操作",
                { runtime.reviews.markHumanReviewed(selectedNode.id) },
                modifier = Modifier.weight(1f),
                style = AtlasButtonStyle.SECONDARY,
                enabled = humanControlsEnabled && !actualOnlineScenario,
            )
        }
        Text(
            "访问 ${yesNo(progress.visitedAt)} · AI ${yesNo(progress.aiTriagedAt)} · 人工 ${yesNo(progress.humanReviewedAt)} · 批准 ${yesNo(progress.approvedAt)}",
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
            ReviewVerdict.entries.forEach { verdict ->
                FilterChip(
                    selected = progress.verdict == verdict,
                    onClick = { runtime.reviews.setVerdict(selectedNode.id, verdict) },
                    label = { Text(verdictLabel(verdict)) },
                    enabled = humanControlsEnabled && !actualOnlineScenario,
                )
            }
        }

        scenarioKey?.let { actionKey ->
            val selectedScenario = runtime.scenarios.selected(actionKey)
            Text("可重复模拟", style = MaterialTheme.typography.titleMedium)
            Text(
                if (actualOnlineScenario) {
                    "这里只预演「${scenarioActionLabel(actionKey)}」的本地 fixture；真实线上服务验收必须在生产包中执行。"
                } else {
                    "为下一次「${scenarioActionLabel(actionKey)}」预选本地结果；不会访问网络。"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                PrototypeScenario.entries.forEach { scenario ->
                    FilterChip(
                        selected = selectedScenario == scenario,
                        onClick = { runtime.scenarios.select(actionKey, scenario) },
                        label = { Text(scenario.label) },
                    )
                }
            }
            Text("当前：${selectedScenario.explanation}", style = MaterialTheme.typography.bodySmall)
        }

        Text("整个原型", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = wholeComment,
            onValueChange = { wholeComment = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("整体审阅意见（自动保存）") },
            minLines = 3,
        )

        val visited = active.progress.count { it.value.visitedAt != null }
        val aiTriaged = active.progress.count { it.value.aiTriagedAt != null }
        val humanReviewed = active.progress.count { it.value.humanReviewedAt != null }
        val approved = active.progress.count { it.value.approvedAt != null }
        Text("覆盖", style = MaterialTheme.typography.titleMedium)
        Text(
            "已访问 $visited / ${ReviewNodeCatalog.nodes.size} · AI 草稿 $aiTriaged · 人工 $humanReviewed · 批准 $approved",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "当前构建阶段 18 个 L/B/M 节点 · 后续真实线上场景 10 个 S/X 节点",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("当前 build：${BuildConfig.PROTOTYPE_BUILD_ID.take(16)}", style = MaterialTheme.typography.bodySmall)
        if (runtime.reviews.staleBuildIds().isNotEmpty()) {
            Text("旧 build（只读）", style = MaterialTheme.typography.titleSmall)
            runtime.reviews.staleBuildIds().forEach { buildId ->
                val stale = reviewSnapshot.builds[buildId] ?: ReviewBuildState()
                ListItem(
                    headlineContent = { Text(buildId.take(20)) },
                    supportingContent = { Text("${stale.nodeComments.size} 条节点意见") },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("导出时包含旧 build", modifier = Modifier.weight(1f))
                Switch(includeStale, { includeStale = it })
            }
        }

        Text("ADB 直通审阅", style = MaterialTheme.typography.titleMedium)
        Text(
            "提交会原子保存当前 build 的审阅 JSON；logcat 只发送 revision 与哈希，不包含评论正文。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        lastLiveSubmission?.let { submission ->
            Text(
                "已本地提交 revision ${submission.revision} · ${submission.nodeId} · build ${submission.buildId.take(12)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        liveSubmissionError?.let { error ->
            Text(
                "提交失败：$error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        AtlasButton(
            "提交当前意见给 OMP",
            { submitLive(ReviewSubmissionKind.NODE) },
            modifier = Modifier.fillMaxWidth(),
        )
        AtlasButton(
            "提交本批并允许更新",
            { submitLive(ReviewSubmissionKind.BATCH_READY) },
            modifier = Modifier.fillMaxWidth(),
            style = AtlasButtonStyle.SECONDARY,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
        ) {
            AtlasButton("导出 JSON", { persistCurrentNode(); onExport(includeStale) }, modifier = Modifier.weight(1f))
            AtlasButton(
                "分享 JSON",
                { persistCurrentNode(); onShare(includeStale) },
                modifier = Modifier.weight(1f),
                style = AtlasButtonStyle.SECONDARY,
            )
        }
        OutlinedButton(
            onClick = { confirmation = ReviewConfirmation.RESET_FAKE_DATA },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("重置假数据") }
        OutlinedButton(
            onClick = { confirmation = ReviewConfirmation.CLEAR_COMMENTS },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("清空审阅意见") }
        Spacer(Modifier.padding(bottom = AtlasSpacing.Sm))
    }

    confirmation?.let { requested ->
        val resetData = requested == ReviewConfirmation.RESET_FAKE_DATA
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (resetData) "重置假数据？" else "清空当前 build 的审阅意见？") },
            text = {
                Text(
                    if (resetData) "产品假数据会恢复种子值；节点意见、进度与整体意见保留。"
                    else "当前 build 的节点意见与整体意见会删除；假产品数据、进度和 verdict 保留。",
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (resetData) {
                        runtime.repository.resetFakeData()
                    } else {
                        runtime.reviews.clearActiveComments()
                        pageComment = ""
                        wholeComment = ""
                    }
                    confirmation = null
                }) { Text(if (resetData) "重置假数据" else "清空意见") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmation = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ReviewChecklist(title: String, values: List<String>) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    values.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
}

private fun reviewContext(context: AtlasContext, recentEvents: List<String>): ReviewContext = ReviewContext(
    profile = context.profile.name.lowercase(),
    theme = context.theme.name.lowercase(),
    state = context.state.name.lowercase(),
    overlay = when {
        context.showModal -> "modal"
        context.showUnresolvedBanner -> "unresolved"
        context.showMutationBanner -> "mutation"
        else -> null
    },
    layout = context.layout?.name?.lowercase(),
    libraryView = context.libraryView.name.lowercase(),
    lastAction = recentEvents.lastOrNull(),
    recentEvents = recentEvents.takeLast(32),
    updatedAt = Instant.now().toString(),
)

private fun scenarioKey(context: AtlasContext): String? = when (context.route.path) {
    "library/updates" -> "updates-check"
    "library/mirror/{bindingId}" -> "mirror-calibration"
    "book/{sourceId}/{remoteBookId}" -> "detail-refresh"
    "book/{sourceId}/{remoteBookId}/reader/{chapterId}" -> "reader-load"
    "browse" -> "source-refresh"
    "search" -> "search"
    "browse/source/{sourceId}/remote-library" -> "remote-library-refresh"
    "source/verification" -> "source-verification"
    else -> null
}

private fun scenarioActionLabel(actionKey: String): String = when (actionKey) {
    "updates-check" -> "检查追更"
    "mirror-calibration" -> "校准镜像"
    "detail-refresh" -> "刷新来源数据"
    "reader-load" -> "加载章节"
    "source-refresh" -> "刷新来源"
    "search" -> "提交搜索"
    "remote-library-refresh" -> "刷新网站收藏"
    "source-verification" -> "完成来源验证"
    else -> actionKey
}

private fun controlModeLabel(mode: ReviewControlMode): String = when (mode) {
    ReviewControlMode.AUTOMATION -> "自动操作"
    ReviewControlMode.PAUSED -> "暂停"
    ReviewControlMode.HUMAN -> "人工接管"
}

private fun authorLabel(author: ReviewCommentAuthor): String = when (author) {
    ReviewCommentAuthor.AI -> "AI 草稿"
    ReviewCommentAuthor.HUMAN -> "人工"
    ReviewCommentAuthor.MIXED -> "人工修订 AI"
}

private fun verdictLabel(verdict: ReviewVerdict): String = when (verdict) {
    ReviewVerdict.PENDING -> "待定"
    ReviewVerdict.ACCEPT -> "接受"
    ReviewVerdict.REVISE -> "需修改"
    ReviewVerdict.BLOCKED -> "阻断"
    ReviewVerdict.NOT_APPLICABLE -> "不适用"
}

private fun yesNo(value: String?): String = if (value == null) "否" else "是"

private enum class ReviewConfirmation { RESET_FAKE_DATA, CLEAR_COMMENTS }
