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
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

internal object Phase2SourceGateway {
    fun create(
        context: Context,
        packageInfo: VerifiedHxpPackage,
        directActionTokens: DirectActionTokenRegistry,
    ): HostNetworkGateway =
        SourceGatewayFactory.create(context, packageInfo, UrlConnectionHostHttpTransport(), directActionTokens)

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
