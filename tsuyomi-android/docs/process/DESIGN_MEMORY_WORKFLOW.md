<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Design decision memory and handoff workflow

## Purpose

Design decisions must survive model context loss without turning chat transcripts, local memory, or issue drafts into competing product contracts. This workflow connects four layers while keeping their authority separate:

| Layer | Purpose | Authority |
|---|---|---|
| Versioned repository contract | Active behavior, architecture, scope, executable review and regression protection | Binding within its owning domain |
| `to-spec` issue | Actionable future implementation package distilled from an established discussion | Proposal until reconciled into the owning contract |
| Mnemopi | Cross-conversation semantic recall of durable decisions, preferences, supersessions and links | Advisory; repository and current user win |
| `.local/ACTIVE_HANDOFF.md` | Current branch, dirty worktree, devices, verification, blockers and next action | Transient local evidence |

Raw prompts, full private transcripts, credentials, source content and local automation state are not public design records.

## Authority ownership

- Product-visible Android UI: `docs/design/UI_CONSTITUTION.md` active constraint spine and its owned detailed sections.
- UI evidence mechanics: `docs/design/UI_ATLAS.md` and `ReviewNodeCatalog.kt`; these prove behavior but cannot invent it.
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

The transaction is incomplete if a requirement exists only in chat, only in Mnemopi, only in a to-spec issue, only in prototype code, or only in a screenshot.

## Mnemopi trigger policy

### Bootstrap

At the start of a new conversation or before revisiting an existing surface, query Mnemopi for:

```text
Tsuyomi + component/surface + active decisions + supersessions + blockers
```

Use `reflect` when a question spans several features or asks for project/history synthesis. Verify every recalled claim against the current user statement and repository authority before acting.

### Checkpoint

Run a memory checkpoint:

- after each accepted design correction;
- after a coherent cluster of related decisions;
- before switching feature families;
- before a non-trivial final response;
- before context compaction or handoff.

Retain durable, reusable facts only. Do not store ephemeral todo state, build output, secrets, private content or unapproved speculation. When a rule changes, recall the old fact, read its full content, then invalidate or replace it through `memory_edit`.

## to-spec trigger policy

The installed `to-spec` skill is explicitly authorized for this repository when:

- a feature/state machine/multi-route flow is ready for later implementation;
- a discussion establishes at least three related user stories or acceptance rules;
- implementation is deferred but another agent must be able to execute it;
- the user asks for a spec, record or handoff;
- a correction spans multiple contracts, modules or test seams.

A small local visual correction does not need an issue unless it creates a reusable rule or the user asks for one.

The issue target is `Xfire233/Tsuyomi`; the triage label is exactly `ready-for-agent`. The installed skill template and highest existing observable test seam are mandatory. The spec must distinguish binding decisions from unresolved questions and must not contain volatile file paths or working code unless a compact state machine/schema is the clearest decision record.

Before publication:

1. verify GitHub CLI authentication;
2. verify the `ready-for-agent` label exists;
3. verify the discussion is sufficiently decided to avoid an interview;
4. reconcile binding decisions into their repository authority first.

If authentication or the label is unavailable, save the synthesized pending spec under `.local/to-spec/`, record the blocker in the active handoff, and never report the issue as published. After publication, retain the issue URL in Mnemopi and the active handoff. Issue content remains proposed work until reconciled into the repository authority.

## Empty-context recovery

A new conversation resumes in this order:

1. root `AGENTS.md`;
2. `WORKSPACE.md` and this workflow;
3. `.local/ACTIVE_HANDOFF.md` when present;
4. Mnemopi recall/reflect for the affected area;
5. current worktree and branch;
6. affected authoritative contract, Review Graph node, implementation and tests.

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

Update stale lower-authority layers immediately, then continue without asking the user to repeat information already recoverable from these sources.

## Handoff contents

`.local/ACTIVE_HANDOFF.md` must remain concise and current:

- branch and immutable baseline when known;
- dirty worktree scope without overwriting user work;
- active product and authorization boundaries;
- latest accepted design corrections and their authority paths;
- exact verification completed and known environmental limits;
- device/evidence ownership restrictions;
- pending to-spec issue or publication blocker;
- next concrete action.

The handoff is local and ignored. Stable rules, regressions, review obligations and public decision summaries remain versioned.

## Failure rules

- Memory unavailable: continue from repository and handoff; report reduced continuity, never invent history.
- Handoff missing/stale: rebuild it from worktree, authority and Mnemopi before changing code.
- to-spec unavailable or unauthenticated: persist a pending local spec and report the exact prerequisite.
- Contract conflict: stop implementation, reconcile one active rule, update Review Graph/tests, then resume.
- Current user correction conflicts with memory: user wins; update repository authority and invalidate stale memory in the same session.
