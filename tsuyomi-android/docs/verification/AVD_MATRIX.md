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

SDK package 和 emulator 的实际 revision必须记录在 `docs/gates/GATE_N.md`；升级 revision 会使运行期证据失效并要求重跑。

## AVD 配方

| 名称 | portrait physical size | density | RAM | graphics | 用途 |
|---|---:|---:|---:|---|---|
| `Tsuyomi_API29` | `1080×2400` | `420 dpi` | `1536 MB` | software/auto, no device frame dependency | 标准手机 |
| `Tsuyomi_EInk_API29` | `1264×1680` | `240 dpi` | `1536 MB` | software/auto, no vendor waveform claim | E-ink 几何与交互模拟 |

E-ink AVD 只证明 Android/Compose profile 行为，不证明实体面板 ghosting、waveform 或全刷能力。发布 E-ink 声明仍需物理设备证据。

## 不可替代的竖屏基线

每次 Gate 或 PR 的运行期 AVD 验收必须在同一目标 head 上分别完成并记录以下两次独立验证：

| 验证 | AVD | 物理分辨率与方向 | profile | 最低证据 |
|---|---|---|---|---|
| 标准手机竖屏 | `Tsuyomi_API29` | `1080×2400` portrait | forced Standard | 受影响用户流完成；记录 `wm size`、`wm density`、方向、`font_scale` 和至少一张截图 SHA-256 |
| E-ink 竖屏 | `Tsuyomi_EInk_API29` | `1264×1680` portrait | forced E-ink | 同一受影响用户流完成；记录 `wm size`、`wm density`、方向、`font_scale` 和至少一张截图 SHA-256 |

两条记录必须分别列入 `docs/gates/GATE_N.md`，不能用同一 AVD、profile 切换、Layoutlib golden、横屏、分屏或另一种分辨率替代。横屏与分屏仍是附加必测窗口；它们不抵扣上述两次竖屏验收。缺少任一竖屏基线即阻塞 Gate/PR 准入。

用 `tools/avd/Create-GateAvds.ps1` 创建；脚本只读取 `ANDROID_SDK_ROOT`/`ANDROID_HOME`，不写入用户路径到仓库。

## 每次验收矩阵

每个 AVD 都执行下列附加矩阵；其中 portrait 结果必须满足上一节的两条独立证据记录：

1. portrait 与 landscape，且 landscape 不得替代 portrait；
2. `font_scale = 1.0` 与 `2.0`；
3. touch、TalkBack 语义检查、键盘/DPAD 焦点；
4. standard、手动 E-ink、auto/fallback 的适用状态；
5. clean install、进程重建、应用重启后的持久化；
6. route、滚动、焦点和可恢复失败状态；
7. 无裁切、重叠、不可达操作、残留焦点或无效选项。
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

执行后把命令、结果摘要和截图 SHA-256 写入 Gate evidence；标准手机竖屏和 E-ink 竖屏必须使用独立小节或表格行。`build/acceptance` 只作本地暂存。
