/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.Context
import org.tsuyomi.core.network.HostNetworkGateway
import org.tsuyomi.core.network.UrlConnectionHostHttpTransport
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

internal object Gate2SourceGateway {
    fun create(context: Context, packageInfo: VerifiedHxpPackage): HostNetworkGateway =
        SourceGatewayFactory.create(context, packageInfo, UrlConnectionHostHttpTransport())
}
