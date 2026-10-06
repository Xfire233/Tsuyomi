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

## Review and pull requests

- Discuss substantial behavior, architecture or dependency changes in an issue before implementation. Describe scope, risks, verification and the rollback boundary; small fixes and documentation changes do not need a separate planning ceremony.
- Follow the affected [Android](tsuyomi-android/CONTRIBUTING.md) or [protocol](tsuyomi-protocol/CONTRIBUTING.md) guide. Include the relevant commands, results and environmental limitations in the PR; mark unexecuted checks explicitly.
- UI changes follow the product contracts and evidence requirements in [`UI_ATLAS.md`](tsuyomi-android/docs/design/UI_ATLAS.md). The versioned [review policy](.agents/skills/tsuyomi-android-review/review-policy.json) selects active/deferred profiles. Human visual/accessibility approval is not supplied by CI or an assistant.
- Required GitHub checks and maintainer review govern merge. A local check does not replace protected hosted checks, and contributors must not update screenshot references merely to make a failure pass.
- OMP, named assistant roles, Skills discovery, MCP servers, private memory and a maintainer's local handoff are not contribution prerequisites. Optional assistant integration is documented in [`TOOLING.md`](TOOLING.md); ordinary contributors can use their own editor and tools.

## Checks

Use Python 3.11 or newer for repository tools and a supported Node.js LTS with npm for protocol work. Use `python3` instead of `python` where that is your interpreter name. Install REUSE 6.2.0 in your own Python environment to match the [repository workflow](.github/workflows/repository-quality.yml).

From the monorepo root, common repository checks are:

```text
python -m reuse lint
python tools/check_repository.py
```

When changing repository tooling, CI planning or the review workflow, also run the affected existing suites:

```text
python -m unittest tools/check_repository_test.py tools/android_ci_plan_test.py
python -m unittest discover -s .agents/skills/tsuyomi-android-review/scripts -p "*_test.py"
```

For protocol changes, run from `tsuyomi-protocol`:

```text
npm ci
npm test
```

Android setup, platform-specific build commands and optional local emulator checks are in the [Android guide](tsuyomi-android/CONTRIBUTING.md). GitHub Actions selects affected Android tasks through [`tools/android_ci_plan.py`](tools/android_ci_plan.py); do not treat a copied full task list as the current CI contract.

## Licensing and secrets

- New source files require SPDX copyright and license identifiers.
- Copied or adapted upstream work requires compatibility review, pinned provenance, retained notices, and an updated `THIRD_PARTY_NOTICES.md` in the same PR.
- Never commit cookies, tokens, accounts, private keys, signing material, unredacted site content, databases, local SDK paths, or private test data.
- The host/protocol remain Apache-2.0; independent derivative extensions are AGPL-3.0-only with historical Apache obligations retained, as owned by ADR 0001. Adopting third-party copyleft implementation into the host/protocol requires separate compatibility review. Repository separation grants no license exception; public behavior research is not permission to copy or translate protected code.
