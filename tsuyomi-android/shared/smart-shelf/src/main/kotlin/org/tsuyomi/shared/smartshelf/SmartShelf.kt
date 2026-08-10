/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.shared.smartshelf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

enum class MatchMode { ALL, ANY }
enum class ProgressState { UNSTARTED, READING, FINISHED }
enum class PublicationStatus { UNKNOWN, ONGOING, COMPLETED, HIATUS, CANCELLED }

sealed interface SmartRuleNode {
    data class All(val children: List<SmartRuleNode>) : SmartRuleNode
    data class Any(val children: List<SmartRuleNode>) : SmartRuleNode
    data class Not(val child: SmartRuleNode) : SmartRuleNode
    data class Predicate(val value: SmartPredicate) : SmartRuleNode
}

sealed interface SmartPredicate {
    data class SourceIn(val sourceIds: Set<String>) : SmartPredicate
    data class InManualCollection(val collectionIds: Set<String>) : SmartPredicate
    data class TagContains(val mode: MatchMode, val tags: Set<String>) : SmartPredicate
    data class FacetIn(val sourceId: String, val facetIds: Set<String>) : SmartPredicate
    data class TitleContains(val terms: Set<String>) : SmartPredicate
    data class AuthorContains(val terms: Set<String>) : SmartPredicate
    data class StatusIn(val statuses: Set<PublicationStatus>) : SmartPredicate
    data class RatingBetween(val minimum: Double?, val maximum: Double?) : SmartPredicate
    data class AddedWithinDays(val days: Long) : SmartPredicate
    data class LastReadWithinDays(val days: Long) : SmartPredicate
    data class MetadataUpdatedWithinDays(val days: Long) : SmartPredicate
    data class ProgressIn(val states: Set<ProgressState>) : SmartPredicate
    data object HasUnreadUpdate : SmartPredicate
    data object HasSourceUpdate : SmartPredicate
    data object IsDormantSource : SmartPredicate
}

data class SmartRule(val version: Int = CURRENT_VERSION, val root: SmartRuleNode) {
    init { require(version == CURRENT_VERSION) }
}

data class SmartRuleViolation(val code: String, val path: String)

object SmartRuleValidator {
    fun validate(rule: SmartRule): List<SmartRuleViolation> {
        val violations = mutableListOf<SmartRuleViolation>()
        var nodes = 0
        fun text(value: String, path: String) {
            val count = value.codePointCount(0, value.length)
            if (count !in 1..MAX_TEXT_CODE_POINTS) violations += SmartRuleViolation("invalid-text-length", path)
        }
        fun terms(values: Set<String>, path: String) {
            if (values.isEmpty() || values.size > MAX_TERMS) violations += SmartRuleViolation("invalid-term-count", path)
            values.forEachIndexed { index, value -> text(value, "$path[$index]") }
        }
        fun visit(node: SmartRuleNode, depth: Int, path: String) {
            nodes += 1
            if (depth > MAX_DEPTH) violations += SmartRuleViolation("max-depth", path)
            when (node) {
                is SmartRuleNode.All -> {
                    if (node.children.isEmpty()) violations += SmartRuleViolation("empty-group", path)
                    node.children.forEachIndexed { index, child -> visit(child, depth + 1, "$path.children[$index]") }
                }
                is SmartRuleNode.Any -> {
                    if (node.children.isEmpty()) violations += SmartRuleViolation("empty-group", path)
                    node.children.forEachIndexed { index, child -> visit(child, depth + 1, "$path.children[$index]") }
                }
                is SmartRuleNode.Not -> visit(node.child, depth + 1, "$path.child")
                is SmartRuleNode.Predicate -> when (val predicate = node.value) {
                    is SmartPredicate.SourceIn -> terms(predicate.sourceIds, "$path.sourceIds")
                    is SmartPredicate.InManualCollection -> terms(predicate.collectionIds, "$path.collectionIds")
                    is SmartPredicate.TagContains -> terms(predicate.tags, "$path.tags")
                    is SmartPredicate.FacetIn -> { text(predicate.sourceId, "$path.sourceId"); terms(predicate.facetIds, "$path.facetIds") }
                    is SmartPredicate.TitleContains -> terms(predicate.terms, "$path.terms")
                    is SmartPredicate.AuthorContains -> terms(predicate.terms, "$path.terms")
                    is SmartPredicate.StatusIn -> if (predicate.statuses.isEmpty() || predicate.statuses.size > MAX_TERMS) violations += SmartRuleViolation("invalid-term-count", "$path.statuses")
                    is SmartPredicate.RatingBetween -> {
                        val min = predicate.minimum
                        val max = predicate.maximum
                        if ((min == null && max == null) || min?.isFinite() == false || max?.isFinite() == false || min?.let { it !in 0.0..5.0 } == true || max?.let { it !in 0.0..5.0 } == true || (min != null && max != null && min > max)) {
                            violations += SmartRuleViolation("invalid-rating-range", path)
                        }
                    }
                    is SmartPredicate.AddedWithinDays -> if (predicate.days !in 0..MAX_WINDOW_DAYS) violations += SmartRuleViolation("invalid-time-window", path)
                    is SmartPredicate.LastReadWithinDays -> if (predicate.days !in 0..MAX_WINDOW_DAYS) violations += SmartRuleViolation("invalid-time-window", path)
                    is SmartPredicate.MetadataUpdatedWithinDays -> if (predicate.days !in 0..MAX_WINDOW_DAYS) violations += SmartRuleViolation("invalid-time-window", path)
                    is SmartPredicate.ProgressIn -> if (predicate.states.isEmpty() || predicate.states.size > MAX_TERMS) violations += SmartRuleViolation("invalid-term-count", "$path.states")
                    SmartPredicate.HasUnreadUpdate, SmartPredicate.HasSourceUpdate, SmartPredicate.IsDormantSource -> Unit
                }
            }
        }
        visit(rule.root, 1, "rule")
        if (nodes > MAX_NODES) violations += SmartRuleViolation("max-nodes", "rule")
        return violations.distinct()
    }

    fun requireValid(rule: SmartRule): SmartRule {
        val violations = validate(rule)
        require(violations.isEmpty()) { violations.joinToString { "${it.code}:${it.path}" } }
        return rule
    }
}

object SmartRuleCodec {
    private val json = Json { explicitNulls = false }

    fun encode(rule: SmartRule): String {
        SmartRuleValidator.requireValid(rule)
        val root = buildJsonObject {
            put("version", rule.version)
            put("rule", encodeNode(rule.root))
        }
        return json.encodeToString(JsonElement.serializer(), root)
    }

    fun decode(value: String): Result<SmartRule> = runCatching {
        val root = json.parseToJsonElement(value) as? JsonObject ?: error("invalid-root")
        require(root.keys == setOf("version", "rule"))
        val version = root.long("version")?.toInt() ?: error("invalid-version")
        require(version == CURRENT_VERSION)
        SmartRuleValidator.requireValid(SmartRule(version, decodeNode(root.obj("rule") ?: error("missing-rule"))))
    }

    private fun encodeNode(node: SmartRuleNode): JsonObject = when (node) {
        is SmartRuleNode.All -> group("all", node.children)
        is SmartRuleNode.Any -> group("any", node.children)
        is SmartRuleNode.Not -> buildJsonObject { put("type", "not"); put("child", encodeNode(node.child)) }
        is SmartRuleNode.Predicate -> encodePredicate(node.value)
    }

    private fun group(type: String, children: List<SmartRuleNode>) = buildJsonObject {
        put("type", type)
        put("children", buildJsonArray { children.forEach { add(encodeNode(it)) } })
    }

    private fun encodePredicate(predicate: SmartPredicate): JsonObject = buildJsonObject {
        put("type", "predicate")
        when (predicate) {
            is SmartPredicate.SourceIn -> { put("field", "source"); putStrings("values", predicate.sourceIds) }
            is SmartPredicate.InManualCollection -> { put("field", "manualCollection"); putStrings("values", predicate.collectionIds) }
            is SmartPredicate.TagContains -> { put("field", "tag"); put("match", predicate.mode.name.lowercase()); putStrings("values", predicate.tags) }
            is SmartPredicate.FacetIn -> { put("field", "facet"); put("sourceId", predicate.sourceId); putStrings("values", predicate.facetIds) }
            is SmartPredicate.TitleContains -> { put("field", "title"); putStrings("values", predicate.terms) }
            is SmartPredicate.AuthorContains -> { put("field", "author"); putStrings("values", predicate.terms) }
            is SmartPredicate.StatusIn -> { put("field", "status"); putStrings("values", predicate.statuses.mapTo(sortedSetOf()) { it.name.lowercase() }) }
            is SmartPredicate.RatingBetween -> { put("field", "rating"); predicate.minimum?.let { put("minimum", it) }; predicate.maximum?.let { put("maximum", it) } }
            is SmartPredicate.AddedWithinDays -> { put("field", "addedWithinDays"); put("days", predicate.days) }
            is SmartPredicate.LastReadWithinDays -> { put("field", "lastReadWithinDays"); put("days", predicate.days) }
            is SmartPredicate.MetadataUpdatedWithinDays -> { put("field", "metadataUpdatedWithinDays"); put("days", predicate.days) }
            is SmartPredicate.ProgressIn -> { put("field", "progress"); putStrings("values", predicate.states.mapTo(sortedSetOf()) { it.name.lowercase() }) }
            SmartPredicate.HasUnreadUpdate -> put("field", "hasUnreadUpdate")
            SmartPredicate.HasSourceUpdate -> put("field", "hasSourceUpdate")
            SmartPredicate.IsDormantSource -> put("field", "isDormantSource")
        }
    }

    private fun decodeNode(value: JsonObject): SmartRuleNode {
        val type = value.string("type") ?: error("missing-type")
        return when (type) {
            "all", "any" -> {
                require(value.keys == setOf("type", "children"))
                val children = value.array("children")?.map { decodeNode(it as? JsonObject ?: error("invalid-child")) } ?: error("missing-children")
                if (type == "all") SmartRuleNode.All(children) else SmartRuleNode.Any(children)
            }
            "not" -> { require(value.keys == setOf("type", "child")); SmartRuleNode.Not(decodeNode(value.obj("child") ?: error("missing-child"))) }
            "predicate" -> SmartRuleNode.Predicate(decodePredicate(value))
            else -> error("unknown-node")
        }
    }

    private fun decodePredicate(value: JsonObject): SmartPredicate = when (val field = value.string("field") ?: error("missing-field")) {
        "source" -> { requireKeys(value, "type", "field", "values"); SmartPredicate.SourceIn(value.strings("values")) }
        "manualCollection" -> { requireKeys(value, "type", "field", "values"); SmartPredicate.InManualCollection(value.strings("values")) }
        "tag" -> { requireKeys(value, "type", "field", "match", "values"); SmartPredicate.TagContains(MatchMode.valueOf(requireNotNull(value.string("match")).uppercase()), value.strings("values")) }
        "facet" -> { requireKeys(value, "type", "field", "sourceId", "values"); SmartPredicate.FacetIn(requireNotNull(value.string("sourceId")), value.strings("values")) }
        "title" -> { requireKeys(value, "type", "field", "values"); SmartPredicate.TitleContains(value.strings("values")) }
        "author" -> { requireKeys(value, "type", "field", "values"); SmartPredicate.AuthorContains(value.strings("values")) }
        "status" -> { requireKeys(value, "type", "field", "values"); SmartPredicate.StatusIn(value.strings("values").mapTo(linkedSetOf()) { PublicationStatus.valueOf(it.uppercase()) }) }
        "rating" -> { require(value.keys.all { it in setOf("type", "field", "minimum", "maximum") }); SmartPredicate.RatingBetween(value.double("minimum"), value.double("maximum")) }
        "addedWithinDays" -> days(value, SmartPredicate::AddedWithinDays)
        "lastReadWithinDays" -> days(value, SmartPredicate::LastReadWithinDays)
        "metadataUpdatedWithinDays" -> days(value, SmartPredicate::MetadataUpdatedWithinDays)
        "progress" -> { requireKeys(value, "type", "field", "values"); SmartPredicate.ProgressIn(value.strings("values").mapTo(linkedSetOf()) { ProgressState.valueOf(it.uppercase()) }) }
        "hasUnreadUpdate" -> flag(value, SmartPredicate.HasUnreadUpdate)
        "hasSourceUpdate" -> flag(value, SmartPredicate.HasSourceUpdate)
        "isDormantSource" -> flag(value, SmartPredicate.IsDormantSource)
        else -> error("unknown-field:$field")
    }

    private fun days(value: JsonObject, factory: (Long) -> SmartPredicate): SmartPredicate {
        requireKeys(value, "type", "field", "days")
        return factory(value.long("days") ?: error("missing-days"))
    }

    private fun flag(value: JsonObject, predicate: SmartPredicate): SmartPredicate {
        requireKeys(value, "type", "field")
        return predicate
    }
}

data class HikariSmartCondition(
    val field: String,
    val values: List<String> = emptyList(),
    val matchAll: Boolean = false,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val days: Long? = null,
    val excluded: Boolean = false,
)

sealed interface HikariSmartRuleTranslation {
    data class Compatible(val rule: SmartRule) : HikariSmartRuleTranslation
    data class DisabledDraft(val warningCode: String) : HikariSmartRuleTranslation
}

object HikariSmartRuleTranslator {
    fun translate(matchAll: Boolean, conditions: List<HikariSmartCondition>): HikariSmartRuleTranslation =
        runCatching { translateChecked(matchAll, conditions) }
            .getOrElse { HikariSmartRuleTranslation.DisabledDraft("invalid-smart-rule") }

    private fun translateChecked(matchAll: Boolean, conditions: List<HikariSmartCondition>): HikariSmartRuleTranslation {
        if (conditions.isEmpty()) return HikariSmartRuleTranslation.DisabledDraft("empty-smart-rule")
        val nodes = conditions.map { condition ->
            val predicate = when (condition.field.lowercase()) {
                "source" -> SmartPredicate.SourceIn(condition.values.toSet())
                "folder", "manualcollection" -> SmartPredicate.InManualCollection(condition.values.toSet())
                "tag" -> SmartPredicate.TagContains(if (condition.matchAll) MatchMode.ALL else MatchMode.ANY, condition.values.toSet())
                "title" -> SmartPredicate.TitleContains(condition.values.toSet())
                "author" -> SmartPredicate.AuthorContains(condition.values.toSet())
                "status", "update" -> SmartPredicate.StatusIn(condition.values.mapTo(linkedSetOf()) { PublicationStatus.valueOf(it.uppercase()) })
                "rating" -> SmartPredicate.RatingBetween(condition.minimum, condition.maximum)
                "added" -> SmartPredicate.AddedWithinDays(condition.days ?: return HikariSmartRuleTranslation.DisabledDraft("invalid-smart-window"))
                "lastread", "date" -> SmartPredicate.LastReadWithinDays(condition.days ?: return HikariSmartRuleTranslation.DisabledDraft("invalid-smart-window"))
                "metadataupdated" -> SmartPredicate.MetadataUpdatedWithinDays(condition.days ?: return HikariSmartRuleTranslation.DisabledDraft("invalid-smart-window"))
                "progress" -> SmartPredicate.ProgressIn(condition.values.mapTo(linkedSetOf()) { ProgressState.valueOf(it.uppercase()) })
                "unread" -> SmartPredicate.HasUnreadUpdate
                "sourceupdate" -> SmartPredicate.HasSourceUpdate
                "dormant" -> SmartPredicate.IsDormantSource
                "section", "subscription" -> return HikariSmartRuleTranslation.DisabledDraft("unsupported-smart-condition")
                else -> return HikariSmartRuleTranslation.DisabledDraft("unknown-smart-condition")
            }
            val node = SmartRuleNode.Predicate(predicate)
            if (condition.excluded) SmartRuleNode.Not(node) else node
        }
        val root = if (matchAll) SmartRuleNode.All(nodes) else SmartRuleNode.Any(nodes)
        val rule = SmartRule(root = root)
        return if (SmartRuleValidator.validate(rule).isEmpty()) HikariSmartRuleTranslation.Compatible(rule)
        else HikariSmartRuleTranslation.DisabledDraft("invalid-smart-rule")
    }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putStrings(name: String, values: Set<String>) {
    put(name, buildJsonArray { values.sorted().forEach { add(JsonPrimitive(it)) } })
}
private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull
private fun JsonObject.double(name: String): Double? = (this[name] as? JsonPrimitive)?.doubleOrNull
private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray
private fun JsonObject.strings(name: String): Set<String> = array(name)?.mapTo(linkedSetOf()) { (it as? JsonPrimitive)?.contentOrNull ?: error("invalid-string") } ?: error("missing-values")
private fun requireKeys(value: JsonObject, vararg keys: String) = require(value.keys == keys.toSet())

const val CURRENT_VERSION = 1
const val MAX_DEPTH = 8
const val MAX_NODES = 128
const val MAX_TEXT_CODE_POINTS = 256
const val MAX_TERMS = 64
const val MAX_WINDOW_DAYS = 36_500L
