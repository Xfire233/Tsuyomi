/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import org.tsuyomi.core.security.SourceCredentialStore
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.webview.ControlledWebLoginSession
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

@Composable
fun ManualVerificationRoute(
    packageInfo: VerifiedHxpPackage,
    onCompleted: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webView by remember(packageInfo.packageSha256) { mutableStateOf<WebView?>(null) }
    var failure by remember(packageInfo.packageSha256) { mutableStateOf(false) }
    var blockedNavigation by remember(packageInfo.packageSha256) { mutableStateOf(false) }
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

    LaunchedEffect(session, initialUrl) {
        if (!packageInfo.manifest.capabilities.webLogin.enabled || initialUrl == null) {
            failure = true
        } else {
            runCatching { session.open(initialUrl) }
                .onSuccess { webView = it }
                .onFailure { failure = true }
        }
    }
    DisposableEffect(session) {
        onDispose { scope.launch { session.cancel() } }
    }

    when {
        failure -> StateView(
            kind = TsuyomiStateKind.ERROR,
            title = stringResource(R.string.verification_unavailable),
            actionLabel = stringResource(R.string.verification_back),
            onAction = onCancel,
            modifier = modifier,
        )
        webView == null -> StateView(
            kind = TsuyomiStateKind.LOADING,
            title = stringResource(R.string.verification_loading),
            modifier = modifier,
        )
        else -> Column(
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
                factory = { requireNotNull(webView) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TsuyomiButton(
                    text = stringResource(R.string.verification_cancel),
                    onClick = { scope.launch { session.cancel(); onCancel() } },
                    modifier = Modifier.weight(1f),
                    style = TsuyomiButtonStyle.SECONDARY,
                )
                TsuyomiButton(
                    text = stringResource(R.string.verification_done),
                    onClick = { scope.launch { session.finish(); onCompleted() } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
