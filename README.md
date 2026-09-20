<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi

Tsuyomi 是一个本地优先的 Android 轻小说阅读器，使用 Kotlin 和 Jetpack Compose 开发。

它目前主要支持 Wenku8，提供找书、查看详情和目录、阅读、保存进度、管理书架与检查更新等功能。来源以独立签名扩展提供，不需要为每个网站安装一个 APK。

> [!IMPORTANT]
> 最新公开版本是预发布版 [`android-v0.3.0-beta.4`](https://github.com/Xfire233/Tsuyomi/releases/tag/android-v0.3.0-beta.4)，不是稳定版。
>
> 仓库中的代码已经包含公开 Beta 之后的状态恢复、封面缓存和登录恢复修复。这批修复已通过单独的私有测试包完成真机验证，但尚未重新发布，因此公开 Beta 不包含全部最新修复。

## 主要功能

- 搜索轻小说，查看详情、目录、标签和来源信息。
- 分页或连续阅读，保存语义阅读位置、章节完成状态和书签。
- 管理本地书架、稍后阅读、收藏夹、快捷书架和自定义顺序。
- 使用网格、列表或紧凑布局，并支持多选和拖放整理。
- 缓存章节和封面，在已有数据可用时减少重复请求。
- 查看网站收藏，并在明确确认后执行添加、移动或移除。
- 检查书籍更新；计划检查默认关闭，由用户自行开启。
- 导入、导出和迁移本地数据。
- 从官方签名目录安装来源扩展，也支持手动导入可信的 `.hxp` 文件。

Tsuyomi 不要求账号，不接入遥测、远程功能开关或自动崩溃上报，也不依赖 Google Play Services。

## 当前状态

- 普通 Android 界面（Standard）是当前主要维护和验证的版本。
- E-ink 模式保留在代码中，但目前冻结，尚未完成新一轮墨水屏真机适配。
- Wenku8 是当前完成度最高的来源；更多来源仍在规划中。
- 公开 Beta 之后的修复已经进入当前源码，但新的公开安装包尚未发布。

详细变更见 [`CHANGELOG.md`](CHANGELOG.md)。

## 下载与安装

请从 [GitHub Releases](https://github.com/Xfire233/Tsuyomi/releases) 下载 APK。

安装更新时不要先卸载旧版本，否则 Android 会删除应用数据。预发布版可能仍有问题，重要数据请先导出备份。

来源扩展由独立仓库维护：

- [Chachaanteng/tsuyomi-extensions](https://github.com/Chachaanteng/tsuyomi-extensions)

应用会校验扩展包、发布者身份和来源权限。Tsuyomi 不提供 CAPTCHA 求解或反爬绕过；需要登录或安全验证时，必须由用户在受控页面中完成。

## 仓库结构

| 目录 | 内容 |
|---|---|
| [`tsuyomi-android`](tsuyomi-android) | Android 应用、阅读器、书架、数据库和系统集成 |
| [`tsuyomi-protocol`](tsuyomi-protocol) | 扩展协议、JSON Schema、测试 fixture 和一致性检查 |
| [Chachaanteng/tsuyomi-extensions](https://github.com/Chachaanteng/tsuyomi-extensions) | 独立维护的来源扩展、测试和发布流程 |

Android、协议和扩展分别使用自己的版本号。Android 构建不依赖本机旁边存在扩展仓库，也不会从远端的“最新版”动态取代码。

## 从源码构建

需要：

- JDK 17
- Android SDK（包括 API 37、platform-tools 和 emulator）
- Node.js 与 npm
- Python，以及 `reuse` 6.2.0

Windows PowerShell：

```powershell
git clone https://github.com/Xfire233/Tsuyomi.git
cd Tsuyomi

$env:ANDROID_SDK_ROOT = '<你的 Android SDK 路径>'
./tsuyomi-android/tools/Doctor.ps1

cd tsuyomi-android
./gradlew.bat --no-daemon --console=plain --dependency-verification strict :app:assembleDebug
```

协议测试：

```powershell
cd tsuyomi-protocol
npm ci
npm test
```

仓库规则检查：

```powershell
python -m reuse lint
python tools/check_repository.py
```

更完整的开发要求见 [`CONTRIBUTING.md`](CONTRIBUTING.md)。Android 专用要求见 [`tsuyomi-android/CONTRIBUTING.md`](tsuyomi-android/CONTRIBUTING.md)。

## 接下来

- 发布包含最新修复的新测试版本，并完成对应制品的真机验收。
- 恢复 E-ink 模式的界面审查和墨水屏真机测试。
- 支持更多签名来源扩展。
- 增加本地 EPUB、TXT 等文档的阅读支持。

## 内容与隐私

- Tsuyomi 不托管、不提供小说正文，也不隶属于任何内容网站。
- 用户和扩展作者应遵守所在地法律、版权规定和网站服务条款。
- 请勿在 Issue、日志、截图或备份中公开 Cookie、Token、账号、数据库或未脱敏网页内容。

## 致谢与许可证

Tsuyomi 受到 Tachiyomi 等开源阅读器在书架、阅读体验和扩展生态方面的启发，但不是这些项目的分支或官方继任者。

项目自己的代码和文档使用 [Apache License 2.0](LICENSE)。第三方项目、依赖和研究来源保留各自许可证，详情见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。
