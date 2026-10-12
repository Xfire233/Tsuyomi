---
name: domain-modeling
description: Resolve changing domain terms, relationships or invariants and record design decisions in Tsuyomi's existing contract owners; use during design interviews and authority reconciliation.
---

<!-- SPDX-FileCopyrightText: 2026 Matt Pocock -->
<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: MIT -->

# Domain modeling

Project adaptation of upstream `engineering/domain-modeling`; pinned source and license: [THIRD_PARTY_NOTICES.md](../../../THIRD_PARTY_NOTICES.md). Paths below are repository-root-relative unless linked.

## When to use

Use when domain terms, relationships or invariants change, including design interviews and reconciliation of accepted decisions.

## Do not use

Merely reading established terminology does not require a modeling exercise.

## Required inputs

Identify the changing concepts, affected component, current definitions and accepted decisions from the request and owning sources.

## 1. Find the owner

Use `WORKSPACE.md` for component boundaries and `DOCUMENTATION.md` for the unique document owner. For durable design decisions, apply `tsuyomi-android/docs/process/DESIGN_MEMORY_WORKFLOW.md`, especially authority ownership and the design-intake transaction. Read only affected contract, architecture/ADR and implementation sections.

Terminology belongs beside the invariant it explains: product-visible Android behavior in the UI Constitution, domain/storage/security invariants in their architecture or ADR owner, protocol language in its protocol owner. Phase scope and gate authorization remain separate. Existing code is evidence, not permission to override an active contract; proposed issues and memory are not parallel authorities.

This step is complete when each changing concept has an owner and the applicable authority order is known.

## 2. Sharpen only consequential ambiguity

Compare the user's terms with the owner's language and actual callers. For overloaded terms, propose a precise canonical meaning and distinguish neighboring concepts. Use a concrete edge-case scenario to test relationships, states and boundaries. Identify whether a discrepancy is stale code, a superseded rule or a genuine unresolved decision before asking the user.

Use [grill-with-docs](../grill-with-docs/SKILL.md)'s interview step only for remaining material choices; do not restart its workflow when already inside that interview. This step ends when affected terms and invariants are precise enough to distinguish allowed and forbidden behavior.

## 3. Persist at the existing authority

Record accepted definitions and decisions inline as they crystallize. Reconcile conflicting active text and follow the design-intake transaction for affected review/regression obligations, under the existing verification-scope authorization. Read [writing-for-agents](../writing-for-agents/SKILL.md) before editing agent-facing instructions.

Reuse the owning section; do not create a root `GLOSSARY.md`, glossary map, duplicate spec or second decision log by default. Create an ADR only when the decision is **hard to reverse**, **surprising without context**, and the result of an **actual tradeoff**. All three must hold. Use the component's existing ADR location and format, with alternatives and rationale; an ordinary definition or deterministic fix does not merit an ADR. New document ownership must follow `DOCUMENTATION.md`.

## Completion and fallback

Complete when every resolved concept has one active owning definition, superseded rules are reconciled, and affected review/regression obligations are updated or accurately identified as authorization-blocked. Report the owner paths and unresolved boundaries, not a duplicate model document. Optional memory follows repository persistence.

If sources conflict, apply the root authority order and the design workflow; if no unique owner can be established, use the existing context router and stop only the disputed edits. Missing tools or private history do not block repository-only modeling. Without edit authorization, present the exact proposed owner changes and state that persistence remains pending.
