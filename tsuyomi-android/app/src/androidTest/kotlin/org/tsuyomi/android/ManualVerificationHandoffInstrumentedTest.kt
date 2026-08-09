/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.net.Uri
import android.webkit.CookieManager
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.SourceCredentialStore
import org.tsuyomi.feature.browse.BrowseUiState
import org.tsuyomi.shared.sourcecontract.HttpsOrigin

@RunWith(AndroidJUnit4::class)
class ManualVerificationHandoffInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun standard_profile_completes_blocked_navigation_and_cookie_handoff() {
        exerciseVerificationHandoff(DisplayPreference.STANDARD)
    }

    @Test
    fun e_ink_profile_completes_blocked_navigation_and_cookie_handoff() {
        exerciseVerificationHandoff(DisplayPreference.EINK)
    }

    @Test
    fun recreation_closes_the_old_source_runtime_before_opening_a_new_session() {
        cleanSessionState()
        waitForText("书架")
        composeRule.onNodeWithText("浏览").performClick()
        waitForText("进入内容源")
        composeRule.onNodeWithText("进入内容源").performClick()
        waitForText("搜索已安装内容源")
        waitForQuickJsLaneCount(1)

        composeRule.activityRule.scenario.recreate()

        waitForText("搜索已安装内容源")
        waitForQuickJsLaneCount(1)
        composeRule.onNodeWithText("搜索书名").performTextInput("fixture")
        composeRule.onNodeWithText("搜索书名").performImeAction()
        waitForText("雾港纪事")
    }

    private fun exerciseVerificationHandoff(profile: DisplayPreference) {
        cleanSessionState()
        runBlocking {
            (composeRule.activity.application as TsuyomiApplication).displayController
                .setDisplayPreference(profile)
        }
        waitForText("书架")
        composeRule.onNodeWithText("浏览").performClick()
        waitForText("进入内容源")
        composeRule.onNodeWithText("进入内容源").performClick()
        waitForText("搜索已安装内容源")

        composeRule.onNodeWithText("搜索书名").performTextInput("challenge")
        composeRule.onNodeWithText("搜索书名").performImeAction()
        waitForText("此来源要求用户手动完成安全验证。")
        composeRule.onNodeWithText("手动登录或验证").performClick()
        waitForText("已完成")
        composeRule.runOnUiThread {
            requireNotNull(findWebView(composeRule.activity.window.decorView))
                .loadUrl("https://outside.example/blocked")
        }
        waitForText("已阻止跳转到未授权站点。仅允许此内容源声明的 HTTPS 站点。")


        val cookieAccepted = AtomicBoolean(false)
        val cookieSet = CountDownLatch(1)
        composeRule.runOnUiThread {
            CookieManager.getInstance().setCookie(
                WENKU8_ORIGIN.canonical,
                "fixture_session=accepted; Path=/; Secure",
            ) { accepted ->
                cookieAccepted.set(accepted)
                cookieSet.countDown()
            }
        }
        assertTrue(cookieSet.await(5, TimeUnit.SECONDS))
        assertTrue(cookieAccepted.get())

        composeRule.onNodeWithText("已完成").performClick()
        waitForText("搜索书名")

        val storedCookie = requireNotNull(
            SourceCredentialStore(targetContext).getSnapshot(
                SourceCredentialPartition(WENKU8_SOURCE_ID, WENKU8_ORIGIN),
            ),
        ).plaintext.decodeToString(throwOnInvalidSequence = true)
        assertTrue(storedCookie.contains("fixture_session=accepted"))

        composeRule.onNodeWithText("搜索书名").performImeAction()
        waitForText("雾港纪事")
    }

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { index ->
            findWebView(view.getChildAt(index))
        }
        else -> null
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForQuickJsLaneCount(expected: Int) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            Thread.getAllStackTraces().keys.count { thread ->
                thread.isAlive && thread.name.startsWith("tsuyomi-quickjs-")
            } == expected
        }
    }

    companion object {
        private const val WENKU8_SOURCE_ID = "org.tsuyomi.wenku8"
        private val WENKU8_ORIGIN = HttpsOrigin("https://www.wenku8.net")
        private val targetContext
            get() = InstrumentationRegistry.getInstrumentation().targetContext

        @BeforeClass
        @JvmStatic
        fun installFixtureSource() = runBlocking {
            cleanPrivateState()
            val fixture = File(targetContext.cacheDir, "wenku8-fixture.hxp")
            targetContext.assets.open("wenku8-fixture.hxp").use { input ->
                fixture.outputStream().use(input::copyTo)
            }
            val controller = SourceInstallController(targetContext)
            controller.prepare(Uri.fromFile(fixture), targetContext.contentResolver)
            check(controller.state is BrowseUiState.Approval) { "Fixture source was not prepared" }
            controller.approve(allowDowngrade = false)
            check(controller.state is BrowseUiState.Installed) { "Fixture source was not installed" }
        }

        @AfterClass
        @JvmStatic
        fun cleanUpFixtureSource() {
            cleanPrivateState()
        }

        private fun cleanSessionState() {
            File(targetContext.noBackupFilesDir, "source-credentials").deleteRecursively()
            File(targetContext.cacheDir, "source-network-cache").deleteRecursively()
        }

        private fun cleanPrivateState() {
            File(targetContext.noBackupFilesDir, "extensions").deleteRecursively()
            cleanSessionState()
            File(targetContext.cacheDir, "hxp-staging").deleteRecursively()
        }
    }
}
