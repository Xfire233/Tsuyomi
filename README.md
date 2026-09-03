<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi

本地优先、面向墨水屏的原生 Android 轻小说阅读器。Android 宿主使用 Kotlin 与 Jetpack Compose；平台无关协议和签名来源扩展与宿主在同一个 Monorepo 中独立版本化。

> [!NOTE]
> 项目目前已完成 **Phase 0 到 Phase 3** 的基础设施、数据与契约，以及 **Phase 4A (Standard UI/UX 生产级交互切片)**。已实现 Wenku8 在线搜索、详情、目录、章节正文阅读、双排/单排排版、语义进度持久化、书架拖拽与快捷栏交互、数据导入导出迁移。墨水屏全局适配（E-ink）处于临时冻结状态，待 Standard 阶段闭环后开展专项恢复。
## 目录

| 目录 | 职责 |
|---|---|
| [`tsuyomi-android`](tsuyomi-android) | 原生 Android 宿主、Reader、持久化、安全、UI 和系统集成 |
| [`tsuyomi-protocol`](tsuyomi-protocol) | JSON Schema、fixtures、Host API、transfer/backup 和一致性测试 |
| [`tsuyomi-extensions`](tsuyomi-extensions) | 签名 `.hxp` 来源扩展、构建工具和来源验收 fixtures |

三个组件共享一个 Git 提交，使协议、扩展和 Android consumer 可以在一个 PR 中原子变更；发布仍使用独立 SemVer 和标签：

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

- **Phase 0：协议与安全基线（已完成）**：HXP 签名扩展包规范、Host API 1.1、加密安全凭据分区（Android Keystore AES-GCM）、跨平台传输/备份协议契约。
- **Phase 1：Android 宿主骨架与全局显示模式（已完成）**：Jetpack Compose + Material 3 原生界面架构、Room 数据模型与事务不变量、Standard / E-ink 双模式配置架构与 API 29 验证基线。
- **Phase 2：Wenku8 只读垂直阅读切片（已完成）**：QuickJS 隔离执行沙箱、受控 WebView 登录验证、搜索 → 详情 → 目录 → 章节阅读 → 语义进度保存与恢复端到端闭环。
- **Phase 3：本地书架与迁移体系（已完成）**：多层级系统/手动/智能收藏夹、`tsuyomi-transfer` 数据导入导出、从旧版 Hikari 无凭据安全平滑迁移、远端书架只读拉取与同步。
- **Phase 4A：Standard 交互与 UI Atlas 生产级落地（已完成）**：
  - **书架交互全套迁移**：网格、列表、紧凑三布局长按多选，`SelectionAppBar` 批量移动/添加至收藏夹及本地删除。
  - **书籍拖拽与归类**：书籍拖至书籍创建收藏夹、拖入现有收藏夹、根目录横向展开插入位并挤开相邻元素。
  - **快捷栏双模式重构**：
    - **常驻锁定模式**：快捷栏固定于 AppBar 下方，便于大量书籍拖拽归类，保持完整拖拽、插入、重排与交互能力。
    - **内联收折模式**：跟随页面滚动，滑出视口后收折为 ≥48dp 悬浮手柄，支持点击、上滑或拖拽书籍悬停动态展开。
  - **单次长按连续拖拽**：非多选模式下长按书籍达到平台阈值即刻拾取拖动，无需二次手势；拖拽时具备动态让位与收藏夹缩放高亮反馈。
  - **自定义排序持久化**：Room v4 引入 `display_order`，支持书架根目录及手动收藏夹自由重排与持久化。
  - **发现页与推荐源站化**：原生解析 Wenku8 首页推荐栏目（7 月新番、新书风云榜、本周会员推荐榜）及《这本轻小说真厉害！》专属榜单页。
  - **界面细节与规范**：对称标签栏、200ms M3 展开收起动效、滚动方向自适应 FAB。

## 更新日志 (Changelog)

详细版本变更历史见 [`CHANGELOG.md`](CHANGELOG.md)。近期主要更新：

### [Unreleased] (Phase 4A Standard UX Cutover)
- **书架交互与 Atlas 对齐**：长按多选、SelectionAppBar、书籍拖拽归类与合集创建、Room v4 自定义排序持久化。
- **快捷书架双模式**：常驻锁定（保持固定且全交互可用）与内联收折（悬浮把手动态展开），单次长按连续拾取。
- **Wenku8 发现与推荐栏目**：首页三大推荐栏目、轻小说排行榜专页、对称标签栏与 200ms M3 动效。
- **稳定性与测试**：消除 Compose 触摸输入与手势事件循环死锁，CI 全自动化测试（API 29 Instrumentation、Lint、Goldens）全绿通过。

### [0.1.0] - 2026-08-09
- 建立 Phase 1 Android、协议与扩展契约基线。

## 未来计划 (Roadmap)

项目后续迭代遵循公开阶段规划与本地架构契约：

- [ ] **Phase 4B: 授权远端回写与云端书架镜像**：基于 Host API 1.2 / HXP v2 写入能力子集，提供显式受控的远端书架目标选择、远端移出与移入对账机制。
- [ ] **Phase 4C: 更新协调中心与可控计划检查**：前台显式小说更新状态汇总，用户可配置的手动检查与后台定时轻量更新检查。
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

贡献与质量规则见 [`CONTRIBUTING.md`](CONTRIBUTING.md)、[`tsuyomi-android/docs/process/QUALITY_GATES.md`](tsuyomi-android/docs/process/QUALITY_GATES.md) 和 [`tsuyomi-android/docs/design/OPTION_APPLICABILITY.md`](tsuyomi-android/docs/design/OPTION_APPLICABILITY.md)。

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
