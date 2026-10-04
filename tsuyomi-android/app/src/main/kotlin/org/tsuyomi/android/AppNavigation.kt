/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import org.tsuyomi.core.ui.components.AppScaffold
import org.tsuyomi.core.ui.components.TsuyomiInstantEnter
import org.tsuyomi.core.ui.components.TsuyomiInstantExit
import org.tsuyomi.core.ui.layout.TsuyomiNavigationLayout
import org.tsuyomi.core.ui.layout.TsuyomiWindowSize
import org.tsuyomi.core.ui.components.TsuyomiNavigationItem
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.feature.library.LibraryTagDestination

internal object Routes {
    const val Library = "library"
    const val LibrarySearch = "library/search"
    const val LibrarySystem = "library/system/{filter}"
    const val LibraryCollection = "library/collection/{collectionId}"
    const val LibraryTags = "library/tags"
    const val UpdateSettings = "library/update-settings"
    const val LibraryTagBooks = "library/tag/{ownership}/{sourceId}/{tag}"
    const val Browse = "browse"
    const val NewCollection = "library/collections/new"
    const val CollectionBookPicker = "library/collections/new/books"
    const val CollectionRule = "library/collections/{collectionId}/rule"
    const val More = "more"
    const val Display = "more/display"
    const val ReaderSettings = "more/reader"
    const val Help = "more/help"
    const val About = "more/about"
    const val Search = "source/search"
    const val SourceHome = "source/home"
    const val Detail = "source/detail"
    const val Directory = "source/directory"
    const val Reader = "source/reader"
    const val Verification = "source/verification"
    const val VerifiedHomePage = "source/verification/verified-home-page"
    const val VerifiedPage = "source/verification/verified-page"
    const val VerifiedDetailPage = "source/verification/verified-detail-page"
    const val VerifiedDirectoryPage = "source/verification/verified-directory-page"
    const val VerifiedChapterPage = "source/verification/verified-chapter-page"
    const val RemoteLibrary = "source/remote-library"
    const val Transfer = "more/data"
    const val LibraryMirror = "library/mirror/{bindingId}"
    const val LibraryMirrorFolder = "library/mirror/{bindingId}/folder/{targetId}"

    fun librarySystem(filter: org.tsuyomi.feature.library.SystemLibraryFilter): String =
        "library/system/${filter.name}"

    fun libraryCollection(collectionId: String): String =
        "library/collection/${Uri.encode(collectionId)}"
    fun collectionRule(collectionId: String): String = "library/collections/${Uri.encode(collectionId)}/rule"

    fun libraryTag(destination: LibraryTagDestination): String =
        "library/tag/${destination.ownership.name}/${Uri.encode(destination.sourceId ?: "_")}/${Uri.encode(destination.normalizedName)}"

    fun libraryMirror(bindingId: String): String = "library/mirror/${Uri.encode(bindingId)}"
    fun libraryMirrorFolder(bindingId: String, targetId: String): String =
        "library/mirror/${Uri.encode(bindingId)}/folder/${Uri.encode(targetId)}"
}


internal fun rootRouteFor(route: String): String = when (route) {
    Routes.NewCollection,
    Routes.CollectionBookPicker,
    Routes.CollectionRule,
    Routes.LibrarySearch,
    Routes.LibrarySystem,
    Routes.LibraryCollection,
    Routes.LibraryTags,
    Routes.LibraryTagBooks,
    Routes.LibraryMirror,
    Routes.LibraryMirrorFolder,
    Routes.UpdateSettings,
    -> Routes.Library
    Routes.Display, Routes.ReaderSettings, Routes.Help, Routes.About, Routes.Transfer -> Routes.More
    Routes.SourceHome,
    Routes.Search,
    Routes.Detail,
    Routes.Directory,
    Routes.Reader,
    Routes.Verification,
    Routes.VerifiedHomePage,
    Routes.VerifiedPage,
    Routes.VerifiedDetailPage,
    Routes.VerifiedDirectoryPage,
    Routes.VerifiedChapterPage,
    Routes.RemoteLibrary,
    -> Routes.Browse
    else -> route
}

internal const val BookCallerRootKey = "book.caller.root"
internal const val BookCallerEntryKey = "book.caller.entry"
internal const val BookCallerRouteKey = "book.caller.route"

/** Keep the caller entry's own bounded query, ordering and position state on its back stack. */
internal fun NavHostController.navigateToBookRoute(route: String) {
    require(route == Routes.Detail || route == Routes.Directory || route == Routes.Reader)
    val caller = currentBackStackEntry
    val callerRoute = caller?.destination?.route
    val originRoot = caller?.savedStateHandle?.get<String>(BookCallerRootKey)
        ?: callerRoute?.let(::rootRouteFor)
        ?: Routes.Library
    navigate(route)
    currentBackStackEntry?.savedStateHandle?.apply {
        set(BookCallerRootKey, originRoot)
        caller?.id?.let { set(BookCallerEntryKey, it) }
        callerRoute?.let { set(BookCallerRouteKey, it) }
    }
}

internal fun routeOwnsSourceFlow(route: String): Boolean = route == Routes.Browse || rootRouteFor(route) == Routes.Browse

internal fun restorationTargetForRoute(route: String): SourceRestorationTarget? = when (route) {
    Routes.Search -> SourceRestorationTarget.SEARCH
    Routes.Detail -> SourceRestorationTarget.DETAIL
    Routes.Directory -> SourceRestorationTarget.DIRECTORY
    Routes.Reader -> SourceRestorationTarget.READER
    Routes.RemoteLibrary, Routes.LibraryMirror, Routes.LibraryMirrorFolder -> SourceRestorationTarget.SEARCH
    else -> null
}

@Composable
internal fun navigationItems(): List<TsuyomiNavigationItem> = listOf(
    TsuyomiNavigationItem(
        route = Routes.Library,
        label = stringResource(R.string.nav_library),
        icon = TsuyomiIcons.Shelf,
    ),
    TsuyomiNavigationItem(
        route = Routes.Browse,
        label = stringResource(R.string.nav_browse),
        icon = TsuyomiIcons.Compass,
    ),
    TsuyomiNavigationItem(
        route = Routes.More,
        label = stringResource(R.string.nav_more),
        icon = TsuyomiIcons.More,
    ),
)

@Composable
internal fun routeTitle(route: String): String = when (route) {
    Routes.Library -> stringResource(R.string.nav_library)
    Routes.LibrarySearch -> stringResource(R.string.title_library_search)
    Routes.NewCollection -> stringResource(R.string.collection_manual_create_title)
    Routes.CollectionBookPicker -> stringResource(R.string.nav_library)
    Routes.CollectionRule -> stringResource(R.string.title_collection_rule_edit)
    Routes.LibrarySystem, Routes.LibraryCollection -> stringResource(R.string.nav_library)
    Routes.LibraryTags, Routes.LibraryTagBooks -> stringResource(R.string.title_library_tags)
    Routes.UpdateSettings -> stringResource(R.string.title_updates_settings)
    Routes.Browse -> stringResource(R.string.nav_browse)
    Routes.More -> stringResource(R.string.nav_more)
    Routes.Display -> stringResource(R.string.title_display_settings)
    Routes.ReaderSettings -> stringResource(R.string.title_reader_settings)
    Routes.Help -> stringResource(R.string.title_help)
    Routes.About -> stringResource(R.string.title_about)
    Routes.SourceHome -> stringResource(R.string.title_source_home)
    Routes.Search -> stringResource(R.string.title_source_search)
    Routes.Detail -> stringResource(R.string.title_book_detail)
    Routes.Directory -> stringResource(R.string.title_book_directory)
    Routes.Transfer -> stringResource(R.string.title_data_transfer)
    Routes.Reader -> stringResource(R.string.title_reader)
    Routes.Verification,
    Routes.VerifiedHomePage,
    Routes.VerifiedPage,
    Routes.VerifiedDetailPage,
    Routes.VerifiedDirectoryPage,
    Routes.VerifiedChapterPage,
    -> stringResource(R.string.title_verification)
    Routes.RemoteLibrary, Routes.LibraryMirror, Routes.LibraryMirrorFolder -> stringResource(R.string.title_remote_library)
    else -> stringResource(R.string.app_name)
}

internal fun NavHostController.selectRoot(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

internal fun destinationOwnsChrome(route: String): Boolean = when (route) {
    Routes.Reader,
    Routes.RemoteLibrary,
    Routes.LibraryMirror,
    Routes.LibraryMirrorFolder,
    Routes.Verification,
    Routes.VerifiedHomePage,
    Routes.VerifiedPage,
    Routes.VerifiedDetailPage,
    Routes.VerifiedDirectoryPage,
    Routes.VerifiedChapterPage,
    -> true
    else -> false
}

internal class DestinationScaffold(
    val windowSize: TsuyomiWindowSize,
    val topBar: @Composable (String, NavBackStackEntry) -> Unit,
    val navigation: @Composable (String, NavBackStackEntry, TsuyomiNavigationLayout) -> Unit,
)

internal val LocalDestinationScaffold = staticCompositionLocalOf<DestinationScaffold> {
    error("Navigation destination has no scaffold owner")
}

/** Animate the whole destination, including its route-specific chrome, in one fixed viewport. */
internal fun NavGraphBuilder.appDestination(
    route: String,
    instantPopEnter: Boolean = false,
    instantPopExit: Boolean = false,
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(
        route,
        popEnterTransition = if (instantPopEnter) ({ TsuyomiInstantEnter }) else null,
        popExitTransition = if (instantPopExit) ({ TsuyomiInstantExit }) else null,
    ) { entry ->
        val scaffold = LocalDestinationScaffold.current
        AppScaffold(
            windowSize = scaffold.windowSize,
            topBar = { scaffold.topBar(route, entry) },
            navigation = { layout -> scaffold.navigation(route, entry, layout) },
        ) {
            content(entry)
        }
    }
}
