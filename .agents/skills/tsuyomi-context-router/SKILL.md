---
name: tsuyomi-context-router
description: Routes ambiguous, cross-component, or conflicting Tsuyomi context requests to existing document and tool owners; stops before implementation or specialist execution.
---
<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi context routing

## When to use

Use when the task owner is unknown, current authority conflicts with history/state, a cold handoff lacks the relevant owner, or a cross-component task needs multiple distinct authorities selected. Re-evaluate at a new request, material scope change, or context recovery; not on every tool call.

## Do not use

Bypass for a precise known-file edit, a known symbol lookup, a bounded reproduction with owners already established, an external-only question, or a specialist workflow already in progress. Multiple files alone do not trigger routing. Never supersede mandatory bootstrap or specialist prerequisites.

## Required inputs

Use the current request and already-read repository context. WORKSPACE.md owns component/object classification, DOCUMENTATION.md owns document routing, and TOOLING.md owns tool dispatch. Read their relevant sections only if not already available and unchanged. Private memory and local handoff are supplemental; absence must not prevent repository-only navigation.

## Route

1. Identify the requested deliverable and unresolved questions, not a universal checklist. Classify each question using the governance objects in WORKSPACE.md; distinguish required behavior, current implementation, evidence, and authorization.
2. Find the matching responsibility rows in DOCUMENTATION.md. Search the registry for topic/owner terms first and read the surrounding rows. Do not load the full registry when a bounded section answers the question. If the registry itself is under audit, inspect every row required by that audit rather than sampling.
3. Use TOOLING.md only for an unresolved tool-dispatch question. Reuse already-known tool ownership. Follow a link only when its trigger or a necessary dependency matches this request; do not recursively traverse references for completeness theater.
4. Read the owning source's complete relevant section, including applicable global constraints and exceptions. A registry row, heading, generated summary, or search hit is navigation, never sufficient evidence of detailed behavior.
5. For implementation questions, use existing module boundaries to narrow scope, then semantic code tools for symbols where available and scoped search for text. Read implementation and the highest relevant observable evidence seam. Do not replace specialist test selection or run tests merely to select an owner.
6. Treat search misses as incomplete evidence: try domain synonyms, adjacent ownership rows, or one wider relevant scope. Never invent an authority or silently omit a facet to save tokens.
7. Resolve conflicts using the repository authority order. Historical relevance is not current authority; timestamps alone do not authorize behavior. If unresolved, report the exact conflicting sources and missing decision; do not implement either interpretation.
8. Stop routing once every requested facet has an owner, required section, downstream action, and known authorization boundary. Continue the actual user task immediately with the selected specialist/tool; do not yield just because routing finished.

## Minimal trace

Normally retain only a short in-session mapping: question -> owner/section -> reason -> next action. Do not repeat source bodies in a context packet. Expose this mapping only for a requested audit, unresolved conflict, handoff, or routing diagnosis. Distinguish not-read, unavailable, historical, and verified evidence.

## Freshness and maintenance

Read the live source; no persistent semantic tree, duplicate registry, embedding, server, or memory writer. After a relevant file changes, discard stale locations and re-read the affected section. Same repository bytes provide the same navigation inputs, not a guarantee of identical model judgment.

If routing fails, identify the existing owner's missing trigger, stale path, duplicate authority, or missing stop condition. Report a targeted repair; change governance only within the current authorization. Never automatically promote a conversation, memory, or inferred rule into a contract.

## Discovery boundary

A startup entry may point here, but must not duplicate these triggers or force a full routing pass on every request. A client without Skill discovery can read this file directly. Already-running sessions need an explicit instruction or reload to learn a newly installed Skill. No prompt-only mechanism guarantees compliance by every model.
