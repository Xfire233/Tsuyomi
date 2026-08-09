<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Gate 2 工程复盘与后续执行流程

## 交付结论

Gate 2 交付了签名 Wenku8 只读垂直切片，并在最终 PR head `8af6d44839be57080f9fc5c59c088da73e09436d` 通过全部 required checks。后续 Gate 不得把“功能实现完成”视为交付；只有计划、独立审阅、可回退实现、分层验收证据和受保护合并门禁都闭合，才算完成。

## Gate 2 复盘

| 发现 | 根因 | 源头修复 | 长期规则 |
|---|---|---|---|
| 已签名 HXP 的运行时入口仍可从另一个 ZIP 视图重选 | 验证路径与执行路径没有共享同一中央目录 entry bytes | verifier 保留中央目录验证、完整性覆盖的 entry bytes；client 只读取该副本 | 可执行包必须从认证对象直接取得运行时字节；禁止第二次 archive traversal |
| Cookie capability 只表达声明，未覆盖三条数据路径 | WebView handoff、请求发送和响应存储的策略没有同源建模 | cookie mode 与 origin 同时在 import、request、`Set-Cookie` store 强制 | capability 审查必须列举 create/import、read/send、write/store、delete 四条路径 |
| QuickJS timeout/cancel/OOM 后，以及 Activity recreate 后可能留存可用 runtime | cancellation 指向可变 handle；来源 client 没有 Compose-owner disposal | per-operation cancellation target；terminal context discard；`SourceFlowController.close()` 与 generation-safe late-open cleanup | 每个 executor/native resource 都必须声明 owner、close 时机、late completion 和下一 operation 隔离测试 |
| 资源限制扩大没有成为用户可见权限变化；extensions CI 未运行真实 fixture proof | 审批只比较 capability 名称；CI 只检查静态存在 | 六项上限进入审批/fingerprint；CI 执行 locked install、tests、双重打包、checksum、clean diff | 任意安全、成本、配额或网络放宽都视为 capability escalation；required CI 必须执行产物而不是检查文档 |
| AVD 的 `challenge` WebView 显示 `ERR_CACHE_MISS` | debug source transport 只 mock HXP host HTTP；受控 WebView 按签名 manifest 直接加载真实 `https://www.wenku8.net`，不复用 fixture/cache | 明确它是网络不可达/缓存未命中的 WebView 页面，不是 source cache 或 Cookie handoff 成功；没有真实 Cookie 时必须取消，不得点“已完成” | 验收矩阵必须分开记录 fixture-host 请求、真实 WebView 网络和手动验证 cookie handoff；错误页不构成 verification 成功证据 |

`ERR_CACHE_MISS` 在本次离线或受限 AVD 的 debug fixture 场景中符合实现边界：`Gate2SourceGateway` 不接管 `WebView.loadUrl()`，因此它无法渲染 `challenge.html` fixture。它不是安全绕过，也不表示 Host network cache 有缺陷；但它只证明受控 WebView 的失败路径，不能证明真实 Wenku8 验证可完成。真实账号、验证码和 Cookie 均不得输入该 debug AVD。

## 后续 Gate 的强制流程

```text
需求与范围
  → Planner：计划包、风险和验收矩阵
  → Designer：仅 UI/交互变更的独立审阅
  → Adviser：计划/架构/安全独立审阅
  → 执行授权
  → 可回退实现与持续验证
  → Adviser：PR 独立审阅
  → required checks
  → 用户人工确认
  → 受保护分支合并
```

### 1. Planner 计划包

实现前必须提交一个版本化计划包；它是后续所有审阅的共同输入，至少包含：

- 可观察目标、非目标、端到端主路径和失败/取消路径；
- 受影响的组件、API/schema、数据迁移、权限/capability、持久化和回退边界；
- UI 变更清单，或明确 `无 UI 变更`；
- 风险模型：安全、隐私、并发/取消、资源上限、离线、API 29、E-ink 和无障碍；
- 先写出的验收矩阵、测试层次、真实设备/AVD 输入，以及每项的通过条件；
- 拆分为可 revert 的提交顺序和 PR 证据清单。

### 2. 实施前独立审阅与授权

- 有任何 screen、文案、交互、导航、视觉层级、状态呈现或 golden 变化时，**Designer 必须先审阅**计划包和 UI acceptance。结论为 `approve`、`approve with changes` 或 `reject`；未获 approve 不得实现 UI。
- 每个整体代码改动，**Adviser 必须先审阅**计划包，覆盖架构边界、安全、生命周期、并发/取消、数据迁移、测试与回退。P0/P1/P2 finding 未关闭不得进入实现。
- 默认模式：Planner、Designer（如适用）和 Adviser 均通过后，必须等待用户对该计划的明确实施确认。
- 用户在当前请求中明确声明“无人值守”或“自主批准执行”时，实施者可记录该授权并开始执行；授权仅适用于所审阅的范围。范围、风险或 UI 变化扩大时必须重新审阅和重新授权。
- 无人值守授权**不等于**合并授权。受保护 `main` 永远保留 required checks 和用户的独立合并确认，除非用户另行明确授权该次合并。

### 3. 实施与验收

- 实现以计划包为约束；方案变化先更新计划，重新取得受影响的 Designer/Adviser 结论。
- 每个问题按“源头修复 + 回归测试 + 可复用规则 + 可 revert 提交”关闭。
- UI 必须由真实 Compose screen 的语义、golden 和 AVD/设备交互证明；安全/持久化必须包含 API 29 instrumentation、进程重建和负例隔离；协议/扩展必须包含 valid/invalid fixture 与 deterministic packaging。
- 受控 WebView 的验收必须分别标记：fixture host transport、实际 declared-origin 页面、blocked navigation、finish/cancel cookie handoff。网页错误、`ERR_CACHE_MISS`、403 或 offline 只能作为失败路径证据。

### 4. PR 与合并

- PR 描述绑定计划包、审阅结论、提交/回退边界、精确命令、设备输入、产物 digest 和已知限制。
- PR 创建或最终功能变更后，**Adviser 必须对 PR head 再审阅一次**。任何新 finding 都按严重度关闭，PR head 变化后重审受影响范围。
- required checks 成功后，仍必须等待用户人工确认。不得将 Adviser 结论、CI success 或无人值守实施授权误当作 GitHub review/merge approval。
- 合并后更新 Gate evidence、复盘和可复用规则；仅在所有验收与记录完成后创建不可移动 Gate tag。
