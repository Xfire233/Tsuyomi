/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.shared.backup

import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import org.tsuyomi.shared.model.BookIdentity

/** Internal crash-recovery format. It is not a portable transfer format and is never exported. */
object ImportPlanCodec {
    private val json = Json { explicitNulls = false }

    fun encode(plan: ImportPlan): ByteArray {
        val transfer = TransferCodec.encode(
            TransferSnapshot(plan.sourceCreatedAt, plan.books, plan.shelves, plan.readerPreferences),
        ).toString(StandardCharsets.UTF_8)
        val transferElement = json.parseToJsonElement(transfer)
        val root = buildJsonObject {
            put("format", "tsuyomi-import-plan")
            put("version", 1)
            put("kind", plan.kind.name)
            put("sourceCreatedAt", plan.sourceCreatedAt.toString())
            put("transfer", transferElement)
            put("forceManualEInk", plan.forceManualEInk)
            put("searchHistory", buildJsonArray {
                plan.searchHistory.sortedWith(compareBy({ it.sourceId }, { it.query }, { it.lastUsedAt })).forEach { row ->
                    add(buildJsonObject { put("sourceId", row.sourceId); put("query", row.query); put("lastUsedAt", row.lastUsedAt.toString()) })
                }
            })
            put("browsingHistory", buildJsonArray {
                plan.browsingHistory.sortedWith(compareBy({ it.identity.sourceId }, { it.identity.remoteBookId }, { it.lastViewedAt })).forEach { row ->
                    add(buildJsonObject {
                        put("sourceId", row.identity.sourceId)
                        put("remoteBookId", row.identity.remoteBookId)
                        put("lastViewedAt", row.lastViewedAt.toString())
                    })
                }
            })
            put("warnings", buildJsonArray {
                plan.warnings.sortedBy(ImportWarning::ordinal).forEach { warning ->
                    add(buildJsonObject {
                        put("ordinal", warning.ordinal)
                        put("safeCode", warning.safeCode)
                        warning.safeRecordRef?.let { put("safeRecordRef", it) }
                        warning.fieldName?.let { put("fieldName", it) }
                        put("severity", warning.severity.name)
                    })
                }
            })
            put("smartCollections", buildJsonArray {
                plan.smartCollections.sortedBy(ImportedSmartCollection::collectionId).forEach { smart ->
                    add(buildJsonObject { put("collectionId", smart.collectionId); put("title", smart.title); put("astJson", smart.astJson) })
                }
            })
            put("subscriptionDrafts", buildJsonArray {
                plan.subscriptionDrafts.sortedBy(ImportedSubscriptionDraft::collectionId).forEach { draft ->
                    add(buildJsonObject {
                        put("collectionId", draft.collectionId)
                        put("title", draft.title)
                        put("mode", draft.mode)
                        put("sourceScopeJson", draft.sourceScopeJson)
                        put("queryJson", draft.queryJson)
                    })
                }
            })
        }
        return json.encodeToString(JsonElement.serializer(), root).toByteArray(StandardCharsets.UTF_8)
    }

    fun decode(bytes: ByteArray): Result<ImportPlan> = runCatching {
        val root = json.parseToJsonElement(bytes.toString(StandardCharsets.UTF_8)) as? JsonObject ?: error("invalid-root")
        require(root.keys == ROOT_FIELDS)
        require(root.string("format") == "tsuyomi-import-plan" && root.int("version") == 1)
        val kind = ImportKind.valueOf(requireNotNull(root.string("kind")))
        val sourceCreatedAt = Instant.parse(requireNotNull(root.string("sourceCreatedAt")))
        val transferBytes = json.encodeToString(JsonElement.serializer(), requireNotNull(root["transfer"])).toByteArray(StandardCharsets.UTF_8)
        val transfer = (TransferCodec.parse(transferBytes) as? ImportParseResult.Ready)?.plan ?: error("invalid-transfer")
        require(transfer.sourceCreatedAt == sourceCreatedAt)
        ImportPlan(
            kind = kind,
            sourceCreatedAt = sourceCreatedAt,
            books = transfer.books,
            shelves = transfer.shelves,
            readerPreferences = transfer.readerPreferences,
            forceManualEInk = root.bool("forceManualEInk") ?: false,
            searchHistory = root.array("searchHistory").orEmpty().map { element ->
                val row = element as? JsonObject ?: error("invalid-search-history")
                require(row.keys == setOf("sourceId", "query", "lastUsedAt"))
                SourceSearchHistory(requireNotNull(row.string("sourceId")), requireNotNull(row.string("query")), Instant.parse(requireNotNull(row.string("lastUsedAt"))))
            },
            browsingHistory = root.array("browsingHistory").orEmpty().map { element ->
                val row = element as? JsonObject ?: error("invalid-browsing-history")
                require(row.keys == setOf("sourceId", "remoteBookId", "lastViewedAt"))
                SourceBrowsingHistory(BookIdentity(requireNotNull(row.string("sourceId")), requireNotNull(row.string("remoteBookId"))), Instant.parse(requireNotNull(row.string("lastViewedAt"))))
            },
            warnings = root.array("warnings").orEmpty().map { element ->
                val row = element as? JsonObject ?: error("invalid-warning")
                require(row.keys.all { it in WARNING_FIELDS })
                ImportWarning(
                    ordinal = row.int("ordinal") ?: error("invalid-warning-ordinal"),
                    safeCode = requireNotNull(row.string("safeCode")),
                    safeRecordRef = row.string("safeRecordRef"),
                    fieldName = row.string("fieldName"),
                    severity = ImportSeverity.valueOf(requireNotNull(row.string("severity"))),
                )
            },
            smartCollections = root.array("smartCollections").orEmpty().map { element ->
                val row = element as? JsonObject ?: error("invalid-smart-collection")
                require(row.keys == setOf("collectionId", "title", "astJson"))
                ImportedSmartCollection(requireNotNull(row.string("collectionId")), requireNotNull(row.string("title")), requireNotNull(row.string("astJson")))
            },
            subscriptionDrafts = root.array("subscriptionDrafts").orEmpty().map { element ->
                val row = element as? JsonObject ?: error("invalid-subscription-draft")
                require(row.keys == setOf("collectionId", "title", "mode", "sourceScopeJson", "queryJson"))
                ImportedSubscriptionDraft(
                    requireNotNull(row.string("collectionId")),
                    requireNotNull(row.string("title")),
                    requireNotNull(row.string("mode")),
                    requireNotNull(row.string("sourceScopeJson")),
                    requireNotNull(row.string("queryJson")),
                )
            },
        )
    }

    private val ROOT_FIELDS = setOf(
        "format", "version", "kind", "sourceCreatedAt", "transfer", "forceManualEInk",
        "searchHistory", "browsingHistory", "warnings", "smartCollections", "subscriptionDrafts",
    )
    private val WARNING_FIELDS = setOf("ordinal", "safeCode", "safeRecordRef", "fieldName", "severity")
}

private fun JsonObject.bool(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull
private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
