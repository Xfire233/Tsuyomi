<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi for Android

<img src="app/src/main/res/drawable-nodpi/tsuyomi_launcher_artwork.png" alt="Tsuyomi F6R 应用图标" width="112" height="112">

本地优先的原生 Android 轻小说阅读器，使用 Kotlin 与 Jetpack Compose 构建，支持 Android 10 及以上版本（`minSdk 29`）。

## 下载与更新

从 [GitHub Releases](https://github.com/Xfire233/Tsuyomi/releases) 下载 APK，以发布页面列出的版本和制品为准。应用目前处于 Beta 预发布阶段，尚不是稳定版。

更新时不要先卸载旧版，否则 Android 会删除应用数据。请先导出重要数据备份。

## 功能与状态

- 搜索、目录与阅读；保存语义阅读进度和书签。
- 管理本地书架、收藏夹、稍后阅读、排序与布局；缓存章节和封面。
- 查看网站收藏并在确认后操作；更新检查默认关闭。
- 通过独立签名扩展支持内容来源；扩展由[独立仓库](https://github.com/Chachaanteng/tsuyomi-extensions)维护。

Wenku8 是当前完成度最高的来源。Standard 界面是维护重点；E-ink 模式保留但暂时冻结，尚未完成新一轮墨水屏适配。应用不提供 CAPTCHA 求解或反爬绕过；安全验证由用户在受控页面完成。

## 开发与贡献

构建、本地 API 29 验证与 Android 贡献说明见 [`CONTRIBUTING.md`](CONTRIBUTING.md)。完整质量门由 [`QUALITY_GATES.md`](docs/process/QUALITY_GATES.md) 维护。Monorepo 与协议入口见[根 README](../README.md)及 [`tsuyomi-protocol`](../tsuyomi-protocol)。

## 隐私、内容与许可

Tsuyomi 不要求账号，不接入遥测、远程功能开关或自动崩溃上报，也不依赖 Google Play Services。项目不托管或提供小说正文，不隶属于内容网站；用户与扩展作者应遵守适用法律、版权和网站服务条款。请勿公开 Cookie、Token、账号、数据库或未脱敏网页内容。

Tsuyomi 是独立项目，与内容网站及所研究的阅读器项目无官方关系。项目自有代码、文档与原创素材使用 [Apache License 2.0](LICENSES/Apache-2.0.txt)；第三方依赖和研究来源的版权、许可及采用边界详见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。
