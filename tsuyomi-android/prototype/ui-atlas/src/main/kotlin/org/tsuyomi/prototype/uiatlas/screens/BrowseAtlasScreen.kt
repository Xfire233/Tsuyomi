/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.components.AtlasButton
import org.tsuyomi.prototype.uiatlas.components.AtlasButtonStyle
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationPhase
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationStatus
import org.tsuyomi.prototype.uiatlas.components.AtlasOverflowItem
import org.tsuyomi.prototype.uiatlas.components.AtlasScaffold
import org.tsuyomi.prototype.uiatlas.components.AtlasSourceMarkCanvas
import org.tsuyomi.prototype.uiatlas.components.AtlasStateKind
import org.tsuyomi.prototype.uiatlas.components.AtlasStateView
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBar
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBarAction
import org.tsuyomi.prototype.uiatlas.fixtures.AtlasFixtures
import org.tsuyomi.prototype.uiatlas.fixtures.SourceAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasFamily
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.model.AtlasSource
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasNavigation
import org.tsuyomi.prototype.uiatlas.runtime.LocalPrototypeRuntime
import org.tsuyomi.prototype.uiatlas.runtime.prototypeRepository
import org.tsuyomi.prototype.uiatlas.theme.AtlasEInkPalette
import org.tsuyomi.prototype.uiatlas.theme.AtlasMotion
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment
// -- #11 Browse root ------------------------------------------------------------------------

@Composable
internal fun BrowseRoot(context: AtlasContext, modifier: Modifier) {
    val navigation = LocalAtlasNavigation.current
    val runtime = LocalPrototypeRuntime.current
    val scope = rememberCoroutineScope()
    val variant = context.variant
    val useFab = variant != null && variant.id.uppercaseChar() == 'A' && variant.option == "b"
    val importSource: () -> Unit = { scope.launch { runtime.scenarios.run("source-import", "browse") } }
    val refreshSources: () -> Unit = { scope.launch { runtime.scenarios.run("source-refresh", "browse") } }
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            AtlasTopBar(
                title = "浏览",
                subtitle = "已安装 3 · 可安装 1",
                actions = buildList {
                    add(AtlasTopBarAction(AtlasIcons.Search, "聚合搜索") { navigation.navigateSearch(null) })
                    if (!useFab) add(AtlasTopBarAction(AtlasIcons.Add, "导入源", importSource))
                },
                overflow = listOf(AtlasOverflowItem("刷新源列表", refreshSources)),
            )
        },
        floatingAction = if (useFab) {
            {
                FloatingActionButton(onClick = importSource) {
                    Icon(AtlasIcons.Add, contentDescription = "导入源")
                }
            }
        } else null,
    ) {
        Column(Modifier.fillMaxSize()) {
            if (context.showMutationBanner) {
                AtlasMutationBanner(
                    if (context.libraryView == AtlasLibraryView.ALL) {
                        AtlasMutationStatus(
                            AtlasMutationPhase.WORKING,
                            "正在安装 源·苇 v0.3（第 2/3 步：校验签名）",
                        )
                    } else {
                        AtlasMutationStatus(
                            AtlasMutationPhase.ERROR,
                            "安装失败：签名校验未通过，未写入任何数据。",
                            AtlasStrings.RETRY,
                            importSource,
                        )
                    },
                )
            }
            when (context.primaryState) {
                AtlasPageState.LOADING -> AtlasStateView(AtlasStateKind.LOADING, AtlasStrings.LOADING, Modifier.weight(1f))
                AtlasPageState.EMPTY -> AtlasStateView(
                    AtlasStateKind.EMPTY,
                    "还没有安装内容源",
                    Modifier.weight(1f),
                    "导入源扩展包后，即可从统一搜索中预选此来源，并浏览网站收藏。",
                    "导入源扩展包",
                    importSource,
                )
                AtlasPageState.ERROR -> AtlasStateView(
                    AtlasStateKind.ERROR,
                    "源列表加载失败",
                    Modifier.weight(1f),
                    "本地扩展注册表读取异常；已安装源数据未受影响。",
                    AtlasStrings.RETRY,
                    refreshSources,
                )
                else -> BrowseContent(
                    context,
                    navigation,
                    { scope.launch { runtime.scenarios.run("source-install", SourceAtlasFixtures.installableSource.name) } },
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BrowseContent(
    context: AtlasContext,
    navigation: org.tsuyomi.prototype.uiatlas.model.AtlasNavigationActions,
    onInstall: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Section("已安装")
        AtlasFixtures.installedSources.forEach { SourceCard(it, navigation) }
        Section("可安装")
        InstallableCard(context, onInstall)
        Spacer(Modifier.height(AtlasSpacing.Lg))
    }
}

@Composable
private fun SourceCard(source: AtlasSource, navigation: org.tsuyomi.prototype.uiatlas.model.AtlasNavigationActions) {
    val eInk = LocalAtlasEnvironment.current.eInk
    val repository = prototypeRepository()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Xs),
        border = if (eInk) BorderStroke(1.5.dp, AtlasEInkPalette.N90) else null,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(AtlasSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AtlasSourceMarkCanvas(
                    source.mark,
                    if (eInk) AtlasEInkPalette.N70 else MaterialTheme.colorScheme.primary,
                    Modifier.size(40.dp),
                )
                Column(Modifier.padding(start = AtlasSpacing.Md)) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        source.capabilityLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val usable = !source.dormant && !source.credentialExpired
            if (!usable) {
                Text(
                    if (source.dormant) "休眠：远程功能暂停，本地缓存仍可浏览。" else "凭据过期：重新登录后恢复。",
                    modifier = Modifier.padding(top = AtlasSpacing.Sm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                AtlasButton("搜索此来源", { navigation.navigateSearch(source.id) }, style = AtlasButtonStyle.TEXT, enabled = usable)
                AtlasButton("网站收藏", { navigation.navigate(AtlasRoute.BROWSE_SOURCE_REMOTE_LIBRARY) }, style = AtlasButtonStyle.TEXT, enabled = usable)
                if (source.credentialExpired) {
                    AtlasButton("重新登录", {
                        repository.record("SourceReloginOpened", source.id, "success")
                        navigation.navigate(AtlasRoute.SOURCE_VERIFICATION)
                    }, style = AtlasButtonStyle.SECONDARY)
                }
            }
        }
    }
}

@Composable
private fun InstallableCard(context: AtlasContext, onInstall: () -> Unit) {
    val environment = LocalAtlasEnvironment.current
    val pkg = SourceAtlasFixtures.installableSource
    val variant = context.variant
    val instant = variant != null && variant.id.uppercaseChar() == 'F' && variant.option == "b"
    var expanded by rememberSaveable(variant?.toString()) {
        mutableStateOf(variant?.id?.uppercaseChar() == 'F')
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AtlasSpacing.Md),
        border = if (environment.eInk) BorderStroke(1.5.dp, AtlasEInkPalette.N90) else null,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(AtlasSpacing.Md)) {
            Text("${pkg.name} ${pkg.version}", style = MaterialTheme.typography.titleMedium)
            Text(pkg.summary, style = MaterialTheme.typography.bodySmall)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .heightIn(min = 48.dp)
                    .semantics { stateDescription = if (expanded) "已展开" else "已收起" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (expanded) AtlasIcons.Collapse else AtlasIcons.Expand, contentDescription = null)
                Text(
                    if (expanded) "收起能力差异" else "展开能力差异（新增 2 项 · 移除 1 项）",
                    modifier = Modifier.padding(start = AtlasSpacing.Sm),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            val diff: @Composable () -> Unit = {
                Column {
                    pkg.diffAdded.forEach { KeyValue("新增", it) }
                    pkg.diffRemoved.forEach { KeyValue("移除", it) }
                }
            }
            if (instant || environment.instantMotion) {
                if (expanded) diff()
            } else {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(tween(AtlasMotion.EXPAND_MS)) + fadeIn(tween(AtlasMotion.FADE_IN_MS)),
                ) { diff() }
            }
            AtlasButton("安装", onInstall, style = AtlasButtonStyle.SECONDARY)
        }
    }
}
