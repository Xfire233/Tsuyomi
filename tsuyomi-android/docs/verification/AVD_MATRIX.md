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
- 启动：验收前 wipe data/cold boot；不得依赖 snapshot 中的应用状态

SDK package 和 emulator 的实际 revision 必须记录在 `docs/phases/PHASE_N.md`；升级 revision 会使运行期证据失效并要求重跑。

## AVD 配方

| 名称 | portrait physical size | density | RAM | graphics | 用途 |
|---|---:|---:|---:|---|---|
| `Tsuyomi_API29` | `1080×2400` | `420 dpi` | `1536 MB` | software/auto, no device frame dependency | 标准手机 |
| `Tsuyomi_EInk_API29` | `1264×1680` | `240 dpi` | `1536 MB` | software/auto, no vendor waveform claim | E-ink 几何与交互模拟 |

E-ink AVD 只证明 Android/Compose profile 行为，不证明实体面板 ghosting、waveform 或全刷能力。发布 E-ink 声明仍需物理设备证据。

## Policy 选择的竖屏基线

每个 Phase exit/admission gate 或 PR 的运行期验收读取 `.agents/skills/tsuyomi-android-review/review-policy.json`，并在同一目标 head 上为每个 `activeProfiles` 项分别完成独立 portrait 验证：

| Profile | AVD | 物理分辨率与方向 | 最低证据 |
|---|---|---|---|
| `STANDARD`（active 时） | `Tsuyomi_API29` | `1080×2400` portrait | 受影响用户流完成；记录 `wm size`、`wm density`、方向、`font_scale` 和至少一张截图 SHA-256 |
| `EINK`（active 时） | `Tsuyomi_EInk_API29` | `1264×1680` portrait | 同一受影响用户流完成；记录相同设备事实和截图 SHA-256；发布 E-ink 声明另需物理面板证据 |

active profile 的记录不能用另一 profile、同一 AVD 内切换、Layoutlib golden、横屏、分屏或其他分辨率替代。当前 policy 若把 `EINK` 标为 `FROZEN`/deferred，日常 gate 不要求 E-ink AVD：保留实现与合同，只运行 policy 允许的 direct-change 最小检查；恢复 active 时重跑完整 retained matrix。

用 `tools/avd/Create-ReviewAvds.ps1` 创建；脚本只读取 `ANDROID_SDK_ROOT`/`ANDROID_HOME`，不写入用户路径到仓库。

## 每次 active-profile 验收矩阵

每个 active profile 的 AVD 都执行适用于受影响表面的附加矩阵；portrait 结果必须满足上一节的独立证据记录：

1. portrait 与 landscape，且 landscape 不得替代 portrait；
2. `font_scale = 1.0` 与 `2.0`；
3. touch、TalkBack 语义检查、键盘/DPAD 焦点；
4. 当前 active profile 及其 auto/fallback 适用状态；
5. clean install、进程重建、应用重启后的持久化；
6. route、滚动、焦点和可恢复失败状态；
7. 无裁切、重叠、不可达操作、残留焦点或无效选项；
8. 受控 WebView：分别记录 fixture host transport、真实 declared-origin 页面、blocked navigation、完成/取消 cookie handoff。WebView 的 `ERR_CACHE_MISS`、offline、403 或错误页只证明失败恢复，不得当作手动验证成功。

执行前记录：

```text
emulator -version
sdkmanager --list_installed
adb shell wm size
adb shell wm density
adb shell settings get system font_scale
adb shell getprop ro.build.version.sdk
```

执行后把命令、结果摘要和截图 SHA-256 写入 Phase evidence；每个 active profile 使用独立小节或表格行，并记录 deferred profile 的 policy 状态与恢复触发条件。`build/acceptance` 只作本地暂存。
