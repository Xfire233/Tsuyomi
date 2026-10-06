<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Design decision authority and optional continuity

## Purpose

Design decisions must survive context loss without turning chat transcripts, private memory, issue drafts, or a local handoff into competing product contracts. Repository contracts and executable evidence remain authoritative. Optional integrations may help continuity, but contributors need no OMP account, personal memory service, local handoff, private archive, or workstation-specific tool.

| Layer | Purpose | Authority |
|---|---|---|
| Versioned repository contract | Active behavior, architecture, scope, executable review and regression protection | Binding within its owning domain |
| `to-spec` issue, when used | Actionable future implementation package distilled from an established discussion | Proposal until reconciled into the owning contract |
| Private semantic memory, when available | Cross-conversation recall of durable decisions, preferences, supersessions and links | Advisory; repository and current user win |
| Local handoff/archive, when used | Transient local snapshot of worktree provenance, proof, blockers and next action | Non-authoritative; unavailable to other contributors |

Raw prompts, full private transcripts, credentials, source content and local automation state are not public design records.

## Authority ownership

 - Product-visible Android UI: `docs/design/UI_CONSTITUTION.md` active constraint spine and its owned detailed sections.
 - UI evidence mechanics: `docs/design/UI_ATLAS.md` plus `.agents/skills/tsuyomi-android-review/review-node-catalog.json`; these prove behavior but cannot invent it. The skill is an optional interface to repository policy, not a contributor prerequisite.
 - Scope and implementation authorization conditions: Phase documents; actual approval/authorization outcomes remain separate gates/checkpoints.
 - Domain, security, protocol, persistence and migration invariants: ADR and architecture documents.
 - Repository process and release evidence: `docs/process`.

A newer user correction supersedes conflicting repository text only after the owning authority is updated. Historical provenance remains history; mark it superseded where needed so one active interpretation remains.

## Design-intake transaction

Treat every explicit requirement, correction, rejection, approval or supersession as one transaction:

1. **Capture** — normalize surface, state, trigger, visible result, forbidden alternative, rationale and superseded decision.
2. **Reconcile** — search the active authority and affected history for conflicts. Do not implement parallel interpretations.
3. **Persist authority** — update the owning versioned contract in the same work session, before or with implementation.
4. **Make executable** — update the affected repository review obligation and highest observable regression seam. Gesture and state-machine requirements require behavior tests; visual geometry requires device/layout evidence.
5. **Optional continuity** — when available and useful, update private memory or a local handoff; neither is required to complete a contribution.
6. **Package when appropriate** — use `to-spec` for coherent future work according to the optional trigger policy below.

The repository decision is incomplete if it exists only in chat, a private memory, a to-spec issue, historical review material, or a screenshot. The owning contract and applicable regression evidence must carry binding decisions.

## Optional private-memory integration

Private semantic memory is an optional continuity aid, not a required workflow. If a contributor has such a capability and chooses to use it, query only the relevant topic and verify recalled claims against the current user direction and repository authority. A search miss does not establish that no decision exists; inspect the owning contract instead. Do not store secrets, private content, build output, ephemeral todo state, or unapproved speculation. When private memory is unavailable, proceed using versioned repository evidence; do not fabricate history or repeatedly retry an unsupported interface.

Checkpoints after accepted corrections, coherent decision clusters, before changing feature families, and before handoff can help a contributor keep optional memory current. They are not contribution gates.

## Optional `to-spec` integration

`TOOLING.md` documents available `to-spec` tooling, dispatch and fallback. The repository-specific triggers below are guidance for contributors choosing to publish a future-work package; they do not require a skill installation or issue publication for routine changes.

Consider `to-spec` when a feature/state machine/multi-route flow is ready for later implementation; a discussion establishes at least three related user stories or acceptance rules; implementation is deferred for another agent; the user asks for a spec/record/handoff; or a correction spans multiple contracts, modules or test seams. A small local visual correction does not need an issue unless it creates a reusable rule or the user asks for one.

When publishing, target `Xfire233/Tsuyomi` and use the existing `ready-for-agent` triage label; reuse an existing owning issue rather than creating a duplicate. The installed skill template and highest existing observable test seam are mandatory for that publication workflow only. Distinguish binding decisions from unresolved questions; avoid volatile paths or working code unless a compact state machine/schema is the clearest record.

Before publication, verify GitHub CLI authentication, label availability, sufficient decision readiness, and reconciliation of binding decisions into repository authority. If credentials or the label are unavailable, either defer publication or retain a local draft if the contributor's environment supports it; do not claim publication or expose private artifacts. After publication, retain its URL in the issue/work handoff as useful; private memory/local handoff are not required.

Track issue state precisely when an issue is used: **proposed** (decided package awaiting implementation), **implemented** (the described change exists in the worktree), **pending-human** (the required human decision or acceptance remains open), or **integrated** (the change is merged into its intended repository baseline). Reconciliation of binding decisions into their owning contract is separate from implementation/integration. A triage label is not lifecycle state. No status transition auto-closes an issue, grants approval, or authorizes external effects.

## Repository-first recovery

For a new task, establish the requested scope and owning repository contract from the current checkout. Read relevant Phase/ADR/process rules and affected code, tests or review policy as needed. Root agent instructions and specialist skills apply only when supplied/available in the contributor's environment; they are not prerequisites to ordinary repository work. Optional memory/router tools may help locate context but cannot override repository authority.

When sources disagree, use:

```text
current explicit user direction
> owning active repository contract
> accepted ADR/Phase/gate/process boundary
> code and executable evidence
> to-spec issue
> private memory
> local handoff
```

An issue, private memory or handoff is not product authority. Verify claims about current branches, worktrees, receipts and runtime handles against observable state before relying on them. A fresh clone recovers versioned rules but cannot recreate private artifacts, credentials, devices, receipts or dirty worktree state; report those facts as unavailable rather than reconstructing them.

Missing evidence blocks only actions that depend on it. Local read/analyze/draft work needs no private APK, receipt, memory service or publication credential. Editing requires the owning source/contract, applicable project review, authorization and safe preservation of user work. Verifying or accepting an exact prior artifact requires artifact-linked evidence and separate approval where applicable. Publishing a spec requires credentials and repository policy; otherwise defer publication without claiming it occurred.

Recovery must preserve completed external effects and receipts. Do not rerun installation, publication, approval, signing, device or other completed effects merely to recover context. Reconcile any binding decision into its owning active contract and required regression protection; update optional lower-authority notes only when useful.

## Optional local handoff

Contributors who use a private work handoff may keep a concise, replace-in-place snapshot outside version control. It can note goal/status, branch and dirty scope, authority pointers, scoped evidence, known environmental limits, pending synchronization and next action. Do not commit private snapshots, prompts, credentials, local automation state or sensitive receipts. If archived locally, preserve history without rewriting or autoloading it; this is a local convenience, never a shared prerequisite.

## Optional integration failure handling

- Private memory unavailable or unsupported: proceed from repository contracts and observed evidence; do not fabricate history or repeatedly retry an unavailable capability.
- Handoff missing/stale: recover from the observed worktree and owning authority; do not reconstruct private state.
- `to-spec` unavailable or unauthenticated: defer publication or retain a local draft only if useful; never claim publication or duplicate an existing issue.
- Contract conflict: pause only implementation or claims that depend on the disputed rule; reconcile one active rule and its regression evidence before that work resumes.
- Current user correction conflicts with memory: user direction wins; update repository authority and regression protection in the same work session. Updating private memory is optional.
