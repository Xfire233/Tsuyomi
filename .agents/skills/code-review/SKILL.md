---
name: code-review
description: Review a branch, PR or scoped dirty-worktree packet on request or at final implementation-packet source review; assess Standards and Spec independently without changing or executing the candidate.
---

<!-- SPDX-FileCopyrightText: 2026 Matt Pocock -->
<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: MIT -->

# Code review

Project adaptation of upstream `engineering/code-review`; pinned source and license: [THIRD_PARTY_NOTICES.md](../../../THIRD_PARTY_NOTICES.md). Paths below are repository-root-relative unless linked.

## When to use

Use for requested branch, PR or work-in-progress review and for final implementation-packet source review before handoff under the repository workflow.

## Do not use

This is not implementation, runtime verification, human UI approval or release authorization. Review is read-only: no edits, builds, tests, formatters, device operations, publication or Git mutations. Existing CI/runtime receipts may be read but not rerun.

## Required inputs

Identify the requested files/feature, baseline and candidate identity, authority sources and available evidence receipts; derive discoverable inputs before asking the user.

## 1. Pin the review packet

Establish the user's requested files/feature and baseline plus candidate identity before reviewing. For branch/PR review, record the resolved base/head and comparison semantics (direct base or merge-base). For work in progress, include the requested committed, staged, unstaged and untracked content: `HEAD` alone is not the candidate. Record a bounded file inventory and snapshot/diff identity so both passes review the same bytes; preserve unrelated dirty user work. Do not stage, stash, reset or rewrite it.

If a supplied base is invalid, report it. If scope is empty, report that and stop. Infer discoverable scope from the user packet and repository evidence; ask only when materially different candidate boundaries remain. Intervening changes invalidate affected findings: identify them and repin the affected packet rather than silently reviewing mixed snapshots.

Complete this step with a reproducible scope, exclusions and available evidence receipts.

## 2. Identify the two authorities

- **Standards:** affected root/component contribution rules and applicable documented coding/architecture constraints, located through `WORKSPACE.md` and `DOCUMENTATION.md`; additionally apply root `AGENTS.md` implementation economy when supplied by the configured integration. Structural smells are labeled judgment calls, not automatic violations or mandates to abstract. Existing policy wins. Reuse lint/check receipts rather than blindly duplicating mechanical lint.
- **Spec:** latest explicit user requirements and the owning active contract, applicable Phase/gate boundaries and acceptance criteria. Fetch a cited issue/spec only when relevant; proposed issue text cannot override authoritative contracts. Surface unresolved authority conflicts instead of treating the lower-authority proposal as binding. If no spec is discoverable, report **Spec: unavailable** rather than inventing one.

Complete when each axis has its own source list and rule-to-scope mapping.

## 3. Run independent read-only passes

Each pass receives the pinned packet and its own authority list, without the other pass's findings. Standards checks concrete rule violations and consequential maintainability risks. Spec checks missing/partial acceptance criteria, incorrect behavior and unrequested scope. Both trace enough surrounding callers to support claims rather than judging isolated hunks.

Use two independent reviewers, one per axis, within the active integration's worker limit. Before launching, inspect the runtime registry and available slots; use the supported native agent/model routing, forbid child delegation, and verify the actual resolved model after launch under the integration's subagent policy. Do not assume a per-task model parameter exists. If capacity, registry/model visibility or delegation support is unavailable, run sequential, separately labeled Standards and Spec passes; disclose that they were not independently isolated agents. A child reviewer always works inline.

## 4. Report and stop

Keep **Standards** and **Spec** separate. Each actionable finding includes severity, file/line or packet hunk, the governing rule/acceptance criterion, observed evidence, consequence and a bounded recommendation. Distinguish proven defects, judgment calls and unverified hypotheses; omit unsupported claims. Count findings per axis, identify its highest risk and cross-reference overlapping findings without double-presenting one defect.

Complete with both passes accounted for, reviewed scope, evidence coverage, unverified runtime behavior and any unavailable authority. “No findings in reviewed scope” is not a runtime pass. Missing evidence blocks only the corresponding conclusion; return a partial-coverage report with the exact missing prerequisite instead of executing checks or modifying the candidate.
