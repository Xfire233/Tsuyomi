<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi Android

本地优先、面向墨水屏的原生 Android 轻小说阅读器。使用 Kotlin 与 Jetpack Compose 构建，目标平台为 Android 10 及以上版本（`minSdk 29`）。

> [!NOTE]
> 项目已完成 **Phase 0 到 Phase 3** 的基础设施、数据与契约；**Phase 4 Standard 功能实现与验收仍在推进**。已实现在线搜索、详情、目录、正文阅读、语义进度、Library 内容系统及签名来源安装。人工视觉、辅助技术和完整真实在线验收仍待完成；墨水屏全局适配（E-ink）保持冻结。
## 项目目标

- **本地优先**：不要求 Tsuyomi 账号，不依赖 Google Play Services，不接入遥测、远程 feature flag 或自动崩溃上报。
- **原生 Android**：Kotlin、Jetpack Compose、Room、DataStore；不继承 Flutter 页面树或组件实现。
- **全局墨水屏模式**：Standard 与 E-ink 复用同一路由、业务状态和持久数据；E-ink 是应用根级显示配置，不是阅读器内的局部开关。
- **来源与宿主分离**：内容来源以签名、平台无关的 `.hxp` 包交付，通过版本化 Host API 运行；不是 Android APK 插件。维护源码位于独立的 [tsuyomi-extensions](https://github.com/Chachaanteng/tsuyomi-extensions) 仓库。
- **语义阅读进度**：持久化章节与文本语义位置，而不是依赖易失效的页码、像素偏移或滚动百分比。
- **可审计发布**：面向 GitHub Releases 与 F-Droid；依赖锁、校验元数据、第三方声明、REUSE、Phase 证据和 gate 判定随代码版本化。

## 当前状态与项目进度

项目采用阶段化递进架构，已完成的核心能力如下：

- **Phase 0：协议与安全基线（已完成）**：HXP 签名扩展包规范、Host API 1.1、加密安全凭据分区（Android Keystore AES-GCM）、跨平台传输/备份协议契约。
- **Phase 1：Android 宿主骨架与全局显示模式（已完成）**：Jetpack Compose + Material 3 原生界面架构、Room 数据模型与事务不变量、Standard / E-ink 双模式配置架构与 API 29 验证基线。
- **Phase 2：Wenku8 只读垂直阅读切片（已完成）**：QuickJS 隔离执行沙箱、受控 WebView 登录验证、搜索 → 详情 → 目录 → 章节阅读 → 语义进度保存与恢复端到端闭环。
- **Phase 3：本地书架与迁移体系（已完成）**：多层级系统/手动/智能收藏夹、`tsuyomi-transfer` 数据导入导出、从旧版 Hikari 无凭据安全平滑迁移、远端书架只读拉取与同步。
- **Phase 4A：Standard 交互生产实现（当前变更验收待完成）**：
  - **书架交互全套迁移**：网格、列表、紧凑三布局长按多选，`SelectionAppBar` 批量移动/添加至收藏夹及本地删除。
  - **书籍拖拽与归类**：书籍拖至书籍创建收藏夹、拖入现有收藏夹、根目录横向展开插入位并挤开相邻元素。
  - **Library 内容系统**：固定 `书架 / 继续阅读 / 稍后再读` 点按分栏；收藏夹、网站镜像与书籍统一根内容流，子节点进入独立页面，不再保留快捷栏。
  - **单次长按连续拖拽**：非多选模式下长按书籍达到平台阈值即刻拾取拖动，无需二次手势；拖拽时具备动态让位与收藏夹缩放高亮反馈。
  - **排序与本地保留**：自定义排序持久化；Room v10 将本地书架归属与书籍元数据分离，移出书架不删除阅读进度、标注、缓存或网站镜像状态。
  - **发现页与推荐源站化**：原生解析 Wenku8 首页推荐栏目（7 月新番、新书风云榜、本周会员推荐榜）及《这本轻小说真厉害！》专属榜单页。
  - **界面细节与规范**：对称标签栏、200ms M3 展开收起动效、滚动方向自适应 FAB。

完整阶段规划与历史证据参见 [`docs/phases/`](docs/phases/) 与 [`docs/architecture/DELIVERY_PHASES_0_3.md`](docs/architecture/DELIVERY_PHASES_0_3.md)。

## 更新日志 (Changelog)

详细版本变更历史见 [`CHANGELOG.md`](CHANGELOG.md)。近期主要更新：

### [Unreleased] (Phase 4 Standard)
- **Library 控件与数据保留**：立即生效的统一筛选与排序、固定点按分栏、独立布局选择，以及保留本地数据的移出书架操作。
- **签名来源目录**：Browse 已安装/可安装分区、插件搜索与详情、明确确认安装和手动更新；本地 `.hxp` 导入继续保留。
- **生产信任边界**：正式目录和包尚未发布。构建只有在另行授权后才配置成对的 `tsuyomi.repository.keyId` 与 `tsuyomi.repository.publicKey` Gradle 属性；公钥为原始 32 字节 Ed25519 公钥的 Base64，不是私钥。未配置时明确显示目录不可用，公开测试密钥不能充当生产根。
- **验证边界**：严格依赖校验、针对性测试与隔离设备证据不等于人工批准；不自动接受 goldens 或替换 canonical。

### [0.1.0] - 2026-08-09
- Phase 1 Android 宿主骨架、全局 Standard/E-ink 配置、Room 架构与 API 29 基线。

## 未来计划 (Roadmap)

项目后续迭代遵循公开阶段规划与本地架构契约：

- [ ] **Phase 4B: 授权远端回写与云端书架镜像**
  - 基于 Host API 1.2 与签名 HXP v1 声明的写入能力子集；
  - 提供用户显式授权的远端书架目标选择、远端移出与移入对账机制，确保网络操作完全透明可控。
- [ ] **Phase 4C: 更新协调中心与可控计划检查（本地实现已落地，生产验收待完成）**
  - 静默可信基线、增量追更收件箱、精确处理与撤销、逐章完成联动和来源/书籍排除；
  - 默认关闭的可控计划检查，以及通知不可用时仍保留的应用内进度、结果和取消。
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

## 构建

要求：

- JDK 17
- Android SDK Platform 37
- Android API 29 default x86_64 system image（instrumentation/AVD 验收）
- Python 与 [REUSE Tool](https://reuse.software/)

Windows：

```powershell
$env:ANDROID_SDK_ROOT = '<your-android-sdk>'
./tools/Doctor.ps1
./gradlew.bat --no-daemon --console=plain --dependency-verification strict :app:assembleDebug
python -m reuse lint
python ../tools/check_repository.py --scope android
```

完整质量命令和固定 AVD 参数分别见：

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
