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

SDK package 和 emulator 的实际 revision 必须记录在 `docs/phases/PHASE_N.md`；升级 revision 会使运行期证据失效并要求重跑。

## AVD 配方

| 名称 | portrait physical size | density | RAM | graphics | 用途 |
|---|---:|---:|---:|---|---|
| `Tsuyomi_API29` | `1080×2400` | `420 dpi` | `1536 MB` | software/auto, no device frame dependency | 标准手机 |
| `Tsuyomi_EInk_API29` | `1264×1680` | `240 dpi` | `1536 MB` | software/auto, no vendor waveform claim | E-ink 几何与交互模拟 |

E-ink AVD 只证明 Android/Compose profile 行为，不证明实体面板 ghosting、waveform 或全刷能力。发布 E-ink 声明仍需物理设备证据。

## Policy 选择的竖屏基线

每个 Phase exit/admission gate 或 PR 的运行期验收以仓库当前生效的 `.agents/skills/tsuyomi-android-review/review-policy.json` 为准，并按其 `activeProfiles` 为每个 profile 分别完成独立 portrait 验证。该版本化 policy 是项目要求；skill 只是可选读取工具，不是参与贡献的前置条件：

| Profile | AVD | 物理分辨率与方向 | 最低证据 |
|---|---|---|---|
| `STANDARD`（active 时） | `Tsuyomi_API29` | `1080×2400` portrait | 受影响用户流完成；记录 `wm size`、`wm density`、方向、`font_scale` 和至少一张截图 SHA-256 |
| `EINK`（active 时） | `Tsuyomi_EInk_API29` | `1264×1680` portrait | 同一受影响用户流完成；记录相同设备事实和截图 SHA-256；发布 E-ink 声明另需物理面板证据 |

active profile 的记录不能用另一 profile、同一 AVD 内切换、Layoutlib golden、横屏、分屏或其他分辨率替代。当前仓库 policy 若把 `EINK` 标为 `FROZEN`/deferred，日常 gate 不要求 E-ink AVD：保留实现与合同，只运行 policy 允许的 direct-change 最小检查；恢复 active 时重跑完整 retained matrix。

`tools/avd/Create-ReviewAvds.ps1` 是 Windows PowerShell 专用创建器，要求 Android SDK command-line tools（`avdmanager.bat`）、对应 API 29 x86_64 system image，并设置 `ANDROID_SDK_ROOT` 或 `ANDROID_HOME`。其他平台可按表中的已签入矩阵规格创建等价的 disposable AVD，但这些输入一致不意味着跨平台执行等价或声称 CI parity。

## 每次 active-profile 验收矩阵

每个 active profile 的 AVD 都执行适用于受影响表面的附加矩阵；portrait 结果必须满足上一节的独立证据记录：

1. portrait 与 landscape，且 landscape 不得替代 portrait；
2. `font_scale = 1.0` 与 `2.0`；
3. touch、TalkBack 语义检查、键盘/DPAD 焦点；
4. 当前 active profile 及其 auto/fallback 适用状态；
5. clean install、进程重建、应用重启后的持久化；
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

执行后把命令、结果摘要和截图 SHA-256 写入 Phase evidence；每个 active profile 使用独立小节或表格行，并记录 deferred profile 的 policy 状态与恢复触发条件。`build/acceptance` 只作本地暂存。
