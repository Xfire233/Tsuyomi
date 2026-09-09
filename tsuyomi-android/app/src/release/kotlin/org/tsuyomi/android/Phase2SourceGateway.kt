/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.Context
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.core.network.HostNetworkGateway
import org.tsuyomi.core.network.UrlConnectionHostHttpTransport
import org.tsuyomi.core.webview.CapturedVerifiedPage
import org.tsuyomi.core.webview.VerifiedBrowserGetTransport
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

internal object Phase2SourceGateway {
    fun create(
        context: Context,
        packageInfo: VerifiedHxpPackage,
        directActionTokens: DirectActionTokenRegistry,
    ): HostNetworkGateway = createSession(context, packageInfo, directActionTokens).first

    fun createSession(
        context: Context,
        packageInfo: VerifiedHxpPackage,
        directActionTokens: DirectActionTokenRegistry,
    ): Pair<HostNetworkGateway, HostNetworkGateway?> {
        val origins = packageInfo.manifest.capabilities.webLogin.origins
            .ifEmpty { packageInfo.manifest.capabilities.cookies.origins }
        return SourceGatewayFactory.createSessionGateways(
            context = context,
            packageInfo = packageInfo,
            nativeTransport = UrlConnectionHostHttpTransport(),
            verifiedGetTransport = VerifiedBrowserGetTransport(
                context = context,
                sourceId = packageInfo.manifest.sourceId.value,
                allowedOrigins = origins,
            ),
            directActionTokens = directActionTokens,
        )
    }

    fun createVerifiedPage(
        context: Context,
        packageInfo: VerifiedHxpPackage,
        snapshot: CapturedVerifiedPage,
        directActionTokens: DirectActionTokenRegistry,
    ): HostNetworkGateway = SourceGatewayFactory.createVerifiedPage(
        context = context,
        packageInfo = packageInfo,
        snapshot = snapshot,
        directActionTokens = directActionTokens,
    )
}
