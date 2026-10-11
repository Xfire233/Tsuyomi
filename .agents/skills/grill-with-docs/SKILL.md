---
name: grill-with-docs
description: Stress-test a plan when material product, security, data or acceptance decisions remain unresolved, or when asked to grill a design; record settled decisions in existing authorities.
---

<!-- SPDX-FileCopyrightText: 2026 Matt Pocock -->
<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: MIT -->

# Grill with docs

Project adaptation of upstream `engineering/grill-with-docs` and `productivity/grilling`; pinned source and license: [THIRD_PARTY_NOTICES.md](../../../THIRD_PARTY_NOTICES.md). Paths below are repository-root-relative unless linked.

## When to use

Automatically for genuine unresolved tradeoffs affecting user-visible behavior, security, data integrity or acceptance; also for an explicit design-grilling request.

## Do not use

Skip already-settled choices and small deterministic fixes. An explicit grill request may examine its requested design scope, but does not reopen settled decisions without a concrete conflict.

## Required inputs

Establish the requested outcome, affected owner, existing authorization and unresolved material choices from the request and repository evidence.

## 1. Establish the decision boundary

Read the affected owners through `WORKSPACE.md` and `DOCUMENTATION.md`, then the relevant contract and implementation. Investigate repository/tool facts before asking questions; separate observed behavior, binding requirements and unresolved choices. Apply root `AGENTS.md` implementation economy when supplied by the configured integration; an unavailable local instruction file does not block repository-only clarification.

Complete this step with the requested outcome, its owner, existing authorization and only the material unresolved decisions identified.

## 2. Interview the frontier

Map dependencies between decisions. A frontier contains only questions whose prerequisites are already known. Batch independent real tradeoffs in one numbered round; put dependent questions in a later round. For each question give concrete alternatives, their material consequence and a recommended answer with rationale. Word it so “yes” accepts that recommendation.

Ask for decisions, never facts available from the checkout or tools. Resolve factual prerequisites first; keep unrelated questions moving. Wait only for answers needed to choose an unresolved material branch. Use concrete scenarios to expose incompatible assumptions without inventing requirements.

After each answer, recompute the frontier. Stop asking once every material branch is resolved or explicitly deferred outside the authorized scope. Do not demand a final confirmation when the user has already authorized the work.

## 3. Reconcile inline and hand off

Read and apply [domain-modeling](../domain-modeling/SKILL.md) as terms or decisions settle. Update the existing owning authority in the same session, removing superseded rules; capture decisions inline rather than creating a parallel glossary, ADR or spec. Read [writing-for-agents](../writing-for-agents/SKILL.md) before agent-document edits. A conversation summary is not a new product contract.

Complete with concise decisions, changed authority paths, any explicitly deferred boundary and the next authorized action. Continue already-authorized implementation under its own workflow; the interview grants no new implementation or publication authority.

## Fallback

If an owner or prerequisite remains unavailable after bounded research, state the exact missing fact or conflicting rule and pause only dependent work. Ask the user only for a genuinely non-discoverable decision. If no material choice remains, bypass rounds and proceed within existing authorization; if edits are not authorized, report the precise proposed owner update without claiming it was persisted.
