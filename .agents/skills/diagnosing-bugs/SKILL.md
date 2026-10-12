---
name: diagnosing-bugs
description: Diagnose reported failures, incorrect behavior or performance regressions using exact symptoms, bounded discriminating probes and a smallest root-cause fix.
---

<!-- SPDX-FileCopyrightText: 2026 Matt Pocock -->
<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: MIT -->

# Diagnosing bugs

Project adaptation of upstream `engineering/diagnosing-bugs`; pinned source and license: [THIRD_PARTY_NOTICES.md](../../../THIRD_PARTY_NOTICES.md). Paths below are repository-root-relative unless linked.

## When to use

Use for a reported failure, incorrect behavior or performance regression requiring root-cause diagnosis.

## Do not use

Do not rerun a check merely to challenge or reconfirm the user's observed failure; that observation is sufficient failure evidence. Do not substitute a nearby easier symptom.

## Required inputs

Record expected versus observed behavior, triggering input/action, environment/artifact identity and available error or timing evidence.

## 1. Capture the exact failure

Read the affected contract and trace the actual call path, including directly coupled state/lifecycle boundaries.

Complete with a symptom-specific pass/fail signal and the existing evidence that establishes failure. Missing reproduction access does not prevent static investigation.

## 2. Choose bounded feedback

Prefer an existing focused check or the smallest original-path runtime flow. Name the scope, exact signal, allowed probe/check, cost bound and stopping point before execution. Reuse evidence and setup; stop repeating a probe once it stops distinguishing causes. For intermittent failures, record uncertainty and discriminate timing/state hypotheses rather than launching open-ended stress or fuzz loops. For performance, use comparable scoped timing/profile evidence, not speculative logging.

For Android, follow the affected contribution guide, `tsuyomi-android/docs/process/QUALITY_GATES.md` and the existing Android review skill when UI evidence is involved. The owning gate determines device selection, scope increases, cleanup and human acceptance; additionally apply configured local `AGENTS.md` instructions when supplied. No automatic new/expanded tests, broad suites, additional AVDs or fuzz campaigns. A delegated diagnosis reports proposed checks to its parent instead of executing verification.

Complete when the next permitted probe has a discriminating signal; if execution is unavailable, label the evidence gap and continue reachable read-only analysis.

## 3. Discriminate causes

List the plausible competing hypotheses warranted by the evidence, ranked with a falsifiable prediction for each. Choose the cheapest permitted probe that separates them; change one variable at a time. Prefer targeted debugger/state inspection to extra logs. Update the ranking from observations, not confidence alone. Stop when a causal chain explains the exact symptom and the competing explanations are excluded to the available evidence level.

Keep credentials and private content out of commands, logs and reports. Use environment-held credentials, minimal redacted excerpts and ignored local artifacts. If temporary instrumentation is authorized, give it a unique removable marker; never log secrets and redact every shared excerpt before disclosure.

## 4. Fix and verify the original path

Apply the root implementation-economy rule: smallest root-cause change, all affected callers migrated, no exception suppression or input special case that merely hides the failure. Add or expand a regression test only with required scope authorization and a seam exercising the real failure pattern; otherwise explain the existing seam and coverage limit.

After the fix, the authorized verifier runs the bounded original-path runtime smoke against the actual changed artifact and checks the exact symptom plus directly coupled behavior. Static review or a nearby unit result is not runtime proof. Respect any assignment that reserves verification for the parent; hand it the exact flow instead of running it here.

## Completion and fallback

Remove only instrumentation and throwaway artifacts introduced for this diagnosis; retain user work. Report symptom, root cause with evidence, fix scope, exercised results and unverified paths. Declare the fix verified only when the original-path smoke supports it; missing runtime access means implemented/unverified, not success.

If probes cannot distinguish causes, state what was inspected, remaining hypotheses and the smallest missing prerequisite (access, a redacted artifact or instrumentation authorization). Ask only for unavailable information, finish independent investigation, and do not fabricate a reproduction or mask the failure to claim completion.
