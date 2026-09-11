<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi monorepo workspace

This directory is one Git repository containing two independently versioned components. Source extensions are maintained separately in the public [Chachaanteng/tsuyomi-extensions](https://github.com/Chachaanteng/tsuyomi-extensions) repository.

| Component | Responsibility |
|---|---|
| `tsuyomi-android` | Kotlin/Compose Android host application. |
| `tsuyomi-protocol` | Platform-neutral JSON Schemas, fixtures, Host API, transfer/backup contracts, and conformance rules. |

## Local prerequisites

- JDK 17.
- Android SDK with API 37, platform-tools, emulator, and `system-images;android-29;default;x86_64`.
- Node.js and npm versions accepted by CI.
- Python with `python -m reuse` 6.2.0.

Set `ANDROID_SDK_ROOT` (or `ANDROID_HOME`) locally, then run `tsuyomi-android/tools/Doctor.ps1`. The script validates tools and generates ignored `local.properties`; no user-specific SDK path belongs in version control.

## Android UI review workflow

Android UI, navigation, interaction, display-profile, accessibility, screenshot and Review Graph work starts with `.agents/skills/tsuyomi-android-review/SKILL.md`; its adjacent `review-policy.json` alone selects active/deferred profiles. Product contracts and human approval authority remain in Android design/Phase documents and explicit gate outcomes.

## Development tooling

[`TOOLING.md`](TOOLING.md) is the repository inventory and dispatch policy for development instructions, Skills, MCP servers, plugins, Android device tooling, and their discovery checks. User Skills have one canonical source under `~/.agents/skills`; the repository-owned Android review Skill lives under `.agents/skills`. Provider-specific links are generated rather than copied.

[`DOCUMENTATION.md`](DOCUMENTATION.md) is the canonical responsibility registry for first-party documents. It states each document's purpose, trigger, method, scope/exclusions, completion/stop condition and lifecycle. A link is not itself a trigger: read only the owning document rows that match the task.

At each new request, material scope change or context recovery, check whether ownership is unresolved: unknown owners, authority/history conflicts, missing handoff ownership, or cross-component questions needing distinct authorities trigger [tsuyomi-context-router](.agents/skills/tsuyomi-context-router/SKILL.md). Read the Skill once when triggered; it owns the routing algorithm and exclusions. Known-file/symbol tasks and an already-running specialist workflow bypass routing. Do not reload unchanged context or build a plan on every tool call. Mandatory bootstrap and specialist prerequisites still apply.

This versioned entry is the direct-file fallback for clients without Skill discovery. Supported clients discover `.agents/skills`; newly installed Skills require a fresh session/reload, and already-running conversations need an explicit instruction. No repository prompt can enforce compliance by clients that never load its instructions or guarantee identical judgment across models. Private memory and ignored handoff state are not required to discover the router.

## Governance object model

One current fact has one owner. Other documents link to that owner instead of copying its value.

| Object | Question answered | Unique owner | Binding effect |
|---|---|---|---|
| Contract | What must the product or system do? | UI Constitution, ADR, or architecture contract for that domain | Binding behavior/invariant |
| Phase | What capability is in the current delivery scope? | `docs/phases/PHASE_N.md` | Binding scope and entry conditions |
| Gate | Who may advance which immutable input, based on what evidence? | `docs/process/QUALITY_GATES.md` plus the recorded gate result | Binding authorization decision |
| Procedure | How is a class of work executed? | Skill or runbook | Execution method; never product authority |
| Policy | Which profiles, stages, nodes, or validation lanes are active now? | The named machine-readable policy | Binding execution selection; never implementation authorization |
| Evidence | What ran against which immutable input and what happened? | Test/CI/review artifact or Phase evidence record | Fact only; never approval by itself |
| State | What is true in the current local session? | `.local/ACTIVE_HANDOFF.md` | Transient and non-authoritative |
| History | Why did an earlier decision or result exist? | Git, retrospective, review history, or Mnemopi | Provenance only |

Naming is explicit: `G0–G7` means repository quality gates; `UI-R0–UI-R4.1` means Android UI review passes; `P4A/P4B/P4C` means Phase partitions; `RG-L01`, `RG-B03`, and similar identifiers mean Review Graph nodes. Historical artifact filenames may retain older names, but current prose must use the namespace.

## Design decision continuity

Agent-assisted design work follows [`tsuyomi-android/docs/process/DESIGN_MEMORY_WORKFLOW.md`](tsuyomi-android/docs/process/DESIGN_MEMORY_WORKFLOW.md). Versioned contracts remain authoritative; Mnemopi, `to-spec` issues and the ignored local handoff provide cross-conversation continuity without becoming parallel product contracts. Explicit user corrections are reconciled into the owning contract, Review Graph obligation and observable regression seam in the same work session.

## Component boundary

The monorepo permits atomic Android/protocol PRs but not source-level boundary violations. Android, protocol, and the independent extension repository interoperate through versioned schemas, DTOs, immutable sanitized replay fixtures, signed `.hxp` artifacts, and release metadata. Android must not import extension implementation code or require a sibling plugin checkout to build; extensions must not receive Android platform handles.

## Change and release order

Cross-repository changes establish the protocol contract first, extension producers second, and Android consumers last. Each transition pins immutable compatible inputs; no consumer follows an external default branch or `latest`. Android and protocol changes may share a PR here. Components retain independent SemVer; extension tags belong to the extension repository:

```text
protocol-vX.Y.Z
extensions-vX.Y.Z
android-vX.Y.Z
phase-N-baseline
```

Every Phase evidence document records the main repository Git SHA, the independent extension Git SHA when exercised, exact protocol/manifest/SDK/application versions and artifact digests. `latest`, uncommitted local paths, and branch names are not compatibility references. Host-only replay tests use explicitly pinned Apache-licensed historical fixtures, not a second maintained extension implementation.
