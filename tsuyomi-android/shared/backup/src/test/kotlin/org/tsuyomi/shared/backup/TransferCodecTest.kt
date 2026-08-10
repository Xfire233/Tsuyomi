/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.shared.backup

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.tsuyomi.shared.model.BookIdentity

class TransferCodecTest {
    @Test
    fun deterministic_export_orders_books_sets_and_shelves() {
        val instant = Instant.parse("2026-08-08T00:00:00Z")
        val first = TransferBook(BookIdentity("org.tsuyomi.wenku8", "1"), "一", setOf("乙", "甲"), updatedAt = instant)
        val second = TransferBook(BookIdentity("org.tsuyomi.wenku8", "2"), "二", updatedAt = instant)
        val snapshot = TransferSnapshot(instant, listOf(second, first), listOf(TransferShelf("b", "B", position = 2), TransferShelf("a", "A", position = 1)))

        assertContentEquals(TransferCodec.encode(snapshot), TransferCodec.encode(snapshot.copy(library = listOf(first, second))))
        val parsed = assertIs<ImportParseResult.Ready>(TransferCodec.parse(TransferCodec.encode(snapshot)))
        assertEquals(listOf("1", "2"), parsed.plan.books.map { it.identity.remoteBookId })
        assertEquals(listOf("a", "b"), parsed.plan.shelves.map { it.id })
    }

    @Test
    fun duplicate_identity_is_fatal_before_mutation() {
        val bytes = """{"format":"tsuyomi-transfer","version":1,"createdAt":"2026-08-08T00:00:00Z","library":[{"identity":{"sourceId":"org.tsuyomi.wenku8","remoteBookId":"1"},"title":"A","updatedAt":"2026-08-08T00:00:00Z"},{"identity":{"sourceId":"org.tsuyomi.wenku8","remoteBookId":"1"},"title":"B","updatedAt":"2026-08-08T00:00:00Z"}],"shelves":[]}""".encodeToByteArray()
        assertEquals("duplicate-book-identity", assertIs<ImportParseResult.Fatal>(TransferCodec.parse(bytes)).safeCode)
    }

    @Test
    fun hikari_import_redacts_credentials_and_maps_all_supported_identities() {
        val bytes = """{"format":"hikari_novel_backup","schemaVersion":1,"createdAt":"2026-08-08T00:00:00Z","payload":{"auth":{"cookies":{"session":"secret"}},"bookshelf":{"folders":[{"id":"fav","name":"收藏"}],"items":[{"aid":"123","title":"文库"},{"aid":"esj:456","title":"ESJ"},{"aid":"yamibo:789","title":"百合会"}]},"readingData":{"readHistory":[{"aid":"123","cid":"9","location":12,"progress":0.5}]},"readerSettings":{"flow":"paged","fontScale":1.2}}}""".encodeToByteArray()
        val result = assertIs<ImportParseResult.Ready>(TransferCodec.parse(bytes))
        assertEquals(setOf("org.tsuyomi.wenku8", "org.tsuyomi.esjzone", "org.tsuyomi.yamibo"), result.plan.books.mapTo(hashSetOf()) { it.identity.sourceId })
        assertTrue(result.plan.warnings.any { it.safeCode == "credential-field-skipped" && it.fieldName == "payload.auth.cookies" })
        assertTrue(result.plan.warnings.none { it.toString().contains("secret") })
        assertEquals("paged", result.plan.readerPreferences?.flow)
    }
    @Test
    fun parser_enforces_byte_bound_and_strict_utf8_before_json() {
        assertEquals("transfer-too-large", assertIs<ImportParseResult.Fatal>(TransferCodec.parse(ByteArray(MAX_TRANSFER_BYTES + 1))).safeCode)
        assertEquals("invalid-utf8", assertIs<ImportParseResult.Fatal>(TransferCodec.parse(byteArrayOf(0xC3.toByte(), 0x28))).safeCode)
    }

    @Test
    fun normalized_import_plan_round_trips_recovery_sidecars_deterministically() {
        val instant = Instant.parse("2026-08-08T00:00:00Z")
        val plan = ImportPlan(
            kind = ImportKind.HIKARI_BACKUP,
            sourceCreatedAt = instant,
            books = listOf(TransferBook(BookIdentity("org.tsuyomi.wenku8", "1"), "书", updatedAt = instant)),
            shelves = listOf(TransferShelf("fav", "收藏")),
            readerPreferences = PortableReaderPreferences(flow = "paged"),
            forceManualEInk = true,
            searchHistory = listOf(SourceSearchHistory("org.tsuyomi.wenku8", "雾港", instant)),
            browsingHistory = listOf(SourceBrowsingHistory(BookIdentity("org.tsuyomi.wenku8", "1"), instant)),
            warnings = listOf(ImportWarning(0, "credential-field-skipped", fieldName = "payload.auth.cookies")),
            smartCollections = listOf(ImportedSmartCollection("smart", "智能", "{\"version\":1}")),
            subscriptionDrafts = listOf(ImportedSubscriptionDraft("draft", "草稿", "disabled", "[]", "{}")),
        )

        val encoded = ImportPlanCodec.encode(plan)
        val decoded = ImportPlanCodec.decode(encoded).getOrThrow()
        assertContentEquals(encoded, ImportPlanCodec.encode(decoded))
        assertEquals(plan.kind, decoded.kind)
        assertEquals(true, decoded.forceManualEInk)
        assertEquals(plan.searchHistory, decoded.searchHistory)
        assertEquals(plan.browsingHistory, decoded.browsingHistory)
        assertEquals(plan.smartCollections, decoded.smartCollections)
        assertEquals(plan.subscriptionDrafts, decoded.subscriptionDrafts)
    }

    @Test
    fun hikari_smart_rules_translate_locally_and_subscription_metadata_stays_disabled() {
        val bytes = """{"format":"hikari_novel_backup","schemaVersion":1,"createdAt":"2026-08-08T00:00:00Z","payload":{"appSettings":{"smartShelfMemberships":[{"id":"smart","name":"奇幻","matchAll":true,"conditions":[{"field":"tag","values":["奇幻"]},{"field":"dormant","excluded":true}]}],"smartShelfSyncMetadata":{"mode":"incremental"}},"bookshelf":{"items":[]}}}""".encodeToByteArray()
        val plan = assertIs<ImportParseResult.Ready>(TransferCodec.parse(bytes)).plan

        assertEquals(1, plan.smartCollections.size)
        assertTrue(plan.smartCollections.single().astJson.contains("isDormantSource"))
        assertEquals(1, plan.subscriptionDrafts.size)
        assertTrue(plan.subscriptionDrafts.all { it.mode == "disabled" })
        assertTrue(plan.warnings.any { it.safeCode == "subscription-imported-disabled" })
    }
    @Test
    fun bounded_export_matches_canonical_bytes_and_stops_at_limit_plus_one() {
        val snapshot = TransferSnapshot(
            Instant.parse("2026-08-08T00:00:00Z"),
            listOf(TransferBook(BookIdentity("org.tsuyomi.wenku8", "1"), "足够长的标题", updatedAt = Instant.EPOCH)),
            emptyList(),
        )
        val canonical = TransferCodec.encode(snapshot)

        assertContentEquals(canonical, requireNotNull(TransferCodec.encodeBounded(snapshot, canonical.size)))
        assertEquals(null, TransferCodec.encodeBounded(snapshot, canonical.size - 1))
        assertEquals(null, TransferCodec.encodeBounded(snapshot, 0))
        assertEquals(null, TransferCodec.encodeBounded(snapshot, 1))
        assertContentEquals(canonical, requireNotNull(TransferCodec.encodeBounded(snapshot, Int.MAX_VALUE)))
        assertFailsWith<IllegalArgumentException> { TransferCodec.encodeBounded(snapshot, -1) }
    }

    @Test
    fun malformed_semantic_text_anchor_is_fatal() {
        val bytes = """{"format":"tsuyomi-transfer","version":1,"createdAt":"2026-08-08T00:00:00Z","library":[{"identity":{"sourceId":"org.tsuyomi.wenku8","remoteBookId":"1"},"title":"A","updatedAt":"2026-08-08T00:00:00Z","progress":{"chapterId":"chapter","textAnchor":"${"A".repeat(64)}","updatedAt":"2026-08-08T00:00:00Z"}}],"shelves":[]}""".encodeToByteArray()

        assertEquals("invalid-book", assertIs<ImportParseResult.Fatal>(TransferCodec.parse(bytes)).safeCode)
    }

    @Test
    fun hikari_record_limits_are_enforced_per_section_and_in_aggregate() {
        assertHikariRecordBound(MAX_HIKARI_BOOKS) { count -> """{"bookshelf":{"items":${jsonArray(count) { index -> "{\"aid\":\"book-$index\",\"title\":\"Book\"}" }}}}""" }
        assertHikariRecordBound(MAX_HIKARI_FOLDERS) { count -> """{"bookshelf":{"folders":${jsonArray(count) { index -> "{\"id\":\"folder-$index\",\"name\":\"Folder\"}" }}}}""" }
        assertHikariRecordBound(MAX_HIKARI_PROGRESS) { count -> """{"readingData":{"readHistory":${jsonArray(count) { index -> "{\"aid\":\"progress-$index\",\"locatorJson\":\"{\\\"v\\\":1,\\\"kind\\\":\\\"chapter\\\",\\\"chapterId\\\":\\\"chapter-$index\\\",\\\"paragraphIndex\\\":0}\"}" }}}}""" }
        assertHikariRecordBound(MAX_HIKARI_SEARCH_HISTORY) { count -> """{"readingData":{"searchHistory":${jsonArray(count) { "{\"source\":\"wenku8\",\"query\":\"query-$it\"}" }}}}""" }
        assertHikariRecordBound(MAX_HIKARI_BROWSING_HISTORY) { count -> """{"readingData":{"browsingHistory":${jsonArray(count) { "{\"aid\":\"browse-$it\"}" }}}}""" }
        assertHikariRecordBound(MAX_HIKARI_SMART_RECORDS) { count -> """{"appSettings":{"smartShelfMemberships":${jsonArray(count) { index -> "{\"id\":\"smart-$index\",\"name\":\"Smart\",\"conditions\":[{\"field\":\"tag\",\"values\":[\"tag\"]}]}" }}}}""" }
        assertHikariRecordBound(MAX_HIKARI_SUBSCRIPTION_RECORDS) { count -> """{"appSettings":{"smartShelfSyncMetadata":${jsonObject(count) { index -> "\"subscription-$index\":{\"mode\":\"incremental\"}" }}}}""" }

        fun aggregatePayload(browsingCount: Int): ByteArray = hikari("""{"bookshelf":{"items":${jsonArray(MAX_HIKARI_BOOKS - 1) { index -> "{\"aid\":\"book-$index\",\"title\":\"Book\"}" }},"folders":${jsonArray(MAX_HIKARI_FOLDERS) { index -> "{\"id\":\"folder-$index\",\"name\":\"Folder\"}" }}},"readingData":{"readHistory":${jsonArray(MAX_HIKARI_PROGRESS) { index -> "{\"aid\":\"progress-$index\",\"locatorJson\":\"{\\\"v\\\":1,\\\"kind\\\":\\\"chapter\\\",\\\"chapterId\\\":\\\"chapter-$index\\\",\\\"paragraphIndex\\\":0}\"}" }},"browsingHistory":${jsonArray(browsingCount) { "{\"aid\":\"browse-$it\"}" }}}}""")
        val aggregateBelow = MAX_HIKARI_RECORDS - 1 - (MAX_HIKARI_BOOKS - 1) - MAX_HIKARI_FOLDERS - MAX_HIKARI_PROGRESS
        assertIs<ImportParseResult.Ready>(TransferCodec.parse(aggregatePayload(aggregateBelow)))
        assertEquals("record-limit", assertIs<ImportParseResult.Fatal>(TransferCodec.parse(aggregatePayload(aggregateBelow + 2))).safeCode)
    }

    @Test
    fun hikari_warning_limit_is_enforced_without_constructing_unbounded_warnings() {
        fun invalidBooks(count: Int): ByteArray = hikari("""{"bookshelf":{"items":${jsonArray(count) { "{}" }}}}""")

        assertIs<ImportParseResult.Ready>(TransferCodec.parse(invalidBooks(MAX_HIKARI_WARNINGS - 1)))
        assertEquals("warning-limit", assertIs<ImportParseResult.Fatal>(TransferCodec.parse(invalidBooks(MAX_HIKARI_WARNINGS + 1))).safeCode)
    }

    @Test
    fun hikari_normalized_sensitive_fields_are_redacted_without_values() {
        val values = setOf("cookie-secret", "account-secret", "cache-secret", "sync-secret", "font-secret", "image-secret", "tts-secret", "refresh-secret")
        val plan = assertIs<ImportParseResult.Ready>(TransferCodec.parse(hikari("""{"auth":{"CoOk_ies":"cookie-secret","Wenku8-User_Info":"account-secret"},"appSettings":{"Assisted_HTML_Cache":"cache-secret","Source-Sync_Configs":"sync-secret"},"readerSettings":{"Text_Family":"font-secret","Day-Bg_Image":"image-secret","TTS_Engine":"tts-secret","Device-Refresh_State":"refresh-secret"}}"""))).plan

        assertEquals(setOf("payload.auth.CoOk_ies", "payload.auth.Wenku8-User_Info", "payload.appSettings.Assisted_HTML_Cache", "payload.appSettings.Source-Sync_Configs", "payload.readerSettings.Text_Family", "payload.readerSettings.Day-Bg_Image", "payload.readerSettings.TTS_Engine", "payload.readerSettings.Device-Refresh_State"), plan.warnings.filter { it.safeCode == "credential-field-skipped" }.mapNotNullTo(linkedSetOf()) { it.fieldName })
        assertTrue(plan.warnings.none { warning -> values.any { it in warning.toString() } })
    }

    @Test
    fun hikari_shelf_parent_cycle_is_fatal_before_review() {
        val bytes = hikari("""{"bookshelf":{"folders":[{"id":"first","name":"First","parentId":"second"},{"id":"second","name":"Second","parentId":"first"}]}}""")

        assertEquals("shelf-parent-cycle", assertIs<ImportParseResult.Fatal>(TransferCodec.parse(bytes)).safeCode)
    }

    @Test
    fun hikari_valid_semantic_locator_precedes_cid_location_and_numeric_progress() {
        val bytes = hikari("""{"bookshelf":{"items":[{"aid":"1","title":"Book"}]},"readingData":{"readHistory":[{"aid":"1","cid":"legacy-chapter","location":7,"progress":0.25,"locatorJson":"{\"v\":1,\"kind\":\"chapter\",\"chapterId\":\"semantic-chapter\",\"paragraphIndex\":3,\"characterOffset\":12,\"chunkProgress\":0.5,\"fallbackProgress\":75}"}]}}""")
        val plan = assertIs<ImportParseResult.Ready>(TransferCodec.parse(bytes)).plan
        val progress = requireNotNull(plan.books.single().progress)

        assertEquals("semantic-chapter", progress.chapterId)
        assertEquals(12, progress.characterOffset)
        assertEquals(0.5, progress.chapterProgress)
        assertEquals(0.75, progress.bookProgress)
        assertTrue(requireNotNull(progress.textAnchor).matches(SHA_256))
        assertTrue(plan.warnings.none { it.safeCode == "reduced-progress-time-precision" })
    }

    @Test
    fun chapter_only_progress_is_rejected_before_review() {
        val bytes = """{"format":"tsuyomi-transfer","version":1,"createdAt":"2026-08-08T00:00:00Z","library":[{"identity":{"sourceId":"org.tsuyomi.wenku8","remoteBookId":"1"},"title":"A","updatedAt":"2026-08-08T00:00:00Z","progress":{"chapterId":"chapter","updatedAt":"2026-08-08T00:00:00Z"}}],"shelves":[]}""".encodeToByteArray()

        assertEquals("invalid-book", assertIs<ImportParseResult.Fatal>(TransferCodec.parse(bytes)).safeCode)
    }

    @Test
    fun transfer_local_tags_match_room_bounds() {
        val tooMany = (0..64).joinToString(prefix = "[", postfix = "]") { "\"tag-$it\"" }
        val tooLong = "x".repeat(65)
        fun transfer(tags: String) = """{"format":"tsuyomi-transfer","version":1,"createdAt":"2026-08-08T00:00:00Z","library":[{"identity":{"sourceId":"org.tsuyomi.wenku8","remoteBookId":"1"},"title":"A","updatedAt":"2026-08-08T00:00:00Z","localTags":$tags}],"shelves":[]}""".encodeToByteArray()

        assertEquals("invalid-book", assertIs<ImportParseResult.Fatal>(TransferCodec.parse(transfer(tooMany))).safeCode)
        assertEquals("invalid-book", assertIs<ImportParseResult.Fatal>(TransferCodec.parse(transfer("[\"$tooLong\"]"))).safeCode)
    }

    @Test
    fun hikari_oversized_identity_and_local_tags_fail_safely() {
        val oversizedAid = "a".repeat(1025)
        val tags = (0..64).joinToString(prefix = "[", postfix = "]") { "\"tag-$it\"" }
        val plan = assertIs<ImportParseResult.Ready>(
            TransferCodec.parse(
                hikari("""{"bookshelf":{"items":[{"aid":"$oversizedAid","title":"Invalid"},{"aid":"1","title":"Valid","localTags":$tags}]}}"""),
            ),
        ).plan

        assertEquals(listOf("1"), plan.books.map { it.identity.remoteBookId })
        assertEquals(64, plan.books.single().localTags.size)
        assertTrue(plan.warnings.any { it.safeCode == "invalid-book-record" })
        assertTrue(plan.warnings.any { it.safeCode == "invalid-tag-list" })
    }

    @Test
    fun hikari_fields_are_portable_before_normalized_plan_persistence() {
        val oversizedTitle = "t".repeat(4097)
        val oversizedShelfId = "s".repeat(129)
        val unicodeAid = "😀".repeat(600)
        val bytes = hikari(
            """{"bookshelf":{"folders":[{"id":"$oversizedShelfId","name":"Invalid"}],"items":[{"aid":"1","title":"$oversizedTitle"},{"aid":"2","title":"Valid","canonicalUrl":"relative/path","coverUrl":"not absolute"},{"aid":"$unicodeAid","title":"Unicode identity"}]},"readingData":{"readHistory":[{"aid":"2","cid":"chapter-only"}]}}""",
        )

        val plan = assertIs<ImportParseResult.Ready>(TransferCodec.parse(bytes)).plan

        assertEquals(listOf("2", unicodeAid), plan.books.map { it.identity.remoteBookId })
        val portableBook = plan.books.first()
        assertEquals(null, portableBook.canonicalUrl)
        assertEquals(null, portableBook.coverUrl)
        assertEquals(null, portableBook.progress)
        assertTrue(plan.shelves.isEmpty())
        assertTrue(plan.warnings.any { it.safeCode == "invalid-book-record" })
        assertTrue(plan.warnings.any { it.safeCode == "invalid-manual-shelf" })
        assertTrue(plan.warnings.any { it.safeCode == "invalid-progress-record" })
        assertTrue(plan.warnings.count { it.safeCode == "invalid-book-uri" } == 2)
        assertTrue(ImportPlanCodec.decode(ImportPlanCodec.encode(plan)).isSuccess)
    }

    private fun assertHikariRecordBound(limit: Int, payload: (Int) -> String) {
        assertIs<ImportParseResult.Ready>(TransferCodec.parse(hikari(payload(limit - 1))))
        assertEquals("record-limit", assertIs<ImportParseResult.Fatal>(TransferCodec.parse(hikari(payload(limit + 1)))).safeCode)
    }

    private fun hikari(payload: String): ByteArray = """{"format":"hikari_novel_backup","schemaVersion":1,"createdAt":"2026-08-08T00:00:00Z","payload":$payload}""".encodeToByteArray()

    private fun jsonArray(count: Int, item: (Int) -> String): String = (0 until count).joinToString(prefix = "[", postfix = "]", transform = item)

    private fun jsonObject(count: Int, entry: (Int) -> String): String = (0 until count).joinToString(prefix = "{", postfix = "}", transform = entry)
}
