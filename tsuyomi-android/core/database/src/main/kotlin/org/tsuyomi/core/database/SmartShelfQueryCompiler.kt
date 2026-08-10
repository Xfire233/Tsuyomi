/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import java.time.Instant
import org.tsuyomi.shared.smartshelf.MatchMode
import org.tsuyomi.shared.smartshelf.ProgressState
import org.tsuyomi.shared.smartshelf.SmartPredicate
import org.tsuyomi.shared.smartshelf.SmartRule
import org.tsuyomi.shared.smartshelf.SmartRuleNode

internal object SmartShelfQueryCompiler {
    private const val MAX_SQL_ARGUMENTS = 900

    fun compile(rule: SmartRule, now: Instant): SupportSQLiteQuery {
        requireWithinArgumentLimit(rule)
        val arguments = ArrayList<Any>(32)
        val predicate = compileNode(rule.root, arguments, now.epochSecond)
        check(arguments.size <= MAX_SQL_ARGUMENTS)
        return SimpleSQLiteQuery(
            """
            SELECT le.source_id, le.remote_book_id
            FROM library_entries le
            JOIN books b ON b.source_id = le.source_id AND b.remote_book_id = le.remote_book_id
            LEFT JOIN source_availability sa ON sa.source_id = le.source_id
            WHERE $predicate
            ORDER BY b.title COLLATE NOCASE, le.source_id, le.remote_book_id
            """.trimIndent(),
            arguments.toTypedArray(),
        )
    }

    fun requireWithinArgumentLimit(rule: SmartRule) {
        require(sqlArgumentCost(rule.root) <= MAX_SQL_ARGUMENTS) { "smart rule exceeds SQL argument limit" }
    }

    private fun sqlArgumentCost(node: SmartRuleNode): Int = when (node) {
        is SmartRuleNode.All -> node.children.sumOf(::sqlArgumentCost)
        is SmartRuleNode.Any -> node.children.sumOf(::sqlArgumentCost)
        is SmartRuleNode.Not -> sqlArgumentCost(node.child)
        is SmartRuleNode.Predicate -> when (val predicate = node.value) {
            is SmartPredicate.SourceIn -> predicate.sourceIds.size
            is SmartPredicate.InManualCollection -> predicate.collectionIds.size
            is SmartPredicate.TagContains -> predicate.tags.size * 2
            is SmartPredicate.FacetIn -> 1 + predicate.facetIds.size
            is SmartPredicate.TitleContains -> predicate.terms.size
            is SmartPredicate.AuthorContains -> predicate.terms.size
            is SmartPredicate.StatusIn -> predicate.statuses.size
            is SmartPredicate.RatingBetween -> (if (predicate.minimum != null) 1 else 0) + (if (predicate.maximum != null) 1 else 0)
            is SmartPredicate.AddedWithinDays, is SmartPredicate.LastReadWithinDays, is SmartPredicate.MetadataUpdatedWithinDays -> 1
            is SmartPredicate.ProgressIn, SmartPredicate.HasUnreadUpdate, SmartPredicate.HasSourceUpdate, SmartPredicate.IsDormantSource -> 0
        }
    }

    private fun compileNode(node: SmartRuleNode, arguments: MutableList<Any>, now: Long): String = when (node) {
        is SmartRuleNode.All -> node.children.joinToString(" AND ", "(", ")") { compileNode(it, arguments, now) }
        is SmartRuleNode.Any -> node.children.joinToString(" OR ", "(", ")") { compileNode(it, arguments, now) }
        is SmartRuleNode.Not -> "(NOT ${compileNode(node.child, arguments, now)})"
        is SmartRuleNode.Predicate -> compilePredicate(node.value, arguments, now)
    }

    private fun compilePredicate(predicate: SmartPredicate, arguments: MutableList<Any>, now: Long): String = when (predicate) {
        is SmartPredicate.SourceIn -> inClause("le.source_id", predicate.sourceIds, arguments)
        is SmartPredicate.InManualCollection -> {
            arguments.addAll(predicate.collectionIds)
            "EXISTS (SELECT 1 FROM manual_collection_memberships m WHERE m.source_id = le.source_id AND m.remote_book_id = le.remote_book_id AND m.collection_id IN (${placeholders(predicate.collectionIds.size)}))"
        }
        is SmartPredicate.TagContains -> combine(predicate.mode, predicate.tags) { tag -> tagClause(tag, arguments) }
        is SmartPredicate.FacetIn -> {
            arguments += predicate.sourceId
            val facets = predicate.facetIds.joinToString(" OR ", "(", ")") { facet -> remoteTagClause(facet, arguments) }
            "(le.source_id = ? AND $facets)"
        }
        is SmartPredicate.TitleContains -> combine(MatchMode.ANY, predicate.terms) { term -> likeClause("b.title", term, arguments) }
        is SmartPredicate.AuthorContains -> combine(MatchMode.ANY, predicate.terms) { term -> jsonArrayValueClause("b.authors_json", term, arguments) }
        is SmartPredicate.StatusIn -> inClause("LOWER(COALESCE(b.status, 'unknown'))", predicate.statuses.map { it.name.lowercase() }, arguments)
        is SmartPredicate.RatingBetween -> {
            val clauses = ArrayList<String>(2)
            predicate.minimum?.let { arguments += it; clauses += "le.rating >= ?" }
            predicate.maximum?.let { arguments += it; clauses += "le.rating <= ?" }
            clauses.joinToString(" AND ", "(", ")")
        }
        is SmartPredicate.AddedWithinDays -> withinDays("le.added_at_epoch_second", predicate.days, arguments, now)
        is SmartPredicate.LastReadWithinDays -> {
            arguments += now - predicate.days * 86_400L
            "EXISTS (SELECT 1 FROM reading_progress p WHERE p.source_id = le.source_id AND p.remote_book_id = le.remote_book_id AND p.updated_at_epoch_second >= ?)"
        }
        is SmartPredicate.MetadataUpdatedWithinDays -> withinDays("b.metadata_updated_at_epoch_second", predicate.days, arguments, now)
        is SmartPredicate.ProgressIn -> progressClause(predicate.states)
        SmartPredicate.HasUnreadUpdate -> "b.has_unread_update = 1"
        SmartPredicate.HasSourceUpdate -> "b.source_update_key IS NOT NULL"
        SmartPredicate.IsDormantSource -> "sa.available IS NOT 1"
    }

    private fun progressClause(states: Set<ProgressState>): String =
        states.joinToString(" OR ", "(", ")") { state ->
            val validProgress = validProgressClause("p")
            when (state) {
                ProgressState.UNSTARTED -> "NOT EXISTS (SELECT 1 FROM reading_progress p WHERE p.source_id = le.source_id AND p.remote_book_id = le.remote_book_id AND $validProgress)"
                ProgressState.READING -> "EXISTS (SELECT 1 FROM reading_progress p WHERE p.source_id = le.source_id AND p.remote_book_id = le.remote_book_id AND $validProgress AND COALESCE(p.book_progress, 0.0) < 1.0)"
                ProgressState.FINISHED -> "EXISTS (SELECT 1 FROM reading_progress p WHERE p.source_id = le.source_id AND p.remote_book_id = le.remote_book_id AND $validProgress AND p.book_progress = 1.0)"
            }
        }

    private fun validProgressClause(alias: String): String =
        """
        $alias.content_id IS NOT NULL AND length($alias.content_id) BETWEEN 1 AND 1024
        AND ($alias.revision IS NULL OR length($alias.revision) BETWEEN 1 AND 256)
        AND ($alias.block_id IS NULL OR length($alias.block_id) BETWEEN 1 AND 1024)
        AND ($alias.text_anchor_digest IS NULL OR (
            $alias.block_id IS NOT NULL
            AND length($alias.text_anchor_digest) = 64
            AND $alias.text_anchor_digest NOT GLOB '*[^0-9a-f]*'
        ))
        AND ($alias.character_offset IS NULL OR ($alias.block_id IS NOT NULL AND $alias.character_offset >= 0))
        AND ($alias.chapter_progress IS NULL OR (
            typeof($alias.chapter_progress) IN ('integer', 'real')
            AND $alias.chapter_progress >= 0.0 AND $alias.chapter_progress <= 1.0
        ))
        AND ($alias.book_progress IS NULL OR (
            typeof($alias.book_progress) IN ('integer', 'real')
            AND $alias.book_progress >= 0.0 AND $alias.book_progress <= 1.0
        ))
        AND (
            ($alias.block_id IS NOT NULL AND $alias.character_offset IS NOT NULL)
            OR ($alias.block_id IS NOT NULL AND $alias.text_anchor_digest IS NOT NULL)
            OR $alias.chapter_progress IS NOT NULL
            OR $alias.book_progress IS NOT NULL
        )
        """.trimIndent().replace("\n", " ")

    private fun tagClause(tag: String, arguments: MutableList<Any>): String {
        arguments += tag
        return "(EXISTS (SELECT 1 FROM local_book_tags t WHERE t.source_id = le.source_id AND t.remote_book_id = le.remote_book_id AND t.display_tag = ?) OR ${remoteTagClause(tag, arguments)})"
    }

    private fun remoteTagClause(tag: String, arguments: MutableList<Any>): String = jsonArrayValueClause("b.remote_tags_json", tag, arguments)

    private fun jsonArrayValueClause(column: String, value: String, arguments: MutableList<Any>): String {
        arguments += "%\"${escapeLike(value).replace("\"", "\\\"")}\"%"
        return "$column LIKE ? ESCAPE '\\'"
    }

    private fun likeClause(column: String, value: String, arguments: MutableList<Any>): String {
        arguments += "%${escapeLike(value)}%"
        return "$column LIKE ? ESCAPE '\\'"
    }

    private fun withinDays(column: String, days: Long, arguments: MutableList<Any>, now: Long): String {
        arguments += now - days * 86_400L
        return "$column >= ?"
    }

    private fun inClause(column: String, values: Collection<String>, arguments: MutableList<Any>): String {
        arguments.addAll(values)
        return "$column IN (${placeholders(values.size)})"
    }

    private fun combine(mode: MatchMode, values: Collection<String>, clause: (String) -> String): String =
        values.joinToString(if (mode == MatchMode.ALL) " AND " else " OR ", "(", ")", transform = clause)

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(",")

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
