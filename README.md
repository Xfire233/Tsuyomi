<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

<p align="center">
  <img src="tsuyomi-android/app/src/main/res/drawable-nodpi/tsuyomi_launcher_artwork.png" alt="Tsuyomi 图标：珊瑚红的开放式つ与奶油色书页" width="128" height="128">
</p>

<h1 align="center">Tsuyomi</h1>

<p align="center">本地优先的原生 Android 轻小说阅读器</p>

<p align="center">
  <a href="https://github.com/Xfire233/Tsuyomi/releases">下载 APK</a> ·
  <a href="CONTRIBUTING.md">参与贡献</a> ·
  <a href="https://github.com/Xfire233/Tsuyomi/issues">反馈问题</a>
</p>

使用 Kotlin 与 Jetpack Compose 开发，支持 **Android 10 及以上版本**。目前处于 **Beta 预发布阶段**。

## 下载与安装

从 [GitHub Releases](https://github.com/Xfire233/Tsuyomi/releases) 下载 APK，以发布页面列出的版本和制品为准。应用目前处于 Beta 预发布阶段，尚不是稳定版。

更新时不要先卸载旧版本，否则 Android 会删除应用数据。预发布版可能存在问题，请先导出重要数据作为备份。

## 功能

- **发现与搜索**：浏览来源分类与榜单，搜索轻小说，查看详情和章节目录。
- **阅读**：分页或连续阅读，调整阅读设置，保存阅读进度、章节完成状态与书签。
- **本地阅读（0.5.0-beta.1，待发布）**：导入 EPUB、TXT、MOBI、AZW3，使用同一阅读器保存进度与书签，并可选择含原书的备份归档。
- **本地书架**：管理收藏夹、稍后阅读、标签、布局与排序。
- **离线与备份**：缓存章节和封面，导入或导出本地数据。
- **网站收藏与更新**：查看网站收藏，确认后执行网站操作；更新检查默认关闭，可自行开启。
- **来源扩展**：从官方签名目录或本地文件安装扩展；扩展由[独立仓库](https://github.com/Chachaanteng/tsuyomi-extensions)维护。

Wenku8 是当前完成度最高的来源，Standard 界面是当前维护重点。登录或安全验证由用户在受控页面中完成，应用不提供 CAPTCHA 求解或反爬绕过。

## 实现计划 · TODO

正在准备 0.5.0-beta.1 预发布，已加入四格式本地阅读与黑白 E-ink 呈现，尚未发布。当前 E-ink 改善与完整恢复验证暂停，实机与人工验收仍未完成；以下列出剩余验证与后续计划，不承诺发布日期。

- [ ] **Standard 完善**：持续修复浏览、书架与阅读中的问题，完善异常恢复与长时间阅读体验。
- [ ] **无障碍与适配**：完成 TalkBack、键盘操作、大字号和不同窗口尺寸的体验验证及修正。
- [ ] **0.5.0 · E-ink 验证**：完整恢复与实机验证暂缓，彩屏原色支持后续推进。
- [ ] **0.5.0 · 本地阅读验证**：四格式导入、阅读、进度恢复与原书备份已实现，继续完成兼容性、数据恢复与安全验证。
- [ ] **更多来源**：后续评估 ESJZone、Yamibo 等来源；不属于当前开发阶段。

下一版本见 [0.5.0 实施计划](tsuyomi-android/docs/phases/VERSION_0_5_0_PLAN.md)；当前实现范围见 [Phase 4](tsuyomi-android/docs/phases/PHASE_4.md)。

## 开发

参与贡献请先阅读[贡献指南](CONTRIBUTING.md)。[Android 构建与验证](tsuyomi-android/CONTRIBUTING.md)说明各平台的环境要求和命令；[协议组件](tsuyomi-protocol)维护扩展与数据交换契约。

## 隐私、内容与许可证

- 不要求 Tsuyomi 账号，不接入遥测、远程功能开关或自动崩溃上报，也不依赖 Google Play Services。
- Tsuyomi 不托管或提供小说正文，也不隶属于内容网站。用户和扩展作者应遵守所在地法律、版权规定及网站服务条款。
- 请勿在 Issue、日志、截图或备份中公开 Cookie、Token、账号、数据库或未脱敏网页内容。
- Tsuyomi 自有代码与文档使用 [Apache License 2.0](LICENSE)。第三方依赖、研究来源及其许可证和归属见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。Tsuyomi 受 Tachiyomi 等开源阅读器启发，但不是其分支、继任者或官方关联项目。
