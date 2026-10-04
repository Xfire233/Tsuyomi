<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Design decision memory and handoff workflow

## Purpose

Design decisions must survive model context loss without turning chat transcripts, local memory, issue drafts, or a local handoff into competing product contracts. This workflow connects four layers while keeping their authority separate. The handoff is one replace-in-place current snapshot; historical snapshots belong in an ignored immutable local archive linked from that current snapshot, never deleted or autoloaded.

| Layer | Purpose | Authority |
|---|---|---|
| Versioned repository contract | Active behavior, architecture, scope, executable review and regression protection | Binding within its owning domain |
| `to-spec` issue | Actionable future implementation package distilled from an established discussion | Proposal until reconciled into the owning contract |
| Mnemopi | Cross-conversation semantic recall of durable decisions, preferences, supersessions and links | Advisory; repository and current user win |
| `.local/ACTIVE_HANDOFF.md` | One current local snapshot of worktree provenance, proof, blockers and next action | Transient local evidence |

Raw prompts, full private transcripts, credentials, source content and local automation state are not public design records.

## Authority ownership

- Product-visible Android UI: `docs/design/UI_CONSTITUTION.md` active constraint spine and its owned detailed sections.
- UI evidence mechanics: `docs/design/UI_ATLAS.md` plus `.agents/skills/tsuyomi-android-review/review-node-catalog.json`; these prove behavior but cannot invent it.
- Scope and implementation authorization conditions: Phase documents; actual approval/authorization outcomes remain separate gates/checkpoints.
- Domain, security, protocol, persistence and migration invariants: ADR and architecture documents.
- Repository process and release evidence: `docs/process`.

A newer user correction supersedes conflicting repository text only after the owning authority is updated. Historical provenance remains history; it must be marked superseded or rewritten so that one active interpretation remains.

## Design-intake transaction

Treat every explicit requirement, correction, rejection, approval or supersession as one transaction:

1. **Capture** — normalize surface, state, trigger, visible result, forbidden alternative, rationale and superseded decision.
2. **Reconcile** — search the active authority and affected history for conflicts. Do not implement parallel interpretations.
3. **Persist authority** — update the owning versioned contract in the same work session, before or with implementation.
4. **Make executable** — update the affected Review Graph operation/check and the highest observable regression seam. Gesture and state-machine requirements require behavior tests; visual geometry requires device/layout evidence.
5. **Distill memory** — retain a concise Mnemopi fact with project, surface, decision, rationale, authority path and supersession. Invalidate stale memories rather than leaving contradictory facts active.
6. **Package when needed** — invoke `to-spec` for coherent future work according to the trigger policy below.
7. **Handoff** — refresh `.local/ACTIVE_HANDOFF.md` with worktree, authorization, proof, blockers and next action.

The transaction is incomplete if a requirement exists only in chat, only in Mnemopi, only in a to-spec issue, only in historical review material, or only in a screenshot.

## Mnemopi trigger policy

### Bootstrap

After obtaining task anchors (the requested surface, current branch/worktree when relevant, and the owning authority), query Mnemopi only for the relevant topic:

```text
Tsuyomi + component/surface + active decisions + supersessions + blockers
```

Recall remains mandatory when the capability is available; use `reflect` only when the question spans several features or asks for project/history synthesis. Verify every recalled claim against the current user statement and repository authority before acting. The current contract always wins.

A generic search miss means only that the query did not return a useful result; it does not establish that no decision exists. Search the owning authority or refine the bounded topic before concluding that no decision is recorded. Do not full-read Mnemopi on every recovery.

If the memory interface is absent or reports an unsupported capability, record it as unavailable and continue with repository evidence and the local handoff. Do not repeatedly invoke the same missing interface or synthesize a memory result. A transient transport error is not proof of permanent absence: use the existing tool-failure policy for a bounded retry or alternative, then declare the fallback and its reduced continuity.

### Checkpoint

Run a memory checkpoint:

- after each accepted design correction;
- after a coherent cluster of related decisions;
- before switching feature families;
- before a non-trivial final response;
- before context compaction or handoff.

Retain durable, reusable facts only. Do not store ephemeral todo state, build output, secrets, private content or unapproved speculation. When a rule changes, recall the old fact, read its full content, then invalidate or replace it through `memory_edit`.

## to-spec trigger policy

`TOOLING.md` owns generic `to-spec` dispatch, exclusions, fallback and health checks. This section owns Tsuyomi's repository-specific publication triggers so they are not independently redefined in Skills, agent configuration or issue templates.

The installed `to-spec` skill is explicitly authorized for this repository when:

- a feature/state machine/multi-route flow is ready for later implementation;
- a discussion establishes at least three related user stories or acceptance rules;
- implementation is deferred but another agent must be able to execute it;
- the user asks for a spec, record or handoff;
- a correction spans multiple contracts, modules or test seams.

A small local visual correction does not need an issue unless it creates a reusable rule or the user asks for one.

The issue target is `Xfire233/Tsuyomi`; the triage label is exactly `ready-for-agent`. Use the existing owning issue when one exists; do not create a duplicate. The installed skill template and highest existing observable test seam are mandatory. The spec must distinguish binding decisions from unresolved questions and must not contain volatile file paths or working code unless a compact state machine/schema is the clearest decision record.

Before publication:

1. verify GitHub CLI authentication;
2. verify the `ready-for-agent` label exists;
3. verify the discussion is sufficiently decided to avoid an interview;
4. reconcile binding decisions into their repository authority first.

If authentication or the label is unavailable, save the synthesized pending spec under `.local/to-spec/`, record the blocker in the active handoff, and never report the issue as published. After publication, retain the issue URL in Mnemopi and the active handoff. Do not publish private artifacts or private receipt contents.

Track the owning issue lifecycle precisely: **proposed** (decided package awaiting implementation), **implemented** (the described change exists in the worktree), **pending-human** (the required human decision or acceptance remains open), or **integrated** (the change is merged into its intended repository baseline). Reconciliation of binding decisions into their owning contract is a separate requirement, not proof of implementation or integration. Record scoped evidence and the relevant pinned revision or receipt reference; independent pending conditions may coexist. A triage label is not lifecycle state. No status transition auto-closes an issue, grants approval, or authorizes external effects.

## Empty-context recovery

Reuse already-loaded, unchanged mandatory bootstrap context: root `AGENTS.md`, `WORKSPACE.md` and this workflow. Establish the requested facts/actions and their owners; apply the existing router only where ownership remains unresolved.

Read the current handoff when present, then obtain bounded topic recall when available. Verify only relevant branch/worktree claims and artifact-dependent receipt claims. Read the owning contract and, when implementation or verification is requested, the affected code/Review Graph/tests. Mandatory specialist prerequisites still apply; recovery is not permission to execute downstream work.

Use the router's bounded retrieval method instead of loading full registries, contracts, histories or file trees. An unavailable capability already established for this request needs no repeated discovery. Open archived history only for a requested historical fact or a conflict that current authority, observed state and receipts cannot resolve; a stale handoff alone is not a reason to read its entire archive.

If sources disagree, use:

```text
current explicit user direction
> owning active repository contract
> accepted ADR/Phase/gate/process boundary
> code and executable evidence
> to-spec issue
> Mnemopi
> local handoff
```

Reconcile a handoff claim against the observed branch, worktree and cited receipt artifacts before relying on it. Old status text has no authority; a timestamp never grants approval. Treat device, emulator, browser, process, agent and other runtime handles as stale until observed. A fresh clone may recover versioned rules but cannot recreate private artifacts, credentials, devices, receipts, runtime state, or a dirty worktree; state those facts as unavailable rather than reconstructing them.

Update only stale lower-authority layers after reconciliation, and record each incomplete synchronization by layer. Recovery is idempotent: preserve completed external effects and their receipts, and do not rerun installation, publication, approval, signing, device, or other completed external effects merely to recover context.

### Missing evidence blocks only dependent work

Classify each requested action; missing evidence blocks only its dependents, never all continuation:

- **Read/analyze/draft locally:** available owners and scope suffice. Private APKs/receipts, memory and publication credentials are not prerequisites. Stop after answering; do not require them for unspecified future work.
- **Edit current source:** require change authorization, owning source/contract, applicable review and safe preservation of user work. Unknown overlapping changes, missing current source or a conflicting rule pause the affected edit, not independent research. An absent old APK does not itself block editing.
- **Identify/verify/install/accept an exact prior artifact:** require artifact-linked evidence for that claim/operation and separate deployment/human approval where applicable. Otherwise retain unknowns or block that operation. A new build is not recovery of the original.
- **Publish a spec:** require credentials and repository policy. Otherwise preserve the decided local spec with publication pending, reuse its existing owning issue, and continue independent authorized work; never claim publication or create a duplicate.

Recovery is complete when supported facts, unknowns and action-specific limits are stated, even if a downstream operation remains unavailable. It grants no new authority.

## Handoff contents and lifecycle

`.local/ACTIVE_HANDOFF.md` must remain one concise, replace-in-place current snapshot. A short target budget is a compression guideline, never a license to omit facts necessary for safe recovery. It must state:

- current goal and status;
- branch, base and worktree provenance, including dirty scope without overwriting user work;
- active authority pointers and each superseded rule;
- artifact and verification references, each with the scope they prove;
- pending synchronization for each affected layer: authority, Review Graph/regression evidence, Mnemopi, to-spec and handoff;
- capability availability and exact known environmental limits;
- active product boundaries, device/evidence ownership restrictions and explicit authorization exclusions;
- the exact next action.

When rolling over a checkpoint, preserve the previous snapshot intact in an ignored immutable local archive and link it from the current snapshot. Refresh the current fields in place instead of appending competing current-state sections; do not create a new archive for every minor status correction. Archive entries are history: never delete, rewrite or autoload them during ordinary recovery. Stable rules, regressions, review obligations and public decision summaries remain versioned.

## Failure rules

- Memory interface absent/unsupported: declare repository/handoff fallback and reduced continuity; never invent history or repeat the same unavailable call. Handle transient failures under the existing bounded tool-failure policy.
- Handoff missing/stale: recover only what observed branch/worktree, owning authority and cited receipts establish; record unavailable private or dirty state rather than reconstructing it.
- to-spec unavailable or unauthenticated: persist a pending local spec, record the exact prerequisite and lifecycle status, and do not duplicate an existing owning issue.
- Contract conflict: stop only implementation or claims that depend on the disputed rule; reconcile one active rule and its Review Graph/tests before that work resumes. Independent authorized research may continue.
- Current user correction conflicts with memory: user wins; update repository authority and invalidate stale memory in the same session.
- Incomplete persistence: record the unsynchronized layer and exact next safe action; recovery may finish the missing persistence but must not repeat completed external effects.
