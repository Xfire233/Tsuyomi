<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi

<img src="tsuyomi-android/app/src/main/res/drawable-nodpi/tsuyomi_launcher_artwork.png" alt="Tsuyomi 图标：珊瑚红的开放式つ与奶油色书页" width="112" height="112">

Tsuyomi 是一款本地优先的原生 Android 轻小说阅读器，使用 Kotlin 和 Jetpack Compose 开发，支持 Android 10 及以上版本。

## 下载与安装

从 [GitHub Releases](https://github.com/Xfire233/Tsuyomi/releases) 下载 APK，以发布页面列出的版本和制品为准。应用目前处于 Beta 预发布阶段，尚不是稳定版。

更新时不要先卸载旧版本，否则 Android 会删除应用数据。预发布版可能存在问题，请先导出重要数据作为备份。

## 功能

- 搜索轻小说、查看详情与目录，并进行分页或连续阅读；保存阅读进度、章节完成状态和书签。
- 管理本地书架、稍后阅读、收藏夹、布局与排序；缓存章节和封面，导入或导出本地数据。
- 查看网站收藏并在确认后进行操作；更新检查默认关闭，由用户自行开启。
- 从官方签名目录或本地文件安装来源扩展；扩展在独立仓库维护：[tsuyomi-extensions](https://github.com/Chachaanteng/tsuyomi-extensions)。

目前 Wenku8 是完成度最高的来源。Standard 界面是当前维护重点；E-ink 模式保留但暂时冻结，尚未完成新一轮墨水屏适配。来源需要登录或安全验证时，由用户在受控页面中完成；应用不提供 CAPTCHA 求解或反爬绕过。

## 开发

Android 构建、贡献要求与本地验证说明见 [`CONTRIBUTING.md`](tsuyomi-android/CONTRIBUTING.md)。协议组件入口见 [`tsuyomi-protocol`](tsuyomi-protocol)。

## 隐私、内容与许可证

- 不要求 Tsuyomi 账号，不接入遥测、远程功能开关或自动崩溃上报，也不依赖 Google Play Services。
- Tsuyomi 不托管或提供小说正文，也不隶属于内容网站。用户和扩展作者应遵守所在地法律、版权规定及网站服务条款。
- 请勿在 Issue、日志、截图或备份中公开 Cookie、Token、账号、数据库或未脱敏网页内容。
- Tsuyomi 自有代码与文档使用 [Apache License 2.0](LICENSE)。第三方依赖、研究来源及其许可证和归属见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。Tsuyomi 受 Tachiyomi 等开源阅读器启发，但不是其分支、继任者或官方关联项目。
