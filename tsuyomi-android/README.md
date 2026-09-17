<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi Android

本地优先、面向墨水屏的原生 Android 轻小说阅读器。使用 Kotlin 与 Jetpack Compose 构建，目标平台为 Android 10 及以上版本（`minSdk 29`）。

> [!NOTE]
> 项目已完成 **Phase 0 到 Phase 3** 的基础设施、数据与契约；**Phase 4 Standard 功能已完成主要实现并进入验收收口**。在线搜索、阅读、Library、远端书架镜像与显式回写、更新协调，以及独立签名来源仓库的发现、安装、更新、卸载和订阅均已接通。**首个公开 Beta 预发布 `android-v0.3.0-beta.4` 已发布**：它是预发布而非稳定版，物理真机验收与人工视觉、辅助技术、完整真实在线验收仍待完成；墨水屏全局适配（E-ink）保持冻结。

## 项目目标

- **本地优先**：不要求 Tsuyomi 账号，不依赖 Google Play Services，不接入遥测、远程 feature flag 或自动崩溃上报。
- **原生 Android**：Kotlin、Jetpack Compose、Room、DataStore；不继承 Flutter 页面树或组件实现。
- **全局墨水屏模式**：Standard 与 E-ink 复用同一路由、业务状态和持久数据；E-ink 是应用根级显示配置，不是阅读器内的局部开关。
- **来源与宿主分离**：内容来源以签名、平台无关的 `.hxp` 包交付，通过版本化 Host API 运行；不是 Android APK 插件。维护源码位于独立的 [tsuyomi-extensions](https://github.com/Chachaanteng/tsuyomi-extensions) 仓库。
- **语义阅读进度**：持久化章节与文本语义位置，而不是依赖易失效的页码、像素偏移或滚动百分比。
- **可审计发布**：面向 GitHub Releases 与 F-Droid；依赖锁、校验元数据、第三方声明、REUSE、Phase 证据和 gate 判定随代码版本化。

## 当前状态与项目进度

项目采用阶段化递进架构，已完成的核心能力如下：

- **Phase 0：协议与安全基线（已完成）**：HXP 签名扩展包规范、Host API 1.2、加密安全凭据分区（Android Keystore AES-GCM）、跨平台传输/备份协议契约。
- **Phase 1：Android 宿主骨架与全局显示模式（已完成）**：Jetpack Compose + Material 3 原生界面架构、Room 数据模型与事务不变量、Standard / E-ink 双模式配置架构与 API 29 验证基线。
- **Phase 2：Wenku8 只读垂直阅读切片（已完成）**：QuickJS 隔离执行沙箱、受控 WebView 登录验证、搜索 → 详情 → 目录 → 章节阅读 → 语义进度保存与恢复端到端闭环。
- **Phase 3：本地书架与迁移体系（已完成）**：多层级系统/手动/智能收藏夹、`tsuyomi-transfer` 数据导入导出、从旧版 Hikari 无凭据安全平滑迁移、远端书架只读拉取与同步。
- **Phase 4A：Standard 交互生产实现（实现完成，验收待收口）**：
  - **Library 内容系统**：网格、列表、紧凑三布局，固定 `书架 / 继续阅读 / 稍后再读` 点按分栏，统一筛选与排序，收藏夹、网站镜像与书籍共享同一根内容流。
  - **拖拽、多选与本地保留**：支持连续长按拖拽、批量归类和自定义排序；Room v10 将本地书架归属与书籍元数据分离，移出书架不删除阅读进度、标注、缓存或网站镜像状态。
  - **来源页面**：原生呈现 Wenku8 推荐栏目与榜单；来源切换先验证新会话，再原子替换当前会话，失败时继续保留可用来源。
- **Phase 4B：网站书架镜像与显式回写（实现完成，验收待收口）**：
  - 持久化网站分组与书籍镜像；`ADD / MOVE / REMOVE` 分别授权、分别对账，网站操作不隐式改变本地书架状态。
  - 取消、失败、恢复和重试均保留明确状态；不会把一次模糊网络结果伪装成成功写入。
- **Phase 4C：更新协调与来源生命周期（实现完成，验收待收口）**：
  - 本地更新收件箱、默认关闭的计划检查、精确处理与撤销，以及来源/书籍排除和逐章完成联动。
  - Browse 提供官方目录与用户订阅仓库、签名包审批、精确第三方包同意、安全卸载/重装和保留安全历史；下载截断仅在总期限内有限恢复，持续失败提供来源正确的重试入口。
- **首个公开 Beta 预发布（已发布，验收中）**：`android-v0.3.0-beta.4`（`versionCode 6`、`versionName 0.3.0-beta.4`）已作为 GitHub 预发布发布，资产为 16 KiB 对齐、仅 v3 签名的 `Tsuyomi-0.3.0-beta.4.apk`；发布后由 `android-release` 工作流独立复核已上传资产的身份、摘要、对齐与签名者。预发布不等于稳定版：物理真机验收与稳定版转换仍待完成。

完整阶段规划与历史证据参见 [`docs/phases/`](docs/phases/) 与 [`docs/architecture/DELIVERY_PHASES_0_3.md`](docs/architecture/DELIVERY_PHASES_0_3.md)。

## 更新日志 (Changelog)

详细版本变更历史见 [`CHANGELOG.md`](CHANGELOG.md)。近期主要更新：

### [0.3.0-beta.4] - 2026-09-17（首个公开 Beta 预发布）
- **发布制品**：签名的 `Tsuyomi-0.3.0-beta.4.apk`（`versionCode 6`、16 KiB 对齐、仅 v3 签名）、Gradle 之外的签名与验证流程、发布后独立复核已上传资产的 `android-release` 工作流，以及 [`docs/process/RELEASE_PROCEDURE.md`](docs/process/RELEASE_PROCEDURE.md) 记录的可复现步骤与密钥托管边界。
- **Library 控件与数据保留**：立即生效的统一筛选与排序、固定点按分栏、独立布局选择，以及保留本地数据的移出书架操作。
- **签名来源目录**：Browse 已安装/可安装分区、插件搜索与详情、明确确认安装和手动更新；本地 `.hxp` 导入继续保留。
- **生产信任边界**：正式签名目录及 Wenku8 `0.2.33` 已发布；默认构建已固定授权的生产公钥。`tsuyomi.repository.keyId` 与 `tsuyomi.repository.publicKey` 必须成对配置；后者是原始 32 字节 Ed25519 公钥的 Base64，不是私钥。显式未配置的构建仍显示目录不可用，公开测试密钥不能充当生产根。
- **来源生命周期**：可通过显式根公钥链接订阅第三方仓库；仓库根认证只负责发现，非官方包仍须完成签名验证和绑定到精确包摘要的风险确认。卸载来源不删除宿主中的书籍、进度或凭据，也不重置发布者身份、撤销和防回滚记录。
- **下载失败恢复**：官方或订阅仓库下载会区分网络、仓库、验签和存储错误；连接提前中断只在原总期限内重试一次并丢弃残缺字节，显式重试仍重新校验并进入正常安装审批。
- **验证边界**：严格依赖校验、针对性测试与隔离设备证据不等于人工批准；不自动接受 goldens 或替换 canonical。

### [0.1.0] - 2026-08-09
- Phase 1 Android 宿主骨架、全局 Standard/E-ink 配置、Room 架构与 API 29 基线。

## 未来计划 (Roadmap)

项目后续迭代遵循公开阶段规划与本地架构契约：

- [x] **首个公开 Beta 预发布**：`android-v0.3.0-beta.4` 已作为 GitHub 预发布发布，并通过 `android-release` 工作流的发布后制品复核。
- [ ] **Phase 4 Standard 验收收口**
  - 在物理设备上验收已发布制品 `android-v0.3.0-beta.4`；模拟器证据、安装成功和 agent 结论都不构成验收；
  - 完成剩余真实在线 S/X 证据、TalkBack / Switch Access、人工视觉判断和未接受的截图差异；
  - 验收证据不自动扩大生产授权，也不解除 E-ink 冻结；预发布到稳定版的转换需要单独的明确决定。
- [ ] **发布签名密钥的异地恢复托管**：现有 Windows 用户绑定 DPAPI 备份仅是同机恢复手段，不构成独立恢复副本，公开发布流程视其为待办前提。
- [ ] **E-ink 墨水屏全量复苏与真机适配**
  - 解除 E-ink 临时冻结状态，执行专项 E-ink 恢复工程；
  - 对齐 28 个 Review 节点的墨水屏高对比度浅色样式、即时动效策略与残影重绘触发机制；
  - 接入物理墨水屏设备实测与双设备（手机 + 墨水屏）证据矩阵。
- [ ] **多来源扩展生态演进**
  - 推进 ESJZone 等更多社区小说的签名 `.hxp` 来源扩展包支持；
  - 完善扩展管理、权限隔离与防爬受控流程。
- [ ] **本地阅读体验深化**
  - 支持本地离线 EPUB / TXT 文档解析与统一 ReaderLocator 映射；
  - 增强阅读器排版预设、字体支持与注音/插图渲染。

## 组件边界

Tsuyomi Monorepo 包含两个独立版本、独立发布和独立回退的组件：

| 目录 | 职责 |
|---|---|
| `tsuyomi-android` | 原生 Android 宿主、Reader、持久化、安全、UI 与系统集成 |
| `tsuyomi-protocol` | JSON Schema、fixtures、Host API、transfer/backup 与一致性测试 |

维护中的来源扩展、打包工具和来源验收 fixtures 位于独立的 [Chachaanteng/tsuyomi-extensions](https://github.com/Chachaanteng/tsuyomi-extensions) 仓库。Android 与 protocol 可以原子更新；跨仓库仅通过版本化协议、签名制品、固定脱敏 fixtures 和发布元数据互操作，兼容性顺序为 protocol → extensions → Android。宿主构建不要求检出相邻插件仓库。

## 构建与本地 API 29 预检

要求：

- JDK 17
- Android SDK Platform 37、platform-tools、emulator 与 `system-images;android-29;default;x86_64`
- Python 与 [REUSE Tool](https://reuse.software/)

仅在显式 **HIGH** 模式下，提交前先完成最小复现和相邻顺序诊断，再运行由 `tools/android_ci_plan.py` 选择的本地 API 29 gate；不要把完整选中矩阵当作重试循环。顺序为有界诊断 → 本地 planned gate → 受保护的 hosted checks。**LOW 模式绝不运行本地 API 29 runner，包括 `--prepare-only`、预检和矩阵；LOW 的 CI 仅由 hosted protected checks 执行。** 有界直接开发编译仍是独立操作，不构成 CI。任何本地结果都不能跳过 hosted 最终验收或授予批准。

线上 Android 重型验证在 PR 上完成，合并到 `main` 后不自动重跑；仓库、协议和固定插件基线的轻量 main 健康检查仍保留。Actions 标题区分 `PR #N admission`、`main health` 和 `manual verification`。需要调查主分支时，可显式手动运行 `android-quality`，没有 PR base 时执行 conservative full plan。五项 protected checks 和 strict base 检查不减少；合并与状态汇报遵循 `docs/process/QUALITY_GATES.md` G4.5/G5。

**Windows 原生优先**（当前没有通用 WSL 发行版时不要为此安装一个）：

```powershell
$env:ANDROID_SDK_ROOT = '<your-android-sdk>'
./tools/Doctor.ps1
$base = git -C .. merge-base origin/main HEAD
python ../tools/android_api29.py --repo-root .. --base $base --head HEAD --mode high --build
```

除 `--prepare-only` 外，每次运行都会先编译 planner 选择的 instrumentation `:module:assembleDebug` 与 `:module:assembleDebugAndroidTest`，再创建或启动 AVD。`HIGH` 在预编译与串行 instrumentation 中省略 `--no-daemon`，允许 Gradle 在 phase 间复用 daemon；hosted `ci` 保持 `--no-daemon`。`--build` 还会在此阶段加入 planner 选择的 build/lint/JVM/screenshot tasks；仅非 focused 的 `--build` 运行是完整 gate（`scope=planner_selected_preflight`、`full_gate=true`）。默认运行的 scope 为 `planner_selected_instrumentation`、`full_gate=false`。一次有界的 focused 诊断可显式覆盖 instrumentation 任务；它只产生诊断证据，不是完整 gate：

```powershell
python ../tools/android_api29.py --repo-root .. --base $base --head HEAD --mode high `
  --task :app:connectedDebugAndroidTest `
  --test-class org.tsuyomi.android.UpdatesProductionJourneyInstrumentedTest
```

可重复传入 `--task`。仅检查环境、隔离 AVD 配置和清理路径时使用：

```powershell
python ../tools/android_api29.py --repo-root .. --base $base --head HEAD --mode high --prepare-only
```

工具默认从 `ANDROID_SDK_ROOT` 或 `ANDROID_HOME` 取得 SDK；可用 `--sdk PATH` 覆盖，`--repo-root` 省略时由工具推导。`--head` 默认为 `HEAD`；本地仅接受 `HEAD`，并把 tracked dirty 与未忽略 untracked paths 作为相对该 head 的 planner overlay，记录 resolved head 和 overlay。 有可用 merge-base 时传入 `--base`；没有有效 base 时省略它，planner 会保守回退到完整选择。工具共享 `tools/android_api29_profile.json`；不要手工复刻 AVD 参数。它使用独立临时 AVD home、唯一 `tsuyomi-ci-*` AVD 与可用端口，证据保存在 `build/api29-ci/<run>/`，随后清理自己的 emulator/AVD。缺少固定 system image 时按报出的 `sdkmanager` 安装提示处理；工具不会自动安装或接受许可证。

共享 profile 固定 `system-images;android-29;default;x86_64`、`pixel_2`、`swiftshader`、1080×2400/420dpi、font scale 1.0、portrait（rotation 0）和 animation 0；CLI 的默认 `--mode` 是 `low`，但本地 runner 在接触 SDK/AVD/Gradle 前就拒绝默认或显式 `low`，只接受显式 `--mode high`；`--mode ci` 仅保留给 `GITHUB_ACTIONS=true` 的 hosted execution。`--prepare-only` 仅允许 AVD 生命周期证据，拒绝 `--build` 与 focused flags。`environment.json` 标明 run、mode、scope、`full_gate`、planner base/head/fallback/reasons、resolved head/worktree overlay、已解析 profile/AVD、host、SDK packages/revisions、tool versions、task selection、logs、exit/failure，以及 `webview_dumpsys`、system fingerprint、resolved device settings 和 `timings_seconds.precompile`、`timings_seconds.emulator_prepare`、`timings_seconds.instrumentation`（prepare-only 不含）与 `timings_seconds.total`；同一 run 另保存 emulator/adb/build/instrumentation logs 与对应的 WebView、system-fingerprint、device-settings 文件。

只有本地通过、hosted 失败的证据确认了实质性主机差异，且 Windows 原生配置对齐、输入固定与同步修复均无法解决时，才把 WSL2 作为最后手段；还必须具备可用 KVM。把 checkout 与 SDK 放在 Linux 文件系统而非 `/mnt/c`，仍运行同一个 runner/profile：

```bash
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
base="$(git -C .. merge-base origin/main HEAD)"
python3 ../tools/android_api29.py --repo-root .. --base "$base" --head HEAD --mode high --build
```

本地、WSL2 与 hosted 环境并不保证相同的 host、kernel 或 emulator build。每次运行记录已安装 image/emulator revision、system fingerprint 与 WebView 版本，供差异比较。这个 automation AVD 只服务 CI-style instrumentation；它不替代 `Tsuyomi_Review_Work_API29` 的视觉/人工 Review_Work 所有权，绝不替换 canonical，也不产生批准。
性能比较必须使用相同 resolved `HEAD`/worktree-overlay policy、scope、selected tasks、profile/image revision 和 host evidence，并比较上述各 phase timing；它们用于定位性能差异，不证明 Windows、WSL2 Linux 与 hosted 的 host、kernel 或 emulator build 相同。

完整质量门、固定人工/视觉 AVD 配方分别见：

- [`CONTRIBUTING.md`](CONTRIBUTING.md)
- [`docs/process/QUALITY_GATES.md`](docs/process/QUALITY_GATES.md)
- [`docs/verification/AVD_MATRIX.md`](docs/verification/AVD_MATRIX.md)
- [`tools/avd/Create-ReviewAvds.ps1`](tools/avd/Create-ReviewAvds.ps1)

## 架构与贡献

- ADR 索引：[`docs/adr/README.md`](docs/adr/README.md)
- 模块边界：[`docs/architecture/MODULES.md`](docs/architecture/MODULES.md)
- 显示与 E-ink：[`docs/architecture/EINK.md`](docs/architecture/EINK.md)
- 迁移边界：[`docs/architecture/MIGRATION.md`](docs/architecture/MIGRATION.md)
- 设置适用性：[`docs/design/OPTION_APPLICABILITY.md`](docs/design/OPTION_APPLICABILITY.md)
- 仓库治理：[`docs/process/REPOSITORY_GOVERNANCE.md`](docs/process/REPOSITORY_GOVERNANCE.md)
- 贡献说明：[`CONTRIBUTING.md`](CONTRIBUTING.md)

界面只展示已经具备真实 handler、状态、失败恢复和当前可观察效果的能力。禁止空入口、“即将推出”、无消费者设置和通过 suppression/baseline 掩盖问题。

## 参考、借鉴与许可证声明

Tsuyomi 是独立项目，与 Hikari Novel、Hikari Novel Plus、Wenku8、ESJZone、Yamibo、300X、Tachiyomi、Mihon、Inkwell、LightNovelReader 及其维护者均无官方关系。

当前 Phase 1 基线**没有复制、翻译或改编下列项目的源文件、图片、字体、站点 Logo、布局或组件实现**。这些项目用于行为迁移、公开架构研究和设计取舍；Tsuyomi 的实现从协议、可观察行为和独立测试出发。若未来引入上游代码或素材，必须先完成许可证兼容审查，并在同一变更中保留版权/NOTICE、标记修改、更新 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) 和 REUSE 元数据。

特别鸣谢 **[Tachiyomi](https://github.com/tachiyomiorg)**。其长期形成的 Android 阅读器体验、来源扩展生态、书库组织方式和开放社区，为包括 Tsuyomi 在内的许多阅读器项目提供了重要灵感。Tachiyomi 官方核心项目已经停止维护并下线；本鸣谢不表示 Tsuyomi 是其分支、继任者或官方关联项目。

| 项目 | 许可证 | 借鉴或研究范围 | 当前采用状态 |
|---|---|---|---|
| [15dd/hikari_novel_flutter](https://github.com/15dd/hikari_novel_flutter) | [MIT](https://github.com/15dd/hikari_novel_flutter/blob/main/LICENSE) | Wenku8 基础行为、阅读器/书架历史契约及旧备份格式的迁移背景 | 行为参考；未复制源码或资产 |
| [Xfire233/hikari_novel_flutter_plus](https://github.com/Xfire233/hikari_novel_flutter_plus) | [MIT](https://github.com/Xfire233/hikari_novel_flutter_plus/blob/main/LICENSE) | 迁移规格来源；来源启用、登录、语义阅读位置、E-ink、智能书架、ESJZone/Yamibo 兼容需求。固定参考输入为 `a1feba6d1dd8dbbdd2b5ae042e44f2ec54d26bef` | 行为与脱敏 fixture 参考；不是实现依赖 |
| [EnableAria/Esjzone](https://github.com/EnableAria/Esjzone) | [MIT](https://github.com/EnableAria/Esjzone/blob/master/LICENSE) | Hikari Plus 的 ESJZone 来源研究上游；用于理解公开请求/解析兼容背景 | 间接研究参考；未复制源码 |
| [prprbell/YamiboReaderPro](https://github.com/prprbell/YamiboReaderPro) | [AGPL-3.0](https://github.com/prprbell/YamiboReaderPro/blob/master/LICENSE) | Hikari Plus 的 Yamibo 来源研究上游；论坛物理页、帖子与阅读场景背景 | 仅研究公开行为；**不复制、翻译或链接其 AGPL 代码** |
| [belleangelina/300X](https://github.com/belleangelina/300X) | [GPL-3.0-only](https://github.com/belleangelina/300X/blob/main/LICENSE) | Yamibo 登录、目录、论坛内容组织、阅读/缓存/离线交互等公开产品行为与工程取舍 | 行为和交互研究；**未复制、翻译或链接其 GPL 代码** |
| [Tachiyomi](https://github.com/tachiyomiorg) | Apache-2.0（历史核心项目） | Android 阅读器、来源扩展生态、书库/分类组织和面向普通用户的交互设计灵感 | 特别鸣谢与产品灵感；未采用源码或品牌资产 |
| [mihonapp/mihon](https://github.com/mihonapp/mihon) | [Apache-2.0](https://github.com/mihonapp/mihon/blob/main/LICENSE) | 空闲后章节切换、受限相邻预载；分类与书库多对多关系的研究依据 | 架构研究；未采用源码 |
| [radiumCN/inkwell](https://github.com/radiumCN/inkwell) | [MIT](https://github.com/radiumCN/inkwell/blob/main/LICENSE) | 测量结果与绘制对象同源、阅读进度使用章节与文本位置的设计依据 | 架构研究；未采用源码 |
| [dmzz-yyhyy/LightNovelReader](https://github.com/dmzz-yyhyy/LightNovelReader) | [Apache-2.0](https://github.com/dmzz-yyhyy/LightNovelReader/blob/refactoring/LICENSE) | 来源无关的结构化章节内容组件/API 边界 | API 设计研究；未采用源码 |

特别说明：`YamiboReaderPro` 使用 AGPL-3.0，`300X` 使用 GPL-3.0-only。当前 Apache-2.0 的 Tsuyomi 仓库不得直接复制、逐行翻译、静态/动态链接或形成这些项目的衍生作品。若未来确需采用其受保护代码，必须先重新评估整个作品的许可证义务；在当前许可策略下，这类采用视为禁止。

以上项目的版权声明、许可证链接和具体使用边界同时记录在 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。构建依赖与测试工具也在该文件中单独列出。

## 内容与站点声明

- Tsuyomi 不托管、不提供、不分发小说正文或站点内容。
- 项目与任何内容站点均无官方关系，不使用其商标为本项目背书。
- 用户及扩展作者应遵守所在地法律、内容版权、站点服务条款与访问频率限制。
- 不实现 CAPTCHA 求解、反爬绕过或自动挑战规避；需要验证时只允许用户主动完成受控 WebView 流程。
- 不应在 Issue、日志、fixture、备份或截图中提交 Cookie、Token、账号、数据库、未脱敏网页内容或其他私人数据。

## 许可证

Tsuyomi Android 自有代码、文档与原创素材以 [Apache License 2.0](LICENSES/Apache-2.0.txt) 发布，文件级版权与许可状态由 [REUSE](https://reuse.software/) 管理。

Apache-2.0 只覆盖 Tsuyomi 自有作品以及明确按兼容许可证引入并完成声明的部分，不改变上述参考项目各自的许可证，也不授予任何第三方商标或内容版权。
