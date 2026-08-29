/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.tsuyomi.feature.browse.BrowseUiState

@RunWith(AndroidJUnit4::class)
internal class SourceInstallControllerInstrumentedTest : SourceFlowInstrumentedTestFixture() {
    @Test
    fun remoteWritebackRequiresCredentialAndFailsClosedWhenCredentialDisappears() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val install = SourceInstallController(context, library)
        install.restoreInstalled()

        assertFalse(install.remoteAddCredentialReady())
        assertFalse(install.setRemoteAddWritebackEnabled(true))
        putCredential(sourceId)
        assertTrue(install.remoteAddCredentialReady())
        assertTrue(install.setRemoteAddWritebackEnabled(true))

        File(context.noBackupFilesDir, "source-credentials").deleteRecursively()
        assertFalse(requireNotNull(install.remotePolicy()).addWritebackEnabled)
        assertFalse(requireNotNull(library.sourceRemotePolicy(sourceId)).addWritebackEnabled)
        assertEquals(packageInfo.packageSha256, install.activePackage?.packageSha256)
    }

    @Test
    fun invalidInstalledPackageIsMarkedUnavailableDuringRestore() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val before = requireNotNull(library.sourceAvailability(sourceId))
        File(context.noBackupFilesDir, "extensions/active/$sourceId.hxp").writeText("tampered")

        val restore = SourceInstallController(context, library)
        restore.restoreInstalled()

        assertTrue(restore.state is BrowseUiState.Failure)
        assertEquals(null, restore.activePackage)
        val after = requireNotNull(library.sourceAvailability(sourceId))
        assertFalse(after.available)
        assertEquals(before.generation + 1, after.generation)
    }
}
