<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi

本地优先、面向墨水屏的原生 Android 轻小说阅读器。Android 宿主使用 Kotlin 与 Jetpack Compose；宿主和平台无关协议在本仓库维护，签名来源扩展在独立仓库维护。

> [!NOTE]
> 项目已完成 **Phase 0–Phase 4C** 的协议、安全、本地书架、Standard 交互、显式网站收藏回写与更新协调实现。当前包含 Wenku8 搜索/详情/目录/阅读、语义进度与精确章节完成、书架拖拽与快捷栏、数据迁移、网站书架镜像、可恢复的单书 `ADD`/`MOVE`/`REMOVE`，以及签名来源目录与扩展生命周期。**首个公开 Beta 预发布 `android-v0.3.0-beta.4` 已发布**；它是预发布而非稳定版，物理真机验收仍待完成。原型 UI Atlas 已退役，生产代码与 Review Graph 是唯一实现/审阅路径；E-ink 仍冻结，待 Standard 发布闭环后专项恢复。
## 目录

| 目录 | 职责 |
|---|---|
| [`tsuyomi-android`](tsuyomi-android) | 原生 Android 宿主、Reader、持久化、安全、UI 和系统集成 |
| [`tsuyomi-protocol`](tsuyomi-protocol) | JSON Schema、fixtures、Host API、transfer/backup 和一致性测试 |
| [Chachaanteng/tsuyomi-extensions](https://github.com/Chachaanteng/tsuyomi-extensions)（独立仓库） | AGPL-3.0-only 插件实现、测试和构建发布；正式签名目录发布独立审批 |

本仓库中的宿主与协议可以原子变更；插件通过固定提交、版本化协议和制品摘要衔接，不依赖相邻 checkout 或远端最新分支。组件使用独立 SemVer 和标签，extensions 标签位于独立插件仓库：

```text
protocol-vX.Y.Z
extensions-vX.Y.Z
android-vX.Y.Z
phase-N-baseline
```

## 项目原则

- **本地优先**：不要求 Tsuyomi 账号，不依赖 Google Play Services，不接入遥测、远程 feature flag 或自动崩溃上报。
- **原生 Android**：不继承 Flutter 页面树、导航或组件实现。
- **全局 E-ink profile**：Standard 和 E-ink 复用同一路由、业务状态和持久数据。
- **来源隔离**：规划中的来源以签名、平台无关 `.hxp` 包交付，通过版本化 Host API 运行，不是 APK 插件。
- **语义进度**：持久化章节与文本语义位置，不依赖易失效的页码、像素偏移或滚动百分比。
- **公开可验证**：测试源码、脱敏 fixtures、screenshot goldens 和 GitHub Actions 随源码发布；构建产物、报告、凭据和本地开发状态不进入仓库。


## 项目进度与阶段概览

- **Phase 0：协议与安全基线（已完成）**：HXP 签名扩展包规范、Host API 1.2、加密安全凭据分区（Android Keystore AES-GCM）、跨平台传输/备份协议契约。
- **Phase 1：Android 宿主骨架与全局显示模式（已完成）**：Jetpack Compose + Material 3 原生界面架构、Room 数据模型与事务不变量、Standard / E-ink 双模式配置架构与 API 29 验证基线。
- **Phase 2：Wenku8 只读垂直阅读切片（已完成）**：QuickJS 隔离执行沙箱、受控 WebView 登录验证、搜索 → 详情 → 目录 → 章节阅读 → 语义进度保存与恢复端到端闭环。
- **Phase 3：本地书架与迁移体系（已完成）**：多层级系统/手动/智能收藏夹、`tsuyomi-transfer` 数据导入导出、从旧版 Hikari 无凭据安全平滑迁移、远端书架只读拉取与同步。
- **Phase 4A：Standard 书架与详情交互（已完成）**：
  - 网格、列表、紧凑三布局的长按多选与 `SelectionAppBar` 批量操作。
  - 书籍拖至书籍创建收藏夹、拖入收藏夹、根书架插入/重排，以及锁定/内联收折快捷栏。
  - Room 自定义顺序持久化、来源主页推荐、原生 Detail 与 Reader 交互。Atlas 原型已退役，仅保留历史设计来源。
- **Phase 4B：网站书架镜像与显式回写（已完成）**：
  - 网站收藏镜像作为书架节点与快捷栏目标；本地 COPY 和网站 `ADD`/`MOVE`/`REMOVE` 保持语义、授权和结果分离。
  - 每个受保护请求使用精确签名策略；目标发现独立授权，解析器对模糊成功页和伪造目标失败关闭。
  - Room 持久化对账、进程重建后的定向 ADD→MOVE 续作、未决结果安全重试，以及远端稀疏摘要不覆盖本地丰富元数据。
  - `tsuyomi-transfer` v2 输出与严格 v1/v2 导入、精确章节完成、来源主页显式缓存恢复和离线优先本地详情。
- **Phase 4C：更新协调中心与来源生命周期（已完成）**：
  - 本地更新收件箱、默认关闭的计划检查、精确处理与撤销，以及来源/书籍排除和逐章完成联动。
  - Browse 提供官方签名目录与用户订阅仓库、签名包审批、精确第三方包同意、安全卸载/重装和保留的安全历史。
- **首个公开 Beta 预发布（已发布，验收中）**：`android-v0.3.0-beta.4`（`versionCode 6`）已作为 GitHub 预发布发布，附带 16 KiB 对齐、仅 v3 签名的发布 APK；发布后由 `android-release` 工作流独立复核已上传资产的身份、摘要、对齐和签名者。稳定版化、物理真机验收与 E-ink 恢复尚未完成。

## 更新日志 (Changelog)

详细版本变更历史见 [`CHANGELOG.md`](CHANGELOG.md)。近期主要更新：

### [0.3.0-beta.4] - 2026-09-17（首个公开 Beta 预发布）
- **发布制品**：签名的 `Tsuyomi-0.3.0-beta.4.apk`（`versionCode 6`，16 KiB 对齐，仅 v3 签名）、Gradle 之外的签名与验证流程，以及发布后独立复核已上传资产的 `android-release` 工作流。
- **Detail 标签**：标签行不再溢出轮廓区域、也不再提前换行；只保留一个水平间距，且仅对整行做两端对齐。
- **阅读与性能**：下一章有界预载；规范化来源快照写入移出 UI 线程；缓存章节探测合并为一次目录列举；封面并发上限提升到 8。
- **扩展发布**：Wenku8 `0.2.33` 已通过官方根签名目录发布，宿主继续使用既有的固定发布者身份消费该目录。

### [0.3.0-beta.3] 及更早
- **Phase 4A/4B 交互与回写**：Library 多选/拖拽/快捷栏、Detail 与 Reader 生产对齐、网站书架镜像与显式单书 `ADD`/`MOVE`/`REMOVE`。
- **信任与生命周期**：官方签名目录、第三方订阅仓库、精确包同意、安全卸载/重装与保留的安全历史。
- **迁移与质量**：Room v5–v13、`tsuyomi-transfer` v2–v5、生产 Review Graph、按 merge-base 选择的 CI 任务和退役原型清理。

### [0.1.0] - 2026-08-09
- 建立 Phase 1 Android、协议与扩展契约基线。

## 未来计划 (Roadmap)

项目后续迭代遵循公开阶段规划与本地架构契约：

- [x] **Phase 4B: 授权远端回写与网站书架镜像**：精确签名请求、显式单书操作、持久镜像/对账和可恢复定向 ADD→MOVE 已实现。
- [x] **Phase 4C: 更新协调中心与可控计划检查**：本地更新收件箱、默认关闭的计划检查、精确处理/撤销与来源、书籍排除规则已实现。
- [x] **首个公开 Beta 预发布**：`android-v0.3.0-beta.4` 已作为 GitHub 预发布发布，并通过发布后的独立制品复核。
- [ ] **Standard 真机验收与稳定版转换**：在用户物理设备上验收已发布制品，再单独决定预发布到稳定版的转换；预发布标记本身不是稳定版。
- [ ] **E-ink 墨水屏全量复苏与真机适配**：解除 E-ink 冻结状态，恢复 28 个 Review 节点的墨水屏高对比浅色样式、即时动效策略与残影重绘触发机制，并接入双设备实测矩阵。
- [ ] **多来源扩展生态演进**：推进 ESJZone 等更多社区小说的签名 `.hxp` 来源扩展包支持与扩展权限管理。
- [ ] **本地阅读体验深化**：支持本地离线 EPUB / TXT 文档解析与 ReaderLocator 语义进度映射，增强阅读器排版预设。
## 构建与验证

要求：JDK 17、Android SDK Platform 36、Node.js/npm、Python 和 REUSE Tool。

```powershell
# Android
cd tsuyomi-android
$env:ANDROID_SDK_ROOT = '<your-android-sdk>'
./tools/Doctor.ps1
./gradlew.bat --no-daemon --console=plain --dependency-verification strict :app:assembleDebug

# Protocol
cd ../tsuyomi-protocol
npm ci
npm test

# Repository policy（回到仓库根目录）
cd ..
python -m reuse lint
python tools/check_repository.py
```

贡献与质量规则见 [`CONTRIBUTING.md`](CONTRIBUTING.md) 和 [`tsuyomi-android/docs/process/QUALITY_GATES.md`](tsuyomi-android/docs/process/QUALITY_GATES.md)；文档职责/读取终止条件见 [`DOCUMENTATION.md`](DOCUMENTATION.md)，工具、Skill 与 MCP 调度/排除/完成条件见 [`TOOLING.md`](TOOLING.md)，功能项适用性见 [`tsuyomi-android/docs/design/OPTION_APPLICABILITY.md`](tsuyomi-android/docs/design/OPTION_APPLICABILITY.md)。

## 参考、借鉴与特别鸣谢

Tsuyomi 是独立项目，与下列项目、内容站点及其维护者均无官方关系。当前基线没有复制、翻译或改编这些项目的源文件、图片、字体、Logo、布局或组件实现；它们用于行为迁移、公开架构研究和产品设计取舍。

特别鸣谢 **[Tachiyomi](https://github.com/tachiyomiorg)**。其 Android 阅读器体验、来源扩展生态、书库组织方式和开放社区，为许多后续阅读器项目提供了重要灵感。Tachiyomi 官方核心项目已经停止维护并下线；Tsuyomi 不是其分支、继任者或官方关联项目。

| 项目 | 许可证 | 参考范围 |
|---|---|---|
| [15dd/hikari_novel_flutter](https://github.com/15dd/hikari_novel_flutter) | MIT | Wenku8 基础行为、旧阅读器/书架契约和旧备份格式背景 |
| [Xfire233/hikari_novel_flutter_plus](https://github.com/Xfire233/hikari_novel_flutter_plus) | MIT | 固定迁移规格：来源启用、登录、语义位置、E-ink、智能书架及 ESJZone/Yamibo 兼容需求 |
| [EnableAria/Esjzone](https://github.com/EnableAria/Esjzone) | MIT | ESJZone 公开请求和解析兼容背景 |
| [prprbell/YamiboReaderPro](https://github.com/prprbell/YamiboReaderPro) | AGPL-3.0 | Yamibo 论坛物理页、帖子和阅读场景的公开行为研究；不采用代码 |
| [belleangelina/300X](https://github.com/belleangelina/300X) | GPL-3.0-only | Yamibo 登录、目录、内容组织、阅读/缓存/离线交互研究；不采用代码 |
| [Tachiyomi](https://github.com/tachiyomiorg) | Apache-2.0（历史核心项目） | Android 阅读器、来源扩展、书库分类和普通用户交互灵感 |
| [mihonapp/mihon](https://github.com/mihonapp/mihon) | Apache-2.0 | 空闲后章节切换、受限预载和书库多对多关系研究 |
| [radiumCN/inkwell](https://github.com/radiumCN/inkwell) | MIT | 测量/绘制同源与章节文本位置进度研究 |
| [dmzz-yyhyy/LightNovelReader](https://github.com/dmzz-yyhyy/LightNovelReader) | Apache-2.0 | 来源无关的结构化章节内容/API 边界研究 |

`YamiboReaderPro` 和 `300X` 使用强 copyleft 许可证。当前 Apache-2.0 策略禁止直接复制、逐行翻译、链接、改编或形成其衍生作品。详细版权、许可证链接和采用边界见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。

## 内容与隐私声明

- Tsuyomi 不托管、不提供、不分发小说正文或站点内容。
- 用户和扩展作者应遵守所在地法律、内容版权、网站服务条款和访问频率限制。
- 不实现 CAPTCHA 求解、反爬绕过或自动挑战规避；验证只能由用户主动完成。
- 不应在 Issue、fixture、日志、备份或截图中提交 Cookie、Token、账号、数据库或未脱敏网页内容。

## 许可证

Tsuyomi 自有代码、文档和原创素材以 [Apache License 2.0](LICENSE) 发布，文件级版权与许可状态由 [REUSE](https://reuse.software/) 管理。第三方项目继续适用各自许可证，本项目的 Apache-2.0 不改变其权利和义务。
