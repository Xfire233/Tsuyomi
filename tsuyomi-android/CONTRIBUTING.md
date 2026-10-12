<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Contributing to Tsuyomi for Android

Start with the [monorepo contribution guide](../CONTRIBUTING.md). Android Studio is optional; command-line builds use the checked-in Gradle Wrapper. OMP, MCP servers, assistant roles and private maintainer devices are not required.

## Build from source

### Prerequisites

- JDK 17, Git and an Android SDK installed for your operating system.
- SDK Platform 37 and platform-tools. The native runtime also uses NDK `28.2.13676358` and CMake `3.22.1`; install them through the SDK manager and accept the Android SDK licenses.
- Node.js 20+ and npm for the pinned local-publication parser bundle and protocol conformance; Python 3.11+ for repository verification and the optional local CI runner.

Build versions are owned by [`build-logic`](build-logic/src/main/kotlin/org/tsuyomi/buildlogic), the [version catalog](gradle/libs.versions.toml) and the [native module](source/quickjs-runtime/build.gradle.kts). Android 10/API 29 is the runtime minimum, not the compile SDK.

Set `JAVA_HOME` to your JDK and `ANDROID_HOME` to your SDK, or configure the SDK in an ignored `local.properties` through Android Studio. `ANDROID_SDK_ROOT` is also accepted by the local runner. Use your own paths; do not commit SDK configuration. No globally installed Gradle or extension sibling checkout is needed.

From `tsuyomi-android`, choose the command for your shell:

**Windows PowerShell**

```powershell
$env:ANDROID_HOME = '<your-android-sdk>'
./gradlew.bat --console=plain --dependency-verification strict :app:assembleDebug
```

**Linux/macOS shell**

```sh
export ANDROID_HOME='<your-android-sdk>'
./gradlew --console=plain --dependency-verification strict :app:assembleDebug
```

Use `gradlew.bat` on Windows and `./gradlew` on Unix-like systems in the remaining Gradle examples. The Windows `Run-Gradle-Low.bat` and `Run-Gradle-High.bat` helpers are optional resource presets, not required entry points.

### Build variants

| Variant | Purpose | Application identity |
|---|---|---|
| `debug` | Isolated deterministic fixture development and regression tests | `org.tsuyomi.android.fixture` |
| `online` | Debug-signed development with real source services | `org.tsuyomi.android` |
| `release` | Release assembly, followed by external signing | `org.tsuyomi.android` |

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. Use `:app:assembleOnline` for real-service development on your own isolated test device. `online` shares the release application ID but **not** its signing identity: do not uninstall a user's release installation or clear its data to install a debug-signed build. Release preparation and publication are maintainer operations governed by [`RELEASE_PROCEDURE.md`](docs/process/RELEASE_PROCEDURE.md).

## Contribution rules

- Preserve the dependency direction in [`MODULES.md`](docs/architecture/MODULES.md) and the platform-neutral extension contract.
- Account/cloud/telemetry changes need an explicit architecture decision; CAPTCHA and anti-bot bypass remain prohibited. Never commit credentials, content dumps or signing material.
- Dependency changes update the version catalog, lock state, verification metadata and third-party notices together. Do not disable strict dependency verification to get a build through.
- Follow [`OPTION_APPLICABILITY.md`](docs/design/OPTION_APPLICABILITY.md): a stored field or enum does not prove that a control is implemented.
- Bind formal gate results to immutable source/artifact inputs. Keep generated logs, device captures and reports ignored; public PRs must still summarize reproducible commands, results and limitations.
- A manifest, parser, Host API or signed-policy change must verify the affected deterministic public HXP inputs before instrumentation. Signed-fixture regression stays in CI; it is not a second manual acceptance round.

## Verification

Select only changed and directly coupled local tasks using [`QUALITY_GATES.md`](docs/process/QUALITY_GATES.md); the [CI planner](../tools/android_ci_plan.py) owns independently authorized local admission and protected hosted checks, not routine scope expansion. For example, the app's basic build, JVM tests and lint can be invoked together **when those tasks are affected**:

```text
./gradlew --console=plain --dependency-verification strict :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

For UI work, follow [`UI_CONSTITUTION.md`](docs/design/UI_CONSTITUTION.md) and [`UI_ATLAS.md`](docs/design/UI_ATLAS.md); the [review policy](../.agents/skills/tsuyomi-android-review/review-policy.json) selects active/deferred profiles. The repository [review procedure](../.agents/skills/tsuyomi-android-review/SKILL.md) can be read directly without installing an assistant. Equivalent IDE/SDK tools may supply the required evidence; record tool versions and device facts. Human qualitative approval remains separate from tests.

Use a disposable emulator for instrumentation that clears fixture data. Keep it separate from a persistent real-service review candidate or personal installation. Record the actual device identity, API, image, display settings and WebView version; another developer need not possess the maintainer's named AVD or private test account. Required protected GitHub checks remain independent final admission.

## Local API 29 runner

The local runner is an optional fast reproduction/preflight tool for contributors with a compatible emulator host. `--mode high` selects a resource mode; a full planner-selected local `--build` preflight is a separately chosen admission task, not an automatic next step after focused UI validation. `--mode low` is rejected, including for `--prepare-only`; `--mode ci` is reserved for GitHub Actions. Assistant-session authorization and resource modes are described separately in [`QUALITY_GATES.md`](docs/process/QUALITY_GATES.md) and [`TOOLING.md`](../TOOLING.md#execution-resource-modes).

Install SDK command-line tools, emulator, platform-tools and the exact image from [`android_api29_profile.json`](../tools/android_api29_profile.json). The profile currently uses an x86_64 API 29 image; do not assume native support on ARM hosts. Linux requires usable KVM; Windows requires supported emulator acceleration. Hosted Linux CI owns the protected baseline, not a claim of identical Windows/macOS execution. If your host cannot run this image, document the limit and rely on hosted checks for that lane; do not substitute another image and call it equivalent.

From `tsuyomi-android`, with your SDK configured:

**Windows PowerShell**

```powershell
$base = git -C .. merge-base origin/main HEAD
python ../tools/android_api29.py --repo-root .. --base $base --head HEAD --mode high --build
```

**Linux shell**

```sh
base="$(git -C .. merge-base origin/main HEAD)"
python3 ../tools/android_api29.py --repo-root .. --base "$base" --head HEAD --mode high --build
```

If the target branch uses another remote, use that branch instead of `origin/main`. If no valid base is available, omit `--base`; the planner falls back conservatively to a full plan. Local runs require `--head HEAD` and include the recorded worktree overlay.

For a focused diagnosis of a changed path, replace `--build` with `--task :app:connectedDebugAndroidTest --test-class org.tsuyomi.android.UpdatesProductionJourneyInstrumentedTest` **only if that named test is affected**. Such a run is diagnostic evidence, not a full gate. `--prepare-only` also creates an AVD and is not an implicit permission to do so. Adjacent sequences and a full admission gate need their own scope decision; a failure does not authorize automatic escalation or repeated full-matrix runs.

Before display configuration or app/test dispatch, the runner waits for a usable shell, completed boot and actual user 0 `RUNNING_UNLOCKED`, then explicitly prepares the pinned image's debuggable `userdebug` runtime. Preparation requests `adb root` once and waits at most 20 seconds, including that request, for a real UID 0 shell; a successful root command alone does not establish transport readiness. Only the exact exit-1 root reply `adb: unable to connect for root: closed\n` may also enter that same UID probe: its acknowledgement is unknown, not proof of root, and no second root request is issued. UID 2000 or the known closed/offline/missing-device query outcomes remain pending only within the existing deadline; other failures or failure to observe UID 0 are terminal. After actual UID 0, preparation must re-prove the owned AVD identity and unchanged image-provided heap properties before continuing; those properties are never overridden.

The pinned image's `/init.zygote64_32.rc` does not make primary `onrestart` cycle the secondary; secondary `onrestart` instead restarts the primary. Preparation therefore uses explicit `stop`/`start`, not a `restart` hook: stop secondary then primary once, verify both init services stopped and the old primary, secondary and `system_server` processes gone, then require both `display` and `activity` framework publications to report `not found` before starting either service. Old process exit alone does not prove that their Binder publications have retired. Early system-context creation explicitly supports an absent display service, whereas a discoverable dead display Binder can fail before new services start. Only these two publication-retirement conditions are polled; unexpected command/output errors are terminal, and incomplete retirement prevents any new start. First/last publication observations, the last found observation when present, and retirement timings remain in the existing preparation receipt. Then start primary and secondary once. Readiness requires new init-owned Zygotes, a new primary-owned `system_server`, unlocked user 0 and a functional user-specific framework-package query. Stop, publication retirement, start and readiness share the same 60-second bound within the existing profile boot deadline. A runtime crash/replacement is a failure, not permission to retry initialization.

Published `activity` service presence is not callable readiness. During this same bounded startup wait, the exact user-state query's Binder `DEAD_OBJECT` response (`Broken pipe (32)`, exit 224) remains pending only after an immediate check proves the new primary, secondary and `system_server` process identities unchanged. The failed response and post-query process snapshot are preserved in the existing preparation receipt. A changed/missing runtime or any other query failure is terminal; even with unchanged processes, readiness still requires a successful `RUNNING_UNLOCKED` query and functional package query before the deadline. This does not classify a broken Binder as harmless or start another runtime cycle.

Denied root, wrong AVD identity, unexpected commands/states or changed heap properties fail the run; there is no heap override, fallback image or restart campaign. This preparation runs once per owned boot, including `--prepare-only`, not once per test/module. It terminates framework/app processes and must not be applied to a personal or persistent review device.

`environment.json` includes the runtime-preparation phase, capability, exact root output/return code, closed-root-transport observation separately from the observed shell UID, old/new runtime parentage, stop/start requests, publication-retirement observations, unchanged properties, user/package readiness and timings; partial preparation evidence and command output survive failure in the same receipt and `adb.log`. This is initialization evidence, not a measurement of an application's inherited heap or proof of 32-bit application behavior. Diagnosis of those properties requires native application/process evidence on the actual prepared image; published services or readable heap properties alone are insufficient.

The runner stops its emulator and removes its disposable AVD in `finally`, including failed runs; a task-scoped manual AVD needs the same lifecycle owner and cleanup. Neither may become an additional human acceptance target. Evidence is saved under `build/api29-ci/`. Compare recorded image revisions, fingerprints, WebView versions and phase timings when diagnosing host differences; a local result never bypasses hosted checks. WSL2 is an optional Windows alternative only with usable KVM and checkout/SDK on the Linux filesystem, not a prerequisite for other contributors.

## Updates verification

- `UpdatesProductionJourneyInstrumentedTest` covers MainActivity, signed fixture, WorkManager, Room, Detail and Reader through `:app:connectedDebugAndroidTest`.
- Storage migration and recovery use `:core:database:connectedDebugAndroidTest` (`RoomUpdateStoreInstrumentedTest` and affected `Phase3MigrationInstrumentedTest` methods), not an empty database JVM task.
- Notification-denial scheduling requires an isolated API 33+ device and `UpdateSchedulerInstrumentedTest#notification_denial_does_not_block_periodic_scheduling`; API 29 does not cover that permission.
- For deliberate diagnostic inspection only, `-Ptsuyomi.keepP4cReviewState=true` together with `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` retains fixture test state. Never use these flags on a persistent shared candidate or treat them as permission to replace an approved APK.
