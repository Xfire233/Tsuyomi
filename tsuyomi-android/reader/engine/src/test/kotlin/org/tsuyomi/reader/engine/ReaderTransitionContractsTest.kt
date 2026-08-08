// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.reader.engine

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator

class ReaderTransitionContractsTest {
    private val document = DocumentIdentity(
        sourceId = "org.tsuyomi.example",
        remoteBookId = "book-42",
        contentId = "chapter-7",
        revision = "revision-1",
    )

    @Test
    fun exactSettledCaptureIsNeverOverwrittenByDegradedCapture() {
        val epochs = epochs()
        val exact = snapshot(epochs = epochs, precision = LocatorPrecision.EXACT)
        val degraded = snapshot(
            epochs = epochs.copy(layoutKey = LayoutKey("rebuilding"), layoutEpoch = 2),
            precision = LocatorPrecision.DEGRADED,
            locator = locator(textAnchorDigest = null),
        )
        val cache = SettledPositionCache()

        assertEquals(CaptureAdmission.ACCEPTED, cache.record(exact))
        assertEquals(CaptureAdmission.REJECTED_BY_EXACT_PRECEDENCE, cache.record(degraded))
        assertEquals(exact, cache.current())
    }

    @Test
    fun compatibleSwitchRejectsCrossDocumentTarget() {
        val source = epochs()
        val differentDocument = DocumentIdentity(
            sourceId = document.sourceId,
            remoteBookId = document.remoteBookId,
            contentId = "chapter-8",
            revision = "revision-1",
        )
        val target = epochs(document = differentDocument, layoutEpoch = 2)
        val capture = snapshot(epochs = source)
        val cache = SettledPositionCache().also { it.record(capture) }

        val result = PresentationSwitchTransaction.begin(
            transactionId = 3,
            targetId = "paged",
            sourceEpochs = source,
            targetEpochs = target,
            captures = cache,
            mountedCapture = capture,
            mountedRendererIsRebuilding = false,
        )

        assertEquals(
            PresentationSwitchStart.Rejected(PresentationSwitchRejection.INCOMPATIBLE_DOCUMENT),
            result,
        )
    }

    @Test
    fun oldVisualWitnessCannotCommitPresentationSwitch() {
        val source = epochs()
        val target = source.copy(layoutKey = LayoutKey("paged"), layoutEpoch = 2)
        val transaction = startedTransaction(source, target)
        val oldWitness = VisualCommitWitness(
            ownerId = transaction.transactionId - 1,
            visualEpoch = 9,
            targetId = "paged",
            epochs = target,
        )

        val rejected = transaction.acceptVisualCommit(oldWitness, target)

        assertEquals(
            PresentationSwitchCommit.Rejected(PresentationSwitchRejection.STALE_OR_FOREIGN_WITNESS),
            rejected,
        )
        assertEquals(PresentationSwitchState.AWAITING_TARGET_VISUAL_COMMIT, transaction.state)
        val committed = transaction.acceptVisualCommit(
            VisualCommitWitness(transaction.transactionId, 10, "paged", target),
            target,
        )
        assertIs<PresentationSwitchCommit.Accepted>(committed)
        assertEquals(PresentationSwitchState.COMMITTED, transaction.state)
    }

    @Test
    fun presentationSwitchCancelsForLayoutSessionAndNavigationChanges() {
        val source = epochs()
        val target = source.copy(layoutKey = LayoutKey("paged"), layoutEpoch = 2)
        val staleTargets = listOf(
            target.copy(layoutEpoch = target.layoutEpoch + 1),
            target.copy(sessionEpoch = target.sessionEpoch + 1),
            target.copy(navigationEpoch = target.navigationEpoch + 1),
        )

        staleTargets.forEach { stale ->
            val transaction = startedTransaction(source, target)
            assertTrue(transaction.invalidateIfStale(stale))
            assertEquals(PresentationSwitchState.CANCELLED, transaction.state)
            assertEquals(
                PresentationSwitchCommit.Rejected(PresentationSwitchRejection.CANCELLED),
                transaction.acceptVisualCommit(
                    VisualCommitWitness(transaction.transactionId, 1, "paged", target),
                    stale,
                ),
            )
        }
    }

    @Test
    fun previewUsesOnlyLatestTargetAndCommitsOnlyOnRelease() {
        val epochs = epochs()
        val first = previewTarget("first", epochs.document)
        val latest = previewTarget("latest", epochs.document)
        val session = PreviewSession(
            sessionId = 12,
            epochs = epochs,
            plan = FrozenPreviewPlan(revision = 4, targets = listOf(first, latest)),
        )

        assertIs<PreviewState.Ready>(session.offerTarget(7, first))
        val firstWitness = previewWitness(session)
        assertIs<PreviewState.Ready>(session.offerTarget(7, latest))
        assertEquals(latest, session.latestTarget)
        assertEquals(
            PreviewRelease.Rejected(PreviewReleaseRejection.STALE_OR_FOREIGN_WITNESS),
            session.release(firstWitness, epochs, 4),
        )
        assertEquals(PreviewState.Ready(latest), session.state)

        val committed = session.release(previewWitness(session), epochs, 4)
        assertEquals(PreviewRelease.Committed(latest), committed)
        assertEquals(PreviewState.Released, session.state)
    }

    @Test
    fun oldWitnessCannotReleaseAAfterPreviewReturnsToEquivalentA() {
        val epochs = epochs()
        val firstA = previewTarget("a", epochs.document)
        val b = previewTarget("b", epochs.document)
        val secondA = firstA.copy()
        val session = PreviewSession(
            sessionId = 13,
            epochs = epochs,
            plan = FrozenPreviewPlan(revision = 5, targets = listOf(firstA, b)),
        )

        session.requestTarget(firstA)
        val oldWitness = previewWitness(session)
        val firstRequest = requireNotNull(session.currentRequest)
        session.requestTarget(b)
        session.requestTarget(secondA)
        val latestRequest = requireNotNull(session.currentRequest)

        assertEquals(firstA, secondA)
        assertEquals(firstA, latestRequest.target)
        assertEquals(1L, firstRequest.generation)
        assertEquals(3L, latestRequest.generation)
        assertEquals(
            PreviewRelease.Rejected(PreviewReleaseRejection.STALE_OR_FOREIGN_WITNESS),
            session.release(oldWitness, epochs, 5),
        )
        assertEquals(PreviewState.Ready(secondA), session.state)
        assertEquals(
            PreviewRelease.Committed(secondA),
            session.release(previewWitness(session), epochs, 5),
        )
    }

    @Test
    fun repeatedEquivalentTargetKeepsItsVisualWitnessCurrent() {
        val epochs = epochs()
        val target = previewTarget("target", epochs.document)
        val session = PreviewSession(
            sessionId = 14,
            epochs = epochs,
            plan = FrozenPreviewPlan(revision = 6, targets = listOf(target)),
        )

        session.requestTarget(target)
        val witness = previewWitness(session)
        val request = requireNotNull(session.currentRequest)
        session.requestTarget(target.copy())

        assertEquals(request, session.currentRequest)
        assertEquals(
            PreviewRelease.Committed(target),
            session.release(witness, epochs, 6),
        )
    }

    @Test
    fun previewOutsideFrozenPlanIsPreparingAndCannotCommit() {
        val epochs = epochs()
        val available = previewTarget("available", epochs.document)
        val outside = previewTarget("outside", epochs.document)
        val session = PreviewSession(
            sessionId = 5,
            epochs = epochs,
            plan = FrozenPreviewPlan(revision = 2, targets = listOf(available)),
        )

        assertEquals(PreviewState.Preparing(outside), session.requestTarget(outside))
        assertEquals(
            PreviewRelease.Rejected(PreviewReleaseRejection.TARGET_NOT_READY),
            session.release(previewWitness(session), epochs, 2),
        )
        assertTrue(session.cancel())
    }

    @Test
    fun previewInvalidatesForLayoutSessionAndNavigationChanges() {
        val frozen = epochs()
        val target = previewTarget("target", frozen.document)
        val staleEpochs = listOf(
            frozen.copy(layoutEpoch = frozen.layoutEpoch + 1),
            frozen.copy(sessionEpoch = frozen.sessionEpoch + 1),
            frozen.copy(navigationEpoch = frozen.navigationEpoch + 1),
        )

        staleEpochs.forEach { current ->
            val session = PreviewSession(
                sessionId = 6,
                epochs = frozen,
                plan = FrozenPreviewPlan(revision = 3, targets = listOf(target)),
            )
            session.requestTarget(target)
            assertTrue(session.invalidateIfStale(current, 3))
            assertEquals(PreviewState.Cancelled, session.state)
            assertEquals(
                PreviewRelease.Rejected(PreviewReleaseRejection.CANCELLED),
                session.release(previewWitness(session), current, 3),
            )
        }
    }

    private fun startedTransaction(
        source: ReaderEpochs,
        target: ReaderEpochs,
    ): PresentationSwitchTransaction {
        val capture = snapshot(epochs = source)
        val cache = SettledPositionCache().also { it.record(capture) }
        return assertIs<PresentationSwitchStart.Started>(
            PresentationSwitchTransaction.begin(
                transactionId = 19,
                targetId = "paged",
                sourceEpochs = source,
                targetEpochs = target,
                captures = cache,
                mountedCapture = capture,
                mountedRendererIsRebuilding = false,
            ),
        ).transaction
    }

    private fun epochs(
        document: DocumentIdentity = this.document,
        documentEpoch: Long = 1,
        sessionEpoch: Long = 2,
        layoutKey: LayoutKey = LayoutKey("scroll"),
        layoutEpoch: Long = 1,
        navigationEpoch: Long = 3,
    ) = ReaderEpochs(
        document = document,
        documentRevision = "revision-1",
        contentDigest = "b".repeat(64),
        documentEpoch = documentEpoch,
        sessionEpoch = sessionEpoch,
        layoutKey = layoutKey,
        layoutEpoch = layoutEpoch,
        navigationEpoch = navigationEpoch,
    )

    private fun snapshot(
        epochs: ReaderEpochs,
        precision: LocatorPrecision = LocatorPrecision.EXACT,
        locator: ReaderLocator? = locator(),
    ) = SettledPositionSnapshot(
        locator = locator,
        precision = precision,
        document = epochs.document,
        documentRevision = epochs.documentRevision,
        contentDigest = epochs.contentDigest,
        documentEpoch = epochs.documentEpoch,
        sessionEpoch = epochs.sessionEpoch,
        layoutKey = epochs.layoutKey,
        layoutRevision = epochs.layoutRevision,
        navigationEpoch = epochs.navigationEpoch,
        visualCommitWitness = VisualCommitWitness(
            ownerId = 1,
            visualEpoch = 1,
            targetId = "scroll",
            epochs = epochs,
        ),
    )

    private fun locator(textAnchorDigest: String? = "a".repeat(64)) = ReaderLocator(
        document = document,
        blockId = "block-5",
        textAnchorDigest = textAnchorDigest,
        characterOffset = 7,
        capturedAt = Instant.parse("2026-08-08T00:00:00Z"),
    )

    private fun previewTarget(id: String, document: DocumentIdentity) = PreviewTarget(
        id = id,
        locator = ReaderLocator(
            document = document,
            blockId = "block-$id",
            textAnchorDigest = "c".repeat(64),
            characterOffset = 0,
            capturedAt = Instant.parse("2026-08-08T00:00:00Z"),
        ),
    )

    private fun previewWitness(session: PreviewSession): PreviewVisualWitness {
        val request = requireNotNull(session.currentRequest)
        return PreviewVisualWitness(
            sessionId = session.sessionId,
            target = request.target,
            epochs = session.epochs,
            planRevision = session.plan.revision,
            requestGeneration = request.generation,
            visualEpoch = 1,
        )
    }
}
