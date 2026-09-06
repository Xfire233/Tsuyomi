<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi 质量门禁

## 原则

Gate 是进入下一阶段或合并/发布的判定点，不是工作范围或完成百分比。每个 gate 必须有明确入口条件、判定主体和 `pass` / `fail`（或 `authorize` / `block`）结果；设计批准、代码审阅和验证证据必须绑定同一 Git 输入，输入变化后受影响批准自动失效。

每个问题只有在“源头修复、回归防线、规则沉淀、可回退提交”全部完成后才能关闭。

## 命名与统一 Change Packet

本文件的固定生命周期使用 `G0–G7`；Android UI 审阅过程使用 `UI-R0–UI-R4.1`；产品工作范围使用 `P1–P4`/`P4A–P4C`；Review Graph 节点写为 `RG-L01`、`RG-B03` 等。历史文件名和 schema 可保留旧名，但当前决策不得只写无命名空间的 `R1` 或 `Gate 4`。

每个非平凡变更复用同一个 Change Packet，不为 Planner、Designer、Adviser 或 PR 另造互不兼容的格式：

```text
Change identity and immutable baseline/input
Goal and non-goals
Affected contracts, components and callers
State transitions, failure recovery and cancellation
Security, privacy, persistence and migration
UI/profile applicability
Verification matrix and evidence owners
Rollback boundary and revert order
Planner result
Designer result when applicable
Adviser result
Implementation/merge authorization result
```

字段可以嵌入 Phase 文档、PR 描述或已有版本化计划；不得再建立一份复制当前状态的新总流程文档。

## 每个 Phase 的固定治理流程

### G0. Plan and execution authorization

实现前，Planner 必须将版本化计划包附在 PR 或 Phase 文档：目标/非目标、端到端与失败路径、组件和迁移、UI 影响、风险、验收矩阵、回退提交顺序。任何 UI/交互/golden 变化必须由独立 Designer 审阅；每个整体代码计划必须由独立 Adviser 审阅架构、安全、生命周期、并发/取消和验证。两项结论都绑定计划输入。

默认必须在这些审阅通过后等待用户明确确认才开始实现。用户在当前请求中明确声明“无人值守”或“自主批准执行”时，实施者可批准已审阅的计划范围；范围扩大、UI 增加或风险变化会使授权失效并要求重审。无人值守实施授权不取代受保护分支的用户合并确认。

### G1. Scope

在实现前记录：

- 用户可观察目标、非目标和首个端到端路径；
- 受影响组件、协议版本、模块和数据迁移；
- persisted/effective/capability 状态；
- 安全、隐私、E-ink、无障碍、离线和失败边界；
- 回退时允许丢弃和必须保留的数据。
- Android UI/交互改动先执行仓库级 `.agents/skills/tsuyomi-android-review/SKILL.md` 的 `UI-R1`；其本地忽略报告只选择受影响 Review Graph 节点和验证层，不授予批准。
- Every explicit user design correction or supersession is reconciled into the owning active contract in the same work session, then propagated to the affected Review Graph obligation and highest observable regression seam. Chat, memory, issue drafts, prototype comments and screenshots are not sufficient standalone persistence. The continuity and empty-context recovery protocol is [`DESIGN_MEMORY_WORKFLOW.md`](DESIGN_MEMORY_WORKFLOW.md).

### G2. Design

设计包必须包含：

- 信息架构、状态转换和错误恢复；
- `docs/design/OPTION_APPLICABILITY.md` 的逐项可见性判定；
- standard/E-ink 共用业务树及差异所有权；
- API 下界、窗口断点、横竖屏、分屏、`fontScale = 2.0`、TalkBack、键盘/DPAD；
- 协议、安全、持久化、迁移和兼容性；
- 可执行验证矩阵，不只列 screenshot。

设计产出必须经过独立 Designer UI/UX 评审。结论仅允许 `approve`、`approve with changes`、`reject`；只有绑定目标 Git 输入和证据摘要的 `approve` 才准入实现。

### G3. Implementation

- 决策放在拥有它的最低公共层，禁止在多个 screen 复制推导。
- 修复根因；禁止 lint suppression、baseline、特殊输入分支或兼容 shim 代替迁移。
- 每个可见控件必须有真实 handler、持久反馈和失败恢复。
- clean cutover：迁移所有调用者并删除旧路径、重复实现和失效文档。
- 新依赖必须同时更新 version/lock、verification metadata、第三方声明和许可证。

### G4. Adviser code review

独立 Adviser 代码评审对目标 Git 输入检查：

- 正确性、生命周期、并发/取消、资源与分配；
- 状态所有权和模块依赖；
- API 29、进程重建、持久化和安全边界；
- 适用性、无障碍、E-ink、窗口矩阵；
- 测试是否保护用户可观察契约；
- 是否存在文档、代码、fixture、golden 或协议漂移。

每个 finding 必须记录严重度、证据路径、源头修复、验证和关闭提交。目标输入改变时，只允许明确标注“不影响审阅范围”，否则重审。公开仓库只保留适合长期维护的结论和规则，不提交本地会话、提示词或私有审阅转录。

### G4.5. PR admission and merge authorization

PR 创建后及最终功能变更后，Adviser 必须对 PR head 再审阅一次；新 finding 必须按严重度关闭，head 变化会使受影响审阅失效。所有 required checks 成功后仍必须等待用户人工确认才可合并。Designer/Adviser `approve`、CI success 和无人值守实施授权都不等同于 GitHub review 或合并许可。

### G5. Verification

验证分三层。三层复用同一 Change Packet、Git 边界和 affected-path 计划；Android Studio/Android CLI 只缩短反馈，不建立第二套 proof system。

#### Tier 1 — Fast edit loop

- Android Studio editor/gutter、Compose Preview、Live Edit/Apply Changes、Layout Inspector 和 debugger 只回答当前编辑问题，不形成 gate evidence。
- 使用已签入的 `.run/` Gradle configuration 或 Gradle tool window 的精确 module task；Gradle Wrapper 仍拥有 compiler/lint/test 结论。
- 不运行全项目 model discovery、全 Android suite、AVD 重建或重复截图。

#### Tier 2 — Affected-change proof

1. UI change 先由 `UI-R1` 以显式 baseline 或 Git merge-base 选择 owning Review Graph nodes、cross-cutting capabilities 和 evidence lanes。
2. `tools/android_ci_plan.py` 将 changed paths 映射为精确 build、lint、JVM、screenshot、instrumentation 与 dependency-lock tasks。已知 module 不得自动扩张到全 Android；未知 Android source 或无有效 Git base 才使用保守全集。
3. Gradle 一次构建受影响 production target；普通验证和 CI admission 不得使用 `--write-locks`。只有 dependency input 改变时才在独立的本地 maintenance 步骤更新 lock，再以干净 worktree 运行一次普通 strict verification 证明提交结果。CI 直接验证提交中的 lock；先重写 lock 再重复构建既掩盖输入事实，也浪费 admission 时间。
4. 运行真实证明：Bug 先复现后消失；UI 用 production semantics/layout/behavior、受影响 screenshot assertion 和必要 AVD interaction；持久化/安全覆盖 API 下界、重建、隔离、删除和错误；协议使用 valid/invalid fixtures 与 conformance。
5. Runtime change 每个 active profile 只部署一次。Android CLI 拥有 isolated AVD、delta install、exact activity launch、layout diff 和 PNG；一个 observable claim 只指定一个 evidence owner。截图不替代 gesture/state-transition test。

#### Tier 3 — CI admission

- `.github/workflows/android-quality.yml` 使用同一 planner 选择 bounded production tasks；documentation-only changes 不启动 Android jobs，known module changes 只跑 owning tasks，invalid/missing base 使用 conservative full plan。
- 每个 PR/ref 只有一个 active `android-quality` run；required Android jobs 有 18 分钟 hard deadline。Instrumentation APK 可并行编译，device execution 串行。
- Hosted admission 必须确认目标 head、关键 build/test/instrumentation/package steps 非 `skipped`，并抽查 log 证明命令真实执行。绿色空任务不是证据。
- Gradle Managed Device 仅是 manual/non-required pilot；未证明 device geometry、稳定性、缓存和 evidence equivalence 前，不替代 required API 29 emulator lane。

最低验证类别保持不变，但由 planner 选择对应 tasks：production assemble、affected lint、affected JVM/unit、affected screenshot validation、affected instrumentation，以及 repository policy/REUSE。跨组件 admission 另加 protocol `npm ci && npm test`、extensions build/fixture/package determinism 和根仓库检查。

Android 运行期验收只执行 `.agents/skills/tsuyomi-android-review/review-policy.json` 当前 `activeProfiles`。每个 active profile 必须在独立 API 29 portrait AVD 上记录同一目标 head、分辨率、density、方向、font scale、用户流结果和截图 SHA-256；横屏、分屏、golden 或在单一 AVD 上切换 profile 不能替代该 portrait 记录。

`deferredProfiles`/`FROZEN` profile 在日常 gate 中不构成缺失证据：保留合同、实现、fixture 和 inventory，不新增设计/批准/golden。直接修改 deferred profile 时，只执行 policy 允许的最小编译、非视觉契约测试和必要启动 smoke；恢复为 active 时重新进入完整 retained matrix 和物理设备要求。完整设备配方以 [`AVD_MATRIX.md`](../verification/AVD_MATRIX.md) 为准。

Required workflow 的 path detection 必须使用仓库根锚点（例如 `git -C "$GITHUB_WORKSPACE"`），不得依赖 job 默认 `working-directory`。

### G6. Evidence

`docs/phases/PHASE_N.md` 必须记录：

- Monorepo baseline tag、组件版本、协议/Host API/manifest 版本；
- 设计和代码评审结论及可公开的 evidence 摘要；
- 精确命令、工具版本、设备/AVD recipe 版本；
- 退出码和不可变产物 SHA-256；
- screenshot/golden diff 结论；
- 每个 active profile 的独立 portrait AVD 记录：目标 head、物理分辨率、density、方向、profile、font scale、用户流结果和截图 SHA-256；
- 每个 deferred profile 的 policy 状态、保留边界和恢复触发条件；
- 已知限制、延期项和回退点。

`build/` 中本地截图只能作为调试证据，不能替代版本化 Phase 记录。

### G7. Retrospective

每个 Phase 结束后更新复盘：问题、根因、源头修复、自动防线、横向/纵向扩展。能复用于未来 Phase 的结论必须进入本文件、架构规则或贡献规则，不能只留在聊天记录。

Design-memory checkpoints are event-driven rather than calendar-driven: after an accepted correction, after a coherent decision cluster, before switching feature families, and before handoff. Stable decisions enter the owning versioned contract; coherent deferred implementation packages may be published through the repository's `to-spec` workflow; local semantic memory and handoff state never replace public authority or evidence.

### Package-review prevention checklist

The Phase 4B retrospective exposed defects that module-local happy-path checks could not catch. Future cross-boundary packages must apply these rules before final Adviser review:

- Review each operation as a complete state machine: authorization, durable intent, transport acceptance, confirmation, cancellation, process loss, explicit retry and UI presentation. A compound action persists its next stage before the first transport and never repeats a confirmed stage.
- Bind every protected network surface to one exact signed operation context. Listing, target discovery and each mutation are separate capabilities; generic transport and inferred/default target IDs are forbidden.
- Keep user-visible outcomes outside transient controls that dismiss after dispatch. Success, unresolved, partial and failure states remain observable and recoverable after menus, sheets or dialogs close.
- Sparse remote projections merge into richer local metadata; they never replace authoritative local/catalog fields with absent summary values.
- Data migrations never infer consent, authorization or acknowledgement from coincidental data overlap. Security- or privacy-significant receipts require explicit historical evidence; otherwise default to not granted.
- Version exported transfer/protocol documents whenever the accepted field set changes. Current writers emit one strict version; readers may retain explicitly tested strict legacy versions.
- Shared preference stores use owner-key allowlists for reset/delete. Whole-store clearing is prohibited unless that store has exactly one owner and the contract explicitly requires it.
- CI planners accumulate overlapping module ownership, discover every test-bearing module, compare from the merge-base and rebuild changed signed fixtures before dependent instrumentation. CI validates committed dependency locks once and never rewrites them.
- Screenshot registration follows the active review profile policy. Frozen-profile previews and goldens remain retained but are not registered as routine `PreviewTest` evidence, regenerated or counted toward the active gate; repository governance rejects accidental frozen E-ink registration.

## Finding 关闭标准

| 严重度 | 规则 |
|---|---|
| P0/P1 | 阻塞对应 gate；必须修复并重验 |
| P2 | 默认阻塞；只有 Phase 文档记录风险、责任边界和明确不影响下一 admission gate 时才可延期 |
| P3 | 可延期，但必须有 issue/记录；不得伪装为已完成 |

## Phase 2 admission gate

Phase 2 开始前的 admission gate 必须确认 `docs/phases/PHASE_1.md` 全部条目满足，Monorepo 拥有可回退基线、Phase 1 设计和代码评审最终为 `approve`，且 required checks 保护 `main`。
