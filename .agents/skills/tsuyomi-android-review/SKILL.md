---
name: tsuyomi-android-review
description: Runs Tsuyomi Android UI change detection and evidence-driven Review Graph workflows with Gradle, Android CLI, Android Studio, UIAutomator2 fallback, Journeys, and human-only approval.
---
<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi Android review workflow

This is the repository's optional assistant execution procedure and a directly readable evidence guide. Contributors do not need OMP, Claude Code, user-scoped Skills, MCP servers, private accounts or the maintainer's named devices. The installed-Skill/tool-dispatch and session resource rules below apply to configured assistant sessions; ordinary contributors may use equivalent IDE, SDK and test tools while preserving the same contracts, policy-selected evidence and human approval boundaries. Local AVD names are examples; record the actual device and required physical geometry.

## When to use

Use this Skill for any Android UI, navigation, interaction, accessibility, display-profile, screenshot, or Review Graph change in this repository. Workflow-only edits to this Skill, its policy, catalog, review scripts, or tooling registry use `UI-R1` change detection without a device pass. Runtime obligations selected by a report are not device-execution permission.

## Do not use

- Do not invoke it for non-UI domain work that does not affect a reviewed surface or review infrastructure.
- Do not treat it as product authority, Phase scope, implementation authorization, human approval, or permission to replace a canonical APK.
- Do not duplicate a successful Gradle, Android CLI, Journey, layout, screenshot, or human evidence owner with another tool.

## Required inputs

Read the installed `android-cli` Skill first. Then read:

1. `.agents/skills/tsuyomi-android-review/review-policy.json` — current executable profile/stage selection;
2. only the affected sections of `UI_CONSTITUTION.md` and `UI_ATLAS.md`;
3. affected nodes from `.agents/skills/tsuyomi-android-review/review-node-catalog.json`;
4. [`QUALITY_GATES.md` — assistant local execution authorization](../../../tsuyomi-android/docs/process/QUALITY_GATES.md#assistant-local-execution-authorization), the sole owner of local scope increases, device lifecycle and artifact-bound approval boundaries.

`review-node-catalog.json` alone owns node identities and required states. Never copy that catalog into another checklist.

## Mandatory UI design source stack

Every visible UI creation or refactor must use the following sources together. They are complementary, not interchangeable:

1. `UI_CONSTITUTION.md`, `review-policy.json`, and the standalone Review Graph catalog define product intent, profile selection and evidence requirements; owning gates record authorization/approval.
2. Claude Code official `frontend-design` supplies an intentional visual direction, differentiation, composition, and self-critique.
3. Google `android/skills` and current Android documentation supply Compose, Material 3, accessibility, adaptive-layout, edge-to-edge, and testing correctness.
4. This Skill owns Tsuyomi implementation review on policy-selected devices, evidence routing, and human handoff.
5. Community skills may provide candidate tactics only after their source, maintenance, install count, repository reputation, security report, and full `SKILL.md` are reviewed.

For product guidance, use this precedence. Local assistant execution instead follows the latest explicit maintainer direction and the owning `QUALITY_GATES.md` authorization boundary; the source stack never grants extra tests, device work or approval.

```text
human review + UI_CONSTITUTION
> executable profile policy and Review Graph contracts
> official Android platform/component guidance
> Tsuyomi runtime/evidence rules
> frontend-design creative direction
> reviewed community suggestions
```

`frontend-design` is web-oriented. Reuse its purpose, tone, differentiation, hierarchy, controlled-density, and deliberate-detail reasoning. Do not import web-only defaults into Android: no forced non-Roboto font, CSS effects, hover-only behavior, custom cursors, gratuitous gradients, or animation-heavy spectacle. Use real Material 3 Compose controls, platform navigation behavior, semantic tokens, accessibility semantics, and Android performance constraints.

### Required design-to-review loop

Before editing visible UI:

1. Read the affected Constitution, production evidence specification and catalog nodes before implementation.
2. Read the official Claude `frontend-design` skill and the applicable official Android skills or docs.
3. State two compact design directions. Critique both against the user request, Tsuyomi identity, information density, touch ergonomics, accessibility, implementation cost, and the current active-profile policy. Select one direction; do not blend incompatible ideas.
4. For Material 3 Expressive, default to **Foundational** intensity. Permit at most one deliberate hero moment on a screen, preserve standard navigation and labels, and use semantic `MaterialTheme` roles rather than hard-coded visual tokens.
5. Keep iteration off a frozen human target. A separately owned iteration device is usable only within the gate-authorized scope; overlapping review does not authorize creating another AVD. The shared online candidate runs only on an explicitly designated dedicated-test-account device, without permission to replace the daily canonical. Resolve serial by actual AVD name; serial numbers are not ownership. Match the designated profile's physical geometry, orientation and baseline font scale before visual evidence.
6. Verify the actual changed production surface: interaction, semantics/layout, PNG at 1:1 where visual judgment matters, relevant state restoration, and the exact API 29 window. Screenshot assertions never prove behavior.
7. Hand off the exact production APK/build identity, evidence and pending human-only claims. Deployment is not approval.

### Project-filtered Google guidance

Apply these rules when relevant:

- Material components first: use `androidx.compose.material3` controls and canonical component behavior. Custom drawing is for product-specific visuals, not replacements for existing buttons, icon buttons, navigation, dialogs, sheets, menus, or progress controls.
- Accessibility: preserve visible labels; use Material icons for general actions/status; target at least 48dp for touch interactions; prefer semantic Compose test matchers and use `testTag` only when semantics cannot identify an element simply.
- Motion: honor the system animation scale and provide an instant/reduced-motion path. Motion must communicate state or continuity. New profile-specific motion decisions are forbidden for every profile currently deferred by policy.
- Adaptive density: choose a minimum usable card width from the content and available window, then derive columns from measured width. Verify compact phone behavior first; do not force Navigation 3, multi-pane migration, or experimental Grid/MediaQuery APIs into this project without an independent need and compatibility review.
- Edge-to-edge: use `Scaffold`/Material inset handling or one explicit inset strategy, pass list insets through `contentPadding`, consume propagated insets, verify IME visibility, and never double-apply IME or safe-drawing padding.
- Testing ownership: behavior tests prove actions and state restoration; screenshots prove appearance; layout/semantics prove bounds and accessibility structure; device evidence proves system bars, IME, drag/drop, and other real-window behavior.
- Canonical defaults: when adopting or migrating a Material component, begin with current official defaults and samples. Do not reproduce an obsolete screenshot by overriding correct platform behavior.

Do not adopt the official Compose Styles skill as part of selective Expressive work: the authorized compileSdk37/toolchain migration satisfies a build prerequisite, not authorization for additional alpha/experimental UI APIs. The Constitution's graduated-API and single-family boundaries still apply. The official Wear Compose Material 3 skill is Wear-only; its APIs, curved layouts, scaffolds, and tokens do not apply to this phone reader. Its reusable process principle is limited to checking canonical component samples before changing component behavior.

Community Material 3/Expressive skills are advisory. Currently accepted cross-checks are: Foundational intensity, no more than one hero moment per screen, standard navigation preserved, labels retained, semantic tokens, reduced-motion support, 48dp touch targets, and contrast verification. Dynamic color, shape morphing, spring motion, and expressive component substitutions are opt-in decisions, not defaults for Tsuyomi.

### Skill discovery and adoption

Use the installed `find-skills` skill for open-ecosystem discovery and Android CLI for the official Google catalog. Search results are leads, never authority. Before installing or embedding a skill:

1. Read its complete `SKILL.md` and the specific references that would enter this workflow.
2. Prefer official sources; otherwise record install count, repository stars/maintenance, license, and security assessment.
3. Extract only rules compatible with the precedence above. Never make a third-party skill a runtime or build dependency.
4. Keep the distilled project rules in this section rather than requiring every contributor to install the same community skill.

## Policy boundary

`review-policy.json` is the single machine-readable source for current mode, active/deferred profiles, execution stages, node prefixes, actual-online requirements, direct-change exceptions, and resume conditions.

This Skill defines only the stable procedure. It must not copy the policy's current mode, stage, milestone, counts, profile partition, or prefix lists into prose. At `UI-R0`, read the file, validate it, record its SHA-256, and derive the current execution matrix from its fields.

The policy selects evidence obligations; it does not grant local execution, Phase implementation, human approval, merge/release permission or deferred-profile readiness. Apply the owning [local authorization boundary](../../../tsuyomi-android/docs/process/QUALITY_GATES.md#assistant-local-execution-authorization) before commands or test additions/extensions. Active profiles, initial-restoration mode and `UI-R1` accounting do not override it or the Constitution's E-ink pause/major-UI trigger. Retained restoration obligations may be deferred without being passed; unexecuted required evidence remains `PENDING`. Approval outcomes remain in the owning gate/checkpoint, not this Skill or an agent-written handoff.

## Invariants

- Gradle Wrapper owns build, compiler, lint, unit, instrumentation, screenshot tests, dependencies, and CI correctness.
- Production Composables, strings, state owners, screenshot tests and instrumentation are the only current review surface. A second fixture UI or in-app review application is forbidden.
- Android CLI owns official docs, AVD lifecycle, APK deployment, layout inspection/diff, and PNG capture.
- Android Studio is escalation for targeted IDE diagnostics, Layout Inspector, debugger, profiler, Preview, and human visual judgment—not a second build/deploy path.
- UIAutomator2 is fallback input/system-UI automation. Never duplicate an Android CLI screenshot or successful hierarchy.
- AI may visit nodes, attach evidence, and write only a `PENDING` draft. AI never sets `humanReviewedAt`, `approvedAt`, `ACCEPT`, or goldens.
- One obligation has one evidence owner: static screenshot assertion, structure/layout, behavioral test, Journey, or human-only review. Never prove the same fact five ways.

### Local API 29 CI preflight

Use local API 29 CI only within the separately authorized admission/diagnostic scope defined by [`QUALITY_GATES.md`](../../../tsuyomi-android/docs/process/QUALITY_GATES.md#bounded-failure-escalation), with explicit `HIGH`; selecting HIGH alone does not authorize preflight or its disposable AVD. The full gate is the non-focused planner-selected `tools/android_api29.py --mode high --build` run. Every non-prepare run precompiles its selected instrumentation before AVD work; `HIGH` omits `--no-daemon` for precompile and serialized instrumentation so Gradle may reuse a daemon between phases, while hosted `ci` retains `--no-daemon`. The shared `android_api29_profile.json` selects automation inputs; evidence is collected under `tsuyomi-android/build/api29-ci/`. Its owned emulator and AVD must be stopped/deleted in `finally` on success, failure or cancellation, with cleanup failures recorded. HIGH increases compilation throughput only; device execution remains serialized.

In `LOW`, do not invoke the local API 29 runner, matrix, preflight or `--prepare-only`; bounded direct development is not CI. `--mode ci` is reserved for hosted execution with `GITHUB_ACTIONS=true`. Authorized focused `--task`/exact `--test-class` runs are diagnostic evidence, not a full gate and not permission to continue into adjacent/full execution. Only the separately authorized admission task follows the owning gate's focused/adjacent/full sequence; hosted protected checks independently remain required. On a repeated signature, collect available logs/device state and classify the shared lifecycle, helper or synchronization boundary before any change—never invent a speculative production patch.

The automation AVD is a CI-only exception: it does not replace a visual/human review device, canonical APK, canonical AVD, or human authorization. Its environment evidence records image/emulator revision, system fingerprint, WebView version, resolved local head/worktree overlay and phase timings for comparison; it must not claim host, kernel or emulator-build identity with hosted CI. Compare timing only for equal resolved head/overlay policy, scope, selected tasks, profile/image revision and host evidence. Contributor-facing Windows/Linux commands and host limitations are documented in [`tsuyomi-android/CONTRIBUTING.md`](../../../tsuyomi-android/CONTRIBUTING.md#local-api-29-runner).

## Fast review path

### Native edit loop — no formal evidence

Use Android Studio before a formal review pass for the smallest interactive feedback loop:

1. Run only the current mapped test method, or a class already explicitly authorized for that proof, from the editor gutter; do not launch an unrelated suite.
2. Use production Compose Preview, Live Edit/Apply Changes, targeted debugger editor analysis, debugger, profiler or Layout Inspector only for the question each tool owns.
3. Select only the exact authorized tasks from checked-in `tsuyomi-android/.run/` configurations or the Gradle tool window. A saved app JVM-test or assemble+lint configuration is not permission to run the whole configuration; do not add another permanent configuration for a one-off module task.
4. Android Studio output is iteration feedback. Gradle still owns compiler, lint and test proof; Layout Inspector does not replace behavioral instrumentation or accessibility review.

### UI-R0 — Reconcile

1. Prefer an exact previous UI-R1 report/baseline. Without one, UI-R1 derives a content-hash baseline from Git `origin/main`, then `main`; use `--base-ref=<checkpoint-or-target>` when the active change packet starts elsewhere. Only unavailable file and Git merge-base baselines cause conservative cold-start full impact accounting, never automatic full execution.
2. Read and validate `review-policy.json`; record its mode, active/deferred profiles, selected node stages, actual-online lane, resume conditions, and SHA-256.
3. Confirm the standalone catalog schema/version, exact production review build ID, locale, active contracts, and the policy-derived node partition. Never compare against counts copied into this Skill.
4. Preflight only the authorized designated AVD before deployment: observed size, density, API, orientation, locale, font scale and profile must match the contract. Stop/report drift; do not automatically repair, recreate or substitute a device outside its authorized ownership/state boundary.

### UI-R1 — Detect before building

Run from the monorepo root with the best available baseline:

```text
python .agents/skills/tsuyomi-android-review/scripts/r1_change_detection.py \
  --baseline .local/ai-reviews/<previous-r1>.json \
  --output .local/ai-reviews/r1-<run-id>.json

python .agents/skills/tsuyomi-android-review/scripts/r1_change_detection.py \
  --base-ref=<change-packet-checkpoint-or-target> \
  --output .local/ai-reviews/r1-<run-id>.json
```

Omit both baseline options only when the default `origin/main`/`main` merge-base is the correct change-packet boundary. For a requested complete AI review, add `--force-full-review`.

UI-R1 compares content hashes, recomputes the production review build ID, applies the profile policy, and selects Review Graph nodes. It never edits review progress.

Rules:

- Product/Phase contracts, catalog schema, shared theme/scaffold or genuinely unknown Android changes expand impact accounting conservatively, not assistant execution authorization.
- Historical design records and review procedures select `X06`; module/file mappings select the owning surface family, and cross-cutting `X*` nodes are added only for named capabilities.
- Workflow-only changes stop after UI-R1: no Gradle, emulator, APK, layout, or PNG work.
- Empty scope never upgrades pending review state.
- `--force-full-review` means every current catalog node is impact-accounted. Policy-required runtime/actual-online lanes remain completion obligations, but commands stay within the gate-authorized mapping; required lanes not executed stay `PENDING` and block finalization rather than trigger an automatic matrix.

### UI-R2 — One build, one deploy, evidence by owner

When UI-R1 identifies runtime obligations and their mapped execution is authorized by the owning gate:
Before changing a device display, bind its serial to `adb -s <serial> emu avd name` and record `adb -s <serial> shell wm size` **both physical and override** values. The active profile requires the prescribed *physical* size; `wm size` overrides cannot make a differently sized AVD equivalent. For a temporary narrow/large-type pass, restore the recorded pre-pass override (`wm size reset` if none, otherwise the exact prior override) and verify it again. Never infer restoration from a screenshot or hard-code `1080x2400`: an override of 1080x2400 on a physical 1080x1920 Pixel 2 previously left the bottom and right of the host emulator unclickable. Before a human touch handoff, verify real mouse input on the affected lower/right controls; ADB-injected taps and Windows hit tests cannot prove emulator mouse mapping.

1. Build the affected production `online` APK once; it uses debug signing and the real transport. CI owns its separate deterministic fixture/test targets. Workflow-only edits do not require an APK build or device pass; an explicit deployment request is separate runtime scope.
2. Deploy once to the explicitly authorized dedicated-account device, using Android CLI delta install/run. Preserve app data and signing identity. Do not replace the existing daily canonical or install/run fixture-cleaning instrumentation on the shared candidate.
3. Account for every affected current-stage node/state obligation, but execute only changed/directly coupled claims in the authorized mapping. Choose the lowest existing evidence seam per claim; neither this list nor a missing test authorizes test additions/extensions, another PNG or an extra run:
   - deterministic static geometry/copy → existing screenshot assertion;
   - bounds, semantics, focusability, or overlap → `android layout --diff`;
   - changed transition/persistence → focused instrumentation or Journey;
   - visual judgment → one Android CLI PNG;
   - qualitative experience → human-only pending item.
4. Batch deterministic route/state launches only as state selection. They do not prove that a visible user control reaches the route.
5. For visual batches, generate a contact sheet, inspect every frame there, then inspect changed, suspicious, high-risk, modal, and Reader frames at 1:1.
6. Write an AI `PENDING` draft and leave control `PAUSED`.

Primary production commands:

```text
tsuyomi-android/gradlew.bat -p tsuyomi-android \
  :app:assembleOnline --console=plain --dependency-verification strict

android install --use-delta-install \
  --apks=tsuyomi-android/app/build/outputs/apk/online/app-online.apk \
  --device=<designated-test-account-serial>

android run \
  --apks=tsuyomi-android/app/build/outputs/apk/online/app-online.apk \
  --device=<designated-test-account-serial> \
  --activity=org.tsuyomi.android.MainActivity

android layout --device=<designated-test-account-serial> --diff --pretty -o=.local/<node>-layout.json
android screen capture --device=<designated-test-account-serial> -o=.local/<node>.png
```

Bounded fallback:

- If `android layout --diff` fails, retry one full `android layout`.
- If the full layout also fails while the device remains healthy, record the CLI failure and use one UIAutomator2 hierarchy. Do not loop retries or capture a duplicate screenshot.
- Do not run `android describe` in the edit/review loop. It resolves the whole Android project model rather than proving the changed behavior; use Gradle tasks and known APK paths.
- Deterministic fixture seams in `org.tsuyomi.android.fixture` remain CI regression or bounded failure-diagnosis tools, not another acceptance round. Never run credential-clearing fixture/live-test setup on the shared candidate. Real task-flow checks start at visible production controls.

### UI-R3 — Journeys only for changed transitions

Select only already-authorized Journeys whose transition, persistence, input or high-risk contract changed. Their presence below does not authorize a run. Established candidates are:

- `L01-layout-selection`
- `S02-search-single-submit`
- `B01-detail-reader-return`
- `B03-seek-cancel`
- `B03-seek-commit`
- `X01-process-restoration`
- `M02-settings-persistence`

Each action is one interaction or one assertion, executed in order. A failure marks remaining actions `SKIPPED`; never rewrite the Journey to obtain a pass. Hash normalized interaction traces separately from PNG bytes.

Journeys selected by `actualOnlineRequirements.nodePrefixes` use the policy-required production package and real host controllers/storage/navigation/live service on the shared candidate. `controlledFixtureReplayOwner=ci` assigns signed replay to the existing local/hosted CI, not this Journey pass. Redact credentials, cookies, verification answers, private content and raw WebView payloads. Fixture-only evidence never closes an actual-online node.

### UI-R4 — Human handoff

After selected automation finishes, stop further input and background state changes and freeze the single designated retained human target: exact installed APK SHA-256, source/build, device/profile, route/state and reusable test session. Follow the owning gate's temporary-device cleanup rules; do not leave a disposable automation AVD as a second review target. Record remaining qualitative items as `PENDING`; do not repeat already proven operations merely for handoff. Human-only judgments include long-reading comfort, Reader seek feel, TalkBack experience, trust/destructive wording and visual/brand quality.

Qualitative and full-matrix items for every deferred or paused profile scope remain explicitly deferred and unpassed. They are not prerequisites for continuing separately authorized Standard/local-reading verification. A major-UI impact assessment does not resume paused improvement or full restoration. When the policy's explicit restoration resume trigger is satisfied, retained physical-device and human evidence requirements become mandatory again for that completion/readiness claim, not automatic local execution; any required but unexecuted item remains `PENDING`.

### UI-R4.1 — Production-device human review

Human review uses the exact installed production APK SHA-256, build ID, route, state, active profile and evidence already produced by UI-R2/UI-R3. Approval is recorded only in the owning gate/checkpoint; an ignored local handoff may carry transient review notes, not approval authority. There is no in-app reviewer, bridge daemon or app-private approval channel.

Use one explicitly designated retained dedicated-account `online` candidate sequentially for automation then human review, under the owning gate's authorization/lifecycle rules. Overlapping iteration does not authorize an extra development AVD or a second acceptance lane.

Deploying successive builds to the designated working AVD during the authorized verification sequence does not require approval for each install. Once the human candidate is frozen, do not replace its APK or change its review state until the owner explicitly resumes iteration. Preserve application data, signing identity and encrypted source-scoped sessions. The dedicated Wenku8 test account may use normal WebView login automation and transient private credential input; other accounts remain manual. Challenges pause for the human. Never embed credentials in hosted CI, public source or APKs, never export them to evidence, and never treat login permission as website-write permission. Do not use credential-clearing `LiveWebViewVerificationInstrumentedTest` setup on this candidate. Restarted reusable sessions do not require another login unless expired or challenged.

Starting and reusing the designated existing AVD requires no per-start approval; creating an additional differently numbered AVD alongside it requires explicit maintainer confirmation. The optional Windows helper is `Create-ReviewAvds.ps1 -ReviewWorkOnly`; never recreate canonical devices or use force/replacement flags on another owner's AVD. Follow [`AVD_MATRIX.md`](../../../tsuyomi-android/docs/verification/AVD_MATRIX.md#assistant-device-ownership-and-cleanup) for inventory, state restoration and temporary cleanup. The agreed Standard API 29 recipe is portrait physical 1080×2400, 420dpi and font scale 1.0; use the designated profile's actual recipe. Authorized narrow, landscape or large-type scenarios restore recorded prior display settings before handoff. Verify actual human mouse input on affected lower/right controls when a touch handoff is claimed; injected tap success is not human mouse acceptance.


## Tool ownership and escalation

| Need | Owner | Escalate only when |
|---|---|---|
| Build/static correctness | Gradle | CI is the required environment or local host is blocked |
| Official guidance | `android docs search/fetch` | direct official URL is already known |
| Deploy/AVD/layout/PNG | Android CLI | documented command fails while device remains healthy |
| Input/system surface | layout coordinates, then UIAutomator2 | Android CLI cannot perform the interaction |
| Human approval record | owning gate/checkpoint only; ignored local handoff carries transient notes, not approval | cross-host evidence transfer needs an explicitly authorized channel |
| Kotlin diagnostics | compiler/lint/LSP | `android studio analyze-file` answers a remaining targeted IDE question |
| Runtime composition | Android Studio Layout Inspector/debugger/profiler | layout/trace cannot expose the state |
| Final qualitative verdict | human | never delegated to AI |

Run `android studio check` once per IDE-assisted session. Successful compiler/lint output is not a reason to analyze every changed Kotlin file.

### Android Studio acceleration, not review transport

- Embedded Running Devices + Layout Inspector is the best interactive supplement for hierarchy, attributes, overlap, recomposition and reference-image overlays. It does not carry human comments or replace canonical Android CLI evidence.
- Compose Preview, Animation Preview and Compose UI Check should preflight isolated components, accessibility and adaptive-layout issues before device review. They do not prove navigation, persistence, system bars or real-window behavior.
- Live Edit can shorten pure composable function-body iteration on an explicitly authorized API 30+ development AVD; this does not authorize a new device. The API 29 review recipe does not support Live Edit. Live Edit state is not an exact APK and never supplies final evidence; rebuild and verify the exact designated API 29 APK within the authorized scope.
- Gemini Transform UI / Match UI can propose Preview diffs, but it is a separate cloud-assisted editor, not the Tsuyomi review authority or OMP comment channel. Use only when explicitly requested and review every diff.
- Google Journeys may exercise a pre-installed APK without adopting a new AGP test-suite integration. A build-toolchain upgrade does not authorize replacing existing Journey ownership. Keep the changed-transition-only rule; AI vision navigation is evidence for the selected Journey, not human qualitative approval.

## Known host failures

- Windows `CreateProcess error=206` in `validateDebugScreenshotTest` is a host/classpath-launch failure. Use any already-authorized screenshot compilation evidence, record the blocked validation exactly once and let CI/another authorized supported host own the golden result; the failure does not authorize another compile/run or identical retries.
- A failed Android CLI layout does not invalidate a successful Android CLI PNG. Record the capabilities separately.

## Scope selection

This list identifies evidence obligations, not permission to execute or add/extend tests. Apply [`QUALITY_GATES.md`](../../../tsuyomi-android/docs/process/QUALITY_GATES.md#assistant-local-execution-authorization) and its exact mapping first; preserve `PENDING` for required evidence outside the authorized local boundary.

- Workflow/docs-only → UI-R1 only.
- Review workflow/catalog changes → UI-R1; identify runtime obligations only when production behavior or the evidence mechanism changed, without automatically executing them.
- Product static geometry → lowest authorized production appearance/layout seam for the affected claim and current-stage node. Conditional EINK requires the Constitution's major-UI trigger before any functional evidence assessment; minor UI, low-impact or non-UI implementation risk does not create a separate E-ink round.
- Navigation/persistence/state transition → lowest authorized behavioral seam: affected contract test or production Journey, not both for the same claim.
- Direct deferred-profile source change → the minimal direct-change exception from `review-policy.json`; no full matrix. An E-ink source change alone cannot bypass the Constitution's major-UI trigger or resume paused improvement.
- Full AI review → every current catalog node impact-accounted according to the active/deferred partition; policy-selected actual-online prefixes receive their additional evidence lanes.
- Deferred/paused-profile restoration → retained complete graph, inventory, Journeys, adaptive matrix and physical human evidence remain unpassed completion/readiness requirements, not current Standard/local-reading execution prerequisites. Resume full restoration only with explicit maintainer authorization; a major-UI check alone does not resume it.
- Actual-online review → policy-required production package, real host state and live task flows on the shared online candidate; signed deterministic replay belongs to CI, with no standalone fixture acceptance and no fixture-only verdict substitution. Device deployment is separately authorized.
- Resolve the policy partition before every Gradle/device command. Tests for a profile currently marked deferred/frozen remain retained but ignored from routine instrumentation and screenshot registration; a test's presence in a class never makes that profile active.
- UI-R1 `requiresDeviceProfiles` reports required device obligations, not execution permission; `conditionalDeviceProfiles` needs the Constitution's major-UI trigger and affected-path assessment before proposing E-ink functional evidence. Record risk and minimum evidence only when applicable; a skipped incremental check is not required, never passed, and does not close retained restoration exits. Required evidence outside local authorization stays `PENDING`. `--force-full-review` accounts for all nodes, not an automatic E-ink matrix; historical initial-restoration coverage cannot override an explicit pause.
- Journey debugging follows [`QUALITY_GATES.md`](../../../tsuyomi-android/docs/process/QUALITY_GATES.md#bounded-failure-escalation): no automatic widening from a focused check. The HIGH full planner-selected gate is required only for a separately, explicitly authorized admission task; LOW invokes no local API 29 runner. Hosted protected admission remains independent.
- When the same failure signature crosses Journeys or appears only in class order, stop broad execution, capture diagnostic state, and classify the shared helper, lifecycle owner, device state, or synchronization boundary before changing anything. Never make speculative production changes to satisfy Compose idling; report the boundary change immediately.

## Evidence and handoff

Every UI-R1/UI-R2/UI-R3 output records exact build ID, policy SHA/mode, catalog version, node IDs, active/deferred profiles, device facts, commands, observed results, artifact hashes, fallbacks, and pending human items.

Store generated evidence under ignored `.local/` or `tsuyomi-android/build/`. Version control contains only stable contracts, this skill/policy, schemas, tests, and concise Phase or checkpoint decisions.
