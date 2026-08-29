/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import org.tsuyomi.core.ui.components.TsuyomiNavigationItem
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.shared.model.BookIdentity

internal object Routes {
    const val Library = "library"
    const val Browse = "browse"
    const val LocalBook = "library/book/{sourceId}/{remoteBookId}"
    const val Collections = "library/collections"
    const val More = "more"
    const val Display = "settings/display"
    const val About = "about"
    const val Search = "source/search"
    const val Detail = "source/detail"
    const val Directory = "source/directory"
    const val Reader = "source/reader"
    const val Verification = "source/verification"
    const val RemoteLibrary = "source/remote-library"
    const val Transfer = "settings/transfer"

    fun localBook(identity: BookIdentity): String =
        "library/book/${Uri.encode(identity.sourceId)}/${Uri.encode(identity.remoteBookId)}"
}

internal fun rootRouteFor(route: String): String = when (route) {
    Routes.LocalBook, Routes.Collections -> Routes.Library
    Routes.Display, Routes.About, Routes.Transfer -> Routes.More
    Routes.Search,
    Routes.Detail,
    Routes.Directory,
    Routes.Reader,
    Routes.Verification,
    Routes.RemoteLibrary,
    -> Routes.Browse
    else -> route
}

internal fun routeOwnsSourceFlow(route: String): Boolean = route == Routes.Browse || rootRouteFor(route) == Routes.Browse

internal fun restorationTargetForRoute(route: String): SourceRestorationTarget? = when (route) {
    Routes.Search -> SourceRestorationTarget.SEARCH
    Routes.Detail -> SourceRestorationTarget.DETAIL
    Routes.Directory -> SourceRestorationTarget.DIRECTORY
    Routes.Reader -> SourceRestorationTarget.READER
    Routes.RemoteLibrary -> SourceRestorationTarget.SEARCH
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
    Routes.LocalBook -> stringResource(R.string.title_local_book)
    Routes.Collections -> stringResource(R.string.title_collections)
    Routes.Browse -> stringResource(R.string.nav_browse)
    Routes.More -> stringResource(R.string.nav_more)
    Routes.Display -> stringResource(R.string.title_display_settings)
    Routes.About -> stringResource(R.string.title_about)
    Routes.Search -> stringResource(R.string.title_source_search)
    Routes.Detail -> stringResource(R.string.title_book_detail)
    Routes.Directory -> stringResource(R.string.title_book_directory)
    Routes.Transfer -> stringResource(R.string.title_data_transfer)
    Routes.Reader -> stringResource(R.string.title_reader)
    Routes.Verification -> stringResource(R.string.title_verification)
    Routes.RemoteLibrary -> stringResource(R.string.title_remote_library)
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
