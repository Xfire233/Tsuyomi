<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi monorepo workspace

This directory is one Git repository containing two independently versioned components. Source extensions are maintained separately in the public [Chachaanteng/tsuyomi-extensions](https://github.com/Chachaanteng/tsuyomi-extensions) repository.

| Component | Responsibility |
|---|---|
| `tsuyomi-android` | Kotlin/Compose Android host application. |
| `tsuyomi-protocol` | Platform-neutral JSON Schemas, fixtures, Host API, transfer/backup contracts, and conformance rules. |

## Local prerequisites

- Android builds: JDK 17 and the SDK/native packages listed in the [Android contribution guide](tsuyomi-android/CONTRIBUTING.md#prerequisites). Use the Gradle Wrapper for your operating system; SDK paths stay in environment variables or ignored `local.properties`.
- Protocol work: a supported Node.js LTS and npm; install dependencies with `npm ci` in `tsuyomi-protocol`.
- Repository checks: Python 3.11+ and REUSE 6.2.0, preferably in a virtual environment.
- Emulator checks: additional SDK command-line tools, acceleration and the image selected by the [runner profile](tools/android_api29_profile.json), only when running that lane locally.

Install only what the affected component/check needs. Windows helper scripts are optional and do not define the cross-platform build contract. Neither a maintainer's SDK path nor their named AVD/test account is a prerequisite.

## Android UI review workflow

Android UI, navigation, interaction, display-profile and accessibility changes follow the [UI Constitution](tsuyomi-android/docs/design/UI_CONSTITUTION.md) and [evidence specification](tsuyomi-android/docs/design/UI_ATLAS.md). The [review policy](.agents/skills/tsuyomi-android-review/review-policy.json) selects active/deferred profiles. The [review procedure](.agents/skills/tsuyomi-android-review/SKILL.md) is readable without Skill discovery; contributors may use equivalent tools to supply its evidence. Product contracts and human approval authority remain in Android design/Phase documents and explicit gate outcomes.

## Development tooling

[`TOOLING.md`](TOOLING.md) documents optional assistant integrations, including OMP-specific dispatch, Skills and MCP servers. It is not a required editor, operating system or personal-tool installation list. Repository-owned scripts and policy remain available directly from the checkout.

[`DOCUMENTATION.md`](DOCUMENTATION.md) is the canonical responsibility registry for first-party documents. It states each document's purpose, trigger, method, scope/exclusions, completion/stop condition and lifecycle. A link is not itself a trigger: read only the owning document rows that match the task.

For assistant sessions, at each new request, material scope change or context recovery, check whether ownership is unresolved: unknown owners, authority/history conflicts, missing handoff ownership, or cross-component questions needing distinct authorities trigger [tsuyomi-context-router](.agents/skills/tsuyomi-context-router/SKILL.md). Read the Skill once when triggered; it owns the routing algorithm and exclusions. Known-file/symbol tasks and an already-running specialist workflow bypass routing. Do not reload unchanged context or build a plan on every tool call. Mandatory bootstrap and specialist prerequisites apply within the configured assistant workflow, not to ordinary contributors.

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
| State | What is true in the current local session? | Optional ignored local handoff, such as `.local/ACTIVE_HANDOFF.md` | Transient and non-authoritative; unavailable in a fresh clone |
| History | Why did an earlier decision or result exist? | Git, public retrospective/review history; optional private memory | Provenance only; private memory is not a contribution prerequisite |

Naming is explicit: `G0–G7` means repository quality gates; `UI-R0–UI-R4.1` means Android UI review passes; `P4A/P4B/P4C` means Phase partitions; `RG-L01`, `RG-B03`, and similar identifiers mean Review Graph nodes. Historical artifact filenames may retain older names, but current prose must use the namespace.

## Design decision continuity

Record durable decisions in the owning versioned contracts and review/regression obligations. Optional agent-assisted continuity follows [`DESIGN_MEMORY_WORKFLOW.md`](tsuyomi-android/docs/process/DESIGN_MEMORY_WORKFLOW.md); private memory, issue-generation tools and ignored local handoffs never become parallel product contracts or prerequisites for repository-only contribution.

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
