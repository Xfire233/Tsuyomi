/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.Context
import java.io.File
import java.util.Base64
import org.tsuyomi.source.extensionmanager.OfficialRepositoryClient
import org.tsuyomi.source.extensionmanager.PublisherKey
import org.tsuyomi.source.extensionmanager.PublisherTrust
import org.tsuyomi.source.extensionmanager.PackageTrustRegistry
import org.tsuyomi.source.extensionmanager.RepositoryRoot
import org.tsuyomi.source.extensionmanager.PublisherKeyResolver
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

/** Public trust configuration only. Private keys never enter the application or its assets. */
internal object OfficialRepositoryConfiguration {
    fun createClient(context: Context): OfficialRepositoryClient? {
        if (BuildConfig.OFFICIAL_REPOSITORY_KEY_ID.isEmpty()) return null
        val root = RepositoryRoot(
            repositoryId = "org.tsuyomi.extensions",
            indexUrl = "https://raw.githubusercontent.com/Chachaanteng/tsuyomi-extensions/repository/index-v1.json",
            signingKey = PublisherKey(
                keyId = BuildConfig.OFFICIAL_REPOSITORY_KEY_ID,
                publicKey = Base64.getDecoder().decode(BuildConfig.OFFICIAL_REPOSITORY_PUBLIC_KEY),
                trust = PublisherTrust.BUILT_IN_OFFICIAL,
            ),
        )
        return OfficialRepositoryClient(root, File(context.noBackupFilesDir, "official-repository"))
    }

    fun publisherKeys(
        client: OfficialRepositoryClient?,
        application: TsuyomiApplication? = null,
    ): PublisherKeyResolver = org.tsuyomi.source.extensionmanager.CompositePublisherKeyResolver(
        listOfNotNull(
            Phase2LocalTrust.resolver(), client?.publisherKeys,
            application?.repositorySubscriptions?.publisherKeys,
            application?.packageTrust?.publisherKeys,
        ),
    )

    fun isTrusted(packageInfo: VerifiedHxpPackage, keys: PublisherKeyResolver, packageTrust: PackageTrustRegistry): Boolean {
        if (packageInfo.publisherTrust == PublisherTrust.LOCAL_UNSIGNED) {
            return !keys.isGloballyRevokedPackage(packageInfo.packageSha256) &&
                packageTrust.isApproved(packageInfo)
        }
        val keyId = packageInfo.manifest.publisherKeyId ?: return false
        val fingerprint = packageInfo.publisherFingerprint ?: return false
        return !keys.isRevokedPublisher(keyId, fingerprint) &&
            !keys.isRevokedPackage(packageInfo.packageSha256, keyId, fingerprint) &&
            keys.resolve(keyId)?.fingerprint == fingerprint && packageTrust.isApproved(packageInfo)
    }

    fun admission(context: Context): (VerifiedHxpPackage) -> Boolean {
        val application = context.applicationContext as TsuyomiApplication
        val keys = publisherKeys(application.officialRepository, application)
        return { packageInfo ->
            isTrusted(packageInfo, keys, application.packageTrust) &&
                (packageInfo.publisherTrust != PublisherTrust.USER_ADDED || application.packageTrust.isApproved(packageInfo)) &&
                File(context.noBackupFilesDir, "extensions/active/${packageInfo.manifest.sourceId.value}.hxp").isFile
        }
    }
}
