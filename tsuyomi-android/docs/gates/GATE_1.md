<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Gate 1 evidence

- Evidence date: 2026-08-09
- Technical baseline: **PASS**
- Public-history conversion: **this evidence is preserved in the clean Monorepo baseline**
- Hosted Gate 2 admission: **BLOCKED until the first push succeeds and protected-main required checks are configured**

The original local component histories are retained only in ignored private bundles. The public baseline is a clean Monorepo snapshot: the annotated `gate-1-baseline` tag and component version tags resolve to the same verified tree.

## Compatibility tuple

| Component | Gate 1 version | Public tag |
|---|---|---|
| `tsuyomi-protocol` | `0.1.0` | `protocol-v0.1.0` |
| `tsuyomi-extensions` | `0.1.0` | `extensions-v0.1.0` |
| `tsuyomi-android` | `versionName=0.1.0`, `versionCode=1` | `android-v0.1.0` |

`git rev-list -n 1 gate-1-baseline` is the authoritative public baseline SHA. Cross-component changes remain ordered protocol → extensions → Android inside one atomic Monorepo PR.

## Tool inputs

- Windows 11 x64
- JDK 17.0.12
- Gradle 8.14.3
- AGP 8.13.1
- Kotlin 2.3.0
- KSP 2.3.11
- Compose BOM 2026.06.01
- Python REUSE 6.2.0
- Node 25.2.1 / npm 11.6.2
- Android compile/target SDK 36; min SDK 29
- Emulator 37.1.11.0
- API 29 default x86_64 image

`tools/Doctor.ps1` passed and refreshed ignored `local.properties` from the environment SDK root. `tools/avd/Create-GateAvds.ps1` parsed successfully as PowerShell.

## Automated verification

### Android JVM, lint, build and screenshot validation

```text
./gradlew.bat --no-daemon --console=plain --dependency-verification strict \
  :app:assembleDebug :app:lintDebug \
  :core:ui:lintDebug :core:security:lintDebug :core:database:lintDebug \
  :shared:model:test :shared:locator:test :reader:engine:test \
  :core:display:testDebugUnitTest :core:files:testDebugUnitTest \
  :core:security:testDebugUnitTest \
  :core:ui:validateDebugScreenshotTest \
  :feature:library:validateDebugScreenshotTest \
  :feature:browse:validateDebugScreenshotTest \
  :feature:settings:validateDebugScreenshotTest
```

Original Gate 1 result: `BUILD SUCCESSFUL in 1m 2s`; 505 actionable tasks, 26 executed. A final focused build/lint/files regression after the last production fixes also passed in 26s with 402 actionable tasks.

The clean Monorepo conversion was revalidated with the same task set: `BUILD SUCCESSFUL in 22s`; 509 actionable tasks, 11 executed, 11 from cache and 487 up-to-date.

Coverage includes real production feature composables, deterministic dynamic color, standard/E-ink, portrait/landscape, `fontScale = 2.0`, auto-unknown/auto-recognized profiles, loading/error/offline, pagination, control states and E-ink no-scrim dialog behavior.

### API 29 instrumentation

```text
./gradlew.bat --no-daemon --console=plain \
  :feature:settings:connectedDebugAndroidTest \
  :core:ui:connectedDebugAndroidTest \
  :core:security:connectedDebugAndroidTest \
  :core:database:connectedDebugAndroidTest
```

Result: `BUILD SUCCESSFUL in 38s`; 246 actionable tasks. This includes credential unavailable/corruption behavior, Room concurrent progress/collection invariants, theme behavior and settings capability visibility.

After adding the explicit E-ink redraw accessibility action contract:

```text
./gradlew.bat --no-daemon --console=plain \
  :feature:settings:connectedDebugAndroidTest
```

Result: `BUILD SUCCESSFUL in 28s`; 115 actionable tasks. The visible “立即重绘界面” node is required to be enabled and expose a click action through Compose semantics.

### Dependency lock stability

```text
./gradlew.bat --no-daemon --console=plain --write-locks :app:assembleDebug
git diff --exit-code -- '**/*gradle.lockfile'
```

The first pass discovered and committed the missing `androidApis` lock configuration. The second pass succeeded in 14s with all 189 tasks up-to-date and no lockfile diff.

### Protocol

```text
npm ci
npm test
```

Result: 18 tests, 18 passed, 0 failed.

### License and repository policy

The clean Monorepo root runs one REUSE inventory and one artifact policy over Android, protocol, extensions, root governance, workflows, test sources, sanitized fixtures and golden evidence. The checker evaluates tracked and untracked non-ignored candidate files, so it also protects an unborn or pre-commit repository. Generated build/report/test-result files and local automation state remain ignored and are not evidence inputs.

## API 29 runtime acceptance

Device input:

```text
serial=emulator-5554
sdk=29
physical size=1264x1680
physical density=240
font_scale=1.0 (baseline restored)
rotation=0 (baseline restored)
```

Observed on the APK built from the Gate 1 implementation input:

1. Auto-unknown resolved to standard and did not expose “墨水屏刷新”, “立即重绘界面” or redraw count nodes.
2. Manual E-ink exposed the section and the explicit limitation copy; clicking “立即重绘界面” changed “已请求重绘 0 次” to “已请求重绘 1 次”.
3. Switching back to standard removed all three E-ink redraw nodes.
4. With `font_scale=2.0` and forced 1680×1264 landscape, top bar, navigation, empty-state description and action stayed visible within the application window.
5. DPAD input created a real focus on the selected library navigation item. UIAutomator consumed the platform accessibility hierarchy; labels, enabled state and focusability were exposed. The instrumentation test above separately protects the redraw action semantics used by TalkBack. The default API 29 image does not contain a TalkBack service, so audible speech output was not claimed.

Evidence images:

| File | SHA-256 |
|---|---|
| `docs/gates/GATE_1/assets/standard-settings.png` | `9b61faeb3e53802624ccc4ec86e17a2f22307c36755d2354f962197a56d5ca42` |
| `docs/gates/GATE_1/assets/eink-settings-redraw.png` | `6f38a52ceffcd6ab4e79d6efe8eeb0eea2776dad85b21d796ff8af316230cbed` |
| `app/build/outputs/apk/debug/app-debug.apk` | `6aebb84fb8cedfcfaf646ab4521a311be35d0aa2894e64284e91775762cb3fe5` |

The debug APK is a local verification artifact and is excluded from Git. The screenshots are licensed through `REUSE.toml` and are part of the tagged evidence.


## Non-blocking observed warnings

- API-level system bar color/contrast APIs used by `MainActivity` are deprecated on newer Android, but remain required for the API 29 window behavior exercised here.
- Android screenshot testing still emits its experimental-option warning and renderer security-manager warning.
- These warnings did not hide lint errors, test failures or dependency verification failures.

## Hosted governance prerequisite

The public repository is reserved at `https://github.com/Xfire233/Tsuyomi`. The clean Monorepo baseline must be pushed before GitHub branch protection and required checks can be enabled and verified.

Before any Gate 2 branch is opened:

1. push `main` and the new clean component/Gate annotated tags without importing old local histories;
2. wait for root and path-scoped GitHub Actions to pass;
3. protect `main` and require the applicable quality workflows;
4. require review from the resolvable owner in `.github/CODEOWNERS`;
5. record protection evidence in the next governance change.

Until these external controls exist, the technical baseline is complete but hosted Gate 2 admission remains blocked.
