/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.webkit.WebView
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.security.SourceCredentialStore
import org.tsuyomi.core.ui.components.PAGINATION_JUMP_THRESHOLD
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiVerificationToolbar
import org.tsuyomi.core.ui.components.TsuyomiVerificationAction
import org.tsuyomi.core.ui.components.TsuyomiTonalIconButton
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
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
                    scope.launch(Dispatchers.Main.immediate) {
                        snapshotWorking = true
                        snapshotFailure = VerifiedPageFailure.NONE
                        snapshotDiagnostic = null
                        try {
                            val accepted = try {
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
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Throwable) {
                                snapshotFailure = VerifiedPageFailure.UNBOUND
                                false
                            }
                            if (accepted) {
                                session.finish()
                                onVerifiedPageCompleted()
                            } else if (snapshotFailure == VerifiedPageFailure.NONE) {
                                snapshotFailure = VerifiedPageFailure.REJECTED
                            }
                        } finally {
                            snapshotWorking = false
                        }
                    }
                }
            },
            onCancel = { scope.launch(Dispatchers.Main.immediate) { session.cancel(); onCancel() } },
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
    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onCancel()
    }

    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    var canGoPrevious by remember(webView) { mutableStateOf(false) }
    var canGoNext by remember(webView) { mutableStateOf(false) }
    var showPageEndpoints by remember(webView) { mutableStateOf(false) }
    DisposableEffect(webView, eInk) {
        val observer = webView.viewTreeObserver
        val listener = ViewTreeObserver.OnPreDrawListener {
            if (eInk) {
                canGoPrevious = webView.canScrollVertically(-1)
                canGoNext = webView.canScrollVertically(1)
                // WebView owns native page geometry; this does not inspect or rewrite the DOM.
                @Suppress("DEPRECATION")
                val renderedHeight = webView.contentHeight * webView.scale
                val viewportHeight = webView.height - webView.paddingTop - webView.paddingBottom
                showPageEndpoints = viewportHeight > 0 &&
                    renderedHeight > viewportHeight.toFloat() * PAGINATION_JUMP_THRESHOLD
            }
            true
        }
        if (eInk) observer.addOnPreDrawListener(listener)
        onDispose { if (observer.isAlive) observer.removeOnPreDrawListener(listener) }
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
            TsuyomiTonalIconButton(
                imageVector = TsuyomiIcons.Close,
                contentDescription = stringResource(if (eInk) R.string.verification_cancel else R.string.verification_cancel_action),
                onClick = onCancel,
            )
            if (eInk && (canGoPrevious || canGoNext)) {
                if (showPageEndpoints) {
                    TsuyomiTonalIconButton(
                        imageVector = TsuyomiIcons.ToTop,
                        contentDescription = stringResource(org.tsuyomi.core.ui.R.string.coreui_pagination_first),
                        onClick = { webView.pageUp(true) },
                        enabled = canGoPrevious,
                    )
                }
                TsuyomiTonalIconButton(
                    imageVector = TsuyomiIcons.ToTop,
                    contentDescription = stringResource(org.tsuyomi.core.ui.R.string.coreui_pagination_previous),
                    onClick = { webView.pageUp(false) },
                    enabled = canGoPrevious,
                )
                TsuyomiTonalIconButton(
                    imageVector = TsuyomiIcons.ToBottom,
                    contentDescription = stringResource(org.tsuyomi.core.ui.R.string.coreui_pagination_next),
                    onClick = { webView.pageDown(false) },
                    enabled = canGoNext,
                )
                if (showPageEndpoints) {
                    TsuyomiTonalIconButton(
                        imageVector = TsuyomiIcons.ToBottom,
                        contentDescription = stringResource(org.tsuyomi.core.ui.R.string.coreui_pagination_last),
                        onClick = { webView.pageDown(true) },
                        enabled = canGoNext,
                    )
                }
            }
            if (onOpenRequestedPage != null) {
                TsuyomiTonalIconButton(
                    imageVector = TsuyomiIcons.Refresh,
                    contentDescription = verifiedPageOpenLabel,
                    onClick = onOpenRequestedPage,
                    enabled = !snapshotWorking,
                )
            }
            if (onUseCurrentPage != null) {
                TsuyomiVerificationAction(
                    text = stringResource(R.string.verification_use_current_page),
                    onClick = onUseCurrentPage,
                    working = snapshotWorking,
                    workingText = stringResource(R.string.verification_snapshot_working),
                    modifier = Modifier.testTag("verification-use-current-page"),
                )
            }
            TsuyomiVerificationAction(
                text = stringResource(if (eInk) R.string.verification_done else R.string.verification_complete_action),
                onClick = onComplete,
                enabled = !snapshotWorking,
                tonal = snapshotActionAvailable,
            )
        }

        AndroidView(
            factory = {
                webView.apply {
                    // WebView uses native LayoutParams, not Compose's measured bounds, for CSS vh.
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
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
            shadowElevation = if (eInk) 0.dp else 2.dp,
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
                shadowElevation = if (eInk) 0.dp else 2.dp,
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


private val VerificationToolbarMaxWidth = 560.dp
private val VerificationFeedbackMaxWidth = 560.dp
private val VerificationFeedbackTopOffset = 56.dp

private const val MAX_VERIFIED_PAGE_BYTES = 2 * 1024 * 1024
