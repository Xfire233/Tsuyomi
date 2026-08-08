<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi 质量门禁

## 原则

Gate 是进入下一阶段的不可变准入点，不是完成百分比。设计批准、代码审阅和验证证据必须绑定同一 Git 输入；输入变化后，受影响批准自动失效。

每个问题只有在“源头修复、回归防线、规则沉淀、可回退提交”全部完成后才能关闭。

## 每个 Gate 的固定流程

### 1. Scope

在实现前记录：

- 用户可观察目标、非目标和首个端到端路径；
- 受影响组件、协议版本、模块和数据迁移；
- persisted/effective/capability 状态；
- 安全、隐私、E-ink、无障碍、离线和失败边界；
- 回退时允许丢弃和必须保留的数据。

### 2. Design

设计包必须包含：

- 信息架构、状态转换和错误恢复；
- `docs/design/OPTION_APPLICABILITY.md` 的逐项可见性判定；
- standard/E-ink 共用业务树及差异所有权；
- API 下界、窗口断点、横竖屏、分屏、`fontScale = 2.0`、TalkBack、键盘/DPAD；
- 协议、安全、持久化、迁移和兼容性；
- 可执行验证矩阵，不只列 screenshot。

设计产出必须经过独立 UI/UX 评审。结论仅允许 `approve`、`approve with changes`、`reject`；只有绑定目标 Git 输入和证据摘要的 `approve` 才准入实现。

### 3. Implementation

- 决策放在拥有它的最低公共层，禁止在多个 screen 复制推导。
- 修复根因；禁止 lint suppression、baseline、特殊输入分支或兼容 shim 代替迁移。
- 每个可见控件必须有真实 handler、持久反馈和失败恢复。
- clean cutover：迁移所有调用者并删除旧路径、重复实现和失效文档。
- 新依赖必须同时更新 version/lock、verification metadata、第三方声明和许可证。

### 4. Review

独立代码评审对目标 Git 输入检查：

- 正确性、生命周期、并发/取消、资源与分配；
- 状态所有权和模块依赖；
- API 29、进程重建、持久化和安全边界；
- 适用性、无障碍、E-ink、窗口矩阵；
- 测试是否保护用户可观察契约；
- 是否存在文档、代码、fixture、golden 或协议漂移。

每个 finding 必须记录严重度、证据路径、源头修复、验证和关闭提交。目标输入改变时，只允许明确标注“不影响审阅范围”，否则重审。公开仓库只保留适合长期维护的结论和规则，不提交本地会话、提示词或私有审阅转录。

### 5. Verification

按变更类型运行真实证明：

- Bug：先复现，再确认复现消失。
- UI：真实 screen 语义测试、golden 和 AVD/设备交互。
- 持久化/安全：API 下界 instrumentation，覆盖重建、隔离、删除和错误。
- 协议：valid/invalid fixtures 与 conformance。
- 功能/API：现有契约测试；只有新增可观察契约时添加测试。

Android Gate 的最低自动检查：

```text
assembleDebug
lintDebug（app + affected Android libraries）
JVM/unit tests
受影响 instrumentation tests
validateDebugScreenshotTest
python -m reuse lint
```

跨组件 Gate 同时要求 protocol `npm ci && npm test`、extensions 的 build/fixture/package determinism 检查（实现后启用）、Android 相关检查，以及根 Monorepo REUSE/制品策略。

### 6. Evidence

`docs/gates/GATE_N.md` 必须记录：

- Monorepo baseline tag、组件版本、协议/Host API/manifest 版本；
- 设计和代码评审结论及可公开的 evidence 摘要；
- 精确命令、工具版本、设备/AVD recipe 版本；
- 退出码和不可变产物 SHA-256；
- screenshot/golden diff 结论；
- 已知限制、延期项和回退点。

`build/` 中本地截图只能作为调试证据，不能替代版本化 Gate 记录。

### 7. Retrospective

每个 Gate 结束后更新复盘：问题、根因、源头修复、自动防线、横向/纵向扩展。能复用于未来 Gate 的结论必须进入本文件、架构规则或贡献规则，不能只留在聊天记录。

## Finding 关闭标准

| 严重度 | 规则 |
|---|---|
| P0/P1 | 阻塞 Gate；必须修复并重验 |
| P2 | 默认阻塞；只有 Gate 文档记录风险、责任边界和明确不影响下一 Gate 时才可延期 |
| P3 | 可延期，但必须有 issue/记录；不得伪装为已完成 |

## Gate 2 准入

Gate 2 开始前必须满足 `docs/gates/GATE_1.md` 全部条目，Monorepo 拥有可回退基线、Gate 1 设计和代码评审最终为 `approve`，且 required checks 保护 `main`。
