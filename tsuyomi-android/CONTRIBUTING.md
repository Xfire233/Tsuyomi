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
- Map each Android change to its smallest compile/test/Journey evidence before running Gradle. Debug in the fixed order `exact reproduction → affected test → adjacent sequence → affected active-profile group → one required final suite`; never use a complete class or module matrix as the inner retry loop.
- Resolve active/deferred profiles from `.agents/skills/tsuyomi-android-review/review-policy.json` before test selection. Frozen-profile screenshot and instrumentation tests remain retained but disabled from routine gates until an explicit policy restoration.
- PR CI owns the complete planner-selected matrix after bounded local proof. Reproduce only a specific CI failure locally; do not rerun the same complete matrix on both local and hosted devices without an unavailable-environment reason.
- A repeated failure signature across Journeys or a focused-pass/class-order-fail split is a harness/lifecycle incident until disproved. Stop broad reruns, report the boundary change, and never modify production behavior only to satisfy Compose idling.
- Interactive local Gradle commands use `tools/Run-Gradle-Low.bat` by default for two-worker sequential execution while sharing the workstation; explicit high-speed work uses `tools/Run-Gradle-High.bat` to consume all logical processors with Gradle parallelism. Resource mode never reduces the required evidence scope.

## Updates verification

- `UpdatesProductionJourneyInstrumentedTest` exercises the real MainActivity, signed fixture, WorkManager, Room, Detail and Reader. Select it through `:app:connectedDebugAndroidTest` and `-Pandroid.testInstrumentationRunnerArguments.class=org.tsuyomi.android.UpdatesProductionJourneyInstrumentedTest`, using the current resource-mode runner and the isolated AVD serial in `ANDROID_SERIAL`.
- Room migration, exact-anchor handling, recovery, cancellation and live-membership admission are owned by `:core:database:connectedDebugAndroidTest` (`RoomUpdateStoreInstrumentedTest` and the affected `Phase3MigrationInstrumentedTest` method), not an empty database JVM task.
- Notification-denial scheduling needs an isolated API 33+ device and `UpdateSchedulerInstrumentedTest#notification_denial_does_not_block_periodic_scheduling`; an API 29 run does not supply that evidence.
- For a deliberate post-Journey visual pass only, add both `-Ptsuyomi.keepP4cReviewState=true` and `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`. The Journey refuses non-fixture packages; the opt-in retains test data/preferences for host inspection. Normal runs clean their scoped state. Never use these flags as permission to replace a canonical APK or preserve approval state.
