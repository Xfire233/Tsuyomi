/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.security.SourceCredentialStore
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.webview.ControlledWebLoginSession
import org.tsuyomi.core.webview.CapturedVerifiedPage
import org.tsuyomi.shared.sourcecontract.SourceDiagnostic
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

private enum class VerifiedPageFailure {
    NONE,
    UNBOUND,
    REJECTED,
}

private enum class VerificationRouteFailure {
    NOT_AUTHORIZED,
    START_FAILED,
}

@Composable
fun ManualVerificationTopBar(
    packageInfo: VerifiedHxpPackage,
    onNavigateUp: () -> Unit,
) {
    TsuyomiTopBar(
        title = stringResource(R.string.verification_atlas_title),
        subtitle = stringResource(
            R.string.verification_atlas_subtitle,
            packageInfo.manifest.displayName,
            packageInfo.manifest.sourceId.value,
        ),
        onNavigateUp = onNavigateUp,
    )
}

@Composable
fun ManualVerificationRoute(
    packageInfo: VerifiedHxpPackage,
    onCompleted: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    verifiedPageRequestUrl: String? = null,
    onVerifiedPageCompleted: () -> Unit = onCompleted,
    onUseVerifiedPage: (suspend (CapturedVerifiedPage) -> VerifiedPageUseResult)? = null,
    verifiedPageOpenLabel: String? = null,
    verifiedPageUnboundMessage: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webView by remember(packageInfo.packageSha256) { mutableStateOf<WebView?>(null) }
    var failure by remember(packageInfo.packageSha256) { mutableStateOf<VerificationRouteFailure?>(null) }
    var blockedNavigation by remember(packageInfo.packageSha256) { mutableStateOf(false) }
    var snapshotWorking by remember(packageInfo.packageSha256) { mutableStateOf(false) }
    var snapshotFailure by remember(packageInfo.packageSha256) { mutableStateOf(VerifiedPageFailure.NONE) }
    var snapshotDiagnostic by remember(packageInfo.packageSha256) { mutableStateOf<SourceDiagnostic?>(null) }
    val origins = packageInfo.manifest.capabilities.webLogin.origins
    val session = remember(packageInfo.packageSha256) {
        ControlledWebLoginSession(
            context = context,
            sourceId = packageInfo.manifest.sourceId.value,
            allowedOrigins = origins,
            credentials = SourceCredentialStore(context),
            onBlockedNavigation = { blockedNavigation = true },
        )
    }
    val initialUrl = packageInfo.manifest.homepage
        ?.takeIf { homepage -> origins.any { homepage.startsWith(it.canonical) } }
        ?: origins.firstOrNull()?.canonical
    val effectiveVerifiedPageOpenLabel = verifiedPageOpenLabel
        ?: stringResource(R.string.verification_open_requested_page)
    val effectiveVerifiedPageUnboundMessage = verifiedPageUnboundMessage
        ?: stringResource(R.string.verification_snapshot_unbound)

    LaunchedEffect(session, initialUrl) {
        if (!packageInfo.manifest.capabilities.webLogin.enabled || initialUrl == null) {
            failure = VerificationRouteFailure.NOT_AUTHORIZED
        } else {
            runCatching { session.open(initialUrl) }
                .onSuccess { webView = it }
                .onFailure { failure = VerificationRouteFailure.START_FAILED }
        }
    }
    DisposableEffect(session) {
        onDispose(session::dispose)
    }

    when {
        failure != null -> StateView(
            kind = TsuyomiStateKind.ERROR,
            title = stringResource(
                when (requireNotNull(failure)) {
                    VerificationRouteFailure.NOT_AUTHORIZED -> R.string.verification_unavailable
                    VerificationRouteFailure.START_FAILED -> R.string.verification_start_failed
                },
            ),
            actionLabel = stringResource(R.string.verification_back),
            onAction = onCancel,
            modifier = modifier,
        )
        webView == null -> StateView(
            kind = TsuyomiStateKind.LOADING,
            title = stringResource(R.string.verification_loading),
            modifier = modifier,
        )
        LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK -> FrozenEInkVerificationContent(
            webView = requireNotNull(webView),
            blockedNavigation = blockedNavigation,
            onCancel = { scope.launch { session.cancel(); onCancel() } },
            onComplete = { scope.launch { session.finish(); onCompleted() } },
            modifier = modifier,
        )
        else -> StandardAtlasVerificationContent(
            sourceName = packageInfo.manifest.displayName,
            webView = requireNotNull(webView),
            blockedNavigation = blockedNavigation,
            snapshotActionAvailable = onUseVerifiedPage != null,
            snapshotWorking = snapshotWorking,
            snapshotFailure = snapshotFailure,
            snapshotDiagnostic = snapshotDiagnostic,
            verifiedPageOpenLabel = effectiveVerifiedPageOpenLabel,
            verifiedPageUnboundMessage = effectiveVerifiedPageUnboundMessage,
            onOpenRequestedPage = verifiedPageRequestUrl?.let { requestUrl ->
                {
                    snapshotFailure = VerifiedPageFailure.NONE
                    snapshotDiagnostic = null
                    runCatching { session.openVerifiedPage(requestUrl) }
                        .onFailure { snapshotFailure = VerifiedPageFailure.UNBOUND }
                }
            },
            onUseCurrentPage = onUseVerifiedPage?.let { useVerifiedPage ->
                {
                    scope.launch {
                        snapshotWorking = true
                        snapshotFailure = VerifiedPageFailure.NONE
                        snapshotDiagnostic = null
                        val accepted = runCatching {
                            val snapshot = session.captureCurrentPage(
                                packageInfo.manifest.capabilities.network.maxResponseBytes
                                    .coerceAtMost(MAX_VERIFIED_PAGE_BYTES),
                            )
                            if (snapshot.requestUrl != verifiedPageRequestUrl) {
                                snapshotFailure = VerifiedPageFailure.UNBOUND
                                false
                            } else {
                                val result = useVerifiedPage(snapshot)
                                snapshotDiagnostic = result.diagnostic
                                if (!result.accepted) snapshotFailure = VerifiedPageFailure.REJECTED
                                result.accepted
                            }
                        }.getOrElse {
                            snapshotFailure = VerifiedPageFailure.UNBOUND
                            false
                        }
                        if (accepted) {
                            session.finish()
                            onVerifiedPageCompleted()
                        } else if (snapshotFailure == VerifiedPageFailure.NONE) {
                            snapshotFailure = VerifiedPageFailure.REJECTED
                        }
                        snapshotWorking = false
                    }
                }
            },
            onCancel = { scope.launch { session.cancel(); onCancel() } },
            onComplete = { scope.launch { session.finish(); onCompleted() } },
            modifier = modifier,
        )
    }
}

@Composable
private fun StandardAtlasVerificationContent(
    sourceName: String,
    webView: WebView,
    blockedNavigation: Boolean,
    snapshotActionAvailable: Boolean,
    snapshotWorking: Boolean,
    snapshotFailure: VerifiedPageFailure,
    snapshotDiagnostic: SourceDiagnostic?,
    verifiedPageOpenLabel: String,
    verifiedPageUnboundMessage: String,
    onOpenRequestedPage: (() -> Unit)?,
    onUseCurrentPage: (() -> Unit)?,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(
                start = TsuyomiSpacing.Md,
                end = TsuyomiSpacing.Md,
                top = TsuyomiSpacing.Md,
            ),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(TsuyomiSpacing.Md),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = TsuyomiIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Column(Modifier.padding(start = TsuyomiSpacing.Md)) {
                    Text(
                        stringResource(R.string.verification_host_notice, sourceName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.verification_status_waiting),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        if (blockedNavigation) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = TsuyomiSpacing.Md,
                    vertical = TsuyomiSpacing.Sm,
                ),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = stringResource(R.string.verification_navigation_blocked),
                    modifier = Modifier.padding(TsuyomiSpacing.Md),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (snapshotFailure != VerifiedPageFailure.NONE) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = TsuyomiSpacing.Md,
                    vertical = TsuyomiSpacing.Sm,
                ),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Column {
                    Text(
                        text = when (snapshotFailure) {
                            VerifiedPageFailure.UNBOUND -> verifiedPageUnboundMessage
                            VerifiedPageFailure.REJECTED -> stringResource(R.string.verification_snapshot_rejected)
                            VerifiedPageFailure.NONE -> error("Snapshot failure is absent")
                        },
                        modifier = Modifier.padding(TsuyomiSpacing.Md),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (snapshotDiagnostic != null) {
                        Text(
                            text = stringResource(
                                R.string.verification_snapshot_diagnostic,
                                snapshotDiagnostic.stage,
                                snapshotDiagnostic.safeCode,
                            ),
                            modifier = Modifier.padding(
                                start = TsuyomiSpacing.Md,
                                end = TsuyomiSpacing.Md,
                                bottom = TsuyomiSpacing.Md,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TsuyomiIcons.Verify,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                stringResource(R.string.verification_webview_caption),
                modifier = Modifier.padding(start = TsuyomiSpacing.Sm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onOpenRequestedPage != null) {
            TsuyomiButton(
                text = verifiedPageOpenLabel,
                onClick = onOpenRequestedPage,
                enabled = !snapshotWorking,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = TsuyomiSpacing.Md, end = TsuyomiSpacing.Md, top = TsuyomiSpacing.Lg),
                style = TsuyomiButtonStyle.SECONDARY,
            )
        }
        if (onUseCurrentPage != null) {
            TsuyomiButton(
                text = stringResource(R.string.verification_use_current_page),
                onClick = onUseCurrentPage,
                enabled = !snapshotWorking,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TsuyomiSpacing.Md,
                        end = TsuyomiSpacing.Md,
                        top = if (onOpenRequestedPage == null) TsuyomiSpacing.Lg else TsuyomiSpacing.Sm,
                    ),
            )
        }
        TsuyomiButton(
            text = stringResource(R.string.verification_complete_action),
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = TsuyomiSpacing.Md, end = TsuyomiSpacing.Md, top = TsuyomiSpacing.Lg),
            style = if (snapshotActionAvailable) TsuyomiButtonStyle.SECONDARY else TsuyomiButtonStyle.PRIMARY,
        )
        TsuyomiButton(
            text = stringResource(R.string.verification_cancel_action),
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
            style = TsuyomiButtonStyle.SECONDARY,
        )
    }
}

@Composable
private fun FrozenEInkVerificationContent(
    webView: WebView,
    blockedNavigation: Boolean,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        Text(
            text = stringResource(R.string.verification_notice),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        if (blockedNavigation) {
            Text(
                text = stringResource(R.string.verification_navigation_blocked),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        AndroidView(
            factory = { webView },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TsuyomiButton(
                text = stringResource(R.string.verification_cancel),
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                style = TsuyomiButtonStyle.SECONDARY,
            )
            TsuyomiButton(
                text = stringResource(R.string.verification_done),
                onClick = onComplete,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private const val MAX_VERIFIED_PAGE_BYTES = 2 * 1024 * 1024
