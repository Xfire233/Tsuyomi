---
name: tsuyomi-android-review
description: Runs Tsuyomi Android UI change detection and evidence-driven Review Graph workflows with Gradle, Android CLI, Android Studio, UIAutomator2 fallback, Journeys, and human-only approval.
---
<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi Android review workflow

## When to use

Use this Skill for any Android UI, navigation, interaction, accessibility, display-profile, screenshot, or Review Graph change in this repository. Workflow-only edits to this Skill, its policy, catalog, review scripts, or tooling registry use `UI-R1` change detection without a device pass unless the report selects runtime evidence.

## Do not use

- Do not invoke it for non-UI domain work that does not affect a reviewed surface or review infrastructure.
- Do not treat it as product authority, Phase scope, implementation authorization, human approval, or permission to replace a canonical APK.
- Do not duplicate a successful Gradle, Android CLI, Journey, layout, screenshot, or human evidence owner with another tool.

## Required inputs

Read the installed `android-cli` Skill first. Then read:

1. `.agents/skills/tsuyomi-android-review/review-policy.json` — current executable profile/stage selection;
2. only the affected sections of `UI_CONSTITUTION.md` and `UI_ATLAS.md`;
3. affected nodes from `.agents/skills/tsuyomi-android-review/review-node-catalog.json`.

`review-node-catalog.json` alone owns node identities and required states. Never copy that catalog into another checklist.

## Mandatory UI design source stack

Every visible UI creation or refactor must use the following sources together. They are complementary, not interchangeable:

1. `UI_CONSTITUTION.md`, `review-policy.json`, and the standalone Review Graph catalog define product intent, execution selection and approval boundaries.
2. Claude Code official `frontend-design` supplies an intentional visual direction, differentiation, composition, and self-critique.
3. Google `android/skills` and current Android documentation supply Compose, Material 3, accessibility, adaptive-layout, edge-to-edge, and testing correctness.
4. This Skill owns Tsuyomi implementation review on policy-selected devices, evidence routing, and human handoff.
5. Community skills may provide candidate tactics only after their source, maintenance, install count, repository reputation, security report, and full `SKILL.md` are reviewed.

When sources conflict, use this precedence:

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
5. Implement only on `Tsuyomi_Review_Work_API29` unless the user explicitly authorizes canonical replacement. Resolve the running serial by AVD name; serial numbers are not ownership. Its size, density, orientation and baseline font scale must match the agreed Standard phone profile before visual evidence is captured.
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

Do not adopt the official Compose Styles skill in the current project: it requires compileSdk 37 and alpha/experimental Compose APIs, while Tsuyomi targets compileSdk 36 and prefers stable, boring implementation. The official Wear Compose Material 3 skill is Wear-only; its APIs, curved layouts, scaffolds, and tokens do not apply to this phone reader. Its reusable process principle is limited to checking canonical component samples before changing component behavior.

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

The policy selects review work; it does not grant Phase implementation authorization, human approval, merge permission, release permission, or readiness for a deferred profile. Those outcomes remain with the owning Phase/gate/human authority.
## Invariants

- Gradle Wrapper owns build, compiler, lint, unit, instrumentation, screenshot tests, dependencies, and CI correctness.
- Production Composables, strings, state owners, screenshot tests and instrumentation are the only current review surface. A second fixture UI or in-app review application is forbidden.
- Android CLI owns official docs, AVD lifecycle, APK deployment, layout inspection/diff, and PNG capture.
- Android Studio is escalation for targeted IDE diagnostics, Layout Inspector, debugger, profiler, Preview, and human visual judgment—not a second build/deploy path.
- UIAutomator2 is fallback input/system-UI automation. Never duplicate an Android CLI screenshot or successful hierarchy.
- AI may visit nodes, attach evidence, and write only a `PENDING` draft. AI never sets `humanReviewedAt`, `approvedAt`, `ACCEPT`, or goldens.
- One obligation has one evidence owner: static screenshot assertion, structure/layout, behavioral test, Journey, or human-only review. Never prove the same fact five ways.

## Fast review path

### Native edit loop — no formal evidence

Use Android Studio before a formal review pass for the smallest interactive feedback loop:

1. Run the current test method/class from the editor gutter; do not launch an unrelated suite.
2. Use production Compose Preview, Live Edit/Apply Changes, targeted debugger editor analysis, debugger, profiler or Layout Inspector only for the question each tool owns.
3. Use the checked-in `tsuyomi-android/.run/` Gradle configurations for app compilation, app JVM tests or the bounded app assemble+lint admission smoke. For another module, select that module's exact Gradle task in the Gradle tool window rather than adding another permanent configuration.
4. Android Studio output is iteration feedback. Gradle still owns compiler, lint and test proof; Layout Inspector does not replace behavioral instrumentation or accessibility review.

### UI-R0 — Reconcile

1. Prefer an exact previous UI-R1 report/baseline. Without one, UI-R1 derives a content-hash baseline from Git `origin/main`, then `main`; use `--base-ref=<checkpoint-or-target>` when the active change packet starts elsewhere. Only an unavailable file baseline and unavailable Git merge-base cause conservative cold-start full scope.
2. Read and validate `review-policy.json`; record its mode, active/deferred profiles, selected node stages, actual-online lane, resume conditions, and SHA-256.
3. Confirm the standalone catalog schema/version, exact production review build ID, locale, active contracts, and the policy-derived node partition. Never compare against counts copied into this Skill.
4. Preflight the active isolated AVD before deployment: observed size, density, API, orientation, locale, font scale, and profile must match the contract. Stop on drift; repair the AVD once rather than capturing invalid evidence.

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

- Product/Phase contracts, catalog schema, shared theme/scaffold, or genuinely unknown Android changes expand conservatively.
- Historical design records and review procedures select `X06`; module/file mappings select the owning surface family, and cross-cutting `X*` nodes are added only for named capabilities.
- Workflow-only changes stop after UI-R1: no Gradle, emulator, APK, layout, or PNG work.
- Empty scope never upgrades pending review state.
- `--force-full-review` means every current catalog node is impact-accounted. Runtime evidence uses the package and evidence lanes selected by policy; any node prefix in `actualOnlineRequirements` receives both required evidence lanes before finalization.

### UI-R2 — One build, one deploy, evidence by owner

When UI-R1 requires runtime evidence:

1. Build the affected production APK/test target once with Gradle.
2. Deploy once to each active isolated profile device with Android CLI delta install/run. Never replace the canonical package as part of routine review.
3. Account for every affected current-stage node/state obligation, but do not automatically create one PNG per obligation:
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
  :app:assembleDebug --console=plain --dependency-verification strict

android install --use-delta-install \
  --apks=tsuyomi-android/app/build/outputs/apk/debug/app-debug.apk \
  --device=<isolated-serial>

android run \
  --apks=tsuyomi-android/app/build/outputs/apk/debug/app-debug.apk \
  --device=<isolated-serial> \
  --activity=org.tsuyomi.android.MainActivity

android layout --device=<isolated-serial> --diff --pretty -o=.local/<node>-layout.json
android screen capture --device=<isolated-serial> -o=.local/<node>.png
```

Bounded fallback:

- If `android layout --diff` fails, retry one full `android layout`.
- If the full layout also fails while the device remains healthy, record the CLI failure and use one UIAutomator2 hierarchy. Do not loop retries or capture a duplicate screenshot.
- Do not run `android describe` in the edit/review loop. It resolves the whole Android project model rather than proving the changed behavior; use Gradle tasks and known APK paths.
- Deterministic fixture state selection may use explicit debug/test seams in `org.tsuyomi.android.fixture`; it must not introduce a second UI implementation. Journeys still start from visible production controls.

### UI-R3 — Journeys only for changed transitions

Select only Journeys whose transition, persistence, input, or high-risk contract changed. Established candidates are:

- `L01-layout-selection`
- `S02-search-single-submit`
- `B01-detail-reader-return`
- `B03-seek-cancel`
- `B03-seek-commit`
- `X01-process-restoration`
- `M02-settings-persistence`

Each action is one interaction or one assertion, executed in order. A failure marks remaining actions `SKIPPED`; never rewrite the Journey to obtain a pass. Hash normalized interaction traces separately from PNG bytes.

Journeys selected by `actualOnlineRequirements.nodePrefixes` execute only with the policy-required package, real host controllers/storage/navigation, and every policy-required live/fixture lane. Redact credentials, cookies, verification answers, private content and raw WebView payloads. Fixture-only execution never closes an actual-online node.

### UI-R4 — Human handoff

Hand off the same APK, node, route, state, active profile, and evidence. Human-only items include long-reading comfort, Reader seek feel, TalkBack experience, trust/destructive wording, and visual/brand judgment.

Qualitative and full-matrix items for every deferred profile remain explicitly deferred. When the policy's resume trigger is satisfied, its retained physical-device and human evidence requirements become mandatory again.

### UI-R4.1 — Production-device human review

Human review uses the exact production APK, build ID, route, state, active profile and evidence already produced by UI-R2/UI-R3. Review comments are recorded in the owning gate/checkpoint or ignored local handoff; there is no in-app reviewer, bridge daemon or app-private approval channel.

Use two Standard AVDs when human review and code iteration overlap:

- human-review AVD: stable exact production APK and final human observation;
- development AVD: assistant install/debug/layout/PNG/Journey work.

Only explicit user authorization permits replacing the APK on the human-review or canonical AVD. Preserve app data unless the authorized scenario explicitly requires clearing it. A comment, screenshot or automation event never implies `humanReviewedAt`, `approvedAt`, `ACCEPT`, production authorization or E-ink approval.

If the non-canonical development AVD is absent, create only that AVD without recreating either canonical review device:

```text
C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe \
  -ExecutionPolicy Bypass \
  -File tsuyomi-android/tools/avd/Create-ReviewAvds.ps1 \
  -ReviewWorkOnly
```

`Tsuyomi_Review_Work_API29` mirrors the Standard profile from `UI_ATLAS.md`: API 29, portrait 1080×2400, 420dpi and baseline font scale 1.0. Verify effective geometry and font scale before visual review. Deliberate landscape, large-font and forced-window tests are additional evidence, not substitutes for the matching portrait capture. This AVD never owns final human approval.

## Tool ownership and escalation

| Need | Owner | Escalate only when |
|---|---|---|
| Build/static correctness | Gradle | CI is the required environment or local host is blocked |
| Official guidance | `android docs search/fetch` | direct official URL is already known |
| Deploy/AVD/layout/PNG | Android CLI | documented command fails while device remains healthy |
| Input/system surface | layout coordinates, then UIAutomator2 | Android CLI cannot perform the interaction |
| Human review record | owning gate/checkpoint plus ignored local handoff | cross-host evidence transfer needs an explicitly authorized channel |
| Kotlin diagnostics | compiler/lint/LSP | `android studio analyze-file` answers a remaining targeted IDE question |
| Runtime composition | Android Studio Layout Inspector/debugger/profiler | layout/trace cannot expose the state |
| Final qualitative verdict | human | never delegated to AI |

Run `android studio check` once per IDE-assisted session. Successful compiler/lint output is not a reason to analyze every changed Kotlin file.

### Android Studio acceleration, not review transport

- Embedded Running Devices + Layout Inspector is the best interactive supplement for hierarchy, attributes, overlap, recomposition and reference-image overlays. It does not carry human comments or replace canonical Android CLI evidence.
- Compose Preview, Animation Preview and Compose UI Check should preflight isolated components, accessibility and adaptive-layout issues before device review. They do not prove navigation, persistence, system bars or real-window behavior.
- Live Edit can shorten pure composable function-body iteration on an optional API 30+ development AVD, but the canonical review AVD is API 29, which Google does not support for Live Edit. Live Edit state is not an exact APK and never supplies final evidence; always rebuild and verify the exact API 29 APK.
- Gemini Transform UI / Match UI can propose Preview diffs, but it is a separate cloud-assisted editor, not the Tsuyomi review authority or OMP comment channel. Use only when explicitly requested and review every diff.
- Google Journeys may exercise a pre-installed APK without upgrading this AGP 8.13.1 project to the AGP 9 test-suite integration. Keep the existing changed-transition-only rule; AI vision navigation is evidence for the selected Journey, not human qualitative approval.

## Known host failures

- Windows `CreateProcess error=206` in `validateDebugScreenshotTest` is a host/classpath-launch failure. Confirm screenshot sources compile, record the blocked validation exactly once, and let CI/another supported host own the golden result. Repeated identical retries waste the review pass.
- A failed Android CLI layout does not invalidate a successful Android CLI PNG. Record the capabilities separately.

## Scope selection

- Workflow/docs-only → UI-R1 only.
- Review workflow/catalog changes → UI-R1; add runtime evidence only when production behavior or the evidence mechanism changed.
- Product static geometry → affected production screenshot assertion plus affected current-stage node on every active profile.
- Navigation/persistence/state transition → affected contract test plus selected current-stage Journey.
- Direct deferred-profile source change → the minimal direct-change exception from `review-policy.json`; no full matrix.
- Full AI review → every current catalog node impact-accounted according to the active/deferred partition; policy-selected actual-online prefixes receive their additional evidence lanes.
- Deferred-profile restoration → the policy's complete retained graph, inventory, Journeys, adaptive matrix, and physical human review.
- Actual-online review → policy-required package, real host controllers/storage/navigation, live and controlled-fixture lanes, redacted evidence, and no fixture-only verdict substitution.
- Resolve the policy partition before every Gradle/device command. Tests for a profile currently marked deferred/frozen remain retained but ignored from routine instrumentation and screenshot registration; a test's presence in a class never makes that profile active.
- Journey debugging follows `QUALITY_GATES.md`: exact reproduction, stable affected test, adjacent sequence, affected active-profile group, then at most one required full class/suite. Repeated full-suite reruns, local duplication of the CI matrix, and production changes made only to satisfy Compose idling are review failures.
- When the same failure signature crosses Journeys or appears only in class order, stop broad execution and classify the shared helper, lifecycle owner, device state, or synchronization boundary before editing. Report the boundary change immediately.

## Evidence and handoff

Every UI-R1/UI-R2/UI-R3 output records exact build ID, policy SHA/mode, catalog version, node IDs, active/deferred profiles, device facts, commands, observed results, artifact hashes, fallbacks, and pending human items.

Store generated evidence under ignored `.local/` or `tsuyomi-android/build/`. Version control contains only stable contracts, this skill/policy, schemas, tests, and concise Phase or checkpoint decisions.
