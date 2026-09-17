<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi Android 发布流程

本文件是 Android 宿主从「已合并的 `main`」到「可安装的公开制品」的唯一可复现流程。它规定步骤、命令、断言和发布后验证，不授予发布授权；授权来自用户在 `PHASE_4.md` 记录范围内的明确指示。

`G0–G7` 门槛判定见 [`QUALITY_GATES.md`](QUALITY_GATES.md)；tag 命名、不可移动性和回退见 [`REPOSITORY_GOVERNANCE.md`](REPOSITORY_GOVERNANCE.md)；分发渠道决策见 [`ADR 0002`](../adr/0002-android-baseline-and-distribution.md)。公开分发目标是 GitHub Releases 和 F-Droid，不依赖 Google Play Services。

## 1. 前置条件

发布前必须同时满足：

- 待发布提交已在受保护的 `main` 上，且工作树干净（`git status --porcelain` 为空）；
- 五个 required checks 在待发布提交上全部成功（`repository-policy`、`protocol-conformance`、`extensions-baseline`、`android-build-test-lint-goldens`、`android-api29-instrumentation`）；
- `python tools/check_repository.py`（仓库根执行）通过；
- 本地 `HIGH` 门禁在该提交或其唯一差异为文档/工具的同树提交上通过；
- 用户已明确授权本次发布；物理手机验收状态按第 7 节如实记录，不得用模拟器证据代替。

## 2. 版本身份

- `versionCode` 单调递增；`versionName` 使用 SemVer，预发布后缀写作 `0.3.0-beta.4`。
- **私有机密候选**与**发布制品**必须可区分：私有候选通过 `-Ptsuyomi.buildFingerprint=<value>` 追加 `+<value>` 后缀，发布制品使用不带后缀的干净 `versionName`。
- 两者的 `versionCode`/`versionName` 都必须能从 `aapt2 dump badging` 和 `dumpsys package org.tsuyomi.android` 读出，避免 `install -r` 静默保留旧构建。

## 3. 构建

在已合并的待发布提交上执行：

```bash
cd tsuyomi-android
sh ./gradlew :app:assembleRelease --dependency-verification strict
```

产物为 `app/build/outputs/apk/release/app-release-unsigned.apk`。不运行 `--write-locks`；依赖锁只由 dependency input 变更时的独立维护步骤更新。

官方 catalog 根公钥由 `tsuyomi-android/gradle.properties` 的 `tsuyomi.repository.keyId` / `tsuyomi.repository.publicKey` 提供；未配置的构建会把仓库报告为不可用，不能作为发布制品。

## 4. Gradle 之外签名

签名**不得**在 Gradle 中进行：keystore 口令一旦进入 configuration cache、build scan 或 CI 作业输入即视为泄漏。

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Publish-AndroidRelease.ps1 `
  -VersionName 0.3.0-beta.4 -VersionCode 6 -SourceRevision (git rev-parse HEAD)
```

脚本按顺序执行并在任一断言失败时终止：

1. 解析本机 build-tools（最高版本），定位 `java`、`apksigner.jar`、`zipalign`、`aapt2`；
2. 校验 `certificate.der` 的 SHA-256 等于保留的发布身份 `0be46968ea9f184b8a5857334d4e4d46eb67ea14b0601c07a5fa3b07f00a7bb7`，否则拒绝签名；
3. 断言 `aapt2 dump badging` 的包名、`versionCode`、`versionName` 与本次发布一致；
4. 断言 APK 既非 `debuggable` 也非 `testOnly`；
5. 断言 APK 未内嵌 `assets/*.hxp`（debug 变体的 testkit fixture 不得进入发布制品）；
6. 目标目录不存在（已发布制品不可覆盖）；
7. `zipalign -P 16 4`；
8. `apksigner sign --min-sdk-version 29 --v1-signing-enabled false --v2-signing-enabled false --v3-signing-enabled true --v4-signing-enabled false`，口令仅通过进程内环境变量传递，结束时清除并清零 BSTR；
9. `zipalign -c -P 16 4` 复核 16KiB 对齐；
10. `apksigner verify --verbose --print-certs`，断言签名证书指纹并断言签名方案恰为 v3；
11. 写出 `release.json` 制品记录。

默认输出目录为 `<monorepo>/.local/release/<versionName>/`，包含 `Tsuyomi-<versionName>.apk` 和 `release.json`。`.local` 与 `.apk` 都在仓库制品策略禁止列表内，因此制品与记录**不得**提交。

### 密钥托管边界

私钥、口令文件和证书位于工作树之外（默认 `%USERPROFILE%\.tsuyomi\signing\android-release\`），口令以 Windows 当前用户 DPAPI 保护。该保护是**同机**恢复手段，不构成异地或独立恢复副本；公开发布前必须建立独立恢复托管。这一点由 `release.json` 的 `keyCustody` 字段如实记录。

## 5. 发布制品记录

`release.json` 至少记录：`applicationId`、`variant`、`versionName`、`versionCode`、`minSdk`、`debuggable`、`testOnly`、`sourceRevision`、`unsignedApkSha256`、`apkSha256`、`apkBytes`、`certificateSha256`、`signatureSchemes`、`alignment16KiBVerified`、`buildTools`、`signatureVerification`、`keyCustody`、`phoneAcceptance`。

同一组事实按 `G6` 记入 `docs/phases/PHASE_4.md`。`release.json` 是本地记录，Phase 文档是版本化证据。

## 6. 发布

1. 在**制品对应的源提交**上创建 annotated tag：`android-v<versionName>`（预发布同样使用该前缀，例如 `android-v0.3.0-beta.4`）。tag 消息记录所属 Phase 文档、宿主/协议版本与制品摘要。tag 一旦推送不得移动。
2. 以该 tag 创建 GitHub Release。预发布必须标记 `prerelease`；`draft` 与 `prerelease` 都不等于稳定版，稳定化需要一次新的、明确的用户决定。
3. 上传资产：`Tsuyomi-<versionName>.apk`，以及可选源码归档。
4. Release 正文必须包含机器可校验的摘要行，供第 7 节工作流解析：

```text
apk-asset: Tsuyomi-0.3.0-beta.4.apk
apk-sha256: <64 hex>
apk-bytes: <decimal>
apk-version-code: <decimal>
signer-certificate-sha256: <64 hex>
source-revision: <40 hex>
```

## 7. 发布后验证

`.github/workflows/android-release.yml` 在 Release 发布时触发，对**已上传的资产**执行独立校验：下载资产、核对上述摘要行、断言签名证书指纹等于工作流内置的固定值（不接受正文自证）、断言签名方案恰为 v3、断言 16KiB 对齐、断言包名/版本身份、断言非 `debuggable`/`testOnly`，并断言未内嵌 `assets/*.hxp`。任一断言失败即为发布事故：不移动 tag，修复后发布新的 patch 版本。

该工作流不持有私钥，因此不签名、不改写已发布资产。

## 8. 验收与稳定化

- 公开发布**不**代替物理手机验收。用户必须在自己的手机上对**该确切制品**给出显式、绑定制品的接受或拒绝；本地构建、模拟器证据、安装成功和任何 agent 结论都不能提供该批准。
- 预发布到稳定版的转换需要用户单独、明确的决定，并在 `PHASE_4.md` 记录。
- 被拒绝时：修复行为，提升 `versionCode`，产生新的可区分制品并重新验收；不覆盖或替换已发布制品。

## 9. 回退

不移动已发布 tag、不替换已发布资产。回退按 [`REPOSITORY_GOVERNANCE.md`](REPOSITORY_GOVERNANCE.md) 执行：revert 引入问题的提交，保留数据，重跑受影响检查，再发布新的 patch 版本。
