<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Contributing

Tsuyomi's monorepo contains independently versioned Android and protocol components; extensions are maintained in the independent `Chachaanteng/tsuyomi-extensions` repository.

## Change boundaries

- Protocol behavior changes update `tsuyomi-protocol` schemas, valid/invalid fixtures, conformance tests, version, and changelog first within the same PR.
- Extension implementation and its contribution policy live in `Chachaanteng/tsuyomi-extensions`; every origin, capability, cookie scope, controlled WebView request and storage requirement remains explicitly declared. CAPTCHA or anti-bot bypass is prohibited.
- Android changes preserve module dependency direction, API 29 behavior, shared Standard/E-ink business state, and the option-applicability rules.
- Cross-component changes update affected Android/protocol consumers atomically; cross-repository extension integration pins an immutable commit and artifact digest. Host builds must not require an extension sibling checkout. Do not leave temporary shims, dual parsers, aliases or dead settings.

## Public quality evidence

Test source, deterministic fixtures, screenshot goldens, and GitHub Actions remain versioned so contributors and F-Droid reviewers can reproduce behavior. Generated reports, APKs, local screenshots, emulator state, credentials, and local automation/assistant files must remain ignored.

## 实施前审阅与授权

- 每项实现先提交 Planner 计划包：范围、风险、验收矩阵、回退边界和可回退提交顺序。
- UI、交互、导航、文案、视觉层级或 golden 变更必须先获 Designer 审阅；整体代码计划必须先获 Adviser 审阅。
- 未明确声明“无人值守”或“自主批准执行”时，审阅完成后必须等待用户确认才开始实现。该授权只覆盖审阅过的范围。
- 开 PR 后及最终功能变更后必须有 Adviser PR review；required checks 全绿后仍只由用户确认合并。
- Android UI、导航、交互、显示配置或无障碍改动先按 `.agents/skills/tsuyomi-android-review/SKILL.md` 执行 `UI-R1`；当前活跃/延后 profile 只读取相邻的 `review-policy.json`。`.local/` 影响报告只选择节点和验证层，不表示批准。

Before opening a PR, run the checks relevant to each changed path:

```text
python -m reuse lint
python tools/check_repository.py
python -m unittest tools/check_repository_test.py
python -m unittest discover -s .agents/skills/tsuyomi-android-review/scripts -p '*_test.py'

cd tsuyomi-protocol
npm ci
npm test

cd ../tsuyomi-android
./gradlew --no-daemon --console=plain --dependency-verification strict \
  :app:assembleDebug :app:lintDebug \
  :core:files:testDebugUnitTest \
  :core:ui:validateDebugScreenshotTest \
  :feature:library:validateDebugScreenshotTest \
  :feature:browse:validateDebugScreenshotTest \
  :feature:settings:validateDebugScreenshotTest

```

## Licensing and secrets

- New source files require SPDX copyright and license identifiers.
- Copied or adapted upstream work requires compatibility review, pinned provenance, retained notices, and an updated `THIRD_PARTY_NOTICES.md` in the same PR.
- Never commit cookies, tokens, accounts, private keys, signing material, unredacted site content, databases, local SDK paths, or private test data.
- The host/protocol remain Apache-2.0; independent derivative extensions are AGPL-3.0-only with historical Apache obligations retained, as owned by ADR 0001. Adopting third-party copyleft implementation into the host/protocol requires separate compatibility review. Repository separation grants no license exception; public behavior research is not permission to copy or translate protected code.
