/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.navigation.NavHostController
import androidx.navigation.createGraph
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.feature.browse.BrowseUiState

@RunWith(AndroidJUnit4::class)
internal class SourceHomeSwitchInstrumentedTest : SourceFlowInstrumentedTestFixture() {
    @Test
    fun uninstallLeavesBrowseWithoutADeadSourceBackEntryAndReinstallCanOpenAgain() = runBlocking(Dispatchers.Main) {
        val first = installFixture()
        val installer = SourceInstallController(context, library)
        installer.restoreInstalled()
        controller { FakeSession() }.use { flow ->
            flow.open(first)
            val navController = navigationController()
            navController.navigate(Routes.SourceHome)
            val owner = routeOwner(installer, flow, navController)
            assertTrue(owner.uninstallSource(first.manifest.sourceId.value))
            assertEquals(Routes.Browse, navController.currentDestination?.route)
            assertEquals(null, installer.activePackage)
            assertTrue(navController.currentBackStack.value.none { it.destination.route == Routes.SourceHome })
            installFixture()
            installer.refreshInstalled()
            owner.navigateToSourceHome()
            assertEquals(Routes.SourceHome, navController.currentDestination?.route)
            assertTrue(navController.popBackStack())
            assertEquals(Routes.Browse, navController.currentDestination?.route)
        }
    }

    @Test
    fun home_switch_activates_signed_target_current_choice_is_noop_and_back_has_no_old_source_entry() = runBlocking(Dispatchers.Main) {
        val first = installFixture()
        val installer = SourceInstallController(context, library)
        installer.restoreInstalled()
        val home = installSignedSwitchOverlay(installer, "source-switch-home")
        check(installer.activateInstalledSource(first.manifest.sourceId.value)?.manifest?.sourceId == first.manifest.sourceId)
        val openedSourceIds = mutableListOf<String>()
        controller { candidate ->
            openedSourceIds += candidate.manifest.sourceId.value
            FakeSession()
        }.use { flow ->
            flow.open(first)
            val navController = navigationController()
            navController.navigate(Routes.SourceHome)
            val owner = routeOwner(installer, flow, navController)

            assertEquals(SourceHomeSwitchResult.OPENED_HOME, owner.switchSourceHome(home.manifest.sourceId.value))
            assertEquals(home.manifest.sourceId, installer.activePackage?.manifest?.sourceId)
            assertEquals(Routes.SourceHome, navController.currentDestination?.route)
            assertEquals(listOf(first.manifest.sourceId.value, home.manifest.sourceId.value), openedSourceIds)

            assertEquals(SourceHomeSwitchResult.CURRENT_SOURCE, owner.switchSourceHome(home.manifest.sourceId.value))
            assertEquals(listOf(first.manifest.sourceId.value, home.manifest.sourceId.value), openedSourceIds)

            assertTrue(navController.popBackStack())
            assertEquals(Routes.Browse, navController.currentDestination?.route)
        }
    }

    @Test
    fun search_only_signed_target_replaces_home_with_scoped_search_without_back_accumulation() = runBlocking(Dispatchers.Main) {
        val first = installFixture()
        val installer = SourceInstallController(context, library)
        installer.restoreInstalled()
        val searchOnly = installSignedSwitchOverlay(installer, "source-switch-search")
        check(installer.activateInstalledSource(first.manifest.sourceId.value)?.manifest?.sourceId == first.manifest.sourceId)
        controller { FakeSession() }.use { flow ->
            flow.open(first)
            val navController = navigationController()
            navController.navigate(Routes.SourceHome)
            val owner = routeOwner(installer, flow, navController)

            assertEquals(SourceHomeSwitchResult.OPENED_SEARCH, owner.switchSourceHome(searchOnly.manifest.sourceId.value))
            assertEquals(searchOnly.manifest.sourceId, installer.activePackage?.manifest?.sourceId)
            assertEquals(Routes.Search, navController.currentDestination?.route)
            assertTrue(navController.popBackStack())
            assertEquals(Routes.Browse, navController.currentDestination?.route)
        }
    }
    @Test
    fun concurrent_source_choices_commit_in_order_without_interleaving_sessions_or_back_entries() = runBlocking(Dispatchers.Main) {
        val first = installFixture()
        val installer = SourceInstallController(context, library)
        installer.restoreInstalled()
        val home = installSignedSwitchOverlay(installer, "source-switch-home")
        val searchOnly = installSignedSwitchOverlay(installer, "source-switch-search")
        check(installer.activateInstalledSource(first.manifest.sourceId.value)?.manifest?.sourceId == first.manifest.sourceId)
        val homeSessionOpening = CompletableDeferred<Unit>()
        val releaseHomeSession = CompletableDeferred<Unit>()
        controller { candidate ->
            if (candidate.manifest.sourceId == home.manifest.sourceId) {
                homeSessionOpening.complete(Unit)
                releaseHomeSession.await()
            }
            FakeSession()
        }.use { flow ->
            flow.open(first)
            val navController = navigationController()
            navController.navigate(Routes.SourceHome)
            val owner = routeOwner(installer, flow, navController)
            val chooseHome = async { owner.switchSourceHome(home.manifest.sourceId.value) }
            homeSessionOpening.await()
            val chooseSearch = async { owner.switchSourceHome(searchOnly.manifest.sourceId.value) }

            releaseHomeSession.complete(Unit)

            assertEquals(SourceHomeSwitchResult.OPENED_HOME, chooseHome.await())
            assertEquals(SourceHomeSwitchResult.OPENED_SEARCH, chooseSearch.await())
            assertEquals(searchOnly.manifest.sourceId, installer.activePackage?.manifest?.sourceId)
            assertEquals(Routes.Search, navController.currentDestination?.route)
            assertTrue(navController.popBackStack())
            assertEquals(Routes.Browse, navController.currentDestination?.route)
        }
    }
    @Test
    fun failed_target_session_keeps_the_active_source_usable_during_preparation_and_after_failure() = runBlocking(Dispatchers.Main) {
        val first = installFixture()
        val installer = SourceInstallController(context, library)
        installer.restoreInstalled()
        val home = installSignedSwitchOverlay(installer, "source-switch-home")
        check(installer.activateInstalledSource(first.manifest.sourceId.value)?.manifest?.sourceId == first.manifest.sourceId)
        val targetOpening = CompletableDeferred<Unit>()
        val failTargetOpen = CompletableDeferred<Unit>()
        val searchedSourceIds = mutableListOf<String>()
        controller { candidate ->
            if (candidate.manifest.sourceId == home.manifest.sourceId) {
                targetOpening.complete(Unit)
                failTargetOpen.await()
                throw IllegalStateException("fixture-target-open-failed")
            }
            FakeSession(searchResult = { _, _ ->
                searchedSourceIds += candidate.manifest.sourceId.value
                emptyList()
            })
        }.use { flow ->
            flow.open(first)
            val navController = navigationController()
            navController.navigate(Routes.SourceHome)
            val owner = routeOwner(installer, flow, navController)
            val switch = async { owner.switchSourceHome(home.manifest.sourceId.value) }
            targetOpening.await()

            flow.updateQuery("kept-active-source")
            flow.search()
            assertEquals(listOf(first.manifest.sourceId.value), searchedSourceIds)

            failTargetOpen.complete(Unit)
            assertEquals(SourceHomeSwitchResult.UNAVAILABLE, switch.await())
            flow.updateQuery("still-active-source")
            flow.search()
            assertEquals(listOf(first.manifest.sourceId.value, first.manifest.sourceId.value), searchedSourceIds)
            assertEquals(first.manifest.sourceId, installer.activePackage?.manifest?.sourceId)
            assertEquals(Routes.SourceHome, navController.currentDestination?.route)
        }
    }

    @Test
    fun back_during_target_preparation_prevents_activation_or_home_reentry() = runBlocking(Dispatchers.Main) {
        val first = installFixture()
        val installer = SourceInstallController(context, library)
        installer.restoreInstalled()
        val home = installSignedSwitchOverlay(installer, "source-switch-home")
        check(installer.activateInstalledSource(first.manifest.sourceId.value)?.manifest?.sourceId == first.manifest.sourceId)
        val targetOpening = CompletableDeferred<Unit>()
        val releaseTarget = CompletableDeferred<Unit>()
        controller { candidate ->
            if (candidate.manifest.sourceId == home.manifest.sourceId) {
                targetOpening.complete(Unit)
                releaseTarget.await()
            }
            FakeSession()
        }.use { flow ->
            flow.open(first)
            val navController = navigationController()
            navController.navigate(Routes.SourceHome)
            val owner = routeOwner(installer, flow, navController)
            val switch = async { owner.switchSourceHome(home.manifest.sourceId.value) }
            targetOpening.await()

            assertTrue(navController.popBackStack())
            releaseTarget.complete(Unit)

            assertEquals(SourceHomeSwitchResult.UNAVAILABLE, switch.await())
            assertEquals(first.manifest.sourceId, installer.activePackage?.manifest?.sourceId)
            assertEquals(Routes.Browse, navController.currentDestination?.route)
        }
    }

    @Test
    fun pending_approval_blocks_source_switch_without_changing_the_active_source_or_navigation() = runBlocking(Dispatchers.Main) {
        val first = installFixture()
        val installer = SourceInstallController(context, library)
        installer.restoreInstalled()
        val home = installSignedSwitchOverlay(installer, "source-switch-home")
        check(installer.activateInstalledSource(first.manifest.sourceId.value)?.manifest?.sourceId == first.manifest.sourceId)
        val pendingArchive = assemblePendingSourceApproval()
        try {
            installer.prepare(android.net.Uri.fromFile(pendingArchive), context.contentResolver)
        } finally {
            pendingArchive.delete()
        }
        check(installer.state is BrowseUiState.Approval)
        controller { FakeSession() }.use { flow ->
            flow.open(first)
            val navController = navigationController()
            navController.navigate(Routes.SourceHome)
            val owner = routeOwner(installer, flow, navController)

            assertEquals(SourceHomeSwitchResult.UNAVAILABLE, owner.switchSourceHome(home.manifest.sourceId.value))
            assertEquals(first.manifest.sourceId, installer.activePackage?.manifest?.sourceId)
            assertEquals(Routes.SourceHome, navController.currentDestination?.route)
            assertTrue(installer.state is BrowseUiState.Approval)
        }
    }

    private fun routeOwner(
        installer: SourceInstallController,
        flow: SourceFlowController,
        navController: NavHostController,
    ) = SourceRouteOwner(
        installer = installer,
        flow = flow,
        navController = navController,
        requestImportAction = {},
        library = library,
        onLibraryChanged = {},
    )

    private fun navigationController(): NavHostController = NavHostController(context).apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = Routes.Browse) {
            composable(Routes.Browse) {}
            composable(Routes.SourceHome) {}
            composable(Routes.Search) {}
        }
    }
}
