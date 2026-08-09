/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.quickjsruntime

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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
    fun resetsContextAfterTimedOutExecution() = runBlocking {
        QuickJsRuntimeLane("timeout", QuickJsRuntimeLimits(4L * 1024 * 1024, 100)).use { lane ->
            lane.evaluateModule(resetFixtureModule(), "timeout.mjs")
            val failure = try {
                lane.callJson("poisonAndSpin", "[]")
                throw AssertionError("Expected QuickJS execution timeout")
            } catch (error: QuickJsRuntimeException) {
                error
            }
            assertEquals(QuickJsRuntimeError.EXECUTION_LIMIT, failure.error)
            assertEquals("\"clean\"", lane.callJson("state", "[]"))
        }
    }

    @Test
    fun resetsContextAfterCancelledExecution() = runBlocking {
        QuickJsRuntimeLane("cancellation", QuickJsRuntimeLimits(4L * 1024 * 1024, 1_000)).use { lane ->
            lane.evaluateModule(resetFixtureModule(), "cancellation.mjs")
            val invocation = async { lane.callJson("poisonAndSpin", "[]") }
            delay(25)
            invocation.cancelAndJoin()

            assertEquals("\"clean\"", lane.callJson("state", "[]"))
        }
    }

    @Test
    fun cancellationDoesNotInterruptTheNextSerialOperation() = runBlocking {
        QuickJsRuntimeLane("late-cancellation", QuickJsRuntimeLimits(4L * 1024 * 1024, 1_000)).use { lane ->
            lane.evaluateModule(resetFixtureModule(), "late-cancellation.mjs")
            val firstInvocation = async { lane.callJson("poisonAndSpin", "[]") }
            delay(25)

            firstInvocation.cancel()
            assertEquals("\"clean\"", lane.callJson("state", "[]"))
            firstInvocation.join()
        }
    }

    @Test
    fun closesDuringCancelledExecutionWithoutLeavingANativeHandleUsable() = runBlocking {
        val lane = QuickJsRuntimeLane("close", QuickJsRuntimeLimits(4L * 1024 * 1024, 1_000))
        try {
            lane.evaluateModule(resetFixtureModule(), "close.mjs")
            val invocation = async {
                try {
                    lane.callJson("poisonAndSpin", "[]")
                    throw AssertionError("Expected QuickJS cancellation during close")
                } catch (error: QuickJsRuntimeException) {
                    error
                }
            }
            delay(25)
            lane.close()
            assertEquals(QuickJsRuntimeError.CANCELLED, invocation.await().error)

            val failure = try {
                lane.callJson("state", "[]")
                throw AssertionError("Expected a closed lane")
            } catch (error: QuickJsRuntimeException) {
                error
            }
            assertEquals(QuickJsRuntimeError.CLOSED, failure.error)
        } finally {
            lane.close()
        }
    }

    private fun resetFixtureModule(): ByteArray = """
        globalThis.extensionState = "clean";
        globalThis.tsuyomiExtension = {
            poisonAndSpin: () => {
                globalThis.extensionState = "poisoned";
                while (true) {}
            },
            state: () => globalThis.extensionState,
        };
    """.trimIndent().encodeToByteArray()
 }
