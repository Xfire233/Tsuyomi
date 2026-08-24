<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi

本地优先、面向墨水屏的原生 Android 轻小说阅读器。Android 宿主使用 Kotlin 与 Jetpack Compose；平台无关协议和签名来源扩展与宿主在同一个 Monorepo 中独立版本化。

> [!IMPORTANT]
> 项目目前完成 Phase 1 基础设施、显示系统、数据/安全契约和 UI 基线，尚不是可日常使用的完整阅读器。Wenku8 搜索、详情、目录、章节和阅读器垂直切片属于后续 Phase。

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
