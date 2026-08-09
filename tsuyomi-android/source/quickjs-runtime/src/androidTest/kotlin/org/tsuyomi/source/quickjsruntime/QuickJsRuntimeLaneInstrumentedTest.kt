/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.quickjsruntime

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickJsRuntimeLaneInstrumentedTest {
    @Test
    fun evaluatesModuleAndCallsExportedGlobalThroughOneNativeRuntimeLane() = runBlocking {
        QuickJsRuntimeLane("instrumentation", QuickJsRuntimeLimits(4L * 1024 * 1024, 1_000)).use { lane ->
            lane.evaluateModule(
                source = """
                    const api = { sum: (left, right) => left + right };
                    globalThis.tsuyomiExtension = api;
                    export default api;
                """.trimIndent().encodeToByteArray(),
                filename = "fixture.mjs",
            )

            assertEquals("5", lane.callJson("sum", "[2,3]"))
        }
    }

    @Test
    fun boundsNonTerminatingExtensionExecution() = runBlocking {
        QuickJsRuntimeLane("timeout", QuickJsRuntimeLimits(4L * 1024 * 1024, 100)).use { lane ->
            lane.evaluateModule(
                source = """
                    globalThis.tsuyomiExtension = {
                        spin: () => { let count = 0; while (count >= 0) { count += Math.sqrt(count + 1); } }
                    };
                """.trimIndent().encodeToByteArray(),
                filename = "timeout.mjs",
            )
            val failure = try {
                lane.callJson("spin", "[]")
                throw AssertionError("Expected QuickJS execution timeout")
            } catch (error: QuickJsRuntimeException) {
                error
            }
            assertEquals(QuickJsRuntimeError.EXECUTION_LIMIT, failure.error)
        }
    }
}
