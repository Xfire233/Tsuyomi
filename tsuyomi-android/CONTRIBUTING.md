<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Contributing to Tsuyomi for Android

Start with the [monorepo contribution guide](../CONTRIBUTING.md). Android Studio is optional; command-line builds use the checked-in Gradle Wrapper. OMP, MCP servers, assistant roles and private maintainer devices are not required.

## Build from source

### Prerequisites

- JDK 17, Git and an Android SDK installed for your operating system.
- SDK Platform 37 and platform-tools. The native runtime also uses NDK `28.2.13676358` and CMake `3.22.1`; install them through the SDK manager and accept the Android SDK licenses.
- Python 3.11+ for repository verification and the optional local CI runner. Node.js LTS and npm are needed for protocol conformance, not a basic Android build.

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

Select affected tasks using [`QUALITY_GATES.md`](docs/process/QUALITY_GATES.md) and the [CI planner](../tools/android_ci_plan.py), rather than running every module after every edit. For example, the app's basic build, JVM tests and lint can be invoked together:

```text
./gradlew --console=plain --dependency-verification strict :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

For UI work, follow [`UI_CONSTITUTION.md`](docs/design/UI_CONSTITUTION.md) and [`UI_ATLAS.md`](docs/design/UI_ATLAS.md); the [review policy](../.agents/skills/tsuyomi-android-review/review-policy.json) selects active/deferred profiles. The repository [review procedure](../.agents/skills/tsuyomi-android-review/SKILL.md) can be read directly without installing an assistant. Equivalent IDE/SDK tools may supply the required evidence; record tool versions and device facts. Human qualitative approval remains separate from tests.

Use a disposable emulator for instrumentation that clears fixture data. Keep it separate from a persistent real-service review candidate or personal installation. Record the actual device identity, API, image, display settings and WebView version; another developer need not possess the maintainer's named AVD or private test account. Required protected GitHub checks remain independent final admission.

## Local API 29 runner

The local runner is an optional fast reproduction/preflight tool for contributors with a compatible emulator host. Choosing `--mode high` explicitly opts into its resource-intensive local mode; the full preflight is then the planner-selected `--build` run. `--mode low` is rejected, including for `--prepare-only`; `--mode ci` is reserved for GitHub Actions. Assistant-session scheduling rules are described separately in [`TOOLING.md`](../TOOLING.md#execution-resource-modes).

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

For a focused diagnosis, replace `--build` with `--task :app:connectedDebugAndroidTest --test-class org.tsuyomi.android.UpdatesProductionJourneyInstrumentedTest`. Such a run is diagnostic evidence, not the full gate. Use `--prepare-only` only for emulator lifecycle/environment inspection. Diagnose a failure with the exact affected test, then its adjacent sequence, before one stable full preflight; do not repeatedly run the entire matrix while debugging.

The runner creates and cleans its own disposable AVD. Evidence is saved under `build/api29-ci/`. Compare recorded image revisions, fingerprints, WebView versions and phase timings when diagnosing host differences; a local result never bypasses hosted protected checks. WSL2 is an optional Windows alternative only with usable KVM and checkout/SDK on the Linux filesystem, not a prerequisite for other contributors.

## Updates verification

- `UpdatesProductionJourneyInstrumentedTest` covers MainActivity, signed fixture, WorkManager, Room, Detail and Reader through `:app:connectedDebugAndroidTest`.
- Storage migration and recovery use `:core:database:connectedDebugAndroidTest` (`RoomUpdateStoreInstrumentedTest` and affected `Phase3MigrationInstrumentedTest` methods), not an empty database JVM task.
- Notification-denial scheduling requires an isolated API 33+ device and `UpdateSchedulerInstrumentedTest#notification_denial_does_not_block_periodic_scheduling`; API 29 does not cover that permission.
- For deliberate diagnostic inspection only, `-Ptsuyomi.keepP4cReviewState=true` together with `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` retains fixture test state. Never use these flags on a persistent shared candidate or treat them as permission to replace an approved APK.
