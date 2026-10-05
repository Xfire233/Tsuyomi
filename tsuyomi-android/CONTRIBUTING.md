<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Contributing

- New source files require SPDX copyright and license identifiers.
- New dependencies and copied/adapted upstream code require an entry in `THIRD_PARTY_NOTICES.md` with a pinned source revision and adoption scope.
- Do not add user accounts, cloud synchronization, telemetry, crash reporting, CAPTCHA bypass, or Android-specific APIs to the extension host contract without an ADR.
- Preserve the dependency direction in `docs/architecture/MODULES.md`.
- Do not place session credentials, cookies, source content, private signing keys, or local SDK paths under version control.
- Follow `docs/process/QUALITY_GATES.md`, `docs/process/REPOSITORY_GOVERNANCE.md`, and `docs/design/OPTION_APPLICABILITY.md`; a persisted field or enum is not evidence that a UI control is implemented.
- Every named admission or review gate approval and finding closure must bind to immutable Git input and be recorded under `docs/phases` / `docs/reviews`.
- Dependency changes must update the version catalog, Gradle lock state, verification metadata, `THIRD_PARTY_NOTICES.md`, and validation evidence in one reviewable change.
- Run `python ../tools/check_repository.py --scope android` before release; build output, local SDK state, dumps, credentials, and unknown root files are forbidden.
- Build, sign, tag and publish an Android release only through `docs/process/RELEASE_PROCEDURE.md`. Signing happens outside Gradle, the private key and its password never enter the worktree, a build scan or CI, and a published asset is only accepted after the `android-release` workflow re-derives its identity, signer and digests from the uploaded file.
- A manifest, extension parser, Host API or signed-policy change must rebuild and verify the deterministic public HXP fixture before Android instrumentation; stale signed fixtures are invalid evidence, not a runtime fallback.
- Local API 29 CI is permitted only in explicit `HIGH`; `--build` runs the planner-selected full local preflight on its disposable AVD. The contributor-facing invocation is documented below; `QUALITY_GATES.md` owns gate selection and order.
- In `HIGH`, follow `exact reproduction → affected test → adjacent sequence → local planner-selected gate → hosted protected checks`. Focused task/class runs are diagnostics, not the full gate; fixture, migration, security and screenshot regressions remain in CI.
- In `LOW`, never run local API 29 CI, including `--prepare-only`; bounded direct development compilation is separate, and hosted protected checks remain the CI path and final acceptance.
- Resolve active/deferred profiles from `.agents/skills/tsuyomi-android-review/review-policy.json` before test selection. Frozen-profile screenshot and instrumentation tests remain retained but disabled from routine gates until an explicit policy restoration.
- Hosted protected checks remain independently required final acceptance: a local result cannot bypass, replace or authorize their success. Compare recorded image revision, system fingerprint and WebView version when diagnosing a host discrepancy; never claim local and hosted emulator builds are identical. Performance comparisons must use equal scope, selected tasks, profile/image revision and host evidence (as well as the same resolved `HEAD` and worktree-overlay policy), and compare recorded phase timings rather than a single total.
- A repeated failure signature across Journeys or a focused-pass/class-order-fail split is a harness/lifecycle incident until disproved. Capture diagnostic device/log state and classify the shared boundary; never make speculative production patches or modify production behavior solely to satisfy Compose idling.
- `HIGH` is the only local runner mode and serializes device work while allowing Gradle daemon reuse. `LOW` is for bounded interactive work. `--mode ci` is reserved for hosted execution with `GITHUB_ACTIONS=true`. Disposable CI AVDs never use the dedicated online candidate; automation uses that dedicated debug-signed candidate first, then freezes it for human review. Never run credential-clearing setup on the candidate.

## Build from source

Prerequisites are listed in the [workspace setup](../WORKSPACE.md#local-prerequisites): JDK17, Android SDK Platform37, Node/npm and Python with REUSE6.2.0. From this component directory on Windows:

```powershell
$env:ANDROID_SDK_ROOT = '<your-android-sdk>'
./tools/Doctor.ps1
./tools/Run-Gradle-Low.bat --console=plain --dependency-verification strict :app:assembleDebug
```

The debug APK uses the isolated `.fixture` application identity; it is not a distributable release. Real-service development uses `:app:assembleOnline`. Release assembly, external signing and publication follow [`RELEASE_PROCEDURE.md`](docs/process/RELEASE_PROCEDURE.md).

## Local API 29 runner

The runner is optional unless the change requires the explicit-`HIGH` local preflight. `LOW` MUST NOT invoke it, including `--prepare-only`; hosted protected checks remain independent final acceptance. Follow [`QUALITY_GATES.md`](docs/process/QUALITY_GATES.md) for required gate selection and order. The runner uses a disposable AVD, never the dedicated online candidate; candidate automation/review ownership and privacy rules above remain in force.

On Windows, use the native environment first:

```powershell
$env:ANDROID_SDK_ROOT = '<your-android-sdk>'
./tools/Doctor.ps1
$base = git -C .. merge-base origin/main HEAD
python ../tools/android_api29.py --repo-root .. --base $base --head HEAD --mode high --build
```

For one focused diagnosis, select an exact task and test class; this is diagnostic evidence, not the full planner-selected gate:

```powershell
python ../tools/android_api29.py --repo-root .. --base $base --head HEAD --mode high `
  --task :app:connectedDebugAndroidTest `
  --test-class org.tsuyomi.android.UpdatesProductionJourneyInstrumentedTest
```

Repeat `--task` only for the bounded focused diagnosis. `--prepare-only` is for explicit-HIGH AVD environment/lifecycle inspection, not LOW preflight:

```powershell
python ../tools/android_api29.py --repo-root .. --base $base --head HEAD --mode high --prepare-only
```

If Windows native execution cannot resolve a confirmed host discrepancy, WSL2 is a last resort and requires usable KVM. Keep checkout and SDK on the Linux filesystem (not `/mnt/c`) and use the same runner/profile:

```bash
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
base="$(git -C .. merge-base origin/main HEAD)"
python3 ../tools/android_api29.py --repo-root .. --base "$base" --head HEAD --mode high --build
```

Runner details, AVD profile, evidence and diagnosis rules are owned by [`QUALITY_GATES.md`](docs/process/QUALITY_GATES.md) and [`tools/android_api29_profile.json`](tools/android_api29_profile.json); do not reproduce their policy here.

## Updates verification

- `UpdatesProductionJourneyInstrumentedTest` exercises the real MainActivity, signed fixture, WorkManager, Room, Detail and Reader. Select it through `:app:connectedDebugAndroidTest` and `-Pandroid.testInstrumentationRunnerArguments.class=org.tsuyomi.android.UpdatesProductionJourneyInstrumentedTest`, using the current resource-mode runner and the isolated AVD serial in `ANDROID_SERIAL`.
- Room migration, exact-anchor handling, recovery, cancellation and live-membership admission are owned by `:core:database:connectedDebugAndroidTest` (`RoomUpdateStoreInstrumentedTest` and the affected `Phase3MigrationInstrumentedTest` method), not an empty database JVM task.
- Notification-denial scheduling needs an isolated API 33+ device and `UpdateSchedulerInstrumentedTest#notification_denial_does_not_block_periodic_scheduling`; an API 29 run does not supply that evidence.
- For deliberate diagnostic inspection only, add both `-Ptsuyomi.keepP4cReviewState=true` and `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`. The Journey refuses non-fixture packages; the opt-in retains test data/preferences for host inspection. These flags do not create a separate acceptance obligation and must never be used on a persistent shared candidate or as permission to replace a canonical APK or preserve approval state.
