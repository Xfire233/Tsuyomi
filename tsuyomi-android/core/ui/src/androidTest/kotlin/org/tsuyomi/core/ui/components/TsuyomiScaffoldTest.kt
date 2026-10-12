/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components
import android.view.View

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.ui.R
import org.tsuyomi.core.ui.layout.TsuyomiWindowSize
import org.tsuyomi.core.ui.theme.TsuyomiTheme

@RunWith(AndroidJUnit4::class)
class TsuyomiScaffoldTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun railControlsStayInsideSystemBarsWithEitherChromeOwnerAndDisplayProfile() {
        composeRule.runOnUiThread {
            // API 29 native equivalent of setDecorFitsSystemWindows(false), with both bars visible.
            composeRule.activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        val profile = mutableStateOf(DisplayProfile.STANDARD)
        val scaffoldOwnsChrome = mutableStateOf(false)
        var safeTop = 0
        var safeBottom = 0
        composeRule.setContent {
            val density = LocalDensity.current
            safeTop = WindowInsets.safeDrawing.getTop(density)
            safeBottom = WindowInsets.safeDrawing.getBottom(density)
            val eink = profile.value == DisplayProfile.EINK
            val environment = DisplayEnvironment(
                preferences = DisplayPreferences(displayPreference = if (eink) DisplayPreference.EINK else DisplayPreference.STANDARD),
                effectiveProfile = profile.value,
                decisionReason = if (eink) DisplayDecisionReason.MANUAL_EINK else DisplayDecisionReason.MANUAL_STANDARD,
                detectedDeviceLabel = null,
                dynamicColorEligible = false,
                dynamicColorEffective = false,
                effectiveDarkTheme = false,
                motionPolicy = if (eink) MotionPolicy.INSTANT else MotionPolicy.STANDARD,
                redrawEpoch = 0L,
            )
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    Box(Modifier.fillMaxSize().testTag("window")) {
                        AppScaffold(
                            windowSize = TsuyomiWindowSize(800, 1000),
                            modifier = Modifier.fillMaxSize(),
                            topBar = {
                                if (scaffoldOwnsChrome.value) TsuyomiTopBar("书架", modifier = Modifier.testTag("chrome"))
                            },
                            navigation = {
                                Box(Modifier.width(88.dp).fillMaxHeight()) {
                                    TsuyomiButton("书架", {}, modifier = Modifier.testTag("rail-action"))
                                }
                            },
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                if (!scaffoldOwnsChrome.value) TsuyomiTopBar("书架", modifier = Modifier.testTag("chrome"))
                                PaginationBar(
                                    page = 2,
                                    pageCount = 3,
                                    onPrevious = {},
                                    onNext = {},
                                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                                )
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitUntil(5_000) { safeTop > 0 && safeBottom > 0 }
        for (displayProfile in listOf(DisplayProfile.STANDARD, DisplayProfile.EINK)) {
            var contentChromeHeight: Float? = null
            var contentChromeLeft = 0f
            var contentChromeWidth = 0f
            var railTop = 0f
            for (hostChrome in listOf(false, true)) {
                composeRule.runOnIdle {
                    profile.value = displayProfile
                    scaffoldOwnsChrome.value = hostChrome
                }
                val window = composeRule.onNodeWithTag("window").fetchSemanticsNode().boundsInRoot
                val rail = composeRule.onNodeWithTag("rail-action").assertIsDisplayed().fetchSemanticsNode().touchBoundsInRoot
                val next = composeRule.onNodeWithText(composeRule.activity.getString(R.string.coreui_pagination_next))
                    .assertIsDisplayed().fetchSemanticsNode().touchBoundsInRoot
                val chrome = composeRule.onNodeWithTag("chrome").fetchSemanticsNode().boundsInRoot
                assertTrue("Rail action overlaps the status bar", rail.top >= window.top + safeTop)
                assertTrue("Pagination overlaps the navigation bar", next.bottom <= window.bottom - safeBottom + 1f)
                assertEquals("Chrome must consume the top inset once", window.top + safeTop, chrome.top, 1f)
                assertTrue("AppBar must start after the rail, not overlay it", chrome.left >= rail.right)
                if (hostChrome) {
                    assertEquals(contentChromeLeft, chrome.left, 1f)
                    assertEquals(contentChromeWidth, chrome.width, 1f)
                    assertEquals("Rail controls must not move with the AppBar owner", railTop, rail.top, 1f)
                } else {
                    contentChromeLeft = chrome.left
                    contentChromeWidth = chrome.width
                    railTop = rail.top
                }
                if (hostChrome) assertEquals(contentChromeHeight!!, chrome.height, 1f)
                else contentChromeHeight = chrome.height
            }
        }
    }
}
