<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# 0.5.0 实施计划：全局 E-ink 恢复与本地书籍阅读

## 1. 状态、目标与授权边界

- 规划输入：2026-10-06 维护者要求全面评估两个版本目标，形成详细实施计划，提前处理架构、兼容和验收问题；本地阅读技术选型重点参考 KOReader，并对照其它成熟开源阅读器。
- 版本目标：**恢复全应用黑白 E-ink；加入 EPUB、TXT、MOBI、AZW3 本地阅读**。四种格式均保留；维护者明确将彩屏原色支持顺延，不作为本版交付或验收目标。
- 研究基线：主仓库 `e9c97c04e99d692b5cc5eacee13bc31696906b9c`。当前公开应用是 `0.3.0-beta.5`／versionCode `7`，Room schema `13`，transfer 输出 `v5`，最低 Android API `29`。
- 状态：**2026-10-06 维护者明确授权开始实施；2026-10-09 暂停 E-ink 改善与首次恢复执行，继续 Standard／本地阅读功能验证。** 15 项主决策与 3 项条件补充已协调到各自契约；四格式与原全局黑白范围保留，不把暂停或局部成功写成完整验收。具体 parser／kernel 由 P0 实证选定，不把候选比较当作运行能力。
- 当前执行遵循 [UI Constitution](../design/UI_CONSTITUTION.md) 的暂停／重大 UI 触发边界及 [review policy](../../../.agents/skills/tsuyomi-android-review/review-policy.json)；历史首次恢复授权不强制当前 Standard／本地阅读补跑 E-ink。原恢复出口延期未完成，不生成 human ACCEPT、golden approval 或真实面板质量通过。真机由维护者手动操作；canonical APK／AVD、真实网站写入、提交、推送、PR、合并、正式签名和发布仍需另行明确授权。
- **2026-10-09 真实语料修复授权：** 在已完成八原文件调查之后，维护者要求全部文件在 Tsuyomi 正常呈现并立即开始修复。范围包括 EPUB 拒绝和 SVG 封面、图像型固定版式漫画与阅读方向／缩放／恢复、大合集安全准入与跳转、AZW3 保护误判、MOBI Android 导入及 KF8 性能、作者／兼容说明和删除后的页面恢复。允许在既有指定工作设备上构建替换候选并逐书验证，保留数据和输入原字节；不扩展为新设备、独立夹具验收、发布或人工接受。当前暂停的 E-ink 完整恢复不重启；重大 Reader UI 变化按 Constitution 评估最小受影响证据。
- **2026-10-09 当前候选人工反馈检查点：** 维护者反馈当前候选人工验收基本通过，绑定下述最终封面修复候选 APK `0e4c94723bf589519039323caa8dbe4f921ec261c111ca5fbeaaef55c151a78b`。这更新了此前尚未收到人工反馈的状态，但不扩展为全部未执行场景、完整0.5.0、真实E-ink面板或CI准入通过；未单独授予扩大测试、提交、推送、PR、合并或发布权限。下列候选技术证据及旧失败记录保留原范围。
- **2026-10-09 完整工作包审阅后整改授权：** 独立 Standards／Spec 审阅发现删除与迟到导入、Reader 结构／工作预算和协议一致性、已导入原书按需重开及 parser 许可打包问题。维护者明确选择本轮完成这些整改，包括既有原书所有者中的有界已验证解析目录、旧记录／损坏／恢复时重新准入，并允许针对已确认缺陷的限定行为回归；不新增产品功能或扩大设备矩阵。另行批准 HIGH 部分本地准入：现有 planner 的22项非golden构建检查与15个instrumentation模块套件（含既有强制EINK回归），仅一个临时API29自动化AVD且结束必清理；已知Windows受阻的4项golden及完整线上保护检查仍待独立证据。冻结人工候选保持不变；本授权不等于整改完成、完整本地CI通过、EINK恢复／人类验收或提交发布许可。
- **同轮补充真实入口验证授权：** 因上述22项任务仅产出fixture调试包，维护者明确批准额外执行 `:app:assembleOnline`，在同一获准临时API29 AVD上用旧候选副本复现缺原书删除，再用新 `org.tsuyomi.android` 包观察删除与按需重开正文／位置。仍为debug签名本地验证，不替换冻结人工候选、不增加设备或测试矩阵、不授权发布；实际结果待制品与操作证据。
- **同轮Ruby内容完整性补充授权：** 复审发现合法同范围／嵌套Ruby在共享渲染中丢失注音后，维护者明确选择完成原生叠排与超限续读，而非保留阻断或收窄输入。行为由UI_CONSTITUTION §14 item9唯一规定；允许扩展既有富内容回归，在现有设备／套件矩阵验证。此为特定内容呈现整改，不扩大为新定位协议、第二渲染器、EINK全面恢复或人工接受。
- **同轮重大Ruby UI变化的最小E-ink证据授权：** 共享注音几何与分页整改触发重大UI评估后，维护者明确批准在同一个临时API29 AVD、新生产APK上，仅补看一组重叠注音与一次续页连续性，随后恢复原显示设置。不新增设备、套件或字号／方向矩阵，不恢复全面E-ink验收；结果仍待实际证据。
- **2026-10-09 EPUB封面兼容修复完成／冻结／有界原生验证，人工及fullCI PENDING：** 按[Data生命周期契约](../architecture/DATA.md)修复EPUB2/3显式legacy cover meta兼容、保留cover-image优先，并从已验证私有原书按需cover-only恢复既有缺封面记录；sidecar先落盘、cover-only Room CAS、manager mutex／cache及移除串行保持，无schema／catalog改动、全库扫描或章节整书重解析。集成负责人记录strict LOW既有 `PublicationOriginalStoreTest.committed_resources_are_reused_repaired_and_exposed_as_pathless_references` 加 `:app:assembleOnline` BUILD SUCCESSFUL56s；安装冻结APK SHA256 `0e4c94723bf589519039323caa8dbe4f921ec261c111ca5fbeaaef55c151a78b`、99554515bytes，buildId `47e2a72fcba3b07fe61dbe7e2a9efdd70040644f4da58beb219e1ed0a599cd4a`。既有null-cover书在普通Library显示时自动填cover且真实图可见，精确非cover book／progress／bookmark／history hashes及原书SHA不变；独立新导入在import-completed、尚未进入Detail／Library时已持久化cover ID。临时副本正常彻底删除，原书保留、既有书数恢复，最终Library且display preference未改。独立静态review无blocker，不作runtime证明。Reader77e076有界结果／be95与7a02失败历史及其pending exclusions保持不变；本段不是完整0.5.0／人工／物理EINK接受或新执行授权，文档代理未运行检查。
- **2026-10-09 Reader纠正包已实现／有界纠正路径已验证，人工PENDING：** 删除EINK特殊预览并共用原mounted-body。后续原生动态视频定位到shared Standard scrub虽使用None transition仍经AnimatedContent保留outgoing正文；现在仅在seek时用既有seeking flag直接绘制同一个ReaderPageSpread并处置outgoing animation composition，普通Standard方向翻页不变。无overlay／第二renderer／缓存掩盖。SCROLL保留engine PreviewSession/witness；PAGED/DUAL原seekPreview/displayPageIndex直接release一次commitPage／commitInternalJump，不等待page-engine witness。提交先更新committed page／override再清seekPreview；cancel退出前原position不变。章内／单页只读／真实完整章NEW edge手势、input mapping与source eligibility不变。仅有界证据，不关闭Review Graph／完整0.5.0／人类接受，不扩大测试／AVD／EINK恢复／提交发布范围。
- **失败候选历史保留：** be95d85d（APK SHA256 `be95d85dde2e97e3f1cb1387f07666cd66c6ed5c8f42a4c0b7c063883b4b3d83`，buildId `b6f21b4b08472fbbdab5bba24cdef028779807d4903d2d54bc5c7188f3d1382f`）的54s build仅编译instrumentation，其章内／单页／边缘观察仍独立绑定旧字节；SPECIAL预览随后因重影被维护者明确REJECTED，静态pixel／geometry、取消／进程恢复观察不能反驳或接受重影。后续7a02共享路径候选的动态失败也保留，显示scrub仍保留outgoing AnimatedContent正文；不得升级为最终路径通过。下项仅为最终修正候选的新有界证据，不覆盖旧失败或代表物理EINK／人工接受。
- **最终纠正候选有界证据（集成负责人记录）：** bg418 strict LOW同一 `:app:assembleOnline :reader:ui:assembleDebugAndroidTest` BUILD SUCCESSFUL27s，instrumentation仅编译、未运行suite。指定既有原生工作设备已安装且摘要核对：APK SHA256 `77e076eff40cabe55e00e16f477931d33c456855bda0f2f5fd0415e1d6820840`，buildId `e3f4678d706ddf3ccfd2e395ee9f9d29942e455bc9293e62d61cbe0215f36a51`，catalog156。Standard／EINK各原生视频含8个held forward／reverse目标、Back及单独release；检查grid和page2帧未见old/new文字叠绘，Back回1/11，held7/11与released7/11正文及bounds一致。EINK Back隐藏chrome时同页最后段落原被裁切的底部展开，source text/page及其它坐标相同，不冒称全viewport pixel一致。临时副本12→11清理，AUTO恢复，用户原PAGED未改。远程失败／登录验证／双页布局、full CI／protected admission、人类质量／物理EINK及完整恢复仍PENDING；本次文档代理未执行任何检查。
- `0.5.0` 是版本里程碑，不等于已有交付规划中的“Phase 5：后续来源”；不附带 ESJZone、Yamibo、TTS、云同步、F-Droid 上架或来源协议扩展授权。

**决策顺序：先确认内容保真与许可证路线 → 统一身份／资源／定位契约 → 打通一个真实端到端切片 → 分别完成四种格式与全局 E-ink → 联合验收。** 不先做四套解析器，再补存储、书签和墨水屏。

## 2. 规划时的基线缺口 — 2026-10-06

| 项目 | 当时观察 | 0.5.0 必须处理的缺口 |
|---|---|---|
| E-ink | 已有全局 DisplayEnvironment、显式覆盖／auto 判定、即时 motion policy 与逻辑 redraw；执行策略冻结 | 逐个核对冻结期间新增的生产组件、来源菜单、Pager、分段、更新与信任流程；灰度不是验收全部 |
| 统一 Reader | ADR 0015 要求一个文档、语义 locator、测量即绘制、增量排版和同文档切换事务 | 本地出版物必须进入同一引擎，不能另建本地 WebView Reader、页码进度或一份本地书签系统 |
| 冻结 Reader | 当前 `FrozenEInkReaderSurface` 只渲染一个 block，显示 block index/count，并从该 index capture；没有当前 Reader 的页控件／chrome／图片 owner 回调 | 恢复真正的共享测量分页、输入、图片与定位事务；不能把启用旧分支称为恢复成熟 Reader |
| 文档词汇 | protocol v1／ADR 描述 heading、paragraph、image、divider、quote、post；当前 Kotlin `ReaderBlock` 仅 Paragraph、Heading、Image | 先处理契约／运行时差距，再定义书内链接、行内样式、注音、列表、表格和本地资源；不能把抽取纯文本称为 EPUB 排版支持 |
| 书籍身份 | 当前 `BookIdentity`、Room 关联、Detail、历史、更新和 transfer 都围绕远程 `(sourceId, remoteBookId)` | 本地身份不能使用文件路径、标题或 Room row ID，也不能伪装成已安装来源 |
| 图片 | 现有远程插图走 host 媒体策略；protocol image 只允许 HTTPS | EPUB／KF8 内嵌资源需要独立的受限资源引用，不能放开远程 `file:`／`content:` URL |
| 备份 | transfer v5 是 ≤32 MiB JSON 元数据交换，不含正文、凭据、安装状态或设备显示 profile | 已选 JSON 元数据＋可选原书归档；两者分别版本化、验证与恢复，不往现有 JSON 塞正文 |
| 验收 | 本地 CI／hosted admission 与一次共享在线候选分工已确定 | 保留 CI 内确定性夹具，不恢复独立夹具验收轮；本地书籍新增 production-route 场景，E-ink 增加真实面板证据 |

本轮运行了现有 AJV schema 的边界 smoke：既有文档、HTTPS 图片、纯文本段落被接受；`file:` 图片、`content:` 图片、paragraph spans 和 table block 被拒绝。它证明当前契约缺口，**不证明任何本地格式已能打开**。

## 3. 成熟阅读器参考与技术路线

### 3.1 应借鉴什么，不应照搬什么

| 项目 | 已核实的价值 | 限制与采用方式 |
|---|---|---|
| [KOReader](https://github.com/koreader/koreader) | 成熟的出版物打开／文档注册、crengine DOM/CSS 排版、XPointer 定位、排版缓存与版本、刷新区域／模式调度、设备适配和长期阅读操作 | pinned app COPYING 为 AGPL3；参考行为／问题划分，不复制其 UI、Lua 应用框架或厂商假设。独立组件逐项核对 grant，不能把 app 的许可证当 engine 许可证 |
| [KOReader crengine](https://github.com/koreader/crengine)／CoolReader engine | 可重排出版物的原生 DOM、CSS 子集、分页和文档内部位置 | pinned README.TXT 声明 core GPLv2，未见 or-later grant，按 GPL2-only 风险处理；不是 Apache 主程序可直接链接库。旧 MOBI6 parser 不证明 KF8／AZW3；app/base/engine 各 SHA 必须独立核实 |
| [Readium Kotlin Toolkit 3.4.0](https://github.com/readium/kotlin-toolkit/releases/tag/3.4.0) | BSD-3-Clause；Publication、metadata、reading order、navigation、资源访问和格式识别适合 EPUB 入口 | 不是 MOBI／AZW3 解析器。ContentService 文档仍明确 experimental／incomplete；不能依赖 `content.text()` 得到保真的全书。优先评估 package／spine／资源层，不直接引入 Navigator 形成第二个 Renderer |
| [libmobi](https://github.com/bfabiszewski/libmobi) | 独立 C 库，声明支持 MOBI/KF8/AZW3 内容与资源；MOBI/AZW3 共用解析候选 | pinned README grant 为 LGPL3-or-later；需逐文件／依赖／发行义务审查、JNI／ABI 和不可信输入隔离。能解析不等于图片、目录、脚注和定位已交付 |
| [Librera Reader](https://github.com/foobnix/LibreraReader) | 成熟 Android 阅读器，可观察文件打开、书架、目录、设备交互和多格式用户体验 | GPL 项目；作为行为与兼容语料参考，不把整套渲染／应用实现复制进 Tsuyomi |
| [lib-mobi](https://github.com/marcelmay/lib-mobi)／[KindleUnpack](https://github.com/kevinhendricks/KindleUnpack) | 分别提供 Java metadata 研究入口和 Kindle 文件结构参考 | Java 项目不是完整正文解析替代；Python 工具不是 Android 运行时方案。不能以 metadata 成功作为格式支持证据 |
| [lingo-reader／Foliate-js](https://github.com/hhk-png/lingo-reader/tree/bc912542d9c09f144257205943c6bd3e421d8743) | MIT 声明的真实 MOBI／KF8 正文解析候选，提供 metadata、TOC、chapter HTML/CSS、链接和资源；lingo 还声明 EPUB | TypeScript／browser／Node 路线，没有已发表 Android 集成或本轮 runtime proof。需审核 inherited/transitive 许可、DOM／Blob／IO 依赖、受限执行／移植与 host normalizer；不是把书内 HTML交给第二 Reader |
| [FBReader SDK](https://sdk.fbreader.org/android.html)／[MuPDF](https://mupdf.com/) | 明确的商业或 copyleft 内核评估路线，而非假设可向 KOReader 单一作者购买整套授权 | FBReader SDK binary core 非开源，价格／source grant 需书面确认，AZW3 未核实；MuPDF 是 AGPL／商业，当前列举输入含 EPUB但不证明 TXT／MOBI／AZW3。都不是四格式 drop-in parser |
| [epub4j](https://github.com/documentnode/epub4j/tree/1b480243cb22362aa001acd48a89dea74b3c925a) | Java／Android EPUB parser 候选，项目声明 Apache2 | README 明示 epublib 血统、上游 LGPL grant；单一 LICENSE／POM 标签不足以证明 inherited code 已合法改授权。来源权利未澄清前不按干净宽松依赖准入 |

核心结论：**没有已证实、许可证直接合适、原生 Kotlin 一站式覆盖四种格式并可直接替换当前 Reader 的方案。** 要把“容器解析”“资源与文档规范化”“排版”“定位”“显示／刷新”分开评估，但维持一个用户可见 Reader 与持久化模型。

### 3.2 建议主路线：保留 Apache 主体和统一 Reader

```text
SAF／系统明确文件打开
  → 有界复制与格式识别
  → EPUB：Publication／spine／资源解析
     TXT：严格解码与稳定段落／章节索引
     MOBI／AZW3：同一个受限 native 解析入口
  → host-owned 出版物身份、资源索引与结构化 ReaderDocument
  → 现有 ReaderSessionCoordinator／增量分页／Scroll／Paged／Dual
  → Standard 或全局 E-ink
```

1. EPUB 首先评估 Readium 的 package／spine／资源层；保留结构与链接的 host normalizer 不依赖 experimental 全文抽取 API。P0 必须用真实特性语料证明适配，不提前声称兼容。
2. TXT 优先标准库解码和流式索引；只有真实语料证明必要时才引入 charset 检测依赖。禁止用宽松替换字符掩盖乱码。
3. MOBI／AZW3 在 P0 比较 MIT lingo-reader／Foliate-js 的受限 host 解析／移植路线与 libmobi native 路线：优先验证宽松许可候选，LGPL 是否可作后备由 D1 明确。前者有 Android适配／语义规范化／性能未知，后者有 JNI／ABI／native 隔离／LGPL 重建合规成本；不为每种格式增加一份 Reader、导航或存储。
4. 把本地解析放在 host publication owner，来源扩展继续只处理网站。不存在“本地来源扩展”、插件签名例外或拿 QuickJS 执行书内脚本的路径。
5. **如果保真语料证明当前 Compose 测量／绘制路线无法完成约定的内容范围，停止接入更多格式，先修改 owning ADR 并确认替代路线。** 不以全书纯文本化、百分比跳转或第二个 WebView Reader 绕过问题。

### 3.3 许可证路线：区分自有代码、组合发行与第三方组件

2026-10-06 维护者要求本轮全部决策通过 AskUserQuestion、保存回答，并扩展过窄二选一。首组已选 L1 宽松优先／合规 LGPL 后备、P0 对照后选内核、基础可重排 EPUB、非 DRM 明确拒绝；下表其余路线只是评估历史，不是并行有效授权。

先区分四层：Tsuyomi 自有代码授权、第三方组件及修改的许可证、APK／组合程序的发行义务、独立协议或应用的边界。开源、免费、可商用、可闭源和许可证兼容不是同一个问题。

| 路线 | 自有代码／发行影响 | 收益 | 必须接受的条件与风险 |
|---|---|---|---|
| L1 Apache 主体＋宽松优先、合规 LGPL 可后备（建议） | 自有 host／protocol 继续 Apache；第三方库及修改保留各自授权；LGPL Combined Work 允许满足条件后的 Application 自定许可 | 先评估 Readium＋MIT lingo／Foliate parser，必要时采用已审 libmobi，不必把主程序自动改成 GPL | MIT 路线仍需 Android／依赖／安全 proof；LGPL 对应源码、notice、可修改库重新组合／链接与适用安装信息必须真正可用；动态 `.so` 本身不是合规证据 |
| L2 严格宽松许可 | 运行时只接 MIT／BSD／ISC／Apache 等兼容组件；不接 LGPL／GPL／AGPL | 发行义务简单，已有 MIT MOBI／KF8 正文 parser 候选，不再等于只能完全自研 | lingo／Foliate 尚无已证明 Android适配；可能需要受限 JS host 能力或移植 MIT 实现；不因性能／兼容失败悄悄延后 MOBI／AZW3，不把 EPUB-only 库当全格式替代 |
| L3 Apache 原始代码＋满足 GPL／AGPL 的组合发行 | 不自动撤销已有 Apache 授权，也不自动重授权整个 monorepo；受影响组合必须满足对应 copyleft 的完整义务 | 可认真评估成熟 crengine／KOReader 组件，减少自行实现排版能力 | GPL 精确版本／or-later／AGPL／例外和依赖逐项审查；Apache2 与 GPL3 兼容不意味着 GPL2-only 兼容；需要修改当前 host 授权边界，不能仅加 notice |
| L4 商业或单独例外授权 | 仅在权利人能对所需组件、修改及其依赖作出完整有效许可时成立；主代码不必自动换许可 | 可能使用成熟 SDK／内核而不按其公开 copyleft 路线发行 | 只是允许评估，不是批准付费／采购；价格、Redistribution、source/build 可用性、免费软件／F-Droid、离线运行与供应商依赖要明确；不能假定 KOReader 所有贡献可向一个人购买授权 |
| L5 独立外部 Reader 应用 | 各应用保持真实独立的发行与许可证，Tsuyomi 用普通用户文件打开方式调用 | 避免把完整 Reader 内核直接并入 host | 明确改变统一 Reader／进度／书签体验，须用户批准 ADR 变更；细粒度紧密 IPC 或独立进程不是自动豁免 copyleft 的办法，不作为 parser-service 的法律捷径 |

许可事实与发行审查：

- [Apache 官方兼容说明](https://www.apache.org/licenses/GPL-compatibility.html)：Apache2 可纳入 GPL3 作品，不能把 GPL 代码无条件当成 Apache-only 作品发行；GPL2-only 的兼容性不能由 GPL3 结论代替。
- [LGPL3 第4节](https://github.com/bfabiszewski/libmobi/blob/906274205c11944b628da1c553b255acb1af7c55/COPYING)：4d0 提供足以重组／重新链接的 source/object 与应用代码；4d1 要求真正合适的可替换共享库机制。对本项目完整公开源码／固定工具链的路线应验证可重新构建修改版 APK，不以源码仓库链接或 `.so` 后缀代替验证，也不自动要求公开私有 release key。
- [GNU FAQ](https://www.gnu.org/licenses/gpl-faq.html#GPLInProprietarySystem)：独立程序的 arms-length 通信与一个结合程序不同；pipe／socket／不同进程不是唯一判断标准，交换内部复杂结构和紧密耦合仍有组合风险。具体组合需要许可复核；这里不是个案法律保证。
- LGPL／GPL 库的修改不能通过改 SPDX 变为 Apache；COPYING 文件的版本还要结合实际 source grant 判断 `only`／`or-later`。商业授权与开源双授权也不能假设覆盖所有 inherited dependencies。
- 构建／研究时使用 GPL 工具但不把其代码链接或打包进 APK，不等于采用 GPL runtime；现有 REUSE 工具就是非发行工具，不应错误禁止。
- Readium／libmobi 仍是候选，尚无 Android 集成、格式保真、JNI 隔离或 license-relink proof。GPL 路线不会自动证明 AZW3；商业路线也不会自动覆盖四种格式。
- 宽松路线补证：[lingo-reader MIT license](https://github.com/hhk-png/lingo-reader/blob/bc912542d9c09f144257205943c6bd3e421d8743/LICENSE)、[MOBI／KF8 parser API与来源](https://github.com/hhk-png/lingo-reader/blob/bc912542d9c09f144257205943c6bd3e421d8743/packages/mobi-parser/README.md)、[Foliate-js MIT license](https://github.com/johnfactotum/foliate-js/blob/master/LICENSE)。这修正“未找到宽松完整正文 parser 候选”的过窄结论，但不证明 Android可运行／恶意文件安全／完整特性或任何测试通过。Foliate 应用与 foliate-js 库也不能仅凭名称视为同一许可证。
- pinned grant 复核：[KOReader AGPL3 COPYING](https://github.com/koreader/koreader/blob/31f862c0578f0eb45dd5c606f4f7ebb5b8acda7d/COPYING)、[crengine GPLv2 README](https://github.com/koreader/crengine/blob/517b8f0a6562d334ff42c89358fe6ee664d9adcb/README.TXT)、[libmobi LGPL3-or-later README](https://github.com/bfabiszewski/libmobi/blob/906274205c11944b628da1c553b255acb1af7c55/README.md)。不能从 COPYING 模板或笼统 GPL header 推断 or-later；copyleft 代码也不能仅改 SPDX 变为 Apache。研究的 crengine SHA 与 KOReader app 实际 base 所指 engine SHA 不同，不把两者写成已验证组合。
- 不提供 DRM 绕过。是否增加合法授权 DRM 支持是单独产品／依赖问题，不能用不明授权工具或把保护状态当损坏数据。

### 3.4 未来 Yamibo 回复流的架构预留 — 2026-10-07

维护者要求未来 Yamibo 实装时提供**可显式开启／关闭的回复流**，呈现参考 hikari-flutter-plus；本轮只在共享 Reader 的 typed post／reply、来源能力、语义定位与内容模式切换边界预留跨插件空间，不提前接入 Yamibo，不实现空设置开关，也不额外做 Yamibo 验证。产品行为已写入 UI Constitution §14，数据与切换边界归 READER／ADR0015。

已核对 Hikari 固定 revision `a1feba6d1dd8dbbdd2b5ae042e44f2ec54d26bef`：设置按 Yamibo 条件显示回复阅读开关；文档保留 postId、作者、楼层、时间、引用和图文；楼主正文与普通回复有不同呈现，引用可定位。只借鉴内容层级／可开关的交互，不复制 Flutter 双 renderer、百分比回退、像素尺寸、签名、可选楼主标题或楼主目录功能。当前共同模型设计不能把 postId 变为页码／列表 index，来源 HTML/XML 不能直接进入 Reader。

未来最高可观察 seam 为真实 mounted Reader 的开启→关闭→重开、引用跳转及 exact／degraded／unavailable 切换与取消；在来源另获实施授权前，Review Graph B02 的对应操作明确 NOT_EXECUTED。四格式本地阅读与全局黑白 E-ink 的当前范围不扩展为论坛来源交付。


## 4. 本地阅读的共同设计：先解决跨格式不变量

### 4.1 身份、书架与更新边界

建议采用显式 `remote | localPublication` origin；当前远程身份和值保持不变。

- 本地 publication ID 为可随备份迁移的稳定 host ID；字节摘要匹配重复／重关联，不充当阅读状态唯一 identity。独立副本可同字节；路径／文件名／书名／provider URI 都不是身份。
- 标题／作者编辑、重命名原文件、清理派生缓存、换 profile 均不改变 identity。
- **已选 D3I：重复由用户确认**关联已有 publication 或创建独立副本，取代同字节自动关联提议。多个匹配须选目标；关联不重置进度／书签，新副本用独立 ID／状态。不同版本／格式不按同名合并／替换。
- 原书不可用时保留元数据、收藏夹、稍后阅读、历史和 locator；重新关联只接受验证匹配的原始文件。缺失状态不伪装成来源未安装。
- 一种身份仍只有一个 canonical Detail／Reader 入口。本地书可以进入本地书架、手动／智能收藏夹、Tags、稍后阅读、继续阅读与本地元数据搜索。
- 进入 Detail 或完成导入本身不是 Reader visit；只有实际进入 Reader 才进入继续阅读历史。已有 G-45 行为不改变。
- 本地书不能触发来源 Home、登录、远程收藏、更新扫描、WorkManager 来源请求或 website mutation。不要为每本本地书建立一个假 sourceId。
- 把 remote-only 能力集中在 origin／capability 边界；不要在每个 UI 回调散布“如果是本地就忽略”的兜底。

### 4.2 文件、资源与生命周期

**已选 D3S：完整应用私有副本**，原 Provider 此后不是阅读依赖；不交付URI引用模式。原书是durable data，卸载会丢失私有副本，须准确说明备份范围。

- **已选D3E：Library＋系统打开／分享**，SAF文件选择及ACTION_VIEW／ACTION_SEND等授权文件输入共用导入owner；覆盖冷／热启动、批量、重复／取消和授权失效，不要求全盘权限，不新增底栏／第二导入流程。
- 原书与不可替代资源不进入 LRU cache；封面缩略图、规范化分段索引、分页计划和解码结果可重建且独立清理。
- 流程：选择 → 有界复制／摘要 → 识别与保护检查 → 解析／规范化／索引 → 文件稳定提交与 Room admission → 可阅读。
- 选择取消、解析取消、存储不足、provider 失效、服务死亡或进程重建必须给出真实状态；不能留下可见空书、半目录、失效指针或把失败算作成功。
- 文件系统和 SQLite 不能伪称一个 ACID 事务。使用可恢复的 staged／committed admission：先稳定文件，再提交记录；恢复只完成或回收本次作业拥有的残留，不删除用户旧书。
- 批量导入采用逐书独立结果和有界并发，单书失败不回滚其它已成功书籍；不预先把全批原书读入内存。
- 不可信 native 解析至少与主 UI 进程隔离。优先验证不导出、无网络能力的隔离服务和只读／输出文件描述符；不得靠停止 coroutine 假定 C 解析已停止，也不得用巨型 Binder Bundle 搬全书。
- 入站字节和解析输出都不可信：限制原始／累计展开字节、entry／section 数、压缩展开、XML 深度／禁用外部实体、文本块和图片像素；拒绝绝对／越界／符号链接资源，规范化引用后再做 owner confinement。不静默截断超预算书籍；P0 用代表性语料固定具体预算，沿用既有 quota 原语而不另建用户可调安全开关。
- **已选 D3D：两级删除**：准确分别确认“移除私有原书／资源但保留元数据／收藏夹／进度／历史／书签”和“该书原书及全部该书状态彻底删除”。前者缺失可重关联；清缓存／unpin不删原书，不删Provider文件。先取消active Reader等owner并恢复file／Room一致性；UI Constitution／DATA已记录。

### 4.3 结构化文档、资源与排版

- 一个 source-neutral ReaderDocument owner；把现有 DTO 的职责与 `shared/model`／`shared/locator` 的依赖方向固定，全部调用方干净迁移，不留 remote／local 两份形状相近的文档。
- 必须覆盖约定的行内强调、链接、脚注／尾注、ruby、列表、引用、图片与表格；采用明确的内容保真 profile，不能逐本静默丢弃不认识的元素。
- 图像引用区分已准入的 remote media 与 publication 内部资源。内部资源使用 publication-bound opaque ID／digest／MIME；不向 renderer、backup 或日志暴露绝对路径／provider URI。
- 书内导航必须能从 TOC／链接／脚注定位到相应 section 与 anchor，再回到原阅读位置；不需要新增全书浏览器或允许书内网络执行。
- **已选D3N：逐书显式允许受限HTTPS资源**，取代永久禁止外部资源提议；默认离线，导入／解析不自动联网。授权绑定publication／原字节／公开origin／支持的资源类型，重定向／DNS／内网／配额／撤回／晚到结果前置验证，不共享来源凭据，不执行脚本。样式／字体若支持，仅作为受限惰性资源规范化；外链仍需单独确认交系统浏览器。READER owns。
- 固定版式、复杂竖排、SVG／MathML、嵌入字体和媒体不是“检测到 EPUB”就自动支持。P0 明确完整支持／可见降级／拒绝规则；需要缩小交付范围时取得明确确认。
- 章节只按需要加载与规范化；分页仍为首屏加小窗口、闲时扩展。首次阅读不等全书分页／全文索引／总页数计算。
- 图片先锁定合理几何并有界解码。晚到资源不应覆盖新 session、改变正在阅读的锚点或触发反复全屏闪烁。
- 同一已测量文本对象用于绘制；行内样式与 ruby 的测量不能和最终 Canvas 绘制采用不一致的算法。

### 4.4 语义定位、版本与恢复

- publication → 稳定 section → 稳定 block／anchor → Unicode code-point offset；排版页码与像素永远不是 durable truth。
- EPUB 使用规范化的 spine resource／fragment 对应关系；MOBI／KF8 映射解析后的内容顺序与锚点；TXT 按明确编码和规范化版本建立稳定段落／章节索引。
- EPUB CFI、KOReader XPointer 是参考和潜在格式适配信息，不另建第二套 durable progress，也不声称二者可直接互换。
- 原书摘要、normalizer／parser 版本、内容 digest、section／block identity 和 locator precision 分开；升级解析器可能影响锚点，不能只改文件缓存名。
- exact／degraded／unavailable 必须可区分。重排、profile 切换、宽度变化、进程重建和缓存重建不能让较低精度 capture 覆盖有效 exact capture。
- 有意向前／向后阅读都遵循既有 `updatedAt` 冲突规则，不取最大阅读百分比。脚注／预览未提交不污染主阅读进度。
- 新 revision 不能把旧页码强套进去。P0 定义可复现的 anchor 映射与可见降级；不自动用不同版本书替换原书以回避迁移。

### 4.5 备份与迁移

- 从 schema 13 到新 schema 的迁移保留全部现有远程身份、进度、书签、历史、收藏夹、信任／会话、更新状态与 reader preferences；禁止 destructive fallback。
- protocol 先定义新的 origin／locator／resource 边界和必要的新版本。旧 schema 不原地放宽；旧 transfer 输入的兼容属于必需数据兼容，不是保留旧运行时双轨。
- 建议先扩展元数据 transfer：本地 publication ID、原书 fingerprint、metadata、目录引用与 semantic locator；不导出路径、URI、native handles 或派生排版数据。
- 元数据恢复缺少正文时显示可重关联的缺失书籍；不能把导入备份成功称为所有本地书均可离线阅读。
- **已选 D3：JSON＋可选含原书归档**，取代metadata-only建议。归档独立版本化，manifest／ID／size／digest／quota／path／conflict与中断恢复前置，不改v5的32MiB／无正文规则。ADR0008 owns；JSON-only恢复准确显示缺失原书。
- 元数据冲突和原书冲突分别处理。相同 publication ID 携带不同 fingerprint 不静默覆盖；恢复中断不破坏既有书籍。
- Room migration 通过后，不允许安装旧 APK 操作已升级数据库；回退采用兼容的新修复版本或事前备份恢复，不假设 `adb install -d` 能安全降级。

## 5. 四种格式的拟交付矩阵

以下为拟验收范围，P0 确认保真边界后锁定；不是当前能力声明。

| 格式 | 必须验证的主路径 | 边界与错误 |
|---|---|---|
| EPUB | EPUB 2 NCX／EPUB 3 nav；metadata／cover；spine 顺序；分卷／章节；混合图文；书内链接／注释；中文和 Unicode；离线资源；语义恢复；图像型固定版式漫画／阅读方向、安全 SVG 图片包装和有界大合集 | 非法 ZIP／OPF／资源缺失、真实 DRM、危险外部资源与未支持复杂特性必须分类；不误拒安全 DOCTYPE／普通合集，不静默删章、扁平化作者目录或改错顺序 |
| TXT | 可靠BOM／UTF严格自动识别；不确定时解码预览与显式选择中文等编码；换行差异、可修正目录建议、无标题稳定分段、大文件按需加载／语义恢复 | 已选D3T；不使用替换字符掩盖错误，不将每个正则命中变章节、不猜后缀或截断全文；原字节保留 |
| MOBI | PalmDB／MOBI7；metadata、目录／章节、文本与图片顺序、链接、正常压缩；进程恢复 | 明确 DRM、损坏记录和不支持压缩。不能只读 metadata；不把所有 Kindle 文件称为 MOBI |
| AZW3 | KF8 内容重建、资源映射、目录、样式与链接、混合 MOBI7／KF8 容器的确定选择、语义恢复 | 不由 `.azw3` 名称直接准入；不把 KFX、AZW4／Print Replica 或加密 AZW 误当支持；不得未经证实宣称 crengine 可替代此路径 |

共同 acceptance：真正导入 → canonical Detail → 打开正确内容 → 模式／字号／profile 切换 → 目录／链接／书签 → process death → 原定位恢复 → 清派生缓存仍可阅读 → metadata export/import 重关联。每种格式至少一份可分发的合法最小语料及覆盖其危险边界的语料；不得在仓库收集私有小说或用户原书。

## 6. E-ink 恢复：全应用，不是换一套黑白主题

### 6.1 保留任务结构，重做交互表达

建议方向：保留 Standard 成熟后的导航与任务分工，使用共同 semantic tokens、固定 chrome、显式操作和稳定分批提交；不建立第二套 E-ink 业务模型或复活历史 prototype。

| 生产面 | 恢复重点 | 必须保留的不变量 |
|---|---|---|
| 初次启动／权限／全局导航 | 手动选择、auto 原因、未知设备回 Standard；不先显示一帧彩色主题；系统栏／IME 边界 | 无设备信息上传；用户手动优先；≥48dp 操作与 focus／Back／Up |
| Library 三投影／收藏夹／Tags／历史／本地搜索 | 长列表离散分页、刷新按钮、稳定选中与计数；同一身份不重复 | `书架 / 继续阅读 / 稍后再读` 投影、独立浏览状态、搜索不联网、真正 Reader visit 语义 |
| 更新／未解决／后台操作 | 固定工作／成功／失败／未解决反馈；非颜色的状态区分 | 默认关闭、能力 gating、操作 fencing；本地书不参与来源更新 |
| Browse／目录／插件信任／来源切换 | 明确页操作、来源状态、静态选中／工作状态、可靠错误和确认 | 签名／批准／原书架数据／会话／撤销边界完全不放松 |
| Source Home／分类／搜索／网站书架 | 折叠 chrome 固定化、tap 导航、可见筛选／页状态；不知道总页数时不编造总页数 | 查询、来源顺序、选中 Tag、目录锚点、cache 与能力结果保持 |
| Detail／目录／缓存／网站变更 | 显式上一／下一／刷新／选择与批量结果；插图／封面灰度 | 一个 Detail；本地／远程操作明确；目录位置、完成状态、备份和取消保持 |
| Reader（远程与四种本地格式） | 即时翻页、章内seek；EINK仅删除special preview并共用原有Standard mounted-body scrub，一次release／cancel起点；已知完整单页只读位置；Standard完整章真实边缘NEW drag／release；音量键及稳定图文 | 同一semantic locator、实测计划／绘制、exact capture及有界预取；UI_CONSTITUTION A.1/§14拥有最新预览纠正、手势／失败边界；不改Standard renderer，非无缝全书scroll |
| Reader settings／More／Data／Backup／帮助 | Reader settings 使用既有 E-ink 全屏不透明 route；左侧按标签测宽的项目导航／右侧选中内容；恢复点按／拖动调节条和清楚数值 | 不缺任一排版／页面／导航／设备控制；不是 quick-controls-only 页或逐项轮播；细节由UI_CONSTITUTION A.1独占 |
| Dialog／sheet／loading／错误／Toast／系统栏 | 透明遮罩／动画语义改为不透明稳定状态；重要结果持久可见；灰度实框与填充反转 | 模态返回／取消／焦点／禁用与 selected 语义仍正确；不是单纯关闭 animation clock |
| Controlled WebView | 高对比 host chrome、明确退出／保存／返回、回程一次稳定 redraw；全帧证据 | 不注入改变验证／登录结果的脚本，不重新登录、不污染 credential partition |

Standard-only 分栏 Pager、来源菜单联动收起、珊瑚选中态和移动分段不能直接带到 E-ink。保留页面身份、查询、选中值与语义状态；将表达替换为固定栏、tap／明确页控件与即时替换，而不是删除成熟任务。

执行映射覆盖现有全部28节点：L01–L08、B01–B03、S01–S04、M01–M07、X01–X06。requiredStates／operations由 JSON catalog唯一持有；已为L01／B02／B03／M04／X03追加0.5.0本地导入／资源授权／备份／flow／panel行为检查。catalog135另记录B02未来Yamibo回复流的conditional操作，本轮NOT_EXECUTED；不由架构预留自动激活EINK或授权来源实装。不得复制另一批准清单或复活prototype ReviewNodeCatalog.kt。

优先复用现有 Library／LibraryUpdates production journeys、SourceHome／RemoteLibrary／ManualVerificationHandoff instrumentation、ReaderPagination／ReaderPageMotion／ReaderBookmark suites，以及 Display resolver／classifier／controller／persistence／settings tests。已有 Standard-only 测试经常强制 Standard，不能改个报告标题当作 E-ink pass；实施时应给这些真实 seams 增加对应 profile 的可观察任务与状态。

**D4F的Reader即时测量分页／合资格dual保留；2026-10-08最小更改及后续人工反馈纠正取代全应用统一重排和全面去除Slider。** 不覆盖Standard持久requested flow，回切恢复；一个semantic locator，实际测量／绘制决定页数。其它生产面保持既有布局、控件主次、分组与灰度明暗层级，仅对确需滚动的内容补必要按键分页，不给原本可完整显示的内容追加页控或逐项页面。接受进度／调节条点按与拖动；阅读设置左窄项目导航＋右宽选中内容保持，实际溢出才分页。保留草稿、值与身份；不缩字或裁切。UI_CONSTITUTION G-05/A.1独占具体产品规则与未决设计轴；此前候选和通过记录不是新纠正的验收，先共同确认未决选择，不由此追加部署或发布授权。

### 6.2 列表分页与跨 profile 状态

- 本地长列表按稳定排序分页，远程列表消费真实 source cursor／page。page-size 与大字号／窗口变化不得变成书籍 identity。
- 跨 profile 切换保存身份锚点及必要偏移；Standard 滚动位置与 E-ink 页状态可以有不同 presentation cache，但不是两套业务数据。
- 返回／重建恢复选中 identity、查询、当前可见页和焦点；不以切换显示模式清空列表或回第一页。
- 首尾未知时只显示真实的位置与 previous／next 可用性；不先抓完整来源目录计算页数。
- 离散页控件必须可点击、可键盘／语义激活；手势只作等价捷径。

### 6.3 刷新：逻辑重绘与硬件 waveform 分开

KOReader 的 `dirty region + refresh kind + coalescing + device backend` 值得参考，但 Android 普通绘制不等于面板控制。

**已选D4并已reconcile：**通用优化＋真实manual redraw，取代ADR0014的automatic／quality／balanced／fast承诺与DATA advanced持久字段。owning ADR／DATA／EINK已清除矛盾；不再把高级coordinator／厂商后端当本版待选或实现目标。

1. 先完成全局稳定提交、去除反复动画／fade／spinner、图片几何和逻辑 redraw；复用当前 root redraw owner，不在每个 composable 添加刷新逻辑。
2. 共享稳定state transaction与现有root redraw owner避免中间帧；本版不新增semantic-hint dispatcher／高级policy接口。WebView返回、晚到图像和取消不能重复提交旧页面。
3. 当前 `manual redraw` 只能称重绘；不能宣称驱动全刷、A2、DU、GC16 或消除残影。
4. 不公开／持久化Automatic／Quality／Balanced／Fast，无新高级coordinator；不只存枚举冒充功能。
5. 本版不接厂商SDK／机型waveform后端，不猜隐藏属性／反射vendor类。未来扩展须重新获scope／ADR／许可／真机proof。
6. 交付通用E-ink任务／稳定逻辑重绘，准确说明不控制硬件全刷／残影；不声称全机型硬件优化。

### 6.4 真机与辅助技术

最终补充回答：**0.5.0仅黑白，彩屏原色顺延**，明确取代早先D4C彩屏选择和G-05例外，不要求本版彩屏physical proof。目标为用户报告掌阅Ocean4 Turbo2025、7英寸mono、portrait1264×1680；用户已确认Android10+/API29+且可装APK，**由维护者手动操作提供结果，不选代理接入**。精确runtime／density／固件／系统刷新设置随未来证据记录；本轮无设备观测／安装／human acceptance。

共同检查：触屏／音量键／键盘／TalkBack、窄窗／横屏／宽窗／fontScale2.0、系统减少动态、旋转／process death、Dialog／IME／WebView、纯文本／混合图文和持续阅读。截图不替代动作、locator 或人类舒适度判断。

## 7. 模块级实施顺序与依赖

### P0 — 收敛范围与证明技术路线

- 执行 §9 已确认选择；列出 EPUB 内容保真 profile、四种格式的 subtype／保护边界、已登记真实黑白 E-ink 目标和已选择的备份口径，不重新询问已回答的产品分叉。
- 绑定可分发的参考语料，对照 KOReader／Readium／Librera 的内容顺序、TOC、链接、ruby／注释、图文和恢复；对照是 oracle 输入，不是照抄排版像素。
- disposable parser／normalizer／统一Renderer proof：API29、ABI、中文、取消／服务死亡、默认零资源请求；逐书受限HTTPS的允许／拒绝／撤回／重定向／内网边界，增量首屏。
- 首次评估记录 import／open／page-turn 时间、峰值内存、图像预算和 APK 增量；按目标机型冻结可测的 performance budget。现阶段没有实测数字，不伪造 P95 或内存结论。
- 审查精确依赖版本／commit／digest／许可证／transitive deps。若需要 GPL 路线、第二渲染内核或扩大 EPUB scope，在接入前修订 ADR，而不是等做完再问。
- 出口：一条被证明可行的 renderer／normalizer 路线、明确的不支持行为、确认后的范围和实施授权；四个 metadata demo 不算出口。

### P1 — 契约、身份、资源与迁移

Owner：`shared/model`、`shared/locator`、当前 `shared/source-contract` Reader DTO、`core/database`、`shared/backup`、`feature/backup`、`tsuyomi-protocol`。

- 固定 origin、publication identity、资源引用、文档词汇／版本、locator precision 和 format compatibility report。
- 导出 symbol 变化先查 references，统一 DTO owner、全部消费者及 source adapters；不留 obsolete alias／兼容 UI／旧双轨 Reader。
- protocol 先完成新 schema 与 valid／invalid fixtures，锁定旧版本输入兼容；不让来源伪造 host-owned publication 或扩大远程 URL 权限。
- 共同 Reader 模型保留跨插件 typed post／reply 与稳定postId／嵌套正文边界；来源能力决定未来可开关回复流，不以Yamibo名硬编码，不增加本轮无消费者的控制或第二renderer。具体Yamibo解析／集成／验证仍在本版之外。
- Room migration 使用来自旧 schema 的真实数据快照；维持业务关联与回退边界；metadata transfer 升级随 identity 一起完成。
- 出口：旧 beta.5 数据无损升级、老 transfer 输入可恢复、remote-only 操作严格排除本地书，local resource 无法越过 publication 边界。

### P2 — 一个完整本地切片，再补全四种格式

建议只新增一个 host-owned publication 模块容纳导入／格式适配／native bridge／资源生命周期；沿用 `core/files`、`core/media` 和现有 Library／Detail／Reader／Data UI，不按格式建四套模块。

现有接入点已经定位：`app/SourceFlowController.kt` 和 `SourceRouteOwner.kt` 要求 installed source；`SourceRoutes.kt` 组织 Detail／Directory／Reader；`SourceRouteScopedOwners.kt` 的图片路径绑定 package、credential partition 和 chapter referrer。干净改造应保留 online provider，增加真实 local publication resolver，让两个 provider 进入同一 `feature/reader/ReaderScreen.kt`、`reader/engine/ReaderDocumentSession.kt` 和 `reader/ui/ReaderSurface.kt`。不得以假 SourceChapter URL、假 HXP 或跳过 active-source 检查完成本地打开。

1. 先打通 TXT＋带插图／链接的 EPUB：选择 → admission → Library → Detail → Reader → 书签 → process death → 清 cache → metadata restore／重关联。
2. 完成 EPUB 内容 profile；对没有支持的关键语义给出可见结果，不静默删除。
3. 接入同一 MOBI／KF8 解析入口并分别证明 MOBI7、AZW3、hybrid 容器；验证 JNI ABI、native 故障隔离和对应源码／relink 流程。
4. 完成批量部分失败、重复导入、storage failure、Provider 失效、入站 Intent 与冷启动恢复；严格区分导入文件、导入备份、安装扩展。
5. 出口：§5 四种格式全部走真实 production route，原书和状态可恢复；不是单独 parser 函数通过。

### P3 — 全局 E-ink owner 与全生产面恢复（2026-10-09 执行暂停）

Owner：`core/display`、`core/ui`、app root／navigation、各 feature、`reader/engine`／`reader/ui`、controlled WebView、Review Graph policy／catalog／goldens。

原步骤与出口保留为未完成的恢复范围，不是当前 Standard／本地阅读执行前置；重大 UI 最小证据不恢复本阶段。2026-10-09 明确获准的三点有限Reader包仅按§1执行，不恢复全局E-ink改善／完整恢复；其余继续改善／完整恢复仍须另获维护者明确授权。

1. 先修 global palette／motion／image／focus／pagination／stable commit，让共享组件承载差异。
2. 覆盖冻结期间新增生产面，按 §6 矩阵逐路修改；保留 Standard 行为与最新版修正，不复活历史 review app。
3. 接入本地切片与原有 remote reader；覆盖 profile 切换、state restoration、图文和 settings 全屏分支。
4. 授权恢复时一次性 reconcile `PHASE_4`、UI Constitution、EINK architecture、review policy、R1 selector、CI planner、goldens 与 AVD_MATRIX；不能只改 activeProfiles 留下脚本仍拒绝 EINK 的矛盾状态。
5. 出口：所有 required production nodes 的 E-ink 任务可达、动作和持久化正确，Standard 回归通过；真机 qualitative approval 独立记录。

### P4 — 联合候选、验收与发布准备

- 一个不可变候选；完成 parser／migration／security regression 与改动范围内本地完整预检，再进入 hosted protected admission。
- 同一候选继续 Standard／本地阅读验证；forced／真实 E-ink 的恢复与联合出口在当前暂停期间延期未完成，不追加执行。在线来源使用已有指定测试账户候选，自动化后冻结给人类审 changed qualitative items。
- 核实所有四种格式、remote regression、备份、profile 切换和长期阅读；不重复另开夹具视觉验收轮。
- 记录准确未完成项与设备限制。缺格式主路径、缺确认的内容保真、数据迁移失败或缺真实 E-ink 验收不能记作 0.5.0 完成。
- 本次预发布交付按 [Phase 4 的 2026-10-11 决定](PHASE_4.md#050-beta1-prerelease-delivery-decision--2026-10-11)执行；版本号／versionCode 单调增加并保留 release signing identity，原 beta.5／tag／APK 不替换。文档审阅、受保护 PR 准入、人工合并确认与正式制品发布仍按该决定及既有发布流程衔接，不将预发布视为完整0.5.0验收。

P1 是共同前置。原 P2／P3 并行安排在 2026-10-09 被暂停决定收窄为继续 P2／Standard，本地阅读不等待 E-ink 补跑；恢复 P3 后的联合完成点仍在 P4，不重复修改同一个 Reader owner。

## 8. 验证矩阵：每个结论一个主要证据 owner

| 层 | 必须证明 | 既有最高 seam／主要 owner |
|---|---|---|
| Protocol／trust boundary | origin／resource／locator 新版本、旧 transfer 兼容、invalid 输入、remote URL 不放宽 | `tsuyomi-protocol` schema／conformance；合法 synthetic／public-domain fixtures |
| Database／backup | schema13 真实数据迁移、书籍关联、进度／书签／历史无损、备份冲突、取消／中断恢复 | 既有 Room migration／transfer repository instrumentation；不跑无 database coverage 的空 JVM task |
| Publication／native | 四格式内容／编码／保护／配额／路径；解析器无网络；host resource default-deny、显式授权HTTPS及origin／DNS／redirect／撤回边界；服务死亡／取消 | 格式行为fixtures＋隔离Android parser／host resource seam；CI内，不是另一验收轮 |
| Reader | 测量即绘制、section／anchor 跳转、exact precedence、跨 flow／profile／字号／window 恢复、stale cancellation、图像晚到 | 既有 engine transition／locator 与 Android Reader instrumentation；补消费者可见行为，拒绝字段转发测试 |
| Production local book | 选择真实文件→Library→Detail→Reader→书签→重建→备份／重关联；未安装任何来源也可读 | 延伸既有 production Library journey 的真实 route／Room／Reader seam，系统 picker／Intent 用隔离设备 smoke |
| Global E-ink（执行暂停，延期未完成） | 保留 §6 全应用任务／状态出口；无重大 UI 变化不执行功能验证，符合触发时仅最小受影响证据，不关闭完整恢复 | Review Graph operation＋production Journey；layout 证明 bounds／semantics；golden 只证明静态几何 |
| Actual online | Wenku8 Home／Search／Detail／Reader／登录回程和已有网站动作未回归 | 既有 actual-online prefix 与 CI-owned signed replay；不另建第二套人工在线候选 |
| Physical E-ink（延期未完成） | 残影、刷新体感、长阅读、音量键、图文、IME／Dialog／WebView return；不作为当前 Standard／本地阅读执行前置 | 未来另获授权后绑定 exact APK 的真机观察与人类 approval；forced emulator 不冒充硬件证据 |
| Standard regression | 原任务、既有物理修正、辅助技术与 remote 状态没有倒退 | 同一候选的 affected Standard paths＋现有 CI lanes |

已定位的既有行为 seam：`RoomReadingProgressInstrumentedTest`（进度／Reader visit／完成／书签）、`SourceRouteScopedOwnersInstrumentedTest`（route admission／取消／过期结果）、`ReaderPaginationInstrumentedTest`（实际排版／图片／恢复）、`LibraryProductionJourneyInstrumentedTest`（生产 Library 端到端）、`QuotaFileStoreTest`（durable quota／路径／非驱逐）、protocol `conformance.test.mjs`（版本化边界）。这是规划时的验证选择；具体执行结果须绑定当前 source／APK，不能由 seam 的存在推定通过。

执行效率：失败先跑最小路径，稳定后跑一次选定完整本地范围；hosted CI 保持准入职责。build 一次、每个 active device deploy 一次、只捕获变动的视觉状态；不为同一确定事实重复 Journey／hierarchy／PNG／人工回放。新的永久测试只针对消费者可见边界、状态转换、数据安全和取消竞争，不测试 symbol wiring、默认值复述或“没有抛异常”。

2026-10-09 暂停／重大 UI 验证边界由 UI Constitution 独占，review policy／UI-R1 负责执行选择，取代 2026-10-07 增量规则及“首次恢复必须继续执行”的例外。原完整恢复出口保留为延期未完成项，不作为当前 Standard／本地阅读验证前置；无符合条件的重大 UI 变化就跳过 E-ink，符合时只选获准的最小受影响证据，不恢复改善或完整矩阵。已有同构建证据不重复采集，不把跳过记作验收通过。

## 9. AskUserQuestion 决策清单与保存规则

四组15项主决策＋3项条件补充均已实际回答、保存并reconcile。最终D4C有效选择为**0.5.0仅黑白，彩屏顺延**；runtime兼容性由用户确认，真机由维护者手动操作。无未答产品选择；ADR0015 已记录授权 P0 后的具体 parser／共享 Reader 选型及实测限制，真机与最终验收不能由合成样本或模拟器推定。

| 编号 | 决策 | 要展示的路线／权衡 | 批准后的 owning contract |
|---|---|---|---|
| D1 | 许可证姿态 | L1 宽松＋合规 LGPL；L2 严格宽松；L3 copyleft 组合发行；L4 有效商业／例外许可；L5 独立外部 Reader | ADR0001／第三方 notices；不自动改所有 source SPDX |
| D1R | Reader 技术选型策略 | 保留 Compose；统一替换成熟内核；先做受许可约束的多路线 P0 对照再定 | ADR0015／READER／MODULES |
| D2 | EPUB 内容 profile | 可重排完整基础语义；再加竖排／RTL；再加固定版式；两者均要求 | READER／文档版本与版本范围；不承诺所有 EPUB3 特性 |
| D2P | 受保护文件 | 0.5 非 DRM 并明确拒绝；合法 LCP 专项；外部合法 Reader 路径 | ADR／source/security/content contract |
| D3 | 备份口径 | metadata＋原书关联；JSON＋可选含原书归档；以含原书归档作为主要本地备份 | Backup ADR／独立 archive contract／transfer；v5 不塞正文 |
| D3S | 原书存储 | 私有完整副本；持久 URI 引用；副本默认＋显式引用模式 | DATA／owned publication lifecycle |
| D3I | 重复／不同 edition 导入 | 同字节自动关联、不同保留；重复时询问；全部独立副本 | Identity／DATA／locator revision |
| D3D | 删除与数据保留 | 正文删除与彻底删除两级；只移除正文保留状态；删除原书同时删除该书状态 | DATA／UI Constitution／Reader cancellation |
| D3E | 文件入口 | Library＋系统文件打开／分享；Library-only；Library＋Data 文件管理＋系统入口 | UI Constitution／Navigation／input boundary |
| D3T | TXT 编码／目录 | 自动严格识别＋不确定时确认／可修正目录；显式确认编码／目录；原文稳定分段不自动章节 | Publication normalization／READER／UI |
| D3N | 外部资源／链接 | 不自动联网，外链经确认交系统浏览器；完全离线不打开外链；逐书显式允许受限 HTTPS 资源 | Security／media policy／UI；无隐式凭据共享 |
| D4 | 重绘／设备后端 | 纯通用优化与 manual；真正逻辑 coordinator；单厂商；开放多厂商框架 | ADR0014／EINK／DATA／MODULES |
| D4C | 黑白／彩色 E-ink | 全灰度；彩屏保留图像原色、chrome 黑白；图像灰度可由用户选择 | UI Constitution G05／image profile；不偷改 full-frame rule |
| D4F | E-ink Reader flow | 默认分页仍可切 flow；保持当前 requested flow；E-ink 仅分页但保留 Standard preference | UI／READER／profile presentation；最后一项需明确改契约 |
| D4V | 真机验收条件 | 有一台／多台，后续录入型号；尚无但安排设备；仅 emulator 且不能宣称 release-ready | AVD／具体 evidence binding／版本范围 |

### 已记录回答 — 2026-10-06

| 编号 | 用户选择 | 约束／authority |
|---|---|---|
| D1 | 宽松优先，LGPL 可后备 | host／protocol 自有代码保持 Apache；MIT 候选先 proof；LGPL 后备须 source／notice／重组／重建与适用安装信息验证；不授权 GPL／AGPL采用、商业采购或外部替代。ADR0001。 |
| D1R | 先 P0 对照，再定内核 | 选择比较策略而非预选具体库；其后授权 P0 的结果与具体选型由 ADR0015 独占。保留单一文档／locator／Reader invariant；选型不是 human acceptance。 |
| D2 | 完整基础可重排＋图像型固定版式 | 2026-10-09真实语料修复扩大范围：保留中文图文、强调、ruby、目录／内部链接、脚注／尾注、列表、基本表格，新增图像型固定版式漫画、声明阅读方向、大合集与安全SVG图片包装；不承诺任意SVG、复杂竖排或所有EPUB3交互。四格式目标不缩减。ADR0015。 |
| D2P | 非 DRM，明确拒绝保护文件 | 不误报损坏／无限 waiting；不启用 LCP／密钥收集／外部受保护 Reader。ADR0015。 |
| D3S | 应用私有完整副本 | 不依赖Provider，原书不进入LRU／清缓存删除，卸载需备份恢复。DATA。 |
| D3I | 重复时由用户确认 | 关联已有书或独立新副本，多匹配须选目标，不自动关联；取代同字节自动关联提议。ADR0009／UI Constitution。 |
| D3 | 保留 JSON＋可选原书归档 | JSON轻量，归档独立版本化／验证／可恢复，取代metadata-only建议。ADR0008。 |
| D3D | 两级删除：正文／彻底删除 | 原书移除保留状态可重关联／彻底删除该书全部状态含独立书签；准确分别确认。DATA／UI Constitution。 |
| D3E | Library＋系统打开／分享 | 共用import owner，冷启动／批量／取消／Intent权限，不新增底栏或第二导入流程。UI Constitution／READER。 |
| D3T | 自动严格识别，不确定时确认 | 可靠BOM／UTF直入，歧义预览选编码／可修正目录，无标题稳定分段，不隐藏解码错误。READER／UI。 |
| D3N | 逐书授权受限 HTTPS资源 | 默认离线，显式publication-bound授权／撤回／redirect／public-address／quota；不共享凭据或执行脚本。取代永久no-network建议。READER／DATA／UI。 |
| D4C | 0.5.0仅黑白，彩屏顺延 | 条件补充明确supersede早先“彩屏原色”；本版EINK全灰度，无未用彩屏策略；Standard原色不变。UI G-05／ADR0014／EINK。 |
| D4F | 即时分页／合资格双页，完整访问 | 2026-10-08取代此前E-ink scroll可选：Reader不再连续滚动；Standard requested flow不覆盖、回切恢复，一个semantic locator。仅实际内容溢出补实测分页；后续人工反馈恢复进度／调节条点按和拖动。UI／ADR0014／EINK。 |
| D4 | 通用优化＋手动重绘 | 无高级coordinator／policy持久字段／厂商后端；已清ADR0014／DATA冲突，不冒充hardware refresh。 |
| D4V | 已有一台真实设备 | 用户报告掌阅Ocean4 Turbo2025、7英寸黑白、1264×1680；确认Android10+/API29+可装APK，维护者手动操作。不是本轮实屏通过；AVD_MATRIX。 |
| D4_RUNTIME | 已确认Android10+，可装APK | 用户确认，精确Android/API／固件随未来证据记录；不授权现在安装。 |
| D4C_EVIDENCE | 0.5.0仅黑白，彩屏顺延 | 明确缩小本版彩屏范围，取代D4C初答；不是悄悄降低证据要求。 |
| D4_OWNER | 我手动操作，提供结果 | human／设备操作owner为维护者，代理不接入、不代签human ACCEPT。 |

保存规则：每组 AskUserQuestion 回答后立即保存规范化选择、用户条件、推荐是否被接受和剩余问题；回答不明确的项保持 pending，不推断默认。绑定决定写入其唯一 owning contract，再同步 issue63 和当前交接；临时 machine-readable intake 只作为答案收集状态，不能成为第二份产品 authority。不保存原始私有提示、凭据或书籍内容。

2026-10-06 的明确“开始实施”授权取代本轮此前的提问／研究／文档限定，允许按上述 P0–P4 实施四格式本地阅读与全应用黑白 E-ink，并执行必要构建、迁移和隔离验证；E-ink 改善与首次恢复执行现由 2026-10-09 暂停决定取代。它不授予采购、维护者真机代理接入、canonical 替换、真实网站写入、commit/push、PR、合并、正式签名、发布或人类验收权限。各路线现有安全／数据保存／人类证据边界不变；具体内核仍须先过 P0。

## 10. 完成条件与明确停点

只有以下项目全部具备，才能把 0.5.0 记为目标交付完成：

- [ ] 决策 D1–D4、内容 profile、设备与 performance budget 已确认，必要 ADR／UI／schema／review obligations 已同步。
- [ ] 旧 beta.5 数据非破坏性升级；旧 transfer 输入仍可导入；remote identity／trust／credential／update 边界无回归。
- [ ] EPUB、TXT、MOBI、AZW3 各自完整 production route、边界失败、语义恢复和清 cache 后继续阅读通过。
- [ ] 本地书不触发来源／登录／网站写入；解析器／未授权资源零请求，显式资源授权／撤回和公开HTTPS边界验证；资源不越界，native故障不摧毁主UI／旧书。
- [ ] 全局 E-ink 的全部 production tasks、状态、分页／显式操作／模态／settings／系统栏证据完成；不是 Reader-only。**当前暂停，延期未完成；不阻止已授权的 Standard／本地阅读验证。**
- [ ] 同一构建 Standard 回归、forced E-ink 行为和绑定设备真实 E-ink 验收完成；人类 acceptance 独立、不可代签。**Standard 继续；E-ink 部分延期未完成，未取得实证前不标完整验收或面板质量通过。**
- [ ] 备份正文口径、DRM／不支持 subtype、硬件刷新能力、迁移／回退与数据删除均与实际行为一致。
- [ ] 选定本地 CI、hosted required checks、依赖／许可／供应链证据与独立 review 完成；发布另获授权。

停点是具体 prerequisite，不是空 TODO：GPL route 未批准时不链接 GPL 引擎；normalizer 保真不成立时不接更多格式；缺真实面板时不宣称 hardware-ready；未批准实施时不改执行 policy 或 APK。实现过程中发现必须缩小格式／保真／全局覆盖范围，先回到维护者确认，不能改成“以后再做”后仍声称完成。

## 11. 研究输入与本轮证据

### 仓库 authority／implementation

- [UI Constitution](../design/UI_CONSTITUTION.md)：G-01／G-05／G-06／G-12／G-13，以及 Library、Reader、来源、settings 与已有 Standard 修正。
- [E-ink architecture](../architecture/EINK.md)、[ADR 0014](../adr/0014-global-eink-display-profile.md)、[Reader architecture](../architecture/READER.md)、[ADR 0015](../adr/0015-unified-reader-document-and-layout.md)：全局 profile／刷新边界、统一 Reader、语义定位与切换。
- [ADR 0009](../adr/0009-stable-identity-and-progress.md)、[Data](../architecture/DATA.md)、[Shelves](../architecture/SHELVES.md)、[Modules](../architecture/MODULES.md)：身份、进度、存储与 owner。
- [ReaderDocument v1/v2](../../../tsuyomi-protocol/docs/reader-document-v1.md)、[transfer v5](../../../tsuyomi-protocol/docs/transfer-v5.md)／[v6 schema](../../../tsuyomi-protocol/schemas/tsuyomi-transfer-v6.schema.json)：旧输入保持闭合兼容，当前结构化文档／本地身份输出由新版本契约独占；Room v14 的迁移与消费者同步实施。
- [UI Atlas](../design/UI_ATLAS.md)、[AVD Matrix](../verification/AVD_MATRIX.md)、[review policy](../../../.agents/skills/tsuyomi-android-review/review-policy.json)、[Review Graph catalog](../../../.agents/skills/tsuyomi-android-review/review-node-catalog.json)：实际面证据、active／frozen 与去重职责。

### 上游 pinned research（研究输入与已选运行路线分别记录）

| 来源 | 研究版本／commit | 直接证据 |
|---|---|---|
| KOReader | `31f862c0578f0eb45dd5c606f4f7ebb5b8acda7d` | [文档打开／定位](https://github.com/koreader/koreader/blob/31f862c0578f0eb45dd5c606f4f7ebb5b8acda7d/frontend/document/credocument.lua)、[dirty region／刷新](https://github.com/koreader/koreader/blob/31f862c0578f0eb45dd5c606f4f7ebb5b8acda7d/frontend/ui/uimanager.lua)、[用户指南](https://koreader.rocks/user_guide/) |
| crengine | `517b8f0a6562d334ff42c89358fe6ee664d9adcb` | [CSS 子集与 GPL 声明](https://github.com/koreader/crengine/blob/517b8f0a6562d334ff42c89358fe6ee664d9adcb/crengine/include/cssdef.h) |
| Readium | `3.4.0`；commit `1b1f6b308a7b6f968b2bf1e66c84912879466f75` | [opening publication](https://github.com/readium/kotlin-toolkit/blob/3.4.0/docs/guides/open-publication.md)、[ContentService 限制](https://github.com/readium/kotlin-toolkit/blob/3.4.0/docs/guides/content.md)、[BSD license](https://github.com/readium/kotlin-toolkit/blob/3.4.0/LICENSE) |
| libmobi | `906274205c11944b628da1c553b255acb1af7c55` | [项目说明](https://github.com/bfabiszewski/libmobi/tree/906274205c11944b628da1c553b255acb1af7c55)、[LGPL obligations](https://github.com/bfabiszewski/libmobi/blob/906274205c11944b628da1c553b255acb1af7c55/COPYING) |
| Librera | `635d67d61af1ab4528d1b2cf9473b060311df116` | [格式／许可证与 Android 用户流程](https://github.com/foobnix/LibreraReader/tree/635d67d61af1ab4528d1b2cf9473b060311df116) |
| Hikari Flutter Plus | `a1feba6d1dd8dbbdd2b5ae042e44f2ec54d26bef` | [回复流设置](https://github.com/Xfire233/hikari_novel_flutter_plus/blob/a1feba6d1dd8dbbdd2b5ae042e44f2ec54d26bef/lib/pages/reader/widgets/reader_setting.dart)、[post／reply数据](https://github.com/Xfire233/hikari_novel_flutter_plus/blob/a1feba6d1dd8dbbdd2b5ae042e44f2ec54d26bef/lib/models/yamibo_thread_read.dart)、[呈现参考](https://github.com/Xfire233/hikari_novel_flutter_plus/blob/a1feba6d1dd8dbbdd2b5ae042e44f2ec54d26bef/lib/pages/reader/widgets/yamibo_reply_read_page.dart)；行为研究，不采用源码或提前验证Yamibo |
| EPUB／Android | EPUB 3.3；Android SAF | [W3C EPUB 3.3](https://www.w3.org/TR/2026/REC-epub-33-20260113/)、[SAF 文件访问](https://developer.android.com/training/data-storage/shared/documents-files) |

2026-10-06 规划研究检查点仅完成源码／合同／上游研究和现有 schema smoke，未构建或部署；这一历史限制不描述其后已获授权的实施验证。随后 P0 的选型、预算和范围由 ADR0015 记录，API29 隔离 parser 与真实本地文件打开／阅读路径已执行；它们不证明完整 EPUB conformance、全部生产节点验收、真实 E-ink 面板或最终 CI 准入。Context7 查询曾遭证书验证失败，使用上游 release／固定源码和官方标准补证，未把失败查询当成 API 证据。

### 2026-10-07 本地实施检查点

- 基于 `e9c97c04e99d692b5cc5eacee13bc31696906b9c` 的未提交变更包已接通四格式 production Library／Detail／Reader、原书归档恢复、两级删除、Room v14／transfer v6 和全局黑白 E-ink。具体 parser／预算仍由 ADR0015 独占；Yamibo 只有合同预留，没有本轮实现或验收。
- 已执行选定非 golden 构建／lint／JVM、协议64项、Room59项与隔离 publication6项、完整 Library production Journey31项、完整 reader/ui46项，以及 Search、共享按钮、真实系统栏和资源授权的受影响回归。真实开发 APK 经系统文件选择器导入 TXT、进入规范 Detail／Reader，观察到全帧灰度、软件重绘计数递增及回切 Standard 后原色与书签保持。精确 input／APK／设备与原失败分类由本轮 change packet 证据记录，不由此段或截图推定联合验收通过。
- 后续 WebView 灰度根因修复已接入：根级 raw Canvas 合成改为框架托管 color-filter layer，真实原生触控／输入、表单保持、Standard 原色与 EINK 灰度回切经隔离 API29 验证；既有共享主题／按钮和会话交接回归通过。实际 online APK 的签名 Wenku8 登录页可见位置可聚焦并输入／清除单个非凭据字符，原始 PNG 全帧非灰度像素0；没有提交登录、保存 session、替换已有登录表单设备或 canonical。此为受影响路径修复证明，不取代下述联合出口。
- 当前运行是 LOW 下的有界开发验证，不是完整本地 CI。首次恢复仍须完成 §8 的全部适用出口；golden 的既有 Windows classpath 限制、受保护 CI、真实来源与 signed replay、Ocean4／辅助技术及人类质量验收均不得记为通过。没有替换 canonical、提升版本、commit／push、PR、合并、正式签名或发布；§10 完成条件保持未验收。

### 2026-10-08 来源恢复与 E-ink 整改追加范围

- 维护者将首详情／目录失败从个别书籍 P0 扩展为 beta.5 已有的来源／会话／请求生命周期缺陷，明确包含登录后及匿名搜索后的首次进入；本轮须修复相同边界影响的主页、搜索、详情／目录、章节、网站收藏及更新恢复，不按书名或 ID 特判。来源与远程会话不变式仍由 SOURCES／既有 ADR 独占。
- 全应用 E-ink 整改按 UI_CONSTITUTION 的 2026-10-08 纠正执行，并保留 Standard 回归；本段记录实施范围，不另建显示行为变体。完整恢复的 §8 出口和 §10 联合验收保持独立，局部回归、布局与 PNG 不替代真实来源、CI、辅助技术或人类面板结论。
- 维护者明确授权在整包受影响验证通过后生成全新开发 APK，并仅替换、清除原隔离人工验证实例 `Tsuyomi_V05_EInk_20261007_58df` 的应用数据，随后停止自动化交由维护者复测。此授权不涉及 daily canonical、Ocean4 部署、真实网站写入、commit／push、PR／合并、正式签名或发布；不得把部署记为 ACCEPT。
- 本轮有界开发结果：来源／会话／Reader-image／网站收藏／更新回归 **101/101**；相邻八个 UI 模块 **202/202**，含完整 shared pager11／Reader48，所有最终报告 failures/errors/skipped0。实际新 APK 已经系统 TXT 选择与显式导入→详情→Reader→左侧项目／右侧设置→按键下一页→返回保留书签；原始 Reader／设置 PNG 全帧非灰度像素0。根因／旧失败和范围记录在唯一审查报告及现有 issue63，不把过时测试选择器或无效窗型操作作为产品修补。
- 经上述受影响验证后，已按唯一实例授权更新并清空 `Tsuyomi_V05_EInk_20261007_58df` 应用数据，冷启动成功；已安装 base.apk 与冻结开发 APK SHA256 **20f473cd057afb1e28c770748acaa79de06a38c63fe166723ff3d84ff6ced5e7** 一致。停止自动化，等待维护者真实来源／首书目录与 E-ink 复测。仍不授予 §10 联合验收、真实面板／辅助技术通过、canonical、commit／push 或发布。


### 2026-10-08 紧凑布局纠正检查点

- 维护者拒绝上一候选的满宽逐项布局与居中／分组损失；G-05／A.1 的最小更改纠正已落实到共享测量和既有 route-owned 组合。原设计、多列卡片、固有按钮宽度、正文居中、组内／组间关系保留；仅实际溢出补按键操作，阅读设置仍采用已明确要求的左侧项目／右侧内容。显示行为由 UI_CONSTITUTION 独占，本文不另建规则。
- 本次独立有界验证为八个UI模块207项及原生集成20项，合计227/227、0失败／error／skipped；最终online assembly／lint通过。精确新开发APK在另有owner的手机开发实例完成实际TXT导入、三列书架／居中状态、分组值调整与设置按键翻页／返回保留书签；原始PNG全帧灰度。此前101／202项保留为旧输入的分别 dated 证据，不拿来关闭此次可见纠正或 §10 联合验收。
- 原人工实例继续冻结于前次20f473c候选；本次没有新的更新／清空、账号、网站写入、canonical／Ocean4、commit／push或发布授权。完整 §8／§10 出口、人类实屏和正式准入保持独立；具体构建／APK／设备／失败分类与证据由现有 change packet 和唯一审查报告记录。

### 2026-10-09 E-ink 暂停与验证边界协调

- 维护者暂停当前 E-ink 改善，后续功能验证无重大 UI 变化就跳过 E-ink；Standard 与四格式本地阅读工作继续。触发与最小证据由 UI_CONSTITUTION 独占，review policy 使用既有 Standard-first／条件选择，不变更 selector 算法或测试。
- 首次全局恢复的 graph／state／device／物理人工出口延期未完成，不再是本轮 Standard／本地阅读执行前置；完整 0.5.0 验收、hardware-ready 和真实 E-ink 质量仍未通过。重大 UI 最小检查不恢复改善或全量恢复，继续恢复须另获明确授权。
- 上述日期之前的授权、检查点及 artifact-bound 结果保留为历史，不改写、不聚合为当前验收；暂停不改变数据保存、安全、取消／生命周期、CI、人工批准或发布边界。

