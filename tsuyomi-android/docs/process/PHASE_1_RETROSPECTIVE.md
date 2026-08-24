<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Phase 1 工程复盘

## 结论

Phase 1 的功能目标已达到，但过程中暴露出一个共同缺陷：若约束只存在于设计文档或单个测试中，它会在相邻层、另一 profile、另一设备矩阵或另一工具链上再次失效。进入 Phase 2 前，所有已知问题必须形成“源头修复 + 自动验证 + admission gate”三重防线。

## 问题与持久化状态

| 问题 | 根因 | 源头修复 | 长期防线 |
|---|---|---|---|
| standard 中显示 E-ink 刷新，且四个策略在所有 profile 都无消费者 | persisted/枚举被误当作已实现能力；UI 与模型提前承诺未来调度语义 | standard 隐藏 E-ink redraw section；删除未消费的 RefreshPolicy 模型、持久化和 selector；只保留真实递增 redraw epoch 的 E-ink 手动重绘 | 真实 screen instrumentation、全仓无死枚举检查、`OPTION_APPLICABILITY.md` |
| 设备分类把普通 Android tablet 误判为 E-ink | permissive substring matching 缺少 token 边界 | classifier 使用字母数字 token boundary | classifier 正反例单元测试；新增签名必须同时提供近似负例 |
| Windows file-backed DataStore 测试偶发 rename 失败 | 单元测试错误依赖宿主文件系统原子 rename 时序 | repository 语义测试改用内存 Preferences DataStore | 单元测试不依赖磁盘；真实文件行为只在 Android instrumentation 覆盖 |
| API 29 KeyStore 拒绝调用方生成 GCM IV | KeyStore key 默认要求 provider 自行随机化；实现契约要求每记录独立 96-bit IV | key generation 显式关闭 provider-side randomized-encryption requirement，由 `SecureRandom` 生成 IV，AAD 绑定分区 | API 29 instrumentation 验证加解密、IV 唯一性、origin/source 隔离和删除 |
| 临时 KeyStore/provider 不可用会删除有效凭据 | AEAD 边界把认证失败、格式损坏和基础设施不可用折叠为同一异常 | 区分 `CORRUPT_OR_UNAUTHENTICATED` 与 `UNAVAILABLE`；只对已确认损坏失效记录 | 故障注入证明 unavailable 保留密文，认证失败才删除；“不可用不得触发破坏性恢复” |
| 并发保存阅读进度可能让旧位置获胜 | 条件 update 与 insert-if-absent 不在同一 Room transaction | 在 transaction 内读取、校验、条件更新/插入/损坏替换，并按数据库实际采用结果返回 | 并发新旧记录竞争 instrumentation；所有时间戳仲裁 API 必须原子 |
| 并发集合父级更新可形成环 | 存在性、祖先链校验和 update 分离 | 同一 transaction 内重读祖先并更新 | 对向并发父级更新测试；所有 read-check-write 层级不变量事务化 |
| `PreviewSession` A→B→A 可接受旧 A 视觉见证 | witness 只比较目标值和 epoch，没有绑定请求代次 | 每次接受目标递增 generation，witness/release 必须匹配最新 generation | ABA 回归测试；异步视觉见证必须防值相等复用 |
| quota cleanup 删除失败后仍返回写入成功，且可能删除耐久数据 | write 丢弃 cleanup 结果；CACHE 与 NO_BACKUP 共用自动 LRU | 成功前验证配额；自动 LRU/部分驱逐仅限可丢弃 CACHE；durable write/cleanup 在删除前失败或只报告状态 | undeletable/部分驱逐/durable 零驱逐故障注入；成功返回必须证明配额成立 |
| `fontScale = 2.0` 时底部导航标签裁切 | 固定容器高度把文字度量当成常量 | 容器改为 `heightIn(min = …)` | 大字体 golden + 两类 AVD 横竖屏运行期检查；禁止文字容器固定高度 |
| 动态颜色在 minSdk 29 触发 NewApi lint | eligibility 状态没有替代真实 API guard | 调用点增加 API 31 guard | warnings-as-errors lint；API 29 构建与运行 |
| reduced-motion 实现偏离冻结设计 | app 重复读取 `Settings.Global`，绕过 Compose `MotionDurationScale` 契约 | 删除 app 私有 observer，统一使用 `core/ui` 的 Compose signal | 单一实现所有权；代码评审检查重复平台读取 |
| app module Layoutlib renderer 在 Windows 无法启动，初始设置 golden 又复制了生产内容 | app classpath/renderer 子进程不稳定；测试宿主所有权错误导致基线与真实 screen 漂移 | app 不再应用 screenshot plugin；真实 screen golden 移到 `feature/library`、`feature/browse`、`feature/settings`，组件/scaffold 留在 `core/ui`；删除复制的 SettingsContent | reference 按生产所有者分层；修改真实资源必然触发 diff；app 路由/生命周期用 instrumentation 和 AVD |
| screenshot/lint detached configurations 被 dependency verification 拒绝 | verification metadata 未覆盖工具插件运行期解析的 artifact | 仅在明确任务下生成并审阅 SHA-256 metadata | 依赖升级必须在干净工作树执行验证写入、审阅 diff、重跑完整任务；禁止关闭 verification |
| lint 首轮暴露 manifest、Activity、资源与图标问题 | 质量任务执行过晚，且 warnings-as-errors 包含版本发现噪音 | 源头修复 API/Activity/resource/icon；版本发现由锁定升级流程管理 | lint 在每个 Phase 实现阶段持续执行；依赖版本升级与源码 lint 分离 |
| REUSE 扫描生成物和 golden 时失败 | 新二进制/生成物没有批量许可证策略，且本机 `reuse` 不在 PATH | `REUSE.toml` aggregate annotations；固定使用 `python -m reuse` | 新文件类型在合入前运行 REUSE；golden、schema、wrapper 明确来源和许可证 |
| 大量模块和文件尚无 Git 基线 | 先实现后建立可回退点，问题定位只能依赖本地状态 | Phase 1 结束时将组件快照转换为一个干净 Monorepo 基线和 annotated tags | `REPOSITORY_GOVERNANCE.md` 规定组件版本、Monorepo tag、原子协议顺序和回退演练 |
| `lockAllConfigurations()` 没有任何 lockfile | 声明了策略但未执行解析图写入和稳定性检查 | 为全部模块及 included build 生成 lock state | CI 重写 locks 后要求无 diff；依赖 PR 独立同步 checksum/notice |
| 第三方声明声称无依赖 | 贡献规则没有进入 quality gate 必过项 | 登记所有直接 runtime/test/build/CI 依赖及固定版本/许可证/用途 | 依赖变更模板与 CI；notice、lock、verification metadata 同步 |
| 初始组件目录没有托管 CI、不可变公开基线或 Phase 证据 | 实现先于仓库治理，评审结论未绑定公开可验证版本 | 增加根 CI/PR 模板、Phase 证据、SemVer/CHANGELOG 和干净 Monorepo 基线规则 | protected main/required checks 在首次推送后配置；Phase 文档记录 Monorepo tag、组件版本与制品哈希 |
| AVD 只存在本机手工配置 | 分辨率被记录，但 image/tool revision/reset 输入不可重建 | 版本化 AVD matrix、Doctor 和创建脚本 | Phase 证据记录 sdk/emulator revision、wipe/cold boot、设备输出与截图哈希 |
| 工作区硬编码用户 SDK 路径并遗留 `g` junction/hprof | 临时诊断没有 clean-tree 禁入流程 | 移除硬编码和临时项；Doctor 从环境变量生成本地配置 | `tools/check_repository.py` 与 CI 禁止本地状态、秘密和构建制品 |

## 纵向分析：一条能力必须贯穿所有层

每个功能按以下链路审查，任何断点都不是“后续优化”：

```text
用户承诺
→ 适用性与失败语义
→ persisted/effective 状态模型
→ 单一 resolver / domain policy
→ feature 可见性与 handler
→ core semantic component
→ Android 生命周期、API 与安全边界
→ 单元/仪器/screenshot/运行期证据
→ 发布、许可、回退记录
```

Phase 1 的刷新问题同时存在于 UI、模型和消费端，证明只隐藏控件不足以修复语义；没有消费者的枚举、持久字段和 selector 必须一起删除。后续能力必须从真实行为向外暴露，不能从预设配置向内寻找用途。

## 横向分析：同一约束必须跨矩阵成立

每个设计评审和回归计划至少横向展开：

- profile：standard、手动 E-ink、自动识别 E-ink、未知设备 fallback；
- API：29 下界、31 动态颜色边界、target SDK 行为；
- 窗口：手机/E-ink 基线、横竖屏、分屏、断点两侧；
- 可访问性：TalkBack、键盘/DPAD、`fontScale = 2.0`、reduced motion；
- 状态：首次加载、持久化恢复、写失败/重试、进程重建、上下文切换；
- 层次：protocol、Android host、extension package，及一个原子 Monorepo PR 内的版本顺序；
- 工具链：干净 checkout、Windows 本地、CI、dependency verification、REUSE；
- 安全：正常输入、近似误判输入、跨来源/跨 origin、删除与备份排除。

“一个正例通过”不能证明约束完整。分类、安全、版本解析和适用性必须包含邻近负例。

## 修复是否真正持久化的判定

修复只有同时满足以下条件才可关闭：

1. 错误语义在拥有该决策的源头被修复，而非调用点补丁。
2. 至少一个测试会在同类回归发生时失败。
3. 设计/架构规则能指导未来不同功能，不只复述当前代码。
4. 对应 quality/admission gate 清单把验证变为必过项。
5. 变更进入可定位提交并有可回退 tag。

只有文档：不充分。只有测试：无法阻止错误架构复制。只有代码：无法防止流程重犯。三者必须同时存在。

## Phase 2 admission gate 前置结论

Phase 2 只能在以下条件全部满足后开始：

- Phase 1 代码和架构评审结论为 `approve`；
- 当前评审阻塞项均完成源头修复；
- standard/E-ink 刷新适用性测试通过；
- build、unit、instrumentation、lint、screenshot validation、REUSE 全部通过；
- Monorepo 工作树不包含构建产物、秘密、本机路径或本地自动化状态；
- 根仓库建立可追溯的 Phase 1 基线提交、annotated component tags 和历史 `gate-1-baseline` tag；
- `QUALITY_GATES.md` 与 `REPOSITORY_GOVERNANCE.md` 被纳入贡献规则。
