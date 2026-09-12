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
- A manifest, extension parser, Host API or signed-policy change must rebuild and verify the deterministic public HXP fixture before Android instrumentation; stale signed fixtures are invalid evidence, not a runtime fallback.
- Local API 29 CI is authorized only in explicit `HIGH` mode. In `HIGH`, before push run `python ../tools/android_api29.py --base <merge-base> --head HEAD --mode high --build`; it creates and cleans its own disposable automation AVD. `--build` adds planner-selected build tasks before instrumentation. The local planned gate is required in this mode, not a retry loop.
- In `HIGH`, debug only in this fixed order: `exact reproduction → affected test → adjacent sequence → local planner-selected gate → hosted protected checks`. A focused `--task`/`--test-class` run is diagnostic evidence, not the complete gate. The local planned gate may run its entire planner-selected matrix; do not prohibit or skip it merely because hosted CI will independently run it.
- In `LOW`, do not run local API 29 CI at all—not even `--prepare-only`, a local matrix, or a local preflight. Bounded direct development compilation remains separate; hosted protected checks are the only CI path and remain final acceptance.
- Resolve active/deferred profiles from `.agents/skills/tsuyomi-android-review/review-policy.json` before test selection. Frozen-profile screenshot and instrumentation tests remain retained but disabled from routine gates until an explicit policy restoration.
- Hosted protected checks remain independently required final acceptance: a local result cannot bypass, replace or authorize their success. Compare recorded image revision, system fingerprint and WebView version when diagnosing a host discrepancy; never claim local and hosted emulator builds are identical. Performance comparisons must use equal scope, selected tasks, profile/image revision and host evidence (as well as the same resolved `HEAD` and worktree-overlay policy), and compare recorded phase timings rather than a single total.
- A repeated failure signature across Journeys or a focused-pass/class-order-fail split is a harness/lifecycle incident until disproved. Capture diagnostic device/log state and classify the shared boundary; never make speculative production patches or modify production behavior solely to satisfy Compose idling.
- `HIGH` increases compilation throughput and is the sole local API 29 runner mode; it omits `--no-daemon` for precompile and serialized instrumentation so Gradle may reuse a daemon between phases, while its runner serializes device execution. `LOW` remains the default for bounded direct interactive Gradle work through `tools/Run-Gradle-Low.bat`, but never grants local CI. `tools/android_api29.py --mode ci` is reserved for actual hosted execution with `GITHUB_ACTIONS=true` and retains `--no-daemon`.

## Updates verification

- `UpdatesProductionJourneyInstrumentedTest` exercises the real MainActivity, signed fixture, WorkManager, Room, Detail and Reader. Select it through `:app:connectedDebugAndroidTest` and `-Pandroid.testInstrumentationRunnerArguments.class=org.tsuyomi.android.UpdatesProductionJourneyInstrumentedTest`, using the current resource-mode runner and the isolated AVD serial in `ANDROID_SERIAL`.
- Room migration, exact-anchor handling, recovery, cancellation and live-membership admission are owned by `:core:database:connectedDebugAndroidTest` (`RoomUpdateStoreInstrumentedTest` and the affected `Phase3MigrationInstrumentedTest` method), not an empty database JVM task.
- Notification-denial scheduling needs an isolated API 33+ device and `UpdateSchedulerInstrumentedTest#notification_denial_does_not_block_periodic_scheduling`; an API 29 run does not supply that evidence.
- For a deliberate post-Journey visual pass only, add both `-Ptsuyomi.keepP4cReviewState=true` and `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`. The Journey refuses non-fixture packages; the opt-in retains test data/preferences for host inspection. Normal runs clean their scoped state. Never use these flags as permission to replace a canonical APK or preserve approval state.
