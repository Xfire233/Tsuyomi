<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi

<img src="tsuyomi-android/app/src/main/res/drawable-nodpi/ic_launcher.png" alt="Tsuyomi 图标：珊瑚红的开放式つ与奶油色书页" width="112" height="112">

Tsuyomi 是一个本地优先的 Android 轻小说阅读器，使用 Kotlin 和 Jetpack Compose 开发。

它目前主要支持 Wenku8，提供找书、查看详情和目录、阅读、保存进度、管理书架与检查更新等功能。来源以独立签名扩展提供，不需要为每个网站安装一个 APK。

> [!IMPORTANT]
> 最新公开版本是预发布版 [`android-v0.3.0-beta.4`](https://github.com/Xfire233/Tsuyomi/releases/tag/android-v0.3.0-beta.4)，不是稳定版。
>
> 当前源码包含公开 Beta 之后的状态恢复、缓存、来源安装与界面修正；本轮 Standard 预发布修正包已于 2026-10-05 由维护者验收。新的公开安装包尚未发布，公开 Beta 不包含全部最新改动；未执行的辅助技术与正式准入检查仍按各自门禁处理。

## 主要功能

- 搜索轻小说，查看详情、目录、标签和来源信息。
- 分页或连续阅读，保存语义阅读位置、章节完成状态和书签。
- 管理本地书架、稍后阅读、手动和智能收藏夹，以及自定义顺序。
- 使用网格、列表或紧凑布局，并支持多选和拖放整理。
- 缓存章节和封面，在已有数据可用时减少重复请求。
- 查看网站收藏，并在明确确认后执行添加、移动或移除。
- 检查书籍更新；计划检查默认关闭，由用户自行开启。
- 导入、导出和迁移本地数据。
- 从官方签名目录安装来源扩展，也支持手动导入 `.hxp` 文件；本地未签名包需单独确认风险，不能通过官方目录安装或自动更新。

Tsuyomi 不要求账号，不接入遥测、远程功能开关或自动崩溃上报，也不依赖 Google Play Services。

## 界面设计

- **图标**：复用应用实际使用的 F6R 标记。珊瑚红的开放式 `つ`、奶油色书页与连续立体深度置于羊皮纸底色；README 与安装图标共用同一份资源。
- **配色**：默认浅色与深色界面以黑、白、灰为主，普通按钮和背景保持中性。珊瑚红只强调已选中图标、文字与状态标记，不铺满卡片或按钮。
- **控件**：使用 Jetpack Compose 与 Material 3 主题、组件。固定单选设置采用基于 Material 3 Theme 的定制分段控件：连续灰轨、移动选中实面与轻阴影；不是官方默认 `SegmentedButton` 样式。
- **导航与筛选**：根书架保留固定的 `书架 / 继续阅读 / 稍后再读` 分栏。来源首页的主分栏与 Tag/排序随向后浏览共同收起，当前位置反向下拉即可重现；换栏不把目录滚回顶部。
- **分类标签**：保持来源顺序，单选用珊瑚描边和文字、不加勾。横向滚动采用原生裁切，边缘半颗标签在拖动与停止后保持所见即所得；返回分类或关闭面板时让当前选中项完整入视。
- **反馈与适配**：加载、空状态与错误使用克制的单色颜文字和明确操作；减弱动画时保持静态。控件按可用窗口和字号适配，保留选中语义、键盘焦点与至少 48dp 触区；这些设计要求不等于未经执行的辅助技术验收。

产品界面约定见 [`UI_CONSTITUTION.md`](tsuyomi-android/docs/design/UI_CONSTITUTION.md)，本轮验收与提交授权见 [`Phase 4`](tsuyomi-android/docs/phases/PHASE_4.md#standard-prerelease-acceptance-and-cleanuppublication-authorization--2026-10-05)。显式 Dynamic Color、阅读器内容主题与冻结的 E-ink 模式保留各自作用范围。

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
