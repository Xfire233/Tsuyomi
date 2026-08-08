<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Gate 1 UI/UX 设计规格

状态：设计评审已批准并完成实施；任何行为性偏离必须重新审阅。

依据：

- `docs/adr/0010-tsuyomi-ink-design-system.md`
- `docs/adr/0014-global-eink-display-profile.md`
- `docs/architecture/EINK.md`
- `docs/architecture/GATES_0_3.md`
- `docs/architecture/MODULES.md`
- `docs/design/OPTION_APPLICABILITY.md`

## 1. 范围

Gate 1 只交付可安装的 Android 应用壳、全局显示配置、通用语义组件与可执行回归基线。

用户可见内容：

- 单 Activity、单一 `NavHost`。
- 三个顶层目的地：`书架`、`浏览`、`更多`。
- `书架`：诚实空态；唯一动作“前往浏览”只执行顶层路由切换。
- `浏览`：静态说明“尚无可用内容源”；不显示安装、搜索或帮助入口。
- `更多`：只显示可真实使用的“设置”和“关于”。
- `设置 > 显示`：所有可见选项必须接入真实 `core/display` 行为，并遵循“只展示当前实际生效或必须解释的保存值”规则。
- `关于`：应用名、版本、许可证声明；不得暗示尚未实现的许可证浏览入口或其他功能。

不在 Gate 1：

- 扩展安装、Wenku8 搜索/详情/目录/章节/阅读。
- 书架管理、备份、恢复、迁移。
- TextField、封面、源品牌图标、Reader 页面、业务假数据。
- 任何不可点击、空响应或标为“即将推出”的伪入口。

`PaginationBar` 只通过组件 preview 与测试宿主验证，不增加用户可见的假长列表。

## 2. 信息架构与导航

### 2.1 根结构

```text
TsuyomiActivity
└── DisplayEnvironmentProvider
    └── TsuyomiTheme
        └── AppScaffold
            ├── FixedTopBar
            ├── Single NavHost
            └── NavigationBar | NavigationRail
```

- 固定 TopBar；不折叠、不随滚动变化。
- 顶层目的地切换不叠加返回栈。
- 子页提供显式返回；系统返回、TalkBack 返回与键盘返回语义一致。
- profile、主题、窗口断点变化不创建第二套路由或第二份 screen state。

### 2.2 自适应规则

只依据运行时可用窗口，不依据设备型号或“手机/平板”判断：

- `width < 600dp` 且 `height >= 480dp`：底部 `NavigationBar`。
- `width >= 600dp`：侧边 `NavigationRail`。
- `height < 480dp` 且 `width >= 480dp`：侧边 `NavigationRail`，避免紧凑高度被底栏继续压缩。
- `width < 480dp` 且 `height < 480dp`：使用底部 `NavigationBar` 的紧凑变体；标签保留、TopBar 动作收敛为最多一个，内容允许纵向滚动。此规则是双紧凑窗口的确定性兜底。
- 窗口运行期跨断点时，保留当前 route、各顶层返回栈、滚动/分页状态与可恢复焦点标识。
- 设置内容最大宽度 560dp；宽窗居中，不制造第二栏伪内容。

## 3. 显示状态模型

### 3.1 持久偏好

`DisplayPreferences` 仅存：

- `displayPreference`: `auto | standard | eInk`
- `colorSchemePreference`: `system | light | dark`
- `dynamicColorEnabled`: Boolean

设备分类、effective 值、重绘 epoch 不持久化。

### 3.2 生效优先级

```text
manual standard/eInk
  > auto + local classifier
  > unknown device fallback standard
```

`DisplayEnvironment` 必须同时公开：

- persisted preference
- effective profile
- classification result 与可读原因
- effective color scheme
- dynamic-color eligibility 与 effective 值
- motion policy
- refresh policy
- monotonic redraw epoch

颜色决策：

1. `effectiveProfile == eInk`：固定 paper/ink 高对比浅色方案。
2. 动态色控件的 `checked` 永远显示持久值 `dynamicColorEnabled`，不显示 effective 值。
3. 仅当 `effectiveProfile == standard` 且 API 31+ 时动态色控件可操作；操作立即写入持久值。
4. E-ink 生效时控件禁用，summary 为“墨水屏模式不使用系统颜色；返回标准后恢复此偏好”；不得改写持久值。
5. API 29/30 下控件禁用，summary 为“Android 12 及以上可用；当前偏好会保留”；不得改写持久值。
6. 深色偏好继续持久化；E-ink 生效时控件禁用并说明“墨水屏模式固定使用高对比浅色”。
7. 返回 standard 后恢复先前的 `system | light | dark` 与 `dynamicColorEnabled` 偏好。
8. standard 且 API 31+、用户开启动态色：使用系统动态色。
9. 其他 standard 状态：使用 Tsuyomi Ink 静态 light/dark 色板。

Motion 决策：

```text
eInk OR system reduced-motion → Instant
otherwise → Standard
```
- system reduced-motion 的可观察信号固定为 Compose `MotionDurationScale.scaleFactor == 0f`；测试通过注入该值，不读取私有系统设置。

Refresh 决策：

- Gate 1 不提供逻辑刷新策略 selector；在调度器存在可观察消费差异前不得持久化或展示策略枚举。
- “墨水屏刷新”section 与“立即重绘界面”只在 `effectiveProfile == eInk` 时出现；standard 下隐藏。
- “立即重绘界面”调用真实根 surface redraw request：递增 redraw epoch 并使稳定根绘制层失效。
- UI 明确说明它只请求应用界面重绘，不控制厂商 waveform 或硬件全刷。

## 4. 模块归属

- `core/display`
  - preference model 与 DataStore adapter
  - local classifier 与 resolver
  - `DisplayEnvironment`
  - root provider / CompositionLocal
  - `MotionPolicy`
  - refresh coordinator 与 redraw request
- `core/ui`
  - 只消费 `DisplayEnvironment`
  - semantic tokens、theme adapter、无业务状态组件
  - 不读取 DataStore、`Build.MODEL`、NavController 或 feature state
- `app`
  - Activity、根注入、唯一 NavHost、顶层目的地与窗口断点协调
- `feature/*`
  - 各自 `UiState`、业务文案与 handler
  - 不分叉 standard/E-ink composable tree

依赖方向必须保持 `app → feature/core`，`feature → core/shared`，`core → shared`。

## 5. 页面状态

每屏唯一主状态：

```text
Loading → Content | Empty | Error
```

`Offline` 与 `Refreshing` 是叠加状态，不覆盖已有内容。

- Loading：静态、稳定几何；standard 可用静态块，E-ink 使用预留空间和“正在加载…”文本。禁止无限 spinner。
- Empty：说明原因；动作可选，只有真实 handler 才出现。
- Error：可读原因与显式“重试”。不得透传堆栈。
- Offline：顶部持久 banner；有缓存时保留内容，无缓存时使用离线空态。
- 保存失败、切换失败、确认请求：进入可恢复 `UiState`，包含稳定 id 与 acknowledgement action。
- one-shot effect 只用于不要求进程恢复的外部副作用，不承载重要反馈。

## 6. 视觉系统

### 6.1 Standard

- Light：暖纸背景 `#FAF8F3`，surface `#FDFCF9`，墨青 primary `#2E4A56`。
- Dark：background `#151A1C`，surface `#1C2225`，primary `#A9C6D2`。
- 可用不透明 tonal surface、受控 elevation 与克制动效。
- 不在 standard 使用纯 `#000000` / `#FFFFFF` 作为应用 chrome。

### 6.2 E-ink

- `ink #000000`、`n90 #1A1A1A`、`n70 #4D4D4D`、`n50 #808080`、`n30 #B3B3B3`、`paper #FFFFFF`。
- 次级文字使用不透明 `n70`；禁用态使用 `n50` 并同时提供边框/文字冗余。
- 禁止 alpha-dependent distinction、渐变、透明层、blur、阴影、背景图、ripple、crossfade、尺寸/可见性动画、折叠栏、overscroll glow、下拉刷新、无限 spinner 和无限滚动。

### 6.3 字体

系统默认 CJK sans-serif；只用 Regular 400 / Medium 500：

| Token | 字号 / 行高 |
| --- | --- |
| Display | 28sp / 42sp |
| Headline | 22sp / 34sp |
| Title | 18sp / 28sp |
| Body | 16sp / 24sp |
| Label | 14sp / 22sp |
| Caption | 12sp / 18sp |

CJK 行高不低于字号 1.5 倍；禁用斜体 CJK 与 Light 字重。

### 6.4 几何

- spacing 基础单位 4dp。
- 触控目标至少 48×48dp。
- 焦点环 2dp，不能只用颜色表达。
- Navigation label 不超过 4 个汉字。
- 字体放大时行高可增长，禁止固定高度截断。

## 7. 动效与反馈

根级 `MotionPolicy` 统一控制 indication、navigation selection、switch/selector、dialog、状态切换与 progress。

Standard：

- 只允许 150–250ms、ease-out 的短过渡。
- 不使用弹性、视差、闪烁、shimmer 或超过 300ms 的动画。

Instant（E-ink / reduced-motion）：

- 状态瞬时替换，不产生中间帧。
- 按压使用即时、不透明 tonal/fill 反馈；禁止 ripple 扩散或淡出。
- 禁止 `AnimatedContent`、`AnimatedVisibility`、`animateContentSize`、无限 progress、默认 Switch 滑动、默认 Navigation 指示器动画与 Dialog dim 泄漏。
- 禁用 overscroll effect。

## 8. 组件契约

Gate 1 必需组件：

- `TsuyomiTheme`
- `AppScaffold`
- `TsuyomiTopBar`
- adaptive `TsuyomiNavigation`
- `TsuyomiButton`
- `SettingsRow`
- `SegmentedSelector`
- `InlineStatus` / `InfoBanner`
- `StateView`
- `TsuyomiDialog`
- `PaginationBar`

### 8.1 E-ink Dialog

- API 29 E-ink 下系统栏使用不透明 paper/ink 映射，并关闭平台自动导航栏对比 scrim；手势与三键导航都不得重新引入半透明层。
- 使用完全不透明 paper 全窗口模态 surface；不使用 scrim、window dim、alpha、blur 或阴影。
- 背景不可点击并从可访问性语义中隔离。
- 焦点进入模态后限制在模态内；关闭后恢复触发控件焦点。
- 信息对话框允许返回键和显式关闭。
- 破坏性确认不得通过外部点击关闭；必须显式确认或取消。
- standard 可使用不透明内容 surface + scrim，但共享同一 title/body/action 语义模型。

### 8.2 自定义语义

- `SegmentedSelector`：collection info、每段 role、selected、stateDescription、disabled/error。
- `SettingsRow`：button/switch/radio role，checked/selected，summary 与 disabled reason。
- `PaginationBar`：集合语义；读出“第 x 页，共 y 页”；首末页 disabled；加载/错误为持久状态。
- `Dialog`：pane title、modal focus、按钮名称与 dismiss 规则。
- 只有纯图标按钮设置 `contentDescription`；已有可见文字的控件避免重复播报。
- 状态变化优先使用 Compose semantics/live region；不无条件调用 `announceForAccessibility`。

## 9. 图标与旧项目边界

- Gate 1 只使用已确认许可的 Material Symbols / Compose `ImageVector`。
- 不复制、导入、描摹或品牌化旧项目的 `wenku8.svg`、`yamibo.svg`、`esj.svg`。
- 后续 Gate 若需要站点品牌，必须取得官方资产与授权；否则使用通用“内容源”图标。
- 禁止参考或复制 Flutter 的布局、导航、配色、视觉层级、动效和组件实现。

## 10. 回归准入

### 10.1 唯一截图方案

使用官方 Compose Preview Screenshot Testing：

- plugin：`com.android.compose.screenshot:0.0.1-alpha11`（与冻结的 AGP 8.13.1 兼容；官方发布说明将 AGP 8.13 支持列为 alpha11）
- Kotlin：2.3.0
- Compose BOM：2026.06.01
- JDK toolchain：17
- compile/renderer API：36
- locale：`zh-rCN`
- 字体：Android Layoutlib `sans-serif`，不读取宿主字体
- density：固定在完整 `device` spec 中，不使用工具默认值
- 动画时钟：所有 golden 使用 `Instant`；standard motion 另做时钟行为测试
- image difference threshold：`0.0001f`（0.01%）
- reference：`{module}/src/screenshotTestDebug/reference`
- 基线只由 reviewer 批准的变更更新

动态色 golden 不读取宿主壁纸；production API 31+ 使用系统动态色，测试通过注入固定 seed 的 deterministic color scheme 验证 light/dark。
- Golden 必须渲染生产 composable 与真实字符串资源：feature screen 的 reference 归对应 `feature/*`，语义组件归 `core/ui`；禁止复制 screen 内容到测试专用 composable。
- app 导航/生命周期行为由 instrumentation 与 AVD smoke 证明，不用无法稳定启动的 app-module Layoutlib renderer 伪造覆盖。

### 10.2 Golden 窗口

- 360×800dp compact portrait：`spec:width=360dp,height=800dp,dpi=420`
- 800×360dp compact-height landscape：`spec:width=800dp,height=360dp,dpi=420`
- 360×320dp double-compact fallback：`spec:width=360dp,height=320dp,dpi=420`
- 599×800dp breakpoint-below：`spec:width=599dp,height=800dp,dpi=320`
- 600×800dp breakpoint-at：`spec:width=600dp,height=800dp,dpi=320`
- 840×900dp expanded：`spec:width=840dp,height=900dp,dpi=240`
- fontScale：1.0、1.3、2.0

运行期标准手机 AVD 固定为 API 29、物理分辨率 1080×2400（面板规格通常写作 2400×1080）、dpi 420；横屏旋转后为 2400×1080。E-ink 验收 AVD 固定为物理分辨率 1264×1680（面板规格 1680×1264），并单独执行 portrait/landscape、字体缩放、导航和布局溢出检查。两套运行期基线用于真实安装与交互验收，不替代上述覆盖断点的 Layoutlib golden 矩阵。

### 10.3 Golden 场景

- 三个根路由、更多列表、显示设置、关于。
- standard-light、standard-dark、fixed-seed dynamic-light/dark、E-ink。
- auto unknown→standard、auto recognized→E-ink、manual standard、manual E-ink。
- E-ink 返回 standard 后恢复主题偏好。
- Loading、Empty、Error、Offline。
- PaginationBar 首/中/末页。
- button、settings row、selector 的 focus/disabled/error。
- 无 scrim E-ink 模态。
- 导航选中态。
- profile 切换前后的静态视觉分别进入 golden；状态保持本身由下一节的交互测试证明。

### 10.4 非截图验证

- JVM：classifier/resolver、状态优先级、动态色 persisted/effective 组合、偏好恢复、refresh epoch、MotionPolicy。
- Compose semantics：role、selected/checked、stateDescription、集合与 live region。
- DPAD：全路径、模态焦点限制与关闭后恢复。
- 运行期 resize：跨 599/600dp、compact-height 与 360×320dp 双紧凑规则，保持 route/state/focus。
- profile-switch 交互序列：进入指定 route，设置 LazyList scroll、PaginationBar page 与控件焦点；切换 profile；等待 Compose idle 与 redraw epoch visual-commit witness；断言 route、scroll、page、focus 和顶层返回栈未变。
- 模态交互：断言背景 pointer 不接收点击、背景语义不可遍历、Back/外点 dismiss 规则正确、关闭后焦点恢复。
- 动画：E-ink/reduced-motion 无中间帧；无默认 ripple/switch/nav/dialog 动效泄漏。

### 10.5 API 29 smoke

在 API 29 模拟器分别使用手势导航与三键导航：

- 启动、三根路由、设置、关于、返回。
- standard/E-ink 强制切换与进程重建后持久化。
- edge-to-edge、insets、底栏/侧栏、横屏、分屏、fontScale 2.0。
- 键盘/DPAD、TalkBack 语义树、无高版本 API 泄漏、无 GMS。

任何 E-ink 禁止项、文字截断/重叠、不可达焦点、重复语义、profile 状态丢失、假入口或非确定性基线均阻塞 Gate 1。
