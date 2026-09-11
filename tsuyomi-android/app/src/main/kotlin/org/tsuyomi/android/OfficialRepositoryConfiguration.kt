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

    fun publisherKeys(client: OfficialRepositoryClient?): PublisherKeyResolver {
        val localKeys = Phase2LocalTrust.resolver()
        return object : PublisherKeyResolver {
            override fun resolve(keyId: String): PublisherKey? {
                val local = localKeys.resolve(keyId)
                val repository = client?.publisherKeys?.resolve(keyId)
                if (local != null && repository != null && !local.publicKey.contentEquals(repository.publicKey)) return null
                return repository ?: local
            }

            override fun isRevokedFingerprint(fingerprint: String): Boolean =
                localKeys.isRevokedFingerprint(fingerprint) || client?.publisherKeys?.isRevokedFingerprint(fingerprint) == true

            override fun isRevokedPackage(packageSha256: String): Boolean =
                localKeys.isRevokedPackage(packageSha256) || client?.publisherKeys?.isRevokedPackage(packageSha256) == true
        }
    }

    fun isTrusted(packageInfo: VerifiedHxpPackage, keys: PublisherKeyResolver): Boolean =
        !keys.isRevokedFingerprint(packageInfo.publisherFingerprint) &&
            !keys.isRevokedPackage(packageInfo.packageSha256) &&
            keys.resolve(packageInfo.manifest.publisherKeyId)?.fingerprint == packageInfo.publisherFingerprint

    fun admission(context: Context): (VerifiedHxpPackage) -> Boolean {
        val keys = publisherKeys((context.applicationContext as TsuyomiApplication).officialRepository)
        return { packageInfo -> isTrusted(packageInfo, keys) }
    }
}
