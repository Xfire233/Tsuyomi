/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.quickjsruntime

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun evaluatesUnicodeScriptAndExtensionsThroughJni() = runBlocking {
        QuickJsRuntimeLane("unicode", QuickJsRuntimeLimits(4L * 1024 * 1024, 1_000)).use { lane ->
            lane.evaluateModule(
                source = """
                    globalThis.tsuyomiExtension = {
                        script: () => /\p{Script=Latin}/u.test("A") && !/\p{Script=Latin}/u.test("Ж"),
                        scriptExtensions: () => /\p{Script_Extensions=Latin}/u.test("A"),
                    };
                """.trimIndent().encodeToByteArray(),
                filename = "unicode.mjs",
            )

            assertEquals("true", lane.callJson("script", "[]"))
            assertEquals("true", lane.callJson("scriptExtensions", "[]"))
        }
    }

    @Test
    fun sourceIntrinsicsCannotEnableBlockingAtomicsWait() = runBlocking {
        QuickJsRuntimeLane("atomics-admission", QuickJsRuntimeLimits(4L * 1024 * 1024, 1_000)).use { lane ->
            lane.evaluateModule(
                source = """
                    globalThis.tsuyomiExtension = {
                        rejectsBlocking: () => {
                            if (typeof Atomics === "undefined" || typeof SharedArrayBuffer === "undefined") return true;
                            const values = new Int32Array(new SharedArrayBuffer(4));
                            try {
                                Atomics.wait(values, 0, 0, 0);
                                return false;
                            } catch (error) {
                                return error instanceof TypeError;
                            }
                        },
                    };
                """.trimIndent().encodeToByteArray(),
                filename = "atomics-admission.mjs",
            )
            assertEquals("true", lane.callJson("rejectsBlocking", "[]"))
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
            val operationArmed = CompletableDeferred<Unit>()
            lane.onNextOperationArmedForTest { operationArmed.complete(Unit) }
            val invocation = async { lane.callJson("poisonAndSpin", "[]") }
            operationArmed.await()
            invocation.cancelAndJoin()

            assertEquals("\"clean\"", lane.callJson("state", "[]"))
        }
    }

    @Test
    fun cancellationDoesNotInterruptTheNextSerialOperation() = runBlocking {
        QuickJsRuntimeLane("late-cancellation", QuickJsRuntimeLimits(4L * 1024 * 1024, 1_000)).use { lane ->
            lane.evaluateModule(resetFixtureModule(), "late-cancellation.mjs")
            val operationArmed = CompletableDeferred<Unit>()
            lane.onNextOperationArmedForTest { operationArmed.complete(Unit) }
            val firstInvocation = async { lane.callJson("poisonAndSpin", "[]") }
            operationArmed.await()

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
            val operationArmed = CompletableDeferred<Unit>()
            lane.onNextOperationArmedForTest { operationArmed.complete(Unit) }
            val invocation = async {
                try {
                    lane.callJson("poisonAndSpin", "[]")
                    throw AssertionError("Expected QuickJS cancellation during close")
                } catch (error: QuickJsRuntimeException) {
                    error
                }
            }
            operationArmed.await()
            val queuedInvocation = async {
                try {
                    lane.callJson("state", "[]")
                    throw AssertionError("Expected a closed lane for the queued operation")
                } catch (error: QuickJsRuntimeException) {
                    error
                }
            }
            lane.close()
            assertEquals(QuickJsRuntimeError.CANCELLED, withTimeout(5_000) { invocation.await() }.error)
            assertEquals(QuickJsRuntimeError.CLOSED, withTimeout(5_000) { queuedInvocation.await() }.error)

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

    @Test
    fun returnsClosedWhenCloseWinsTheSubmissionRace() = runBlocking {
        val lane = QuickJsRuntimeLane("submission-race", QuickJsRuntimeLimits(4L * 1024 * 1024, 1_000))
        val submissionChecked = CountDownLatch(1)
        val continueSubmission = CountDownLatch(1)
        try {
            lane.onNextSubmissionCheckedForTest {
                submissionChecked.countDown()
                check(continueSubmission.await(5, TimeUnit.SECONDS)) { "Timed out waiting to continue submission" }
            }
            val invocation = async(Dispatchers.Default) {
                try {
                    lane.callJson("state", "[]")
                    throw AssertionError("Expected a closed lane")
                } catch (error: QuickJsRuntimeException) {
                    error
                }
            }
            assertTrue("Submission did not reach the close race seam", submissionChecked.await(5, TimeUnit.SECONDS))

            lane.close()
            continueSubmission.countDown()

            assertEquals(
                QuickJsRuntimeError.CLOSED,
                withTimeout(5_000) { invocation.await() }.error,
            )
        } finally {
            continueSubmission.countDown()
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
