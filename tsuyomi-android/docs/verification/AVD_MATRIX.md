<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Android 验证设备矩阵

## 固定工具输入

- Android API：29
- system image：`system-images;android-29;default;x86_64`
- ABI：`x86_64`
- device template：`pixel_2`（只提供基础硬件字段，显示参数由脚本覆盖）
- locale：`zh-CN`
- navigation：three-button 与 keyboard/DPAD 场景均验证
- Disposable CI AVD 与迁移证据可 wipe/cold boot 并 clean-install；不得依赖 snapshot 应用状态。此清理规则绝不适用于持久候选或 canonical 实机/覆盖安装验收：这些受保护状态必须保留同一 APK 签名身份、应用数据与获准 session，禁止 `-wipe-data`、clean-install 或清凭据 fixture setup/instrumentation。
- `tools/avd/Start-CanonicalAvd.ps1` 是 Windows PowerShell 专用的可选启动器，要求已安装 Android SDK Emulator 且设置 `ANDROID_SDK_ROOT` 或 `ANDROID_HOME`。只有在受控 declared-origin WebView 验证且已观察到主机代理/TUN 导致模拟器 DNS 故障时，才使用该启动器配置的 `-dns-server 8.8.8.8,1.1.1.1 -netdelay none -netspeed full`；DNS 修复只需重启模拟器进程，不必 cold boot。常规 Android 贡献不依赖 canonical AVD、私人网络设置或此启动器。

SDK package 和 emulator 的实际 revision 必须记录在 `docs/phases/PHASE_N.md`；升级 revision 会使受影响运行期证据失效。需要重跑的必需证据保持 `PENDING`，升级／失效本身不授予 assistant 额外执行权限。

## AVD 配方

| 名称 | portrait physical size | density | RAM | graphics | 用途 |
|---|---:|---:|---:|---|---|
| `Tsuyomi_API29` | `1080×2400` | `420 dpi` | `1536 MB` | software/auto, no device frame dependency | 标准手机 |
| `Tsuyomi_EInk_API29` | `1264×1680` | `240 dpi` | `1536 MB` | software/auto, no vendor waveform claim | E-ink 几何与交互模拟 |

E-ink AVD 只证明 Android/Compose profile 行为，不证明实体面板 ghosting、waveform 或全刷能力。发布 E-ink 声明仍需物理设备证据。

## Policy 选择的竖屏基线

运行期证据需求由 `.agents/skills/tsuyomi-android-review/review-policy.json`、UI Constitution 和 UI-R1 识别；assistant 本地执行一律先遵循 [`QUALITY_GATES.md` 的唯一授权边界](../process/QUALITY_GATES.md#assistant-local-execution-authorization)，默认只有 changed/directly coupled production-app proof 和一个指定 AVD。active、UI-R1 selection 或历史首次恢复模式均不授权增加测试、矩阵或 AVD。E-ink 当前暂停／重大 UI 触发规则由 UI Constitution 独占；无符合条件的重大 UI 变化就跳过功能验证，符合时只选获准的最小受影响路径。首次恢复完整范围及物理／人工出口延期未完成，不作为当前 Standard／本地阅读执行前置，不得记为通过。以下是获准执行后的设备记录要求；skill 不是贡献前置工具：

| Profile | AVD | 物理分辨率与方向 | 最低证据 |
|---|---|---|---|
| `STANDARD`（active 且本次获准设备证据时） | `Tsuyomi_API29` | `1080×2400` portrait | 受影响用户流完成；记录 `wm size`、`wm density`、方向、`font_scale` 和至少一张截图 SHA-256 |
| `EINK`（符合 Constitution 重大 UI 触发且本次获准设备证据时） | `Tsuyomi_EInk_API29` | `1264×1680` portrait | 完成选定受影响路径；记录设备事实和必要截图 SHA-256；E-ink readiness 声明另需物理面板证据 |

要求独立 portrait 记录的 profile，不能用另一 profile、同一 AVD 内切换、Layoutlib golden、横屏、分屏或其他分辨率冒充。一个指定 AVD 的局部通过不关闭完整 Phase／首次恢复； retained 验收／readiness 记录仍延期未完成。无重大 UI 变化时跳过的增量 E-ink 检查记不需要，不记通过。`FROZEN`/deferred profile 只保留必要且获准的 direct-change 最小检查，并仍受 Constitution 当前边界约束；首次恢复 retained matrix 不授权绕过暂停，也不成为当前 Standard／本地阅读执行前置。

`tools/avd/Create-ReviewAvds.ps1` 是 Windows PowerShell 专用创建器，要求 Android SDK command-line tools（`avdmanager.bat`）、对应 API 29 x86_64 system image，并设置 `ANDROID_SDK_ROOT` 或 `ANDROID_HOME`。表中名称是 recipe 示例，不要求创建同名设备；启动和复用既有指定 AVD 不逐次请示。只有在现有指定 AVD 之外创建编号不同的额外 AVD，才须维护者直接确认；不得以缺失设备、`-Force` 或 recipe 对齐为由删除／重建他人或持久设备。其他平台可按同一规格创建获准的 disposable AVD；输入一致不意味着跨平台执行等价或 CI parity。

## Assistant device ownership and cleanup

[`QUALITY_GATES.md`](../process/QUALITY_GATES.md#assistant-local-execution-authorization) owns authorization, preservation, cleanup and the single artifact-bound human target. For an authorized device task:

1. Record preexisting AVD names, running serial-to-name bindings, owners and the designated target before changes. Report unexpected extra AVDs; do not stop/delete them without owner confirmation. Record temporary versus explicitly retained ownership separately.
2. Bind each mapped production route/state/action and observed result to the designated AVD/profile and exact source/build/APK SHA-256, including the installed APK identity. Record prior physical/override display facts, density, orientation and font scale before an authorized scenario; restore any task-applied changes on success, failure or cancellation.
3. Preserve persistent/canonical installed APK signing, app data and authorized dedicated-account sessions. Do not wipe, clean-install or run credential-clearing fixture instrumentation there. Before handoff, leave exactly one designated retained candidate frozen with its exact installed APK, route/state and reusable session; stop automation/input and background state changes on it. A second development device is not a second human target.
4. In terminal cleanup for success, failure or cancellation, stop each task-owned temporary emulator **and delete its AVD**; record both outcomes and any cleanup blocker. Do not leave one running/stopped for potential reuse or pending human review. Retaining a candidate requires explicit owner designation; do not silently promote a disposable automation AVD. Leave other owners' persistent/canonical devices untouched.

## 已选设备证据的附加场景

以下是按变动行为和具体风险选择的证据场景，不是自动执行清单；任何增加仍按 owning gate 获准，不因为 active 就展开矩阵。E-ink 先满足 Constitution 的重大 UI 触发；首次恢复完整任务／状态及物理人工出口在暂停期间延期未完成，不要求当前 Standard／本地阅读补跑。获准 portrait 设备证据须满足上一节的独立记录：

1. portrait 与 landscape，且 landscape 不得替代 portrait；
2. `font_scale = 1.0` 与 `2.0`；
3. touch、TalkBack 语义检查、键盘/DPAD 焦点；
4. 当前 active profile 及其 auto/fallback 适用状态；
5. clean install（仅 disposable CI／迁移设备）、进程重建、应用重启后的持久化；持久候选不清数据；
6. route、滚动、焦点和可恢复失败状态；
7. 无裁切、重叠、不可达操作、残留焦点或无效选项；
8. 受控 WebView：CI 记录 fixture host transport、blocked navigation 与完成/取消 cookie handoff；若授权的工作范围包含真实 declared-origin 在线流，则必须使用获准的测试环境完成相应用户流。任何共享/持久 online review candidate 及其专用测试账号均为受限的维护者环境，不是普通贡献者前置条件；只有普通用户可见的 allowlisted WebView 登录可由授权自动化执行，挑战暂停交给人，不绕过。错误页只证明失败恢复，不能冒充成功。Disposable CI 不访问持久候选或其凭据。

CI fixture、migration、security 与 screenshot assertions 均是 planner-selected regression evidence，不形成单独 fixture walkthrough 或视觉 acceptance round。若有 opt-in fixture-retention flags，它们仅供诊断检查，不是额外验收义务。

执行前记录：

```text
emulator -version
sdkmanager --list_installed
adb shell wm size
adb shell wm density
adb shell settings get system font_scale
adb shell getprop ro.build.version.sdk
```

`wm size` 必须核对**物理**分辨率；只有 Override size 与表格一致，不能把物理尺寸不同的 AVD 当作当前 profile。窄窗测试前记录是否存在覆盖值及其原值；结束后原先无覆盖值就执行 `wm size reset`，否则恢复原覆盖值，再读取 `wm size` 并实际点击模拟器右侧和底部。不得将物理 1080×1920 的 Pixel 2 固定“恢复”为 1080×2400：ADB 注入可能正常，但模拟器窗口的键盘和按钮会出现触控死区。

执行后把 exact change→evidence mapping、命令、结果摘要、installed APK/source/build identity、必要截图 SHA-256 和所有 task-owned temporary AVD 的 stop/delete 结果写入既有 Phase／gate evidence；每个实际执行 profile 使用独立小节或表格行。另记真正不适用／deferred 的原因、policy 状态与恢复触发条件，以及未获准／未执行必需证据的 `PENDING` 状态。`build/acceptance` 只作本地暂存，不是批准来源。

## 0.5.0 真实 E-ink 登记与证据 — 2026-10-06

维护者报告设备：**掌阅iReader Ocean4 Turbo2025，7英寸黑白，portrait1264×1680**与既有E-ink几何标准一致；并明确确认**Android10+/API29+、可安装第三方APK**。这些为用户报告，不是本轮设备观测。维护者手动操作／提供证据；未选择代理接入，未授权现在安装／清数据／替换日常或canonical APK。精确Android/API／固件、density及系统刷新设置在未来获授权的实屏证据中记录。

最终补充决定：**0.5.0仅黑白，彩屏原色顺延**，取代本轮早先彩屏选择。当前完成条件不再要求彩屏物理分支；只按实际mono panel验证全局任务／灰度／图文／持续阅读／输入／IME／Dialog／WebView，并绑定exact APK/source digest和人类结果。黑白证据仍不得宣称彩屏／全厂商已验证。人工accept在实际执行前保持PENDING，profile激活／候选部署仍须Phase／明确授权。
