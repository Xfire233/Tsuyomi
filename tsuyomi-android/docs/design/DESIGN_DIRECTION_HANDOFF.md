<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi RC2.1 参考深挖与审阅结论回传

- 日期：2026-08-13
- 输入：`DESIGN_REFERENCE_REVIEW.md`、Flutter 参考项目 `hikari_novel_flutter`、RC2 Atlas `manifestSha256=1013fb35c0b566d2ad60cf8087dd7c3e7815c3995870f1ce2b7cdb184939d3`、`tsuyomi-atlas-review-bundle-0813.json`。
- 性质：这是 RC2.1 设计与下一轮 fixture Atlas 的约束，不是 Gate 4 生产实现授权。
- 纠正：上一版把 31 个项目压成“亮点/风险”，但没有把可复用的页面骨架、几何、状态语法和操作位置落实到每个 Tsuyomi 页面；因此虽然引用很多，Atlas 仍像独立猜测。本版以“参考事实 → 审阅问题 → Tsuyomi 具体合同”重做。

## 0. 文档职责与最新审阅

本文件从本轮起只保存**审阅原件、归一化意见和 supersession 历史**，不再作为第二份产品 UI 合同。当前可见 UI 的唯一执行合同是 `UI_CONSTITUTION.md` 的 Active constraint spine；Atlas 只证明该 spine，Gate 4 只管理范围和授权。

最新绑定审阅：`build/atlas-review/tsuyomi-atlas-b0417a5d6ea7-review.json`，SHA-256 `4886767c7ab846f07e8c9afe59d0e1f82e2dc10f9843cd378792f8a9686a5c49`，manifest `b0417a5d6ea76e07c5d06cf93524c2d92defd3089186d133aaba7fe1d8a3f73b`。新增 stable IDs：`B041-B`、`B041-D`、`B041-H`。

该审阅 supersede：Detail icon-only/pill add-tag、Search field 的 leading Search 与 source-selector controls、E-ink quick-controls-only settings 和 E-ink group subpages。Standard Reader sheet 不受本轮修改。

随后用户在本会话直接给出最终 Detail 结论，不再导出 JSON；该指令归一化为 `B041-B2`：左右严格等宽；左侧显示全部可用标签并保证完整 `添加标签`，标签文字上下居中且不得用 `+n` 隐藏；右侧圆角矩形 `稍后再读` 占满整个半区并自适应填满左侧 FlowRow 高度。它 supersede B041-B 中仅要求“固定在右侧/同规格容器”但未明确半区和高度的部分。

用户随后补充 `B041-B3`：`稍后再读` 不再占固定右半区，宽度必须随本地化标签和字体缩放取 intrinsic size，仅继续填满自适应高度；标签最多两行，E-ink 的 tag 与 `添加标签` 必须视觉等高。该指令 supersede `B041-B2` 的严格等宽部分，其余“完整添加、上下居中、无 `+n`”仍有效。其“外层区域也按内容收紧”的错误归一化已由 `B041-B4` 纠正。

用户进一步明确 `B041-B4`：动态宽度只针对单个 tag、`添加标签` 和 `稍后再读` 控件；整个标签区域始终占满屏幕可用宽度。该区域上下留白应更紧凑，使用 4dp external vertical inset。其此前保留 48dp 可见控件高度的归一化已由 `B041-B5` supersede。

用户继续明确 `B041-B5`：此前所说的“留白”主要指文字到各自 tag、`添加标签`、`稍后再读` 边框的内部距离，而不只是整条区域的外部 padding。三类可见控件统一收至 Material button 的 40dp 最小高度；tag/add 横向 inset 为 4dp，read-later 为 8dp；`稍后再读` 不再被 FlowRow 的 interactive height 拉高；可点击控件仍保留独立 48dp minimum interactive target。

用户指出 `B041-B6`：虽然三类可见容器数值上都是 40dp，tag 仍直接占 40dp layout slot，而按钮在 48dp interactive slot 中居中，导致 tag 边框比右侧按钮高 4dp。修正为 tag 同样占 48dp layout slot，并将 40dp Surface 居中；三类边框 top/bottom 必须精确一致。

用户在继续修改前确认 `B041-B7`：整条区域满宽，左侧 tag FlowRow 占 `稍后再读` 之前的全部剩余空间且最多两行；`稍后再读` 按内容宽度固定在 trailing edge。tag、`添加标签`、`稍后再读` 统一使用 `labelLarge`、40dp 可见高度、8dp horizontal content inset，并在相同 48dp slot 内居中。

用户要求 `B041-B8`：同一 Detail 控件组必须保持圆角一致，不允许 tag/add/read-later 分别取 shape token。三者统一 `shapes.small`：Standard 均为 8dp，E-ink 均为 0dp；字体、inner inset、可见高度和 layout slot 继续共用同一组规则。

上一轮 C3/B4 审阅与 stable IDs 继续作为已裁决历史输入；未被 B041 明确修改的决定保持有效。

## 1. 结论

**Tsuyomi 仍是静谧的编辑式书籍索引，但“静谧”不能成为低密度、低对比、全靠文字的借口。** 下一版必须像成熟阅读器一样：书架负责快速定位，收藏夹就是书架中的文件夹，详情直接承载目录，Reader chrome 直接承载进度跳转，状态优先使用图标、位置、字重、容器层级和有限颜色表达。

上一版最严重的四个方向错误：

1. 把收藏夹拆成管理页和多个次级路由，违背 Flutter 版本、Readest、ReadEra、Google Play Books 的书架组织模型，额外增加一步。
2. 只借了 Book's Story 的“底部 sheet”，没借成熟 Reader 的“正文中心 tap → 上下 chrome → 直接进度控制 → 再进设置”完整操作顺序。
3. 说参考 Mihon，却没采用其详情与章节同一连续 surface、更新列表 56dp 稳定行、选择 AppBar 和图标状态语法。
4. 把“信息减法”做成了 raw text 和 `·` 串联；真正的高密度应减少句子，固定信息槽位，并用视觉语法区分，而不是删掉结构。

## 1.1 RC2.1-3 审阅纠偏与停线规则

绑定输入：`build/atlas-browser-rc21/tsuyomi-atlas-rc21-3-review.json`，SHA-256 `9beb5e567542232fd5d6d222e81bc63371b5f38ffaa055dd52c544a617fe0599`。该审阅否决 RC2.1-3 的 A–H 执行，不构成设计批准或生产授权。

本次失败不是单个页面失误，而是工作流失效：需求被摘要后丢失了可观察结果；文档中的旧推导没有与用户新决定做冲突消解；实现完成后只检查编译、hash、文件数量和页面可加载，没有逐条证明截图符合用户任务；旧、新 evidence 还曾被模糊匹配到同一输出名。自本节起采用以下停线顺序，任一步失败都不得渲染或交付下一版 Atlas：

1. **原话归一化**：每条审阅意见保留 `用户要求 → 页面可观察结果 → 明确禁止 → 证据帧` 四列。摘要不能替代原话，技术内部语义不能覆盖用户可见行为。
2. **冲突消解**：修改原型前搜索全部绑定文档和参考实现；旧合同与新审阅冲突时，先删除/改写旧合同。禁止一边保留旧规则，一边在代码中增加特例。
3. **内容清单与密度预算**：为每个 board 先冻结“可见元素白名单、首屏必须完整显示的任务、最多说明文案、触控区与视觉高度”。不在白名单内的帮助性说明默认禁止进入常驻 UI。
4. **实现前验收账本**：每个 board 必须列出可观察断言和 stop-ship 反例；没有账本不得写该 board 的 Compose 页面。
5. **实现与静态检查**：Standard 真实委托 M3；E-ink 保持同任务但使用单色、分页或按钮切换。实现不得自行扩充信息层级、帮助文案或容器。
6. **设备实看，不以构建成功代替设计正确**：在两台 API 29 AVD 上按任务路径操作；逐帧核对元素、顺序、密度、可读性、系统栏和交互状态。编译、hash、manifest 只证明 artifact 完整，不证明设计合格。
7. **证据血缘唯一**：生成器只接受 canonical capture 精确文件名；资源名含内容 hash；评论存储绑定 manifest hash；每个 manifest 写入 review 输入 hash、原型源 hash和断言结果。禁止覆盖旧 evidence 或从混合目录模糊收集。
8. **内部反证审阅**：交付前逐条尝试证明它仍违反用户原话。存在一个未闭合反例，就不生成 reviewer bundle。

### RC2.1-3 审阅验收账本

| Board | 绑定的可观察结果 | Stop-ship 反例 |
|---|---|---|
| A Library | 常规手机 Standard/E-ink 固定三列；宽屏按 150dp 最小可读卡宽自适应且不设任意列数上限；系统节点可隐藏/重建但规则不变；Standard 快捷书架横滑/拖放并露出下一项提示，E-ink 用按钮分页/移动；仅 AppBar `+` 进入创建流；手动排列与确认后两书入新收藏夹；所有快捷项/卡片严格等高 | 宽屏仍固定三列；来源 hint 改布局；出现第二创建入口；系统 membership 可任意手改；无横向可发现性；同排卡片高度不同；拖放未确认或只移动一本书 |
| B Detail | 不重复来源名；封面右侧阅读进度下固定显示五颗可点星；标签区域始终占满可用宽度。左侧 FlowRow 占 `稍后再读` 之前全部剩余空间且至多两行；内容宽度的 `稍后再读` 固定贴 trailing edge。单个 tag、完整 tonal `添加标签` 与 `稍后再读` 必须共用一套组内几何：`labelLarge`、40dp 可见高度、8dp horizontal inset、相同 48dp layout/touch slot 和 `shapes.small`；边框 top/bottom 与圆角必须精确一致。可点击控件保留 48dp minimum target。不得用 `+n` 折叠可显示标签；整条区域 external vertical inset 为 4dp。缓存顶栏一级动作；章节工具左对齐 | read-later 未贴 trailing edge；固定宽度控件；同组字体、inner inset、高度、slot 或圆角不一致；可点击目标小于 48dp；超过两行；`+n` 隐藏可显示标签；添加标签被裁切；外层区域按内容收紧；评分离开 header |
| C Reader | semantic locator、点按跳转和单次提交仍为架构要求；seek-preview 的具体 Standard/E-ink 呈现不在当前 fixture 中审批，等待实体设备 Reader 测试 | 将 emulator still 或未审阅的 WYSIWYG/E-ink preview 当作已批准视觉合同 |
| D Search | draft inert；输入框只有右侧 Search icon 可操作；一次提交启动本地与全部 active sources（source-addressed route 可隐式限定）；一个总进度和结果流；exact identity 合并；高级筛选/D33 不出现 | leading Search；source-selector button；输入即刷新；两次提交；常驻来源 lane/status/教学；同名猜测合并 |
| E Updates | E-ink 每项完整呈现标题、状态和主动作；Standard working 用短时 M3 indicator，E-ink 用静态 glyph；关键结果保留短文字 | E-ink spinner/动画；只剩小封面/碎片；关键失败只靠图标或颜色 |
| F Remote | 顶栏明确显示刷新列表与全部复制；E-ink 增加纵向间距；标题按可读下限调整并允许换行 | 标题固定窄宽截断；只显示含混 sync；动作深藏 |
| G Tags | 本地/来源结构明确；compact chips 不显示书籍数，list rows 显示书籍数；不使用 `·` 串和常驻教学说明 | list 缺少数量；compact chip 塞入数量；页面内教学；点分隔元数据 |
| H Settings | Standard 保持同一 M3 sheet 部分/全高两态；E-ink 改为带 AppBar 的独立全屏页，在一个 route 直接展示排版/页面/导航/设备全部设置，wide 双列、compact 单列 | E-ink sheet/quick-only 页面；E-ink group 子页；整屏超长 slider 后留下大块空白；Standard sheet 被误删 |
| 全局 E-ink | Compose 内容、状态栏、导航栏和系统图标全部为不透明灰阶；整张 evidence PNG 通过全像素 `R=G=B` 检查 | 任一系统栏或导航按钮带色；依赖透明度表达状态 |

该账本是下一版 fixture Atlas 的最低门槛，不授权生产实现。

## 1.2 全量审阅来源与最终冲突裁决

本轮冲突复核把以下实质审阅原件视为不可改写的历史输入：初始决策稿 `docs/design/tsuyomi-atlas-review-bundle.json`（SHA-256 `84b065c851713a10e2c6ad1e9fd2dfde7d413cf0e3ea053bd2ce147c9e96c968`）、RC2 审阅 `build/atlas-browser/tsuyomi-atlas-review-bundle-0813.json`（SHA-256 `07f0b9a6f444005b44472ea644b1b844f2cd55e12d04510e4984f2bbda2ce08d`）、RC2.1 中间审阅 `tsuyomi-atlas-rc21-review.json`（SHA-256 `267a873b3c9cf73c227ddfafdc6f62c238b401be4c4f17cea011fde251e3de70`，绑定 manifest `44e6cdd127603122f27fb17f0b1a1993fdd5ed15be836c4e5e1abc92644d7ca5`）以及 RC2.1-3 否决稿（§1.1）。空白 provisional review 不是审阅结论。历史原件不覆盖、不删除；下列显式裁决只负责消解它们之间以及它们与旧 Gate/ADR 的冲突。

最终执行决定：

- **Search**：query draft inert；输入框只保留右侧 Search icon；一次显式提交同时启动本地与全部 active sources，source-addressed route 可隐式限定来源；成功结果进入一个增量结果流，只显示一个总进度；仅 exact `BookIdentity(sourceId, remoteBookId)` 合并；不显示 source selector、leading Search 或高级筛选。
- **Library 几何与布局**：Standard/E-ink 均默认网格；常规手机固定三列，宽屏按 150dp 最小可读卡宽自适应且不设任意列数上限；只有确实低于可读/可触控下限的 double-compact 窗口才降为两列；忽略来源 layout hint。卡片严格等高，截断标题必须有进入 Detail/无障碍完整名称的完整入口。
- **Library 桌面模型**：系统节点默认创建但可隐藏，并可在创建页重建；定义和规则不可改写。除 `稍后再读` 可手动拖入/移出外，`继续阅读 / 最近阅读 / 休眠来源 / 追更` membership 始终由规则自动产生。主内容流保留规则排序，并增加独立“手动排列”模式；Standard 可拖放，E-ink 用明确移动按钮实现同结果。
- **创建与拖放**：创建入口只保留 Library AppBar `+`。Standard 将书拖到另一书上先弹命名/确认，确认后两本书原子加入新普通收藏夹；E-ink 通过按钮/选择流程完成同一任务。快捷书架 Standard 支持拖放，E-ink 使用前移/后移/移除/替换按钮。
- **层级与镜像**：普通收藏夹最多两级。镜像默认只显示网站结构；只有用户在镜像页显式创建本地整理后才出现 `本地整理` 分区。
- **Detail**：只使用一个动态多功能 FAB；持续下翻时变为快速到底，持续上翻时变为快速到顶，停止操作一段时间恢复继续阅读。来源动态区只放低风险快捷动作；缓存固定为顶栏一级图标。
- **Reader seek**：semantic locator、点按跳转和单次提交仍为架构要求；具体 WYSIWYG/离散 preview presentation 本轮不由 fixture 批准，等待实体设备 Reader 测试。
- **Reader settings**：Standard 保持底栏一级入口和同一 M3 sheet 的部分/全高两态。E-ink 不使用 sheet、quick-only 页面或 group 子页，改为带 AppBar 的独立全屏设置页，在同一滚动 route 直接展示 `排版 / 页面 / 导航 / 设备`；wide 双列，compact 顺序堆叠。
- **Updates 与状态**：Standard 单项处理中使用短时 M3 progress indicator；E-ink 使用静态工作 glyph，禁止连续动画。常态优先图标，关键成功、失败、部分完成结果保留短文字。
- **Remote / Tags / identity**：Remote 顶栏动作是刷新列表与全部复制；Tags compact chips 不显示书籍数，list rows 显示书籍数；来源身份只在确需消歧时显示紧凑 mark，不显示常驻来源名或 identity band。

本节保留为 2026-08-13 冲突裁决历史。最新产品可见合同已归并到 `UI_CONSTITUTION.md` Active constraint spine；与 `B4-*`、`C3-*` 或该 spine 冲突的条目均已 supersede，不得继续执行。


### 1.2a B4 审阅映射（历史保留）

| Review ID | Normalized obligation | Active contract | Evidence |
|---|---|---|---|
| `B4-A` | 宽屏 Library 自适应列数；创建只保留 AppBar `+` | Constitution Board A | A phone + wide E-ink geometry |
| `B4-B` | 标签直接列出；评分移出 split container；章节动作左对齐 | Constitution Board B | B Detail |
| `B4-C` | Reader seek preview 暂缓到实体设备阶段 | Constitution Board C / `B4-04` | No current visual approval frame |
| `B4-D` | 搜索提交放入输入框；删除 dormant/status 行 | Constitution Board D / `B4-02` | D Search |
| `B4-E` | 检查时只增量加入发现项；每项显示更新日期 | Constitution Board E / `B4-03` | E working state |
| `B4-F` | 删除 `无需重复加入` 冗余文案 | Constitution Board F | F Remote |
| `B4-G` | 密集/列表切换必须直接可见 | Constitution Board G | G both layouts |
| `B4-H` | E-ink settings 保留明确顶部安全间距 | Constitution Board H | H E-ink |
| `B4-ALL` | 文档与实现必须共享一套结构化合同，不能只做机械对账 | Constitution Active constraint spine | whole-bundle conformance |

### 1.2b C3 审阅映射（历史保留）

| Review ID | Normalized obligation | Active contract | Evidence |
|---|---|---|---|
| `C3-B` | 评分固定在封面右侧、阅读进度下方 | Constitution Board B | B Detail header |
| `C3-D` | 放大 Search list 封面；来源行和全部文字不得越过封面上下边界 | Constitution Board D / `G-09` | D list geometry |
| `C3-E` | working 状态只保留 `正在检查更新` | Constitution Board E | E working state |
| `C3-G` | Tags list 显示书籍数量 | Constitution Board G | G list in both profiles |
| `C3-H` | E-ink settings 进一步远离屏幕顶部 | Constitution Board H | H E-ink ≥64dp inset |
| `C3-ALL` | 所有 cover-leading list row 使用同一垂直边界规则 | Constitution `G-09` | D and list-row geometry assertions |

### 1.2c 最新 B041 审阅映射

| Review ID | Normalized obligation | Active contract | Evidence |
|---|---|---|---|
| `B041-B` | tag/add 使用同规格圆角矩形；添加项不同 tonal color；按宽度自适应铺排 | Constitution Board B | B Detail tag FlowRow |
| `B041-D` | 输入框只保留右侧 Search action | Constitution Board D | D Search field |
| `B041-H` | E-ink settings 重构为一次显示全部设置的全屏页 | Constitution Board H | H E-ink full page |
| `B041-B2` | Detail 左右等宽；左侧全量标签 + 完整添加按钮且文字垂直居中；右侧稍后再读填满半区和自适应高度；禁止 `+n` | Constitution Board B / `B041-B2` | B Detail Standard + E-ink split geometry |
| `B041-B3` | Leading intrinsic tag/read-later widths，无空白 stretch；标签最多两行；E-ink tag/add 48dp 等高 | Constitution Board B / `B041-B3` | Detail-only Standard + E-ink render |

### 1.3 Review → contract → evidence coverage index

Coverage IDs are stable references to non-empty review inputs. `I-*` = initial bundle decisions/frame comments; `R2-*` = RC2 route/module comments; `M-*` = RC2.1 intermediate comments; `L-*` = RC2.1-3 comments. RC2 and intermediate route comments with identical payloads remain listed together rather than silently deduplicated. Every ID below maps to an active contract and at least one executable Atlas assertion; the source JSON remains the verbatim quote authority.

| Review IDs | Normalized obligation / supersession | Active contract | Evidence assertion |
|---|---|---|---|
| `I-D19` | Read Later remains an independent presence origin | Constitution §0/§5.5; Gate 4 D19/D20 | S2 system membership; S4 folder/membership mutation |
| `I-D32`, `I-D33`, `I-F09-search`, `R2-search`, `M-search`, `M-d`, `L-d` | One shared search route; inert draft; one-submit concurrent basic search; one progress/flow; exact identity only; D33 advanced filters explicitly superseded/deferred | §1.2 Search; Constitution §6.3a; Gate 4 D32/D33 | S10; D board denylist for filters, lanes, status/tutorial prose |
| `I-Q1` | Restrained Standard motion; E-ink/reduced immediate | Constitution §11 | 30/60fps motion matrix; S9 working-state profile pair |
| `I-Q2`, `I-Q3`, `I-F01-library`, `I-F02-library`, `R2-library`, `M-library`, `M-a`, `M-f`, `L-a` | High-density cover-preserving Library; regular-phone fixed three columns; equal cards; compact/list alternatives; discoverable shortcut shelf | §1.2 Library; Constitution §5.2–5.6 | A board geometry assertions; S1/S3/S15 |
| `I-Q4` | Real empty state with restrained monochrome emoticon, reason and recovery | Handoff §2.1; Constitution §9.1 | S14 empty-state inventory; F2 |
| `I-Q5`, `I-D`, `I-B` | Visible selection entry + long press; replacement app bar; Standard swipe only with visible/TalkBack equivalent | Constitution §4.2/§10; Handoff §2.3 | S4 + S15 focus/TalkBack |
| `I-Q6` | E-ink Reader locked to pagination | Constitution §14; ADR 0014/0015 remain authoritative | S6 E-ink confirmed full-page preview; S15 profile switch |
| `I-Q7` | Source identity stays compact and never reserves phantom Up space; later reviews supersede theme-color backdrop with compact mark only where needed | Constitution §4.2/§15.5; §1.2 identity | S12/S17 source-context stills; invalid/missing mark fixture |
| `I-Q8` | System font default plus complete user-selectable Reader font settings | Constitution §3.1/§14.1 | S7 complete typography inventory; S15 fontScale/glyph stress |
| `I-A` | Creation placement follows task frequency; B4-A supersedes the old dual-entry interpretation: Library uses only AppBar `+`, with no final-page card or creation FAB | Constitution Board A / §5.5 | S4 AppBar create flow; A board |
| `I-C` | Historical modal-family rule retained for ordinary sheets: Standard modal bottom sheet / E-ink opaque full-window replacement. B041-H makes Reader settings a narrower exception: Standard keeps its two-anchor sheet, while E-ink uses a dedicated full-screen AppBar route. | Constitution §7.3/§14.1 / Board H | S7; modal focus/Back assertions; H wide/compact E-ink page |
| `I-F03-history`, `R2-history`, `M-history` | History removes irrelevant metadata, supports clear-all, and switches to exact date/time after seven days | Handoff §5.3; Constitution route matrix | S16 |
| `I-F04-updates`, `R2-updates`, `M-updates` | Compact update session; three layouts; status glyphs; Standard short indicator/E-ink static glyph; obstruction only expands when needed | Handoff §5.3/§5.7; Constitution route matrix | S9 + E board |
| `I-F05-collections`, `I-F06-collection-templates`, `R2-collections`, `R2-collection-templates`, `R2-collection`, `M-collections`, `M-collection-templates`, `M-collection` | Collections are Library peers with three layouts; template manager removed; SystemNodes hide/rebuild; two-level hierarchy; desktop-style creation/manual order | Handoff §1.2/§5.1–5.2; Constitution §5.5; ADR 0016 amendment | S1–S4; A board |
| `R2-collection-rule`, `M-collection-rule` | Database-backed rule values use pickers; create entry/help are discoverable; drafts/validation survive | Handoff §5.2; Constitution §6.3 | S11 |
| `R2-tags`, `M-tags`, `M-g`, `L-g` | Local/source tabs, visible compact/list switch, compact chips without counts, list rows with book counts, no `·` or teaching prose | Constitution Board G | S13 + G board |
| `R2-mirror`, `M-mirror` | Website/local ownership never blurs; local partition appears only after explicit mirror-page creation and never remote-writes | Handoff §1.2/§5.2; Gate 4 D24 | S8 |
| `I-F07-book-detail`, `R2-book-detail`, `M-book-detail`, `M-b`, `L-b` | Compact Detail whitelist; rating fixed in the header at cover right; tags/dynamic split; no repeated source; cache top-level; full chapters; standard icons; direction-driven single FAB | Constitution Board B | S5 + B board |
| `I-F08-directory`, `R2-directory`, `M-directory` | Routine directory removed; chapters integrated; sorting/download/cache state use standard glyph/action slots | Handoff §5.4; Gate 4 canonical graph | S5 |
| `R2-reader`, `M-reader`, `M-c`, `L-c` | Center-tap chrome; direct progress rail; tap-to-jump; semantic locator truth and exactly one final commit remain required; preview presentation is deferred | Constitution Board C / §14 | S6 physical-device evidence later; no current C approval frame |
| `R2-reader-settings`, `M-reader-settings`, `M-h`, `L-h`, `B041-H` | Bottom-level settings entry and complete inventory remain. Standard keeps one partial/full sheet with four first-viewport controls; E-ink replaces quick/group pages with one dedicated full-screen AppBar route showing all typography/page/navigation/device sections, two columns wide and stacked compact. | Handoff §1.2; Constitution Board H / §14.1 | S7 + H board |
| `R2-browse`, `M-browse` | Browse uses one consistent real-M3 action hierarchy and sufficient density | Constitution Browse route / §6.4 | S17 |
| `R2-remote-library`, `M-remote-library`, `L-f` | Three layouts; aligned actions; refresh list + copy all visible; selection/target picker; readable E-ink spacing/titles | Handoff §5.7; Constitution Remote route | S12 + F board |
| `M-e`, `L-e` | E-ink Updates cover/title geometry must retain identifiable content | Handoff board E; Constitution density/readability rule | S9 + E board readable-bounds assertion |
| `R2-help`, `M-help` | Help is locally searchable and topics use accordions | Constitution Help route | S17 |
| `I-F10-more`, `I-F11-display` | More/Display omit internal renderer narration, use compact grouped controls, and route explanations to Help | Constitution §3.6 and More/Display routes | S17 + visible-copy denylist |
| `R2-ALL` | Stable slots, icon/state hierarchy, no `·`, long-title fallback, fewer unnecessary routes | Constitution §3.6/§5/§20 | S1/S5/S14/S15; A/B boards |
| `M-ALL`, `L-ALL` | No useless whitespace or missing app chrome; canonical E-ink evidence includes real all-grayscale system bars | Handoff §1.1 global; Constitution §12.1 | S14; full-frame `R=G=B`; real API 29 bars |

Coverage is stop-ship: adding or discovering a non-empty review entry requires assigning a new stable ID and extending this table plus `UI_ATLAS.md` before prototype work. A generic route screenshot is insufficient when the mapped assertion is interactive.

## 2. 用户已确认的 RC2.1 决定

### 2.1 Atlas 决策器

- 常规手机 Standard 与 E-ink 书架均为固定三列网格；宽屏按 150dp 最小卡宽自适应且不设任意列数上限；double-compact 仅在可读/触控下限无法满足时降为两列。
- 空态：克制单色颜文字，但必须有真实空态内容、原因和恢复动作；决策样图不得是无法理解的空白页。
- Standard 单项操作：保留可见操作，同时允许 swipe 快捷；E-ink 不启用 swipe。
- 创建动作：只保留 Library AppBar `+`；不使用末页创建卡、常驻 FAB 或说明文字。

### 2.2 收藏夹澄清（2026-08-13）

- 书籍和收藏夹在书架根内容流中同级；不再要求先进入“收藏夹管理”才能打开收藏夹。
- 默认使用节点置顶的规则排序；用户可切完全混排或独立“手动排列”模式。手动排列时 Standard 拖放，E-ink 使用上移/下移等按钮。
- `继续阅读 / 最近阅读 / 稍后再读 / 休眠来源 / 追更` 默认创建为系统虚拟收藏夹项，可隐藏并从创建页重建；规则定义不可编辑。仅 `稍后再读` 接受显式手动 membership，其他节点由规则自动计算。
- 普通收藏夹最多两级：根收藏夹 → 子收藏夹。再建下级时提示移动到根或现有子级，不形成无限文件树。
- 网站镜像允许“仅本地可见”的整理子收藏夹，但默认只显示 `网站结构`；用户从镜像页显式创建本地整理后才显示 `本地整理` 分区。本地节点有明确本地图标/说明，永不伪装成远端文件夹，永不触发网站写入。

### 2.3 继续有效的产品约束

- Compose + Material 3 是唯一组件语言；Standard 委托真实 M3 控件。
- E-ink 是全局 profile；固定浅色、高对比、显式分页、即时状态替换。
- Library 不常驻 tag 或来源文字；来源只在混合搜索/浏览确需消歧时紧凑出现。
- 无账号、云同步、遥测、远程 feature flag 或 Google Play Services 前置。
- 核心任务不能只靠长按、swipe、overflow 或颜色。

## 3. 参考不是名单：逐项深挖后的可执行借鉴

### 3.1 Flutter Hikari：领域需求与用户肌肉记忆

从源码而不是截图提取：

- `BookshelfPage` 根层级直接展示 folder，folder 内再展示 child folder + book；根和 folder 都可切换 grid/list。这个模型比 RC2 的独立收藏夹管理页更接近用户需求。
- folder 有封面、书籍数、新内容/更新、排序、同步、批量管理和新建子 folder；但旧实现混用了 FAB、Snackbar、透明 overlay 和过多菜单。Tsuyomi 采用信息架构，不复制实现或样式。
- `BookshelfContentView` 的纯文字列表使用独立容器、标题最多 4–8 行、元数据与右侧评分分槽；说明 forum 长标题和评分确实需要稳定空间。RC2 把作者、进度、状态塞进一行是退化。
- `NovelDetailPage` 已把简介、标签、正倒序和完整目录放在同一个 `CustomScrollView`，继续阅读使用 FAB；这直接证明“详情 + 全目录”在手机上可行。Tsuyomi 应保留这一任务结构并简化视觉。
- `ReaderScrubViewportPreview` 使用与正文相同布局的独立、非交互 reader clone 覆盖在原正文区域；拖动时用户看到的是正文 viewport 本身实时变化，而不是浮动预览卡片。mounted reader 与持久 locator 在释放前不提交，释放时只提交最终目标。Tsuyomi 必须复现这个**用户可见合同**，不得把内部“preview-only”误做成额外弹窗。
- 旧 Reader 设置覆盖字号 7–48、行距、段缩进/段距、方向、tap navigation、常亮、沉浸、状态栏、音量键、双页、字体、前景/背景、背景图和四边距。Tsuyomi 可以重新分组，但不能只给三个字号和少数开关。

结论：Flutter 是**领域需求与操作成本基线**；它的代码、品牌、视觉、透明/渐变和 GPL 外部实现均不迁移。

### 3.2 Mihon：成熟 Android 阅读路径与稳定密度

源码事实：

- Library category 与书籍内容共处一个 pager；支持 list、compact grid、comfortable grid，选择状态贯穿同一内容模型。
- 更新页使用日期 group header、固定 56dp 行、小封面、标题、章节、未读圆点、书签图标和下载状态控件；不是每行重复“标记已处理”。
- 详情手机版在同一个 `LazyColumn` 中按 `信息头 → action row → 简介/tag → chapter header → 全章节` 排列；继续阅读使用随滚动收缩的 `SmallExtendedFloatingActionButton`。目录不是必需的独立 route。
- Reader 设置将有限离散值放在 `SettingsChipRow`，连续值用 slider，选择/批量使用替换 AppBar；这说明紧凑不等于把每项写成整行 switch。

Tsuyomi 采用：

- 详情与目录合一、章节稳定行、正倒序/筛选/跳转固定在章节 header。
- 更新页以更新条目为主、会话状态为次；图标表达处理中/已确认/失败，文字只作无障碍和异常解释。
- Library/Updates/Remote Library 共用三布局与选择模型。

不采用：漫画 viewer 模型、漫画专用 unread/download badge 库存、fake pull-refresh 状态、扩展生态视觉密度。

### 3.3 Book's Story：就地 Reader 设置与可调书架

源码事实：

- Reader settings 是固定高度 ModalBottomSheet，内部以 tab/pager 分成阅读、排版、颜色；当前页可改变 scrim/菜单显示，而不是从正文直接跳独立设置 route。
- Library 支持 grid/list、可调 grid size、标题 inside/below、进度和继续按钮；grid 的 below title 明确 `minLines=2/maxLines=2`，所有卡片保持同高。
- List item 使用独立选中容器色、封面、标题、进度 pill 和继续阅读 icon，密度比 RC2 compact 更高。

Tsuyomi 采用：

- Standard 阅读设置继续使用真实 M3 sheet；但快速层先呈现高频控制，高级分组再在同容器切页。
- Grid 标题区固定两行；E-ink 也必须固定 card 高度，不能让边框随标题高度变化。
- 紧凑列表可以使用低间距的 M3 container/list-item family，但必须保持 48dp 命中区。

不采用：GPL 代码/布局复制、透明 scrim 作为 E-ink 表达、彩色渐变标题 overlay。

### 3.4 KOReader：进度导航和 E-ink 不是“上一页/下一页”

用户指南明确区分：

- Skim Widget：可点进度轨、输入页码/百分比、前后章节、前后书签，并可返回打开 widget 时的位置。
- 进度轨包含章节刻度和本次会话起点；Book Map 提供全书鸟瞰、已读区、书签和批注；Page Browser 是局部页预览。
- 整书进度和当前章节进度可以切换；改变排版先局部预渲染，再完成全书重排。

Tsuyomi 第一阶段不复制专家功能，但 Reader chrome 必须直接包含：

1. 当前章节 + 全书语义位置。
2. 可拖/点的进度轨，带章节刻度。
3. 上一章、下一章、目录、返回原位置。
4. 拖动时显示 Flutter 同源 WYSIWYG 预览；释放后才提交 semantic locator。
5. E-ink 拖动可降级为离散步进/点击刻度 + 静态预览，避免连续刷新。

Book Map/Page Browser 留作后续高级工具，不阻塞基础 scrubber。

### 3.5 Readest / ReadEra / Google Play Books：书架就是组织层

- Readest 把 group/series/作者作为书架 grouping，grid 默认、list 面向大库；selection bar 直接执行 group/bulk action。
- Google Play Books 的 shelves 位于 Library，长按多选后直接 Add to shelf；shelf 内有 Add Books，不要求先经过一个“收藏夹管理中心”。
- ReadEra 强项是离线无账号、Favorites/To Read/Have Read、作者/系列/Collections 并列为大库导航维度。

Tsuyomi 推导：系统虚拟收藏夹、普通收藏夹、规则收藏夹、网站镜像都是 `LibraryNode`，在书架内容流中同级出现；管理动作从节点的 overflow/selection 进入，而不是先跳管理页。

### 3.6 Material 3 / Now in Android / Adaptive samples：结构化，不是默认留白

- M3 的价值是 roles、状态、组件语义和响应式重排，不是把所有内容放大间距。
- 高密度页可以使用 48–56dp ListItem、section container、tonal/outline 层级和固定 trailing slot；触控区与视觉留白是两件事。
- filter 必须由真实控件表达：Checkbox/Radio/Dropdown/Slider/Date/Range，而不是 `字段 · 值 · 状态` raw text。
- expanded width 只在有任务时增加 pane；不能用额外 route 替代就地展开/sheet。

### 3.7 全部参考的合同落点（31/31）

以下表不是“优点/缺点”复述；每一项都指定进入哪个 Tsuyomi surface、保留什么行为、拒绝什么，并对应下一轮证据。

| 参考 | RC2.1 可执行落点 | 明确不采用 | 证据 |
|---|---|---|---|
| Material 3 Compose | Standard 语义 wrapper 必须委托真实 AppBar/ListItem/Card/Sheet/Field/Selection 控件；所有状态有 role/label/focus | 用自制 clickable Surface 冒充 M3；默认大留白代替信息设计 | 所有 board + delegate audit |
| Reply | Expanded 的 Library→Detail list-detail；断点变化保留选择、位置和栈 | 邮件行密度/动作照搬；无任务的第二栏 | A/D wide |
| Adaptive Apps Samples | 依据运行时 window class 重排；Reader 正文限宽，Detail/目录可作 supporting pane | `isTablet` 分叉；为填空造 pane | A/D/E wide/split |
| Now in Android | token→component→route→screenshot/semantics manifest 链；稳定 Loading/Empty/Error/Offline | 推荐 feed、兴趣 onboarding 和品牌视觉 | Atlas manifest/state matrix |
| Jetcaster | Continue/SystemNode 持续阅读状态；宽屏可用 supporting pane | 播放器式常驻底栏侵占正文 | A/E |
| Jetsnack | 只保留少量领域组件 `BookNodeCard / ProgressRail / MetadataModule`，用布局形成辨识度 | 折叠 hero、复杂封面转场、装饰渐变 | A/B/D |
| Tivi | canonical Detail 的身份/状态/动作层级；state/data/UI 分离 | 引入 KMP 或复制其产品结构 | D |
| Thunderbird Android | Library mixed flow/批量选择/持久失败状态；高密度固定槽位 | 邮件账户/收件箱概念和邮件级噪声 | A/C/H |
| Jetchat | Reader chrome/辅助容器按需出现，关闭恢复焦点和 locator | 把 Reader 变成输入面板或气泡布局 | E/F |
| JetLagged | 章节刻度轨、下载/更新进度使用低成本线条/块 | 装饰性 Canvas 动画和颜色唯一语义 | E/H |
| JetNews | Detail 标题/作者/进度/正文层级；Filter 分组结构 | 文章 feed 直接套为书架 | D/G |
| Element X | stable key、长文本/RTL、列表错误和可恢复 loading | AGPL 代码、聊天 IA 或品牌资源 | B/G/H |
| Tusky | 时间线式分页/离线/未读状态的可见性 | 连续刷新、账号切换和社交密度 | H |
| AntennaPod | 下载/解析队列、持久失败原因、批量结果 | 播放器控制常驻 Reader；GPL 实现 | H/data states |
| AndroidX M3 Catalog | 每个 semantic component 覆盖 enabled/disabled/error/selected/Fs2/E-ink | 只截默认状态 | component evidence + all boards |
| NIA Figma Case Study | token/组件/布局/源码证据可追溯 | Google/NIA 颜色、图标、布局资产 | manifest hashes |
| Mihon | Detail+章节连续 surface；Updates 日期组/56dp 行/图标状态；三布局/SelectionAppBar/紧凑设置控件 | 漫画 viewer、漫画 badge 库存和扩展视觉密度 | C/D/H |
| Tachiyomi archive | Library→Browse→Detail→Reader 任务链作为历史兼容认知 | 已归档 API/生态假设 | navigation scenes |
| KOReader | 章节刻度、seek/preview/return-origin；E-ink 离散导航和重绘克制 | 200+ 动作、深层专家菜单、AGPL 实现 | E/F |
| Readest | Grid 默认、list 面向大库、group/series 组织、批量归组 | 云同步/桌面 Web IA 前置 | A/C |
| Librera | 完整边距/排版需求清单、目录/OPDS 行为研究 | 文件管理器成为根导航、GPL 实现 | F |
| Book's Story | Reader 内 sheet→同容器高级页；固定两行 grid card；明确选中容器 | GPL 布局/主题、透明 E-ink、渐变 overlay | B/C/F |
| Foliate | 低干扰 Reader、目录/搜索/批注统一辅助容器、导航历史 | GTK 结构、AGPL/GPL 实现、隐藏入口 | E |
| Kavita | Library/Series/Book 层级、智能筛选、Reading List 大库组织 | 服务端/账户作为本地阅读前置 | A/G |
| Komga | Collection/Read-list 层级、离线状态、OPDS 能力边界 | Web reader 实现和漫画优先 UI | A/H |
| Kindle | 主题/排版入口、词典/高亮任务位置与同步失败可见性研究 | 商店首页、账户前置、品牌/动效 | E/F |
| Google Play Books | Shelves 位于 Library；多选后直接加入 shelf；Contents 统一章节/书签/笔记 | 商店/云前置和品牌 UI | A/C/E |
| Apple Books | Collections、完整 typography、Line Guide、返回原位置 | iCloud 前置、卷页/品牌视觉 | E/F |
| Moon+ Reader | 高级 Reader 设置需求上限，用分组渐进披露 | 自动滚动、手势唯一入口、专家默认 | F |
| ReadEra | 无账号离线；Favorites/To Read/Have Read/作者/系列/Collection 并列组织 | 不透明闭源视觉、自动扫描无界耗电 | A |
| Panels | 在线/本地/已下载状态与整系列批量操作 | 漫画 panel-by-panel、彩色滤镜和双页默认 | H |

Hikari Flutter 不计入上述 31 个清单项，但作为迁移需求基线单列：folder 同流、详情全目录、Reader WYSIWYG seek session 和完整设置范围进入 A/D/E/F；品牌、签名、代码、透明 overlay、渐变和旧 FAB/menu 混用全部拒绝。

## 4. RC2.1 跨页面视觉语法

### 4.1 禁止 `·` 元数据流水账

每个列表项固定四个槽位：

1. `identity`：标题，最高对比。
2. `byline`：作者/系列，较低对比；最多一行。
3. `progress`：章节或百分比，使用进度色/图标；不与作者混排。
4. `state/action`：封面 badge 或固定 trailing；不再追加到同一字符串。

若无某槽位，其他文字可以扩展，但 trailing 评分区域保留测量位置；无评分时视觉透明且不阻挡正文宽度。

### 4.2 状态词汇

- 已读完：封面右下 `check_circle`/E-ink 实心勾，不写入元数据行。
- 有更新：封面左上数字 badge；E-ink 白底黑框。
- 来源休眠：封面边缘 `pause_circle`/冻结图标；点击或 TalkBack 才读完整原因。
- 已下载：download-done 图标。
- 正在处理：Standard 可用 determinate/indeterminate progress indicator；E-ink 使用静态“处理中”图标并仅在刷新帧变化。
- 已确认处理：check icon；页面标题/会话摘要解释一次，不在每行重复动词。
- 选中：Standard secondaryContainer + 左上勾；E-ink 反相/粗边框 + 明确大勾，绝不只改变灰度背景。

关键失败、离线、不可用仍有文字；图标/颜色不能成为唯一语义。

### 4.3 无封面 fallback

- 与真实封面共享完整 3:4 区域，不使用小图标占位。
- 背景使用稳定的 source-neutral tonal seed；E-ink 使用黑白边界。
- 标题在 fallback 内占主要区域：compact grid 最多 4 行，comfortable/dense cover 最多 6 行；使用按字数和 CJK 测量选择的 `titleSmall/bodyMedium`，不缩小至不可读。
- 可选显示作者短名；不显示来源品牌、tag 或伪封面插画。
- footer 外部仍保留固定两行正式标题，保证卡片同高；fallback 内标题负责视觉识别，外部标题负责完整语义。

## 5. 页面重构合同

### 5.1 书架

- Standard/E-ink 常规手机默认固定三列 grid，宽屏按 150dp 最小卡宽自适应且不设任意列数上限；用户可切 dense-cover list / compact-text list。double-compact 仅在触控与可读下限无法满足时降为两列；来源 layout hint 一律忽略。
- 内容流元素是 `BookNode | CollectionNode | SystemNode | MirrorNode`。默认 collection/system/mirror 置顶；可切完全混排或独立手动排列。Standard 手动模式支持拖放，E-ink 用移动按钮实现同结果。
- 系统节点默认创建、可隐藏、可在创建页重建：继续阅读、最近阅读、稍后再读、休眠来源、追更。定义不可编辑；仅稍后再读允许手动 membership，其他节点由规则自动计算。
- 快捷书架所有 item 使用同一固定外框高度、内容高度、标题基线和 supporting slot。Standard 使用横向滑动和拖放，并通过末端露出下一项或等价位置提示保证可发现；E-ink 不使用横滑，改为明确的上一组/下一组和移动按钮。
- grid 每格固定：3:4 visual + 固定两行标题 + 固定一行状态；常规手机固定三列。E-ink 同页所有边框等高。
- compact-text 使用 48–56dp M3-backed outlined/tonal container：标题一至两行、独立进度图标/值、右侧评分槽；行间 0–2dp，不使用 8–16dp 卡片间距。
- 截图内只呈现真实产品 UI；方案名称、场景和验收说明全部位于 reviewer 图片下方。

### 5.2 收藏夹与镜像

- 删除独立 `library/collections` 浏览入口和 `library/collections/templates` route。
- 点击书架内收藏夹即进入其内容；顶部面包屑最多两级，子收藏夹和书籍继续同级显示，复用三布局。
- 新建普通/规则收藏夹、重建系统节点：仅 Library AppBar `+` 进入创建流程；规则收藏夹有明确首次引导。Standard 把一本书拖到另一本书上时先命名/确认，确认后两本书原子加入新普通收藏夹；E-ink 提供选择+按钮等效路径。
- 规则 editor：本地数据库已有集合全部用 picker/dropdown：本地标签、来源标签、作者、来源、阅读状态、评分、收藏夹、存在原因；只有自由文本/数值范围使用 text/range field。
- 镜像页默认只显示 `网站结构（只读/可远端操作）`；用户显式执行镜像页 `新建本地整理` 后才出现 `本地整理（只影响本机）`。任何操作确认文案明确作用域。

### 5.3 历史与追更

- 历史 AppBar 提供清空；最近一周用相对时间，超过 7×24h 显示 `yyyy-MM-dd HH:mm`。
- 追更借 Mihon 的日期分组、56dp 稳定行和图标状态；保留 Tsuyomi 的会话摘要、exact anchor、失败持久报告。
- 追更支持三布局；grid/compact 在书多时生效。`确认已看过` 或 check icon 替代“标记已处理”；Standard 处理中使用短时 M3 progress indicator，E-ink 使用静态工作 glyph；关键成功、失败、部分完成保留短文字。

### 5.4 详情与目录

- 详情和目录合并为一个连续 surface；删除常规独立 directory route。深链目录定位到详情中的章节 section。
- 可见元素白名单：封面、标题、作者、阅读进度、五颗本地评分星、紧凑 tag/动态动作容器、简介、完整目录、一个多功能继续阅读 FAB。无任务证据不得增加第二套 metadata、来源名或帮助说明。
- 阅读进度下直接放置五颗可点/可清除星，不加“评分/仅本地”等常驻标题，不保留多余垂直空白；无障碍名称承载本地属性。
- tag/动态动作容器采用紧凑 split 结构：左侧占剩余宽度，显示 1–2 行 tag 与添加标签；Standard 横向滑动，E-ink 用左右按钮换组；右侧固定一个与容器等高的大号“稍后再读”半按钮。来源特有 text/button 只在确有能力时复用左侧动态区，不重复来源名，不新增独立来源带。
- 缓存是清晰可见的一级动作，优先进入顶栏；动作预算冲突时进入章节筛选/排序工具栏，但从页面顶部即可访问。
- 章节 header 使用标准语义图标：筛选固定为漏斗；正/倒序固定为上下方向箭头并带 contentDescription；跳章保持可见。目录可折叠到当前章节附近，但“显示全部”仍在当前页面完成。
- 详情只使用一个动态多功能 `SmallExtendedFAB`：持续下翻时显示快速到底，持续上翻时显示快速到顶；停止操作一段时间恢复继续阅读。状态由近期真实滚动方向决定，不按屏幕半区猜测。

### 5.5 Reader

- 正文中心点按只切换 chrome，不直接打开 sheet。
- 顶栏：返回、章节标题、目录/搜索/书签、阅读设置。
- 底栏第一层直接呈现：上一章、带章节刻度的进度轨、当前位置、下一章。
- Standard 拖动进度轨时，正文 viewport 原地切换为同排版、同尺寸的 WYSIWYG preview clone，并跟随目标实时更新；不得出现浮动卡片、dialog、sheet 或独立预览窗口。mounted reader 和持久 locator 在按住期间不提交；取消恢复原正文；释放只提交最后一个 semantic locator；提交后显示返回原位置。
- E-ink 不连续拖动，使用刻度/前后按钮离散选择；每次选择以整页静态正文 viewport 预览，确认后一次提交，保留返回原位置。
- 快速设置不显示作用域。Standard 使用同一 M3 sheet 的部分/全高两态：部分态下拉、scrim 或 Back 关闭，上拉吸附全高；全高态下拉直接关闭；始终有 drag handle，无标题/关闭行。部分态首屏完整显示字号、行距、边距、段距，label、当前值和 slider/步进按钮同行；调整立即预览并保存。完整设置仍在同一容器按 `排版 / 页面 / 导航 / 设备` 切换。

### 5.6 搜索与规则筛选

- 搜索采用单一显式提交模型：输入只更新 query draft，不刷新结果、不访问本地仓库或网络；按搜索键后，同一 session 同时启动本地搜索与所选在线来源搜索。不得要求用户先搜本地再第二次确认在线。
- 常态页面可见元素限于搜索 field/按钮、来源选择、布局动作、一个总进度指示和统一结果流。不得常驻显示操作教程、local-first 解释、exact identity 解释、来源调度说明或逐来源正常状态文字；这些内容只进入帮助页或无障碍描述。
- 同一 exact `BookIdentity` 在内部合并；不同 identity 即使同名仍分开。单来源失败不得删除已返回项；失败以紧凑图标或按需详情呈现，不展开正常来源状态流水账。
- 高级公共/本地/来源专属筛选以及 D33 descriptor UI 在 Atlas 与 Gate 4A 均暂缓；当前搜索结果仍支持三布局，选中摘要使用独立 label/value，不用 `·` 串。

### 5.7 Updates、Remote Library、Tags、Browse、Help

- Updates 三布局共享任务信息下限：每个可见条目必须显示可识别标题、章节/更新状态和主要动作。Standard 单项处理中使用短时 M3 progress indicator，E-ink 使用静态工作 glyph；E-ink 的列数、卡片宽度和字号由内容可读性决定，不得把信息压成不可辨认碎片。
- Remote Library 支持三布局、显式全选/多选、`全部复制到…` 选择本地收藏夹；顶栏必须有可见拉取/同步动作。E-ink 增加条目纵向间距，标题在可读下限内自适应字号和宽度并允许换行，不为封面比例牺牲标题。
- Tags 使用 `本地 / 来源` tab；各 tab 支持 compact list/grid、搜索、排序和直接可见操作。页面不显示能力教学句，不使用 `·` 串；来源标签只读由控件状态/无障碍语义表达。
- Browse 的来源 item 统一 M3 ListItem/Card family；按钮层级依据主/次任务统一，不混用无理由的 filled/text/outlined。
- Help 顶部本地搜索；topic 使用 accordion。非 Help 页面不得把设计解释或内部数据规则常驻显示给用户。

## 6. 下一版 Atlas 证据

RC2.1-3 的 17 张图与绑定审阅保留为 rejected evidence，不在其文件上覆盖。下一版只在 §1.1 验收账本全部闭合后生成，至少证明：

1. Library 常规手机固定三列、宽屏 150dp 最小卡宽自适应、快捷书架横向可发现性、E-ink 按钮分页、AppBar-only 创建/手动排列与严格等高。
2. Detail 元素白名单、五星评分、tag/split 动态容器、缓存一级动作、标准筛选/排序图标和方向驱动动态 FAB。
3. Reader 本轮仅证明 route/chrome/settings；seek-preview visual approval 不使用 emulator still，等待实体设备。
4. Search 单次提交并发本地/在线、单一进度、exact identity 去重、零常驻教学说明和零高级筛选 UI。
5. Updates 的 profile-specific 处理中状态与 E-ink 内容可读下限；Remote 的间距/完整标题/刷新与全部复制；Tags 的零计数、零教学说明和零 `·` 串。
6. Reader settings 初始部分 sheet 完整显示字号、行距、边距、段距；证明部分/全高状态机、无重复标题/关闭按钮和同行紧凑 controls。
7. E-ink 整张 PNG（含真实系统栏）通过灰阶像素检查。
8. 所有场景、默认和验收说明只在 reviewer 图片下方，截图内部仅为真实产品 UI。

## 7. 参考使用规则

- Apache/MIT 项目可以研究实现，但 Tsuyomi 仍按自身模块/语义 API 重写。
- GPL/AGPL/商业项目仅研究公开行为和信息架构，不复制代码、布局常量、图标、资产、品牌色或文案。
- “不照搬”不代表不深入；每个引用必须落到一个具体合同、fixture 或明确拒绝项，否则从文档删除。
- 参考发生冲突时优先级：用户审阅决定 → Tsuyomi 本地优先/E-ink/安全边界 → Android/M3/无障碍规范 → 竞品模式。

## 8. 当前授权边界

本文件授权重新设计 fixture-only `:prototype:ui-atlas` 和同步设计文档；**不授权生产 Gate 4 提取、提交、推送、PR 或合并**。收藏夹模型已明确，不再需要为上述四项重复询问；若实现中出现新的远端写入语义、删除语义或数据迁移选择，再单独询问。
