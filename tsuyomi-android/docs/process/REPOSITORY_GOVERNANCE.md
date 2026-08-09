<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Monorepo 治理与回退

## 组件边界

一个 Git 仓库包含三个独立版本组件：

- `tsuyomi-protocol`：Schema、fixtures、规范和 conformance；
- `tsuyomi-extensions`：TypeScript 扩展与 `.hxp` 工具；
- `tsuyomi-android`：Android Host。

Monorepo 允许一个 PR 原子修改协议、生产者和消费者，但禁止源码边界泄漏。组件只通过版本化协议、脱敏 fixture、签名制品和 release metadata 互操作。

## 分支与 PR

- `main` 始终可构建、可验证；启用 branch protection 和 required checks。
- 使用短生命周期分支；禁止把 Android、protocol、extensions 放在三个长期 branch。
- 一个 PR 只承担一个可回退意图；跨组件变更在同一 PR 中按 protocol → extensions → Android 顺序组织。
- 路径过滤 CI 只运行受影响组件，但根级 REUSE 和仓库制品检查对每个 PR 必跑。
- 禁止 force-push 已发布 tag 和受保护 `main`。

### 审阅与合并授权

- 进入实现前，Planner 计划必须先获 Adviser 审阅；若含 UI/交互/golden 变更，还必须先获 Designer 审阅。
- 默认等待用户确认实施；只有当前请求明确声明无人值守/自主执行时才可在已审阅范围内自行开始。
- 每个 PR 的最终 head 必须再获 Adviser 审阅并通过 required checks；合并仍等待用户的独立人工确认。无人值守实施授权不授予 `main` 合并权。

## 提交

提交必须：

- 单一目的、可独立审阅和 revert；
- 同时包含实现、受影响测试、文档、版本和迁移；
- 不含 build、SDK、AVD、报告、heap dump、密钥、cookies、`local.properties`、私人数据或本地自动化/辅助工具状态；
- 使用 `<type>(<scope>): <imperative summary>`，例如 `fix(display): scope redraw controls to e-ink`；
- 协议破坏性变更先修改 protocol，再修改 extensions，最后修改 Android consumer，不以临时 shim 倒置顺序。

不为“保持兼容”留下未使用 alias、双写、双 parser 或 deprecated 路径；回退依赖 Git revert 和版本化数据迁移，不依赖永久代码分叉。

## 版本与 tag

- 三个组件分别使用 SemVer；0.x 阶段仍必须明确 breaking change。
- tag 使用组件前缀：`protocol-vX.Y.Z`、`extensions-vX.Y.Z`、`android-vX.Y.Z`。
- Gate 基线使用单一 annotated tag：`gate-1-baseline`、`gate-2-baseline`，指向同时通过全部相关组件检查的 Monorepo 提交。
- annotated tag 消息记录 Gate 文档、三个组件版本和制品摘要；tag 不移动。

## Gate 基线

`tsuyomi-android/docs/gates/GATE_N.md` 记录：

```text
monorepo Git SHA
protocol version + schema/fixture digest
extensions package/tool version + deterministic artifact digest
android versionName/versionCode + APK digest
```

聊天记录、分支名、未提交路径或“最新版”不是版本引用。

## 依赖与供应链

- Gradle version catalog/npm lockfile 声明版本；Gradle lock state/npm lockfile 固定解析图；verification metadata/integrity 固定制品内容。
- 依赖升级必须在同一 PR 同步：版本、lock、checksum、`THIRD_PARTY_NOTICES.md`、许可证和验证证据。
- 普通功能 PR 不允许无关 lock/checksum 漂移。
- 禁止关闭 dependency verification、删除 lock 或添加宽泛 trusted artifact 规则来通过构建。

## 测试、Golden 与本地状态

- 测试源码、脱敏 fixtures、screenshot references 和 GitHub Actions 是公开可复验证据，必须进入版本控制。
- build、测试报告、临时截图、APK、AVD 状态、凭据及本地自动化/辅助开发文件必须由 `.gitignore` 排除。
- 修改 golden 的 PR 必须同时包含批准的行为/设计变化和目标设备矩阵；禁止只更新 reference 让失败消失。
- CODEOWNERS 对组件边界、CI、许可证声明和 golden 变更提供显式所有权。

## 回退

优先使用 `git revert`，不重写公共历史。

1. 定位最近通过的 Gate/release tag。
2. 回退包含问题的 Monorepo 提交；若提交跨组件，整体 revert 保持原子兼容。
3. 数据库/文件格式只能回退到明确支持的版本；若 downgrade 不安全，保留数据并回退功能入口，不执行破坏性降级。
4. 重跑根级 required checks 和受影响组件检查。
5. 发布新 patch 版本；不移动旧 tag。

每个新增持久化 schema、Host API 或 `.hxp` 格式都必须在设计阶段写出 forward migration、rollback boundary 和不可逆条件。

## 首次公开基线

首次 push 前：

1. 私有旧历史只保存为根目录 `.local/history/*.bundle`，不得上传；
2. 删除嵌套 `.git` 和子目录无效 GitHub workflow；
3. 从干净文件快照创建单一根提交，确保公开历史不含凭据、本机路径、辅助开发会话或私有审阅记录；
4. 运行根级 REUSE/制品检查及三个组件的全部相关测试；
5. 记录一个 Monorepo SHA 与组件/制品摘要并创建新的 `gate-1-baseline` annotated tag；
6. 推送 `main` 和新 tag 后启用 branch protection、required checks 和 CODEOWNERS。
