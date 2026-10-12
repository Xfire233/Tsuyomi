---
name: retro
description: Automatically summarize lessons at explicit component-version completion or release wrap-up, or when the user requests a retrospective; update one evidence-linked local report rather than changing policy.
---

<!-- SPDX-FileCopyrightText: 2026 Matt Pocock -->
<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: MIT -->

# Version retrospective

Project adaptation of upstream `engineering/retro`; pinned source and license: [THIRD_PARTY_NOTICES.md](../../../THIRD_PARTY_NOTICES.md). Paths below are repository-root-relative unless linked.

## When to use

Run automatically when an explicit component-version completion or release wrap-up is established, or on a user retrospective request.

## Do not use

An ordinary commit, fix, build or CI completion is not a version-completion trigger. This procedure records lessons; it cannot declare a version accepted or authorize release.

## Required inputs

Resolve component, version and baseline/candidate identifiers from the requested scope, release/Phase records and actual receipts. Use an artifact digest or recorded worktree packet identity where appropriate; do not label dirty content as just `HEAD`. For a generic explicit “retro,” infer the version from scoped evidence first and ask only if multiple materially different scopes remain or no version is discoverable.

## 1. Identify the completed slice

Read [writing-for-agents](../writing-for-agents/SKILL.md), including its mechanics reference.

Use `(component, version, baseline/candidate identifier)` as the idempotency key. Read the existing `.local/retrospectives/<component>-<version>.md` if present. The same key and same evidence require no rewrite; supplement only newly available evidence, correction or action status. Different candidates for that version are clearly keyed sections in the same report, not extra files.

Complete with a unique scope and the existing report/action state, or an explicit unresolved identifier that blocks only report finalization.

## 2. Gather existing evidence

Use `WORKSPACE.md` and `DOCUMENTATION.md` to locate the existing owners, then read only relevant prior retrospectives, review reports, issue outcomes, CI receipts and available session records for this slice. Record source pointers, artifact/candidate identity, observed outcome and coverage limits. Private history is optional, not a prerequisite; missing data stays missing.

Do not rerun CI, builds, tests or devices to fill a retrospective. Do not reconstruct inaccessible session history or infer a pass from absent failures. Redact secrets and private content; link minimal local receipts instead of copying transcripts. Distinguish measured costs from estimates; make no fabricated token-saving or efficiency claims.

Complete when each conclusion can cite scoped evidence and uncovered stages are named.

## 3. Explain causes and review earlier actions

Separate **product defects** (wrong behavior/invariant) from **process failures** (why prevention, discovery, handoff or acceptance failed). A failure may have both, with separate evidence. Use only relevant root-cause categories: authority/scope drift, navigation/context access, implementation/design, verification/coverage, review/acceptance, tooling/environment, or handoff/release. Distinguish observed cause from inference and open hypothesis.

Review previous actions against current evidence: retain their accepted/proposed/deferred decision status and record implemented, effective, ineffective or not-yet-evaluated progress separately. Absence of a follow-up receipt is not proof of implementation or benefit. Inspect the existing check or policy owner before suggesting a new guardrail; a broken or unused existing check is different from a missing one.

Select only a few high-value actions, prioritized by evidenced recurrence, impact and effort. For each include evidence, root cause, concrete next action, existing owning document/module and responsible role, expected benefit, how benefit would be verified, and status **accepted**, **proposed** or **deferred**. “Accepted” requires actual authorization evidence; otherwise use “proposed.” A next-action link points to the existing owner, not a new competing policy or task system.

## 4. Update one ignored report

Create or update only `.local/retrospectives/<component>-<version>.md`, retaining other keyed candidate sections and prior evidence. Confirm the existing ignore policy covers the target before writing; if it does not, report the blocker rather than changing ignore/configuration policy implicitly. Use this compact structure:

- Scope and idempotency key; baseline/candidate identities.
- Coverage: sources reviewed, missing data and confidence limits.
- Outcomes: product defects versus process failures; evidence-linked causes and what worked.
- Previous actions: decision status, observed progress and effectiveness.
- Prioritized next actions: owner link, evidence, expected benefit, verification and accepted/proposed/deferred status.

Completion means one scoped report exists (or an unchanged matching report was reused), every finding/action is traceable, prior actions are reviewed and missing evidence is explicit. Return its path, key lessons and highest-priority proposed actions without implying gate approval.

## Boundaries and fallback

Proposals do not automatically mutate global configuration, tests, product documents, commits, pushes or releases. Even small resulting edits require separate scope authorization, then use writing-for-agents for agent-facing changes and the owning implementation workflow. An accepted idea alone is not execution permission.

If this version closeout also ends a Phase, [QUALITY_GATES.md §G7](../../../tsuyomi-android/docs/process/QUALITY_GATES.md#g7-retrospective) still owns the Phase retrospective and promotion of reusable conclusions. This local report does not satisfy or replace that public obligation; hand off accepted reusable actions to their existing versioned owners under the applicable authorization.

If source history is unavailable, finish a limited-evidence retrospective from reachable records and label the gaps; never invent history or rerun completed effects. If component/version remains genuinely ambiguous, ask the one scope question before naming a report. If report writing is unavailable or its ignored location is not established, return the evidence-backed report content and exact persistence blocker without claiming it was saved.
