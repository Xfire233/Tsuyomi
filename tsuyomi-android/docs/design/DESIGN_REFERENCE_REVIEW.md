<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi 移动 UI、阅读器与 Android 设计参考审阅

- 调研日期：2026-08-12
- 产品背景：本地优先、简体中文、Kotlin + Jetpack Compose、面向手机/平板/大字体/深色环境与全局 E-ink profile 的轻小说阅读器。
- 文档性质：参考证据索引与许可边界，不是产品 UI 合同，也不是生产实现授权。
- 决策关系：`UI_CONSTITUTION.md` 的 Active constraint spine 是唯一当前产品可见合同；`UI_ATLAS.md` 只定义它的证据执行；`DESIGN_DIRECTION_HANDOFF.md` 保存 review provenance 与 supersession 历史；本文件只保留事实、许可和可参考范围。任何下方横向结论或旧 RC2 映射都不得产生新的可见 UI 约束。

## 0. 如何审阅

每个参考项后均保留决策框：

- [ ] 采用：方向和行为基本照此设计，但实现仍由 Tsuyomi 原创完成。
- [ ] 改造：保留亮点，按简体中文、Compose、无障碍和 E-ink 约束改造。
- [ ] 拒绝：明确不进入 Tsuyomi。
- [ ] 后看：需要截图、原型或代码深读后再决定。

### 许可证标记

| 标记 | 含义 |
|---|---|
| 可参考实现 | Apache-2.0/MIT 等许可下可研究代码；真正采用前仍需核对文件头、NOTICE、第三方资源和依赖许可证 |
| 仅行为研究 | GPL/AGPL 或商业闭源；只研究公开行为、信息架构和用户体验，不复制代码、图标、图片、文案或素材 |
| 官方规范 | Android Developers、Material Design、W3C 等规范或平台文档 |

---

# 第一部分：移动 UI、完整 App 与可实现设计系统

## A. 强烈建议优先看

### A1. Material 3 in Compose

- 链接：[Compose Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3) · [Material Design 3](https://m3.material.io/)
- 代码/许可：AndroidX/官方 Compose 示例，Apache-2.0 范围内可参考实现。
- 亮点：颜色、排版、形状、组件状态均通过语义 token 组合；支持动态色、深色模式与无障碍。
- Tsuyomi 借鉴：继续以 Material 3 为平台基线，但正文主题与全局 E-ink profile 必须覆盖动态色、阴影、透明和动画；feature 页面只消费语义组件。
- 风险：不能把 Material 默认外观当成产品辨识度；需要 Tsuyomi 自有排版节奏、纸张/墨色与领域组件。
- 审阅：- [√] 采用 - [ ] 改造 - [ ] 拒绝 - [ ] 后看

### A2. Reply

- 链接：[源码](https://github.com/android/compose-samples/tree/main/Reply)
- 代码/许可：Kotlin、Jetpack Compose、Material 3；Apache-2.0，可参考实现。
- 亮点：窄屏列表/详情切换，宽屏多栏，自适应导航，手机/平板/折叠屏共用状态模型。
- Tsuyomi 借鉴：手机保持单列书库与详情；宽屏采用 navigation rail + 列表 + 详情/supporting pane，断点变化保留选中项、滚动和返回栈。
- 风险：邮件列表的信息结构不是阅读器结构，不能直接照搬行密度和操作优先级。
- 审阅：- [ ] 采用 - [√] 改造 - [ ] 拒绝 - [ ] 后看

### A3. Adaptive Apps Samples

- 链接：[官方仓库](https://github.com/android/adaptive-apps-samples) · [样例总览](https://developer.android.com/develop/adaptive-apps/samples)
- 代码/许可：Kotlin、Compose Adaptive；Apache-2.0，可参考实现。
- 亮点：canonical layouts、list-detail、supporting pane、网格、折叠姿态、分屏和自由窗口。
- Tsuyomi 借鉴：依据实际窗口而不是“手机/平板”分叉；宽屏增加目录、详情或辅助信息，正文列仍保持最大宽度。
- 风险：不要为“利用空间”强行添加没有真实任务的第二栏或第三栏。
- 审阅：- [√] 采用 - [ ] 改造 - [ ] 拒绝 - [ ] 后看

### A4. Now in Android

- 链接：[源码](https://github.com/android/nowinandroid) · [Figma case study](https://www.figma.com/community/file/1164313362327941158/now-in-android-case-study) · [架构说明](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md)
- 代码/许可：完整 Compose 产品级样例；Apache-2.0，可参考实现。
- 亮点：Material 3、动态色、明暗主题、自适应布局、模块化、UI catalog、跨尺寸 screenshot tests。
- Tsuyomi 借鉴：建立 token → 组件 → 页面 → screenshot matrix 的完整链路；把离线、加载、错误、刷新建模为稳定 UI state。
- 风险：信息流卡片和兴趣选择不应变成商业推荐首页；本地阅读任务优先。
- 审阅：- [√] 采用 - [ ] 改造 - [ ] 拒绝 - [ ] 后看

### A5. Jetcaster

- 链接：[源码](https://github.com/android/compose-samples/tree/main/Jetcaster)
- 代码/许可：Compose、Room、WindowInsets、supporting pane；Apache-2.0，可参考实现。
- 亮点：内容封面、详情、持续状态、队列和宽屏辅助窗格。
- Tsuyomi 借鉴：继续阅读可借鉴“当前播放/队列”的持续状态模型；封面驱动色彩只允许在 Standard 的受限表面，E-ink 固定灰阶高对比。
- 风险：播放器式常驻控件不能抢占阅读正文。
- 审阅：- [√] 采用 - [ ] 改造 - [ ] 拒绝 - [ ] 后看

### A6. Jetsnack

- 链接：[源码](https://github.com/android/compose-samples/tree/main/Jetsnack)
- 代码/许可：Compose 自定义 design system/layout/animation；Apache-2.0，可参考实现。
- 亮点：不依赖默认 Material 卡片堆叠，通过自有主题、网格和详情布局形成清晰风格。
- Tsuyomi 借鉴：抽取少量领域组件，如 `ReaderScaffold`、`BookCard`、`MetadataRow`、`ProgressStatus`，统一边距、分隔、状态和按压反馈。
- 风险：折叠 hero、复杂封面转场和高装饰密度仅适合 Standard 且仍需克制；E-ink 禁用。
- 审阅：- [√] 采用 - [ ] 改造 - [ ] 拒绝 - [ ] 后看
        这bookcard特别好，但只在特定来源适用，看情况应用吧。wenku8首页应该可以用上，参考flutter版本wenku8浏览首页实现。

### A7. Tivi

- 链接：[源码](https://github.com/chrisbanes/tivi) · [许可证](https://github.com/chrisbanes/tivi/blob/main/LICENSE)
- 代码/许可：Kotlin Multiplatform、Compose Multiplatform；Apache-2.0，可参考实现。
- 亮点：收藏、发现、详情、状态模型、截图测试、模块边界；成熟项目的完整演进可读。
- Tsuyomi 借鉴：详情页中封面、标题、状态和主动作的层级；书库/进度按 feature/data/UI 分层。
- 风险：项目已完成/停止活跃演进；不应为 Tsuyomi 引入不需要的 KMP 复杂度。
- 审阅：- [ ] 采用 - [√] 改造 - [ ] 拒绝 - [ ] 后看

### A8. Thunderbird for Android

- 链接：[源码](https://github.com/thunderbird/thunderbird-android) · [开发文档](https://thunderbird.github.io/thunderbird-android/docs/latest/)
- 代码/许可：Kotlin、多模块，Apache-2.0；不是 Compose-first，适合状态与信息架构研究。
- 亮点：高信息密度列表、统一收件箱、多选、离线同步、错误与设置层级。
- Tsuyomi 借鉴：书库统一视图、批量操作、同步/导入状态条和可恢复失败；重要结果不依赖 Toast。
- 风险：邮件密度不适合正文和封面浏览；UI 框架迁移成本中等。
- 审阅：- [ ] 采用 - [√] 改造 - [ ] 拒绝 - [ ] 后看

## B. 值得局部借鉴

### B1. Jetchat

- 链接：[源码](https://github.com/android/compose-samples/tree/main/Jetchat)
- 许可：Apache-2.0，可参考实现。
- 亮点：主内容持续可见，操作区域按需出现；输入、返回和 UI state 清楚。
- 借鉴：阅读工具栏/设置面板按需出现，关闭后恢复语义位置；E-ink 使用即时切换。
- 审阅：- [ ] 采用 - [√] 改造 - [ ] 拒绝 - [ ] 后看

### B2. JetLagged

- 链接：[源码](https://github.com/android/compose-samples/tree/main/JetLagged)
- 许可：Apache-2.0，可参考实现。
- 亮点：Canvas、Path、自定义布局与轻量图形。
- 借鉴：章节时间线、下载队列、阅读统计可采用线条/刻度；E-ink 必须提供纯线条和块状后备。
- 审阅：- [ ] 采用 - [ ] 改造 - [ ] 拒绝 - [√] 后看

### B3. JetNews

- 链接：[源码](https://github.com/android/compose-samples/tree/main/JetNews)
- 许可：Apache-2.0，可参考实现。
- 亮点：内容列表、文章详情、兴趣筛选、明暗主题和窗口尺寸适配。
- 借鉴：详情页的标题/作者/更新时间/进度分层，正文最大宽度，筛选页的多选结构。
- 风险：文章流不等于书库，不应把正文和应用 chrome 混在同一视觉层。
- 审阅：- [ ] 采用 - [√] 改造 - [ ] 拒绝 - [ ] 后看

### B4. Element X Android

- 链接：[源码](https://github.com/element-hq/element-x-android)
- 许可：AGPL/商业双许可，仅行为研究。
- 亮点：大型 Compose App 的列表稳定性、搜索、错误状态、多语言、深色模式和 Gallery。
- 借鉴：稳定 key、滚动位置、长文本本地化、页面内错误与加载状态。
- 风险：禁止复制 AGPL 代码和资源。
- 审阅：- [ ] 采用 - [√] 改造 - [ ] 拒绝 - [ ] 后看

### B5. Tusky

- 链接：[GitHub 旧仓库](https://github.com/tuskyapp/Tusky) · [Codeberg](https://codeberg.org/tusky/Tusky)
- 许可：GPL-3.0，仅行为研究。
- 亮点：时间线中的媒体、状态标签、分页、账户上下文和主题。
- 借鉴：明确本地/下载/更新/未读状态；来源上下文切换。
- 风险：社交时间线的连续刷新和密度不适合低干扰阅读。
- 审阅：- [ ] 采用 - [ ] 改造 - [ ] 拒绝 - [√] 后看

### B6. AntennaPod

- 链接：[源码](https://github.com/AntennaPod/AntennaPod) · [官网](https://antennapod.org/)
- 许可：GPL-3.0，仅行为研究。
- 亮点：本地媒体库、下载队列、离线管理、失败重试和批量操作。
- 借鉴：章节下载队列、持久状态中心、失败原因与重试。
- 风险：播放器控制不应常驻阅读正文；禁止复制 GPL 实现或资源。
- 审阅：- [ ] 采用 - [√] 改造 - [ ] 拒绝 - [ ] 后看

## C. 设计工具与组件目录

### C1. AndroidX Material 3 Catalog

- 链接：[AOSP 源码](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/integration-tests/material-catalog)
- 许可：AndroidX Apache-2.0，可参考实现。
- 亮点：实时查看组件、主题、状态、规范与源码，比静态截图更适合逐项审阅。
- Tsuyomi 借鉴：建设内部 UI Catalog，覆盖 Standard/Dark/E-ink、fontScale 1.0/1.3/2.0、空/错/加载/选中/禁用。
- 审阅：- [√] 采用 - [ ] 改造 - [ ] 拒绝 - [ ] 后看

### C2. Now in Android Figma Case Study

- 链接：[Figma](https://www.figma.com/community/file/1164313362327941158/now-in-android-case-study)
- 类型：视觉与 token 研究，不把 Google/NIA 品牌资产作为 Tsuyomi 资源。
- 亮点：theme、styles、components、layouts 与源码可对照。
- Tsuyomi 借鉴：先审阅 token 和组件，再审阅页面；避免每屏独立配色和间距。
- 审阅：- [ ] 采用 - [√] 改造 - [ ] 拒绝 - [ ] 后看

## D. 移动 UI 跨项目结论

1. 手机和宽屏不是同一页面缩放：Compact 用底部导航/单窗格；宽屏用 rail + list-detail/supporting pane。
2. E-ink 是全局 profile，不是读页中的一个主题开关；它同时改变颜色、边界、动画、刷新、列表和反馈策略。
3. 内容层级固定为“标题/正文任务 > 元数据 > 辅助动作”；每个表面只保留一个明确主动作。
4. 下载、解析、离线、更新、阅读进度和同步必须是可见、可恢复的状态，不使用模糊 spinner 或短暂 Toast 代替。
5. 大字体优先：标题/元数据允许重排，避免固定高度；以 screenshot matrix 验证而不是凭默认手机判断。
6. 阅读工具按需出现；设置完成后保留章节与语义位置；E-ink 切换不使用动画。
7. 领域组件应少而稳定，不堆通用卡片、圆角、阴影和渐变。
8. token、组件、页面、截图证据一一对应；所有审美变体必须在 UI Atlas 中可比较。

---

# 第二部分：阅读器竞品与页面设计

## E. 阅读器逐项参考

### E1. Mihon

- 链接：[仓库](https://github.com/mihonapp/mihon) · [Reader settings](https://mihon.app/docs/guides/reader-settings)
- 许可：Apache-2.0，可参考实现。
- 强项：阅读方向、分页/长条、每系列 viewer override、tap zones、音量键、跳过已读/重复章节、E-ink page flash。
- 适用：Standard 高，E-ink 高。
- Tsuyomi 借鉴：作用域明确的阅读设置、tap-zone 可视化、硬件键、页切换刷新选项、下载/备份状态。
- 不照搬：漫画图片阅读模型、扩展生态密度、手势优先路径。
- 审阅：- [√] 采用 - [ ] 改造 - [ ] 拒绝 - [ ] 后看

### E2. Tachiyomi 历史基线

- 链接：[归档仓库](https://github.com/tachiyomiarchive/tachiyomi)
- 许可：Apache-2.0，可研究历史实现；优先看 Mihon 的现行演进。
- 强项：Library → Browse/来源 → Series/章节 → Reader 的经典 Android 阅读器信息架构。
- 不照搬：已归档实现和旧生态假设。
- 审阅：- [√] 采用 - [ ] 改造 - [ ] 拒绝 - [ ] 后看

### E3. KOReader

- 链接：[仓库](https://github.com/koreader/koreader) · [用户指南](https://koreader.rocks/user_guide/) · [简中指南](https://koreader.rocks/user_guide/zh_Hans.html)
- 许可：AGPL-3.0，仅行为研究。
- 强项：E-ink、无动画、刷新间隔、字体/行距/边距/对比、书籍地图、目录、页面浏览器、书签/批注、PDF reflow、Profiles、阅读统计。
- 适用：E-ink 最高，Standard 高。
- Tsuyomi 借鉴：稳定分页、书籍地图/目录跳转、低动画、刷新策略、可导出批注和阅读统计。
- 不照搬：200+ 动作、深层菜单、插件和专家级默认配置。
- 审阅：- [√] 采用 - [ ] 改造 - [ ] 拒绝 - [ ] 后看

### E4. Readest

- 链接：[仓库](https://github.com/readest/readest) · [Library](https://readest.com/docs/library) · [Sync](https://readest.com/docs/sync)
- 许可：AGPL-3.0，仅行为研究；Tauri/Next.js 不是 Compose 实现基线。
- 强项：Grid/List、Recently read、Group/Series/Tags、批量操作、重复检测、批量导入失败汇总、OPDS、流式/离线、可分类型同步和失败数。
- 适用：Standard 高，E-ink 中高。
- Tsuyomi 借鉴：大型本地书库、最近阅读、状态汇总、导入失败明细、最后同步时间和失败数量。
- 不照搬：账户/云同步优先、跨平台 Web UI 的组件实现。
- 审阅：- [ ] 采用 - [√] 改造 - [ ] 拒绝 - [ ] 后看

### E5. Librera Reader

- 链接：[仓库](https://github.com/foobnix/LibreraReader) · [F-Droid](https://f-droid.org/en/packages/com.foobnix.pro.pdf.reader/)
- 许可：GPLv3，并含强 copyleft 依赖；仅行为研究。
- 强项：多格式、文件/书架结合、PDF/EPUB 目录、边距和版式控制、OPDS。
- 适用：Standard 中，E-ink 中。
- 不照搬：文件管理器式主导航、过多细粒度控件、代码和资源。
- 审阅：- [ ] 采用 - [√] 改造 - [ ] 拒绝 - [ ] 后看

### E6. Book's Story

- 链接：[仓库](https://github.com/Acclorite/book-story) · [F-Droid](https://f-droid.org/en/packages/ua.acclorite.book_story/)
- 许可：GPL-3.0-only，仅视觉/行为研究。
- 强项：Jetpack Compose、Material You、本地优先书架，以及从阅读页底部上拉的快速阅读设置和分层设置行为。
- 适用：Standard 高，E-ink 只借鉴任务分组，不借鉴半透明/动画呈现。
- Tsuyomi 借鉴：仅借鉴“阅读中就地打开快速设置、再在同一容器进入高级分组”的行为原则；Standard 用真实 M3 `ModalBottomSheet`，E-ink 用同一状态/顺序的全窗口不透明实现。
- 不照搬：GPL 代码、Compose 结构、布局、主题资源、颜色、图标、视觉资产、文案或交互细节。Tsuyomi 必须原创实现并按自己的 scope/Back/focus 合同验收。
- 审阅结论：行为原则改造采用；视觉和实现不复用。

### E7. Foliate

- 链接：[官网](https://johnfactotum.github.io/foliate/) · [仓库](https://github.com/johnfactotum/foliate)
- 许可：GPL-3.0，仅行为研究；Linux GTK，不是 Android 代码基线。
- 强项：低干扰阅读、自动隐藏控制栏、侧栏目录/搜索、脚注、竖排、注释导出和导航历史。
- 适用：Standard 高，E-ink 中高。
- Tsuyomi 借鉴：目录/搜索/批注统一辅助容器；关闭后回到原位置。
- 风险：自动隐藏入口必须对 TalkBack、键盘和首次用户可发现。
- 审阅：- [ ] 采用 - [ ] 改造 - [ ] 拒绝 - [ ] 后看

### E8. Kavita

- 链接：[官网](https://www.kavitareader.com/) · [仓库](https://github.com/Kareadita/Kavita)
- 许可：GPLv3，仅行为研究。
- 强项：Library/Series/Book 层级、元数据、智能筛选、Reading Lists、多格式 reader、下载和权限。
- 适用：Standard 中高，E-ink 中。
- Tsuyomi 借鉴：大库层级、系列/卷/书/章节、智能集合和阅读列表。
- 不照搬：服务器管理、账户权限和首页推荐面板。
- 审阅：- [ ] 采用 - [√] 改造 - [ ] 拒绝 - [ ] 后看

### E9. Komga

- 链接：[仓库](https://github.com/gotson/komga) · [Readers](https://komga.org/docs/category/readers/) · [EPUB Webreader](https://komga.org/docs/guides/webreader-epub)
- 许可：MIT，可参考部分实现/API；Web 技术栈仍需重写为 Compose。
- 强项：Library → Series → Book、Collections/Read lists、EPUB 黑白主题、分页/滚动/多列、OPDS、客户端进度同步。
- 适用：Standard 高，E-ink 中高。
- Tsuyomi 借鉴：清晰领域层级、服务端/本地状态边界、分页与多列设置模型。
- 风险：服务器和账户不得成为本地阅读前置条件。
- 审阅：- [ ] 采用 - [ ] 改造 - [ ] 拒绝 - [√] 后看

### E10. Kindle

- 链接：[Android 产品页](https://play.google.com/store/apps/details?id=com.amazon.kindle)
- 许可：商业闭源，仅行为/视觉研究。
- 强项：统一 Aa 菜单、Page Flip 不丢当前位置、进度/章节剩余时间、书签/高亮/笔记聚合、跨设备同步。
- 适用：Standard 高，E-ink 中高。
- Tsuyomi 借鉴：排版入口、预览/跳转后返回原位置、进度表达和笔记本。
- 不照搬：商店推荐、促销、账号/DRM 依赖、品牌文案与资产。
- 审阅：- [ ] 采用 - [√] 改造 - [ ] 拒绝 - [ ] 后看

### E11. Google Play Books

- 链接：[阅读帮助](https://support.google.com/googleplay/answer/185545?hl=en&co=GENIE.Platform%3DAndroid) · [书架分组](https://support.google.com/googleplay/answer/9402600?hl=en&co=GENIE.Platform%3DAndroid)
- 许可：商业闭源，仅行为/视觉研究。
- 强项：Original pages/Flowing text 的显式模式、shelves、筛选、多选、Contents 中的章节/书签/笔记。
- 适用：Standard 高，E-ink 中高。
- Tsuyomi 借鉴：固定版式与流式文本分开解释；书架 shelf/筛选；统一 Contents 面板。
- 不照搬：商店和 Google 服务依赖。
- 审阅：- [√] 采用 - [ ] 改造 - [ ] 拒绝 - [ ] 后看

### E12. Apple Books

- 链接：[iPhone 阅读指南](https://support.apple.com/guide/iphone/read-books-iphc1af7c57/ios) · [iCloud 同步](https://support.apple.com/guide/icloud/set-up-books-mm3941ae3362/icloud)
- 许可：商业闭源，仅行为/视觉研究。
- 强项：Collections、Themes & Settings、字体/粗体/行距/字距/词距/边距、Line Guide、回到原阅读位置、阅读目标。
- 适用：Standard 高，E-ink 中高。
- Tsuyomi 借鉴：可访问排版、Line Guide、目录/搜索/书签一致入口。
- 不照搬：彩色推荐、卷页动效、iCloud 前置和品牌资产。
- 审阅：- [ ] 采用 - [√] 改造 - [ ] 拒绝 - [ ] 后看

### E13. Moon+ Reader

- 链接：[官网](https://www.moondownload.com/) · [Google Play](https://play.google.com/store/apps/details?id=com.flyersoft.moonreader)
- 许可：商业闭源，仅行为/视觉研究。
- 强项：格式、主题、手势、自动滚动、阅读尺、统计、WebDAV/Dropbox 同步，适合作为高级设置需求清单。
- 适用：Standard 中高，E-ink 中。
- Tsuyomi 借鉴：阅读设置按快速/高级/设备分层；阅读尺与统计作为可选工具。
- 不照搬：选项爆炸、广告、促销、复杂动画和自动滚动默认。
- 审阅：- [ ] 采用 - [√] 改造 - [ ] 拒绝 - [ ] 后看

### E14. ReadEra

- 链接：[官网](https://readera.org/) · [Google Play](https://play.google.com/store/apps/details?id=org.readera)
- 许可：商业闭源，仅行为/视觉研究。
- 强项：无注册、离线、自动发现本地文件、Favorites/To Read/Have Read、作者/系列/Collections、大库搜索排序。
- 适用：Standard 最高，E-ink 高。
- Tsuyomi 借鉴：首次启动无账号可用；待读/已读/收藏；本地自动发现与离线明确。
- 风险：自动扫描必须控制耗电、权限和索引反馈；不能假设云同步。
- 审阅：- [ ] 采用 - [√] 改造 - [ ] 拒绝 - [ ] 后看

### E15. Panels

- 链接：[官网](https://www.panels.app/) · [Komga 客户端](https://www.panels.app/komga-ios-app) · [OPDS](https://www.panels.app/opds-client)
- 许可：商业闭源，仅行为/视觉研究。
- 强项：封面驱动 Library、整系列下载、离线/在线共用 reader、进度回写、平板单/双页。
- 适用：Standard 中高，E-ink 中。
- Tsuyomi 借鉴：在线/待下载/已下载状态、整系列离线操作、断网阅读后同步。
- 不照搬：漫画 panel-by-panel、卷页动画和彩色滤镜。
- 审阅：- [ ] 采用 - [√] 改造 - [ ] 拒绝 - [ ] 后看

## F. 深挖后的页面结构综合（RC2.1）

### F1. 书架/资料库

- 参考链：Hikari folder flow + Readest/ReadEra/Google shelves + Mihon/Book's Story layout switching。
- 页面骨架：固定 AppBar → 同一内容流中的 `SystemNode / CollectionNode / MirrorNode / BookNode` → Standard/E-ink 常规手机默认固定三列 grid，dense/compact 可切。默认节点置顶，可切完全混排或手动排列。
- 系统节点 `继续阅读 / 最近阅读 / 稍后再读 / 休眠来源 / 追更` 默认创建，可隐藏/重建/手动排列但规则定义不可改；仅稍后再读允许手动 membership。删除独立模板管理与 selector-only 导航。
- Grid 固定 `3:4 visual + 2-line title + 1-line state`；常规手机三列、double-compact 必要时两列；compact 48–56dp；状态进入固定 badge/glyph/action 槽。E-ink 选中必须有大勾和高对比边界。
- Library 行/卡不显示来源或 tag；无封面用完整 3:4 长标题 field，不显示来源品牌。

### F2. 收藏夹、镜像、历史与追更

- 收藏夹从书架节点直接进入，子收藏夹与书籍同级，最多两级；Library AppBar 与末页加号进入同一创建流。Standard 两书拖放先命名/确认并将两书原子加入新收藏夹；E-ink 使用按钮等效。
- 镜像页默认只显示 `网站结构`；用户在镜像页显式创建后才显示 `本地整理`。本地节点带本地语义且永不触发网站写入。
- 历史 AppBar 一键清空；≤7 天相对时间，之后精确日期时间。
- Updates 采用 Mihon 的日期分组和 56dp 行，叠加 Tsuyomi exact-anchor/session 报告；Standard working 使用短时 M3 indicator，E-ink 使用静态 glyph；支持 grid/list/compact。

### F3. 搜索/发现与规则

- query draft inert；一次显式提交同时启动本地与所选在线来源，完成项进入一个主结果流并共享一个总进度。
- Exact `BookIdentity` 合并到 canonical item；同名不同 identity 分开。内部来源失败可独立重试，但正常来源 status strip/lane 不常驻。
- Search/Remote/Tags 支持 grid/list/compact。Tags 用 `本地 / 来源` tab 明确所有权且总览不显示书籍数。
- D33 高级公共/本地/来源专属筛选与 descriptor UI 在 Atlas/Gate 4A 暂缓；未来重启时仍必须使用真实 M3 控件和 bounded data descriptor，不把字段和值拼成 `·` 字符串。

### F4. 详情/目录

- 参考链：Hikari `NovelDetailPage` + Mihon detail/chapters。
- 一个连续 surface：身份头 → 进度/五星评分 → 紧凑 tag/动态动作 → 简介 → chapter header → 完整章节。普通目录 route 被章节锚点取代。
- 一个动态 `SmallExtendedFAB`：近期下翻时快速到底，近期上翻时快速到顶，idle 恢复继续阅读；章节 header 固定正/倒序、筛选、跳章。删除“最近章节”重复区。
- RatingInput 一步点选/清除；来源动态区仅放低风险快捷动作，缓存为顶栏一级图标；远端/本地副作用分别确认。

### F5. 阅读页与进度导航

- 参考链：Hikari WYSIWYG seek session + KOReader Skim Widget/章节刻度/返回原位置 + 成熟 Reader chrome。
- 正文中心 tap 只切换 chrome。顶栏：返回、章节标题、目录/搜索/书签、设置；底栏：上一章、章节刻度轨、当前位置、下一章。点按进度轨立即跳转。
- 拖动 preview session 不移动 live reader、不持久化；reading viewport 原地显示实时 WYSIWYG 目标，release 一次提交 semantic locator，cancel 返回原位置，提交后仍提供 `返回原位置`。
- E-ink 使用离散整页静态 preview，经确认后一次提交；正文锁定分页。

### F6. 阅读设置

- Standard 为同一个真实 M3 `ModalBottomSheet` 的部分/全高两态；部分态下拉/scrim/Back 关闭，上拉到全高，全高下拉直接关闭。始终有 drag handle，无标题/关闭行。E-ink 为同状态/顺序的全窗口不透明容器。
- 快速层不放 scope，部分态首屏完整显示字号、行距、边距、段距，label/value/control 同行；调整立即预览并保存。其他设置与高级分组仍在同一容器。
- 完整设置分 `排版 / 页面 / 导航 / 设备`；scope 在高级层。必须覆盖连续字号、字重、行/段/字距、首行缩进、四向边距、对齐、前景/背景、tap zones、硬件键、旋转、常亮、沉浸和进度呈现。
- Back/focus/semantic locator 规则与 Standard/E-ink 完全同构；fontScale 2.0 可达。

### F7. 下载、离线、来源与错误

- 使用持久可解释状态机和页面内结果；Toast 不承担关键反馈。
- 来源名称由 route/title/lane 建立；只有混合上下文需要紧凑 mark。来源品牌不进入 Library、Reader、导航、sheet 或 cover fallback。
- E-ink 用位置、文字、图标、边界与显式刷新表达；不虚称控制 OEM waveform。


## G. 明确拒绝的竞品模式

1. 商业商店式首页覆盖继续阅读。
2. E-ink 上的无限滚动、卷页动画、渐变、透明和大面积频繁刷新。
3. 专家设置全部堆在一个页面，缺少快速/高级/设备分层。
4. 漫画 panel-by-panel 或双页模式成为轻小说默认。
5. 账号、服务器或云同步成为本地文件首次阅读前置条件。
6. 颜色是状态的唯一载体。
7. 复制 GPL/AGPL 实现、商业资源、品牌图标、截图或文案。
8. 关键失败只用 Toast 或短暂动画反馈。

---

# 第三部分：Android、Compose、排版与布局规范

## H. 规范等级

- **平台/官方建议**：Android Developers、Material Design 官方指南，作为 Android 体验和实现基线。
- **无障碍标准**：WCAG 2.2 是 Web 标准；其对比、重排、非颜色表达等原则可作为 Android 高质量验收基线，但不能把 CSS px 直接冒充 dp。
- **厂商 E-ink 建议**：Onyx/BOOX 等设备建议，只适用于相应设备能力；不能声称是 Android 通用 API。
- **Tsuyomi 推导**：产品选择，必须经过 UI Atlas、真机和大字体验证，可以调整。

## I. 单位、网格、触控与对比

| 项目 | 数值/规则 | 等级 | 来源 |
|---|---|---|---|
| 布局单位 | 使用 dp，不按物理 px 写布局 | Android 官方 | [Grids and units](https://developer.android.com/design/ui/mobile/guides/layout-and-content/grids-and-units) |
| 字体单位 | 使用 sp，响应系统字体偏好 | Android 官方 | [Grids and units](https://developer.android.com/design/ui/mobile/guides/layout-and-content/grids-and-units) |
| 基线网格 | 大部分布局 8dp，小元素/图标对齐 4dp | Android 官方建议 | [Grids and units](https://developer.android.com/design/ui/mobile/guides/layout-and-content/grids-and-units) |
| 最小触控区 | 48dp × 48dp，视觉图标可更小但命中区不可缩 | Android 官方建议 | [Android accessibility](https://developer.android.com/guide/topics/ui/accessibility/apps) |
| 普通文字对比 | 小于 18sp，或粗体小于 14sp：至少 4.5:1 | Android 官方建议 | [Android accessibility](https://developer.android.com/guide/topics/ui/accessibility/apps) |
| 较大文字对比 | 其他文字至少 3:1 | Android 官方建议 | [Android accessibility](https://developer.android.com/guide/topics/ui/accessibility/apps) |
| WCAG 文字对比 | 普通文字 4.5:1，大文字 3:1；4.499 不可四舍五入 | WCAG 2.2 AA | [SC 1.4.3](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum) |
| 非文本对比 | 识别控件、状态、焦点所需信息与相邻色至少 3:1 | WCAG 2.2 AA | [SC 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast) |
| 颜色表达 | 不能只靠颜色传递错误、成功、书签、下载等信息 | WCAG 2.2 A/Android 原则 | [SC 1.4.1](https://www.w3.org/WAI/WCAG22/Understanding/use-of-color) |

Tsuyomi 当前 4/8/16/24/32/48dp 间距 scale 与官方 4/8dp 网格一致；新需求应先扩展语义 token，不在 feature 中新增零散间距。

## J. 窗口尺寸与自适应布局

Android 官方窗口尺寸类别：

| 类别 | 范围 |
|---|---|
| Compact width | `< 600dp` |
| Medium width | `600dp–839dp` |
| Expanded width | `840dp–1199dp` |
| Large width | `1200dp–1599dp` |
| Extra-large width | `>= 1600dp` |
| Compact height | `< 480dp` |
| Medium height | `480dp–899dp` |
| Expanded height | `>= 900dp` |

来源：[Use window size classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes)

执行原则：

1. 只依据运行时可用窗口，不使用 `isTablet`、型号或物理屏幕尺寸。
2. 窗口类别在旋转、分屏、折叠和自由窗口中动态变化；跨断点保留 route、选择、滚动、分页和焦点。
3. Compact 通常使用底部导航和单窗格；Expanded 通常使用 navigation rail 与 list-detail；Medium 依据真实内容决定，不强制双栏。
4. 额外窗格只有存在真实任务时出现，例如详情、目录、筛选说明；不得为填空创建伪内容。
5. 阅读正文使用单列和最大宽度；宽屏增加目录/详情辅助信息，而不是无限拉长行宽。
6. Android 15/API 35 target 默认 edge-to-edge；内容需处理 `safeDrawing`、`safeGestures`、cutout 和系统栏 insets。[Edge-to-edge](https://developer.android.com/develop/ui/compose/system/setup-e2e) · [Insets](https://developer.android.com/develop/ui/compose/system/insets)
7. List-detail 可优先研究 `NavigableListDetailPaneScaffold`，但必须与 Tsuyomi 三个独立根栈和 E-ink 固定 chrome 兼容。[List-detail](https://developer.android.com/develop/adaptive-apps/guides/list-detail)
8. 非 Reader 页面始终保留系统状态栏和导航栏；只有 Reader 正文可由用户选择沉浸。Reader 工具栏、设置、目录、搜索、错误和验证状态一出现就恢复系统栏，正文即使沉浸也必须避让 `displayCutout`。

## K. 字体层级与中文正文

### K1. Material 3 基线 type scale

M3 提供 display/headline/title/body/label 各 large/medium/small 的 15 个角色；这些是应用层级基线，不是阅读正文的强制字号。[Typography API](https://developer.android.com/reference/kotlin/androidx/compose/material3/Typography) · [M3 type scale](https://m3.material.io/styles/typography/type-scale-tokens)

常用默认值：

| 角色 | 字号/行高 |
|---|---|
| titleLarge | 22/28sp |
| titleMedium | 16/24sp |
| titleSmall | 14/20sp |
| bodyLarge | 16/24sp |
| bodyMedium | 14/20sp |
| bodySmall | 12/16sp |
| labelLarge | 14/20sp |
| labelMedium | 12/16sp |
| labelSmall | 11/16sp |

### K2. 中文阅读执行原则

1. 正文使用独立 reader token，不把 `labelSmall` 等应用控件字号用于正文。
2. 容器 `wrapContent`，不以固定高度裁切文字；所有字体以 sp。
3. 明确 `lineHeight`；使用 Compose 段落能力处理 `LineHeightStyle`、自然对齐和换行。[Style paragraph](https://developer.android.com/develop/ui/compose/text/style-paragraph)
4. 长正文可评估 `LineBreak.Paragraph`；必须以简中、日文、拉丁、数字和标点混排验证。
5. 系统 Serif/SansSerif 可避免打包字体；若嵌入字体，必须核对简中全字形、fallback、APK 体积和许可证。[Compose fonts](https://developer.android.com/develop/ui/compose/text/fonts)
6. 禁止固定字距/行距使大字体不可重排；字号、行高、段距、边距应是独立 token/偏好。
7. 至少验证 fontScale 1.0、1.3、2.0；超长标题、目录、多语言和系统 fallback 不得裁切。

### K3. 建议而非官方硬值

以下仅是原型起点，不能写成 Android 官方规范：

```text
Reader body: 18sp
Reader line height: 30–32sp
Reader title: 24sp
Reader metadata: 14sp
Reader max width: 640–680dp
Compact gutter: 16dp
Wide gutter: 24dp
```

是否采用必须通过简中真文排版、手机/平板、Standard/E-ink 和 fontScale 2.0 对比图决定。

## L. 颜色、深色与动态色

1. 使用 `readerBackground`、`readerForeground`、`readerMuted`、`readerDivider`、`readerSelection`、`readerLink`、`readerError` 等语义 token，不在页面硬编码颜色。
2. Standard 支持 light/dark；Android 12+ 动态色主要用于应用 chrome、选择和操作反馈。
3. 阅读正文纸张/墨色保持稳定；动态色无法满足正文对比或低干扰目标时回退固定 reader palette。
4. 不只靠红/绿或色相表达状态；同时使用文字、图标、形状、边框或位置。[Android color](https://developer.android.com/design/ui/mobile/guides/styles/color)
5. E-ink 固定黑/白/灰，不使用透明、阴影、渐变、模糊、scrim 和 alpha 区分。
6. 焦点、选中、当前章节、滑杆和书签等非文本状态目标至少按 3:1 验收；E-ink 用实线、反相和字重，不用细微阴影。

## M. 无障碍、语义、状态与导航

- 自定义组件必须提供 semantics、角色、状态、动作和可理解的 `contentDescription`；可见文字控件避免重复朗读。[Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility)
- 整行开关/选择使用 `toggleable`/`selectable` 和正确 role；视觉 switch 不是唯一可点击小区域。
- TalkBack 朗读顺序与视觉阅读顺序一致；外接键盘/DPAD 可遍历每个动作，焦点不滞留在已消失控件。
- Back 是历史栈返回，Up 是应用层级返回；Up 不直接退出应用，深链建立可预期返回栈。[Navigation principles](https://developer.android.com/guide/navigation/principles)
- Loading/Empty/Error 每屏只有一个主状态；Offline/Refreshing 是 overlay/banners，不抹掉已有内容。
- 错误提供原因和恢复动作；关键结果持久显示，Toast 不是唯一证据。
- 大字体和窄窗口应重排而不是横向滚动；WCAG Reflow 可作为原则参考。[SC 1.4.10](https://www.w3.org/WAI/WCAG22/Understanding/reflow)

## N. 动效与 E-ink

### Standard

- 动效只解释状态、层级或空间关系；必须可取消、时长克制、由中央 token 管理。
- 不需要动效的列表更新、同步状态和排版切换应直接完成。
- 交互触发的非必要 motion 应支持减少/禁用；WCAG 2.3.3 是 AAA，但可作为阅读器质量目标。[Animation from interactions](https://www.w3.org/WAI/WCAG22/Understanding/animation-from-interactions)

### E-ink

- Onyx/BOOX 公开建议强调黑白/有限灰阶、避免透明、动画和连续滚动，优先分页；这是厂商建议，不是 Android 通用规范。[Onyx E-ink guide](https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/Eink-Develop-Guide.md)
- 不假设通用 Android 能控制 waveform；厂商刷新 API 必须通过设备能力层接入。
- 默认无连续动画、无 ripple/crossfade/shimmer/overscroll、无透明叠层、无大面积频繁重绘。
- 大更新先组装稳定不可变状态，再一次提交；封面解码预留最终几何，避免多次分辨率替换。
- 分页、离散翻页、硬件键和显式刷新动作优先；重要状态持久显示。
- 48dp 触控区仍保持，不能通过缩小控件换取“密度”。

## O. 可执行验收清单

### 布局

- [ ] 全部尺寸来自语义 token；无 feature 私有零散间距。
- [ ] 使用实际 WindowSizeClass；旋转/分屏/折叠后状态不丢失。
- [ ] 正文单列且有最大宽度；宽屏辅助窗格有真实任务。
- [ ] edge-to-edge、cutout、系统手势和 safe insets 均处理。

### 触控与输入

- [ ] 所有 action 命中区至少 48dp。
- [ ] 图标可 24dp/36dp，但外层命中区不缩。
- [ ] TalkBack、键盘、DPAD、硬件翻页键均有可达路径。
- [ ] 核心动作不依赖长按、滑动或隐藏手势。

### 排版

- [ ] 使用 sp；fontScale 1.0/1.3/2.0 不裁切。
- [ ] 检查简中/日文/拉丁/数字/标点 fallback 和换行。
- [ ] 正文、标题、元数据和标签角色清晰；正文不使用小 label token。
- [ ] 字号、行高、段距、字距、边距可独立验证。

### 颜色与状态

- [ ] 普通文字/背景至少 4.5:1，大文字至少 3:1。
- [ ] 控件边界、焦点、选中和状态信息目标至少 3:1。
- [ ] 错误、成功、书签、下载和当前章节不只靠颜色。
- [ ] light/dark/dynamic/E-ink 均有稳定 fallback。

### E-ink 验收

- [ ] 无渐变、透明、阴影、模糊、scrim 和 alpha 区分。
- [ ] 无连续动画、无限 spinner、无限滚动依赖和滚动 chrome。
- [ ] 页面与列表更新保持稳定几何和最小重绘区域。
- [ ] 关键结果持久显示；提供显式分页、重试和重绘动作。
- [ ] 厂商刷新能力仅在真实支持时出现，并明确其效果边界。

---

# 第四部分：设计 skill 的可用思路

## P. 适合 Tsuyomi 的设计思路

1. **先定 purpose**：每屏先写用户任务，不从卡片、FAB 或渐变开始。
2. **确定单一审美方向**：建议候选为“安静的纸本编辑部 / 书脊索引”。记忆点来自排版、章节刻度、纸张与墨色，而不是装饰特效。
3. **Typography first**：标题、正文、元数据、状态的层级先成立，再加入封面、图标和色彩。
4. **明确差异化**：Tsuyomi 的核心差异应是本地优先、低干扰、语义进度与全局 E-ink，而不是“另一个 Material 阅读器”。
5. **克制但精确**：极简设计不是少写代码，而是对行宽、间距、边界、状态和焦点进行更严格控制。
6. **高影响、低频动效**：Standard 只保留能解释导航或状态的少数过渡；E-ink/reduced-motion 即时替换。
7. **空间构图服务任务**：书库可使用 cover-dominant grid 或层级化列表；正文保持稳定单列，不为了“创意”打破阅读节奏。
8. **语义 token 先行**：颜色、排版、间距、形状、边界、动效和 E-ink 差异全部由 token/组件集中控制。

## Q. 不适合直接套用网页设计的部分

- hover、custom cursor、鼠标悬停提示。
- 视差、滚动触发、持续背景动画和无限滚动。
- gradient mesh、grain/noise、玻璃透明层、模糊和复杂阴影。
- 依赖颜色或动画才能理解的状态。
- 重叠、对角线和打破网格用于正文或关键操作。
- 只按 CSS px/桌面宽度设计，不考虑 48dp 触控、TalkBack、fontScale 和系统 insets。

这些手法最多用于 Standard 的非关键书库装饰区，并且必须在窄屏、大字体、灰阶和 E-ink 下退化为清晰的线性信息结构。

---

# 第五部分：映射到 M3-backed UI Atlas RC2.1

旧 RC2 与 RC2.1-3 的图和 review bundle 只保留为问题证据。下一轮以 `DESIGN_DIRECTION_HANDOFF.md` §1.3 的全量 review coverage 和 stop-ship 反例为准，不以单独“最新 review”或参考项目清单替代全历史覆盖。

| 证据板 | 绑定方向 | 必须证明 |
|---|---|---|
| A Library | 常规手机固定三列；系统/收藏夹/镜像/书籍同流 | 系统节点隐藏/重建/自动 membership；AppBar/末页创建；手动排列/两书建夹；快捷书架 Standard 横滑拖放、E-ink 按钮；严格等高 |
| B Detail | 详情 + 完整目录合一 | 元素白名单、五星、tag/split 动态容器、缓存一级动作、标准 filter/sort 图标、方向驱动 FAB |
| C Reader | center tap chrome + direct rail | 点按即跳；reading viewport 原位实时 WYSIWYG；无 popup；cancel/final commit/return origin；E-ink 离散整页确认 |
| D Search | 单一显式提交 | 本地与所选在线来源同 session 启动、一个总进度/结果流、exact identity、无高级筛选和常驻教学/来源状态文案 |
| E Updates | 三布局与 profile-specific 状态 | Standard 短时 M3 indicator；E-ink 静态 glyph；标题、章节/状态、主动作可辨认 |
| F Remote | 刷新列表 + 全部复制 | 两个顶栏入口可见、E-ink 间距充分、标题动态宽度/换行、selection/target picker |
| G Tags | ownership tabs + 直接操作 | 无数量、无 `·` 串、无能力教学说明、来源只读语义 |
| H Reader settings | partial/full quick + complete groups | real M3 sheet、无标题/关闭行、首屏字号/行距/边距/段距、直接关闭状态机、E-ink full-window |

每张截图内只能有产品真实 UI；场景、当前默认和验收说明全部放在 reviewer 图片下方。Canonical E-ink board 必须是包含真实系统栏的 AVD 截图，并通过全帧灰阶检查。

## 代码级安全路线

- 优先代码参考：Android 官方 Compose Samples、Now in Android、Adaptive Apps Samples、AndroidX Catalog、Mihon/Tachiyomi、Komga、Tivi、Thunderbird。
- 行为研究后原创实现：KOReader、Readest、Librera、Book's Story、Foliate、Kavita、Element X、Tusky、AntennaPod、Kindle、Google Play Books、Apple Books、Moon+、ReadEra、Panels。
- 无论许可如何，第三方 Logo、封面、截图、字体、品牌色、文案和插图均不自动获得复用许可；需要单独核验来源和授权。
