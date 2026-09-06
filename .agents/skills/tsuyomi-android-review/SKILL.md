---
name: tsuyomi-android-review
description: Runs Tsuyomi Android UI change detection and evidence-driven Review Graph workflows with Gradle, Android CLI, Android Studio, UIAutomator2 fallback, Journeys, and human-only approval.
---
<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi Android review workflow

## When to use

Use this Skill for any Android UI, navigation, interaction, accessibility, display-profile, prototype, screenshot, or Review Graph change in this repository. Workflow-only edits to this Skill, its policy, review scripts, or tooling registry use `UI-R1` change detection without a device pass unless the report selects runtime evidence.

## Do not use

- Do not invoke it for non-UI domain work that does not affect a reviewed surface or review infrastructure.
- Do not treat it as product authority, Phase scope, implementation authorization, human approval, or permission to replace a canonical APK.
- Do not duplicate a successful Gradle, Android CLI, Journey, layout, screenshot, or human evidence owner with another tool.

## Required inputs

Read the installed `android-cli` Skill first. Then read:

1. `.agents/skills/tsuyomi-android-review/review-policy.json` — current executable profile/stage selection;
2. only the affected sections of `UI_CONSTITUTION.md`, `UI_ATLAS.md`, and `INTERACTIVE_PROTOTYPE_PLAN.md`;
3. affected nodes from `ReviewNodeCatalog.kt`.

`ReviewNodeCatalog.kt` alone owns node identities and their required states. Never copy that catalog into another checklist.

## Mandatory UI design source stack

Every visible UI creation or refactor must use the following sources together. They are complementary, not interchangeable:

1. `UI_CONSTITUTION.md`, the current human review revision, `review-policy.json`, and Review Graph obligations define product intent and approval boundaries.
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

1. Read the latest live-review revision before any other implementation work.
2. Read the affected constitution/Atlas/catalog sections and active profile policy.
3. Read the official Claude `frontend-design` skill and the applicable official Android skills or docs.
4. State two compact design directions. Critique both against the user request, Tsuyomi identity, information density, touch ergonomics, accessibility, implementation cost, and the current active-profile policy. Select one direction; do not blend incompatible ideas.
5. For Material 3 Expressive, default to **Foundational** intensity. Permit at most one deliberate hero moment on a screen, preserve standard navigation and labels, and use semantic `MaterialTheme` roles rather than hard-coded visual tokens.
6. Implement only on `Tsuyomi_Review_Work_API29` until a `batch_ready` submission authorizes replacement of the human-review APK. Resolve the running serial by AVD name; serial numbers are not ownership. Its size, density, orientation and baseline font scale must match the agreed Standard phone profile before visual evidence is captured.
7. Verify the actual changed surface: interaction, semantics/layout, PNG at 1:1 where visual judgment matters, relevant state restoration, and the exact API 29 window. Screenshot assertions never prove behavior.
8. Read the latest live-review revision again before handoff. Continue the watcher; a deployment is not the end of the review loop.

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
- `:prototype:ui-atlas` is the only interactive prototype/reviewer. Never create a second review app.
- Android CLI owns official docs, AVD lifecycle, APK deployment, layout inspection/diff, and PNG capture.
- Android Studio is escalation for targeted IDE diagnostics, Layout Inspector, debugger, profiler, Preview, and human visual judgment—not a second build/deploy path.
- UIAutomator2 is fallback input/system-UI automation. Never duplicate an Android CLI screenshot or successful hierarchy.
- AI may visit nodes, attach evidence, and write only a `PENDING` draft. AI never sets `humanReviewedAt`, `approvedAt`, `ACCEPT`, or goldens.
- One obligation has one evidence owner: static screenshot assertion, structure/layout, behavioral test, Journey, or human-only review. Never prove the same fact five ways.

## Fast review path

### UI-R0 — Reconcile

1. Select an exact previous UI-R1 report/baseline. No baseline marks every current catalog node affected; `review-policy.json` then partitions them into current and deferred execution stages.
2. Read and validate `review-policy.json`; record its mode, active/deferred profiles, selected node stages, actual-online lane, resume conditions, and SHA-256.
3. Confirm `ReviewNodeCatalog.VERSION`, exact source build ID, locale, active contracts, and the policy-derived node partition. Never compare against counts copied into this Skill.
4. Preflight the canonical active AVD before deployment: observed size, density, API, orientation, locale, font scale, and profile must match the contract. Stop on drift; repair the AVD once rather than capturing invalid evidence.

### UI-R1 — Detect before building

Run from the monorepo root:

```text
python .agents/skills/tsuyomi-android-review/scripts/r1_change_detection.py \
  --baseline .local/ai-reviews/<previous-r1>.json \
  --output .local/ai-reviews/r1-<run-id>.json
```

For a requested complete AI review, use:

```text
python .agents/skills/tsuyomi-android-review/scripts/r1_change_detection.py \
  --baseline .local/ai-reviews/<previous-r1>.json \
  --force-full-review \
  --output .local/ai-reviews/r1-<run-id>.json
```

UI-R1 compares file hashes, recomputes the prototype build ID, applies the profile policy, and selects Review Graph nodes. It never edits review progress.

Rules:

- Contract, catalog, shared theme/scaffold, or unknown Android changes expand conservatively.
- Workflow-only changes stop after UI-R1: no Gradle, emulator, APK, layout, or PNG work.
- Empty scope never upgrades pending review state.
- `--force-full-review` means every current catalog node is impact-accounted. Runtime evidence uses the package and evidence lanes selected by policy; any node prefix in `actualOnlineRequirements` receives both required evidence lanes before finalization.

### UI-R2 — One build, one deploy, evidence by owner

When UI-R1 requires runtime evidence:

1. Build once with Gradle.
2. Deploy the resulting APK once to each active profile device with Android CLI.
3. Account for every affected current-stage node/state obligation, but do not automatically create one PNG per obligation:
   - deterministic static geometry/copy → existing screenshot assertion;
   - bounds, semantics, focusability, or overlap → `android layout`;
   - changed transition/persistence → Journey;
   - visual judgment → Android CLI PNG;
   - qualitative experience → human-only pending item.
4. Batch deterministic route/state launches only as state selection. They do not prove that a visible user control reaches the route.
5. For visual batches, generate a contact sheet, inspect every frame there, then inspect changed, suspicious, high-risk, modal, and Reader frames at 1:1.
6. Write an AI `PENDING` draft and leave control `PAUSED`.

Primary commands:

```text
tsuyomi-android/gradlew.bat -p tsuyomi-android -Ptsuyomi.prototype=true \
  :prototype:ui-atlas:assembleDebug --no-daemon --console=plain

android run \
  --apks=tsuyomi-android/prototype/ui-atlas/build/outputs/apk/debug/ui-atlas-debug.apk \
  --device=<serial> \
  --activity=org.tsuyomi.prototype.uiatlas.MainActivity

android layout --device=<serial> --pretty -o=.local/<node>-layout.json
android screen capture --device=<serial> -o=.local/<node>.png
```

Bounded fallback:

- If `android layout --diff` fails, retry one full `android layout`.
- If the full layout also fails while the device remains healthy, record the CLI failure and use one UIAutomator2 hierarchy. Do not loop retries or capture a duplicate screenshot.
- Android CLI does not currently expose prototype intent extras. Device-shell `am start` may select deterministic route/profile/state extras, but this is fixture setup only; Journeys still start from visible controls.
- Intent extras are strings. Use `--es capture true` rather than `--ez capture true`; the Atlas parser reads string extras.

### UI-R3 — Journeys only for changed transitions

Select only Journeys whose transition, persistence, input, or high-risk contract changed. Established candidates are:

- `X06-review-autosave-export`
- `L01-layout-selection`
- `S02-search-single-submit`
- `B01-detail-reader-return`
- `B03-seek-cancel`
- `B03-seek-commit`
- `X01-process-restoration`
- `M02-settings-persistence`

Each action is one interaction or one assertion, executed in order. A failure marks remaining actions `SKIPPED`; never rewrite the Journey to obtain a pass. Hash normalized interaction traces separately from PNG bytes.

Journeys selected by `actualOnlineRequirements.nodePrefixes` execute only with the policy-required package, real host controllers/storage/navigation, and every policy-required live/fixture lane. Redact credentials, cookies, verification answers, private content and raw WebView payloads. Never execute or close a production-only node from the isolated Atlas.

### UI-R4 — Human handoff

Hand off the same APK, node, route, state, active profile, and evidence. Human-only items include long-reading comfort, Reader seek feel, TalkBack experience, trust/destructive wording, and visual/brand judgment.

Qualitative and full-matrix items for every deferred profile remain explicitly deferred. When the policy's resume trigger is satisfied, its retained physical-device and human evidence requirements become mandatory again.

### UI-R4.1 — Live AVD review loop

For same-host human review, manual SAF/share transfer is fallback. Start the checked-in watcher from the monorepo root and keep it under the OMP-managed long-running process lifecycle:

```text
python .agents/skills/tsuyomi-android-review/scripts/review_bridge.py \
  watch --device=<human-review-serial>
```

The reviewer enters comments in the app and explicitly chooses `提交当前意见给 OMP` or `提交本批并允许更新`. The app flushes the current node and whole-prototype comments, writes the current-build export plus a monotonic signal envelope to private no-backup storage, and logs only `revision/session/build/node/hash`. The watcher performs a startup pull, then uses the marker only as a wake-up signal; it reads both files with debuggable `adb exec-out run-as`, validates policy/schema/build/hash, stores content-addressed artifacts under `.local/ai-reviews/live/`, and emits `TSUYOMI_REVIEW_EVENT` for OMP.

Use two Standard AVDs when human review and code iteration overlap:

- human-review AVD: stable exact APK, manual comments, final canonical evidence;
- development AVD: assistant install/debug/layout/PNG/Journey work.

`node` submissions may be processed while the human continues reviewing the stable APK. Only `batch_ready` permits replacing the APK on the human-review AVD. Install with data preservation, create a new build lineage, and require the human to re-review affected nodes on the exact replacement APK. A bridge event or comment never implies `humanReviewedAt`, `approvedAt`, `ACCEPT`, production authorization, or E-ink approval.

If the non-canonical development AVD is absent, create only that AVD without recreating either canonical review device:

```text
C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe \
  -ExecutionPolicy Bypass \
  -File tsuyomi-android/tools/avd/Create-ReviewAvds.ps1 \
  -ReviewWorkOnly
```

`Tsuyomi_Review_Work_API29` mirrors the Standard profile from `UI_ATLAS.md`: API 29, portrait 1080×2400, 420dpi and baseline font scale 1.0. A Pixel 2/default 1080×1920 profile is not interchangeable. Before every visual review, verify the effective device geometry and font scale, not just the AVD name/API. If the non-canonical profile has drifted, stop that AVD, repair its persistent configuration without clearing app data, and verify the effective values after restart; a temporary `wm` override alone does not repair the persistent baseline. Deliberate landscape/large-font/forced-window tests are additional evidence, not substitutes for the matching portrait capture. This AVD never owns final human approval evidence.

Capture is automatic; model execution is immediate only while an OMP live-review turn is waiting on the managed watcher. If no such turn is waiting, the watcher still persists the event and the next OMP turn reads `latest`/`pull` before doing other work. The app-private submission remains durable:

```text
python .agents/skills/tsuyomi-android-review/scripts/review_bridge.py \
  pull --device=<human-review-serial>
```

## Tool ownership and escalation

| Need | Owner | Escalate only when |
|---|---|---|
| Build/static correctness | Gradle | CI is the required environment or local host is blocked |
| Official guidance | `android docs search/fetch` | direct official URL is already known |
| Deploy/AVD/layout/PNG | Android CLI | documented command fails while device remains healthy |
| Input/system surface | layout coordinates, then UIAutomator2 | Android CLI cannot perform the interaction |
| Human review transport | `review_bridge.py` + app-private atomic JSON | SAF/share is needed for cross-host or bridge recovery |
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
- Review runtime/storage/export/live bridge → affected production build/deploy and policy-selected current-stage nodes.
- Product static geometry → affected production screenshot assertion plus affected current-stage node on every active profile.
- Navigation/persistence/state transition → affected contract test plus selected current-stage Journey.
- Direct deferred-profile source change → the minimal direct-change exception from `review-policy.json`; no full matrix.
- Full AI review → every current catalog node impact-accounted according to the active/deferred partition; policy-selected actual-online prefixes receive their additional evidence lanes.
- Deferred-profile restoration → the policy's complete retained graph, inventory, Journeys, adaptive matrix, and physical human review.
- Actual-online review → policy-required package, real host controllers/storage/navigation, live and controlled-fixture lanes, redacted evidence, and no Atlas verdict substitution.

## Evidence and handoff

Every UI-R1/UI-R2/UI-R3 output records exact build ID, policy SHA/mode, catalog version, node IDs, active/deferred profiles, device facts, commands, observed results, artifact hashes, fallbacks, and pending human items.

Store generated evidence under ignored `.local/` or `tsuyomi-android/build/`. Version control contains only stable contracts, this skill/policy, schemas, tests, and concise Phase or checkpoint decisions.
