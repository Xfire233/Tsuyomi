/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.shared.smartshelf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SmartShelfTest {
    @Test
    fun codec_is_stable_and_round_trips_typed_rule() {
        val rule = SmartRule(
            root = SmartRuleNode.All(
                listOf(
                    SmartRuleNode.Predicate(SmartPredicate.SourceIn(linkedSetOf("z.source", "a.source"))),
                    SmartRuleNode.Any(
                        listOf(
                            SmartRuleNode.Predicate(SmartPredicate.TagContains(MatchMode.ALL, setOf("奇幻", "完结"))),
                            SmartRuleNode.Not(SmartRuleNode.Predicate(SmartPredicate.IsDormantSource)),
                        ),
                    ),
                ),
            ),
        )

        val encoded = SmartRuleCodec.encode(rule)
        assertEquals(encoded, SmartRuleCodec.encode(SmartRuleCodec.decode(encoded).getOrThrow()))
        assertTrue(encoded.indexOf("a.source") < encoded.indexOf("z.source"))
    }

    @Test
    fun validator_enforces_depth_nodes_terms_and_code_points() {
        var deep: SmartRuleNode = SmartRuleNode.Predicate(SmartPredicate.HasUnreadUpdate)
        repeat(MAX_DEPTH) { deep = SmartRuleNode.Not(deep) }
        val tooManyNodes = SmartRuleNode.All(List(MAX_NODES) { SmartRuleNode.Predicate(SmartPredicate.HasSourceUpdate) })
        val longTerm = "𠮷".repeat(MAX_TEXT_CODE_POINTS + 1)

        assertTrue(SmartRuleValidator.validate(SmartRule(root = deep)).any { it.code == "max-depth" })
        assertTrue(SmartRuleValidator.validate(SmartRule(root = tooManyNodes)).any { it.code == "max-nodes" })
        assertTrue(
            SmartRuleValidator.validate(
                SmartRule(root = SmartRuleNode.Predicate(SmartPredicate.TitleContains(setOf(longTerm)))),
            ).any { it.code == "invalid-text-length" },
        )
    }

    @Test
    fun decoder_rejects_unknown_fields_and_invalid_ranges() {
        assertTrue(SmartRuleCodec.decode("""{"version":1,"rule":{"type":"predicate","field":"isDormantSource","extra":true}}""").isFailure)
        assertTrue(
            SmartRuleValidator.validate(
                SmartRule(root = SmartRuleNode.Predicate(SmartPredicate.RatingBetween(4.0, 2.0))),
            ).any { it.code == "invalid-rating-range" },
        )
    }

    @Test
    fun hikari_translator_disables_unsupported_remote_conditions() {
        assertIs<HikariSmartRuleTranslation.DisabledDraft>(
            HikariSmartRuleTranslator.translate(
                matchAll = true,
                conditions = listOf(HikariSmartCondition(field = "subscription", values = listOf("remote"))),
            ),
        )
        assertIs<HikariSmartRuleTranslation.Compatible>(
            HikariSmartRuleTranslator.translate(
                matchAll = false,
                conditions = listOf(HikariSmartCondition(field = "author", values = listOf("作者"))),
            ),
        )
    }
}
