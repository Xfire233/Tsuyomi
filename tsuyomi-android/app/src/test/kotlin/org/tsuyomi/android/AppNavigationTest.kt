/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationTest {
    @Test
    fun nestedRoutesMapToTheirStableRoot() {
        mapOf(
            Routes.LocalBook to Routes.Library,
            Routes.Collections to Routes.Library,
            Routes.Reader to Routes.Browse,
            Routes.Transfer to Routes.More,
            Routes.Library to Routes.Library,
        ).forEach { (route, expectedRoot) ->
            assertEquals(route, expectedRoot, rootRouteFor(route))
        }
    }

    @Test
    fun sourceOwnershipIncludesBrowseAndEveryNestedSourceRoute() {
        listOf(
            Routes.Browse,
            Routes.Search,
            Routes.Detail,
            Routes.Directory,
            Routes.Reader,
            Routes.Verification,
            Routes.RemoteLibrary,
        ).forEach { route -> assertTrue(route, routeOwnsSourceFlow(route)) }
        listOf(Routes.Library, Routes.Transfer).forEach { route ->
            assertFalse(route, routeOwnsSourceFlow(route))
        }
    }

    @Test
    fun restorationTargetsRemainSemanticAndRouteSpecific() {
        mapOf(
            Routes.Search to SourceRestorationTarget.SEARCH,
            Routes.Detail to SourceRestorationTarget.DETAIL,
            Routes.Directory to SourceRestorationTarget.DIRECTORY,
            Routes.Reader to SourceRestorationTarget.READER,
            Routes.RemoteLibrary to SourceRestorationTarget.SEARCH,
        ).forEach { (route, expectedTarget) ->
            assertEquals(route, expectedTarget, restorationTargetForRoute(route))
        }
        listOf(Routes.Browse, Routes.Library).forEach { route ->
            assertNull(route, restorationTargetForRoute(route))
        }
    }
}
