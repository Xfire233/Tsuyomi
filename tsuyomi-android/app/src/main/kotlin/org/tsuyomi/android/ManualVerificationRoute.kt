/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.security.SourceCredentialStore
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiVerificationToolbar
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.core.ui.theme.rememberSystemReducedMotion
import org.tsuyomi.core.webview.CapturedVerifiedPage
import org.tsuyomi.core.webview.ControlledWebLoginSession
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
fun ManualVerificationRoute(
    packageInfo: VerifiedHxpPackage,
    onCompleted: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    verifiedPageRequestUrl: String? = null,
    verifiedPageRequestResolved: Boolean = true,
    onVerifiedPageCompleted: suspend () -> Unit = { onCompleted() },
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
    val homepageUrl = packageInfo.manifest.homepage
        ?.takeIf { homepage -> origins.any { homepage.startsWith(it.canonical) } }
        ?: origins.firstOrNull()?.canonical
    val requestUrl = verifiedPageRequestUrl?.takeIf { url ->
        origins.any { url.startsWith(it.canonical) }
    }
    val initialUrl = requestUrl ?: homepageUrl
    val effectiveVerifiedPageOpenLabel = verifiedPageOpenLabel
        ?: stringResource(R.string.verification_open_requested_page)
    val effectiveVerifiedPageUnboundMessage = verifiedPageUnboundMessage
        ?: stringResource(R.string.verification_snapshot_unbound)

    LaunchedEffect(session, initialUrl, requestUrl, verifiedPageRequestResolved) {
        if (!verifiedPageRequestResolved) return@LaunchedEffect
        if (!packageInfo.manifest.capabilities.webLogin.enabled || initialUrl == null) {
            failure = VerificationRouteFailure.NOT_AUTHORIZED
        } else {
            try {
                webView = session.open(initialUrl, bindAsVerifiedPage = requestUrl != null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                failure = VerificationRouteFailure.START_FAILED
            }
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
        else -> VerificationContent(
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
private fun VerificationContent(
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
    val staticMotion = LocalDisplayEnvironment.current.instantMotion || rememberSystemReducedMotion()
    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onCancel()
    }

    Box(modifier.fillMaxSize()) {
        TsuyomiVerificationToolbar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm)
                .widthIn(max = VerificationToolbarMaxWidth)
                .testTag("verification-action-dock")
                .zIndex(2f),
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = TsuyomiIcons.Close,
                    contentDescription = stringResource(R.string.verification_cancel_action),
                )
            }
            if (onOpenRequestedPage != null) {
                IconButton(onClick = onOpenRequestedPage, enabled = !snapshotWorking) {
                    Icon(
                        imageVector = TsuyomiIcons.Refresh,
                        contentDescription = verifiedPageOpenLabel,
                    )
                }
            }
            if (onUseCurrentPage != null) {
                Button(
                    onClick = onUseCurrentPage,
                    enabled = !snapshotWorking,
                    modifier = Modifier.testTag("verification-use-current-page"),
                    contentPadding = VerificationButtonPadding,
                ) {
                    if (snapshotWorking && staticMotion) {
                        Text(
                            text = stringResource(R.string.verification_snapshot_working),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    } else if (snapshotWorking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.verification_use_current_page))
                    }
                }
            }
            if (snapshotActionAvailable) {
                FilledTonalButton(
                    onClick = onComplete,
                    enabled = !snapshotWorking,
                    contentPadding = VerificationButtonPadding,
                ) {
                    Text(stringResource(R.string.verification_complete_action))
                }
            } else {
                Button(
                    onClick = onComplete,
                    enabled = !snapshotWorking,
                    contentPadding = VerificationButtonPadding,
                ) {
                    Text(stringResource(R.string.verification_complete_action))
                }
            }
        }

        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxSize()
                .testTag("verification-webview"),
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(top = TsuyomiSpacing.Sm, end = TsuyomiSpacing.Md)
                .testTag("verification-host-identity")
                .zIndex(2f),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = TsuyomiIcons.Verify,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.verification_host_identity, sourceName),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        if (blockedNavigation || snapshotFailure != VerifiedPageFailure.NONE) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(
                        start = TsuyomiSpacing.Md,
                        end = TsuyomiSpacing.Md,
                        top = VerificationFeedbackTopOffset,
                    )
                    .widthIn(max = VerificationFeedbackMaxWidth)
                    .testTag("verification-feedback")
                    .zIndex(2f),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 2.dp,
            ) {
                Column(Modifier.padding(TsuyomiSpacing.Md)) {
                    if (blockedNavigation) {
                        Text(
                            text = stringResource(R.string.verification_navigation_blocked),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (snapshotFailure != VerifiedPageFailure.NONE) {
                        Text(
                            text = when (snapshotFailure) {
                                VerifiedPageFailure.UNBOUND -> verifiedPageUnboundMessage
                                VerifiedPageFailure.REJECTED -> stringResource(R.string.verification_snapshot_rejected)
                                VerifiedPageFailure.NONE -> error("Snapshot failure is absent")
                            },
                            modifier = Modifier.padding(top = if (blockedNavigation) TsuyomiSpacing.Sm else 0.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (snapshotDiagnostic != null) {
                            Text(
                                text = stringResource(
                                    R.string.verification_snapshot_diagnostic,
                                    snapshotDiagnostic.stage,
                                    snapshotDiagnostic.safeCode,
                                ),
                                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
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

private val VerificationButtonPadding = PaddingValues(horizontal = 12.dp)
private val VerificationToolbarMaxWidth = 560.dp
private val VerificationFeedbackMaxWidth = 560.dp
private val VerificationFeedbackTopOffset = 56.dp

private const val MAX_VERIFIED_PAGE_BYTES = 2 * 1024 * 1024
