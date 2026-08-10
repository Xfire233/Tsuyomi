<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Gate 4：手工设备发现项计划

## 目的

Gate 4 的范围由真实设备/AVD 手工验收中可复现的产品发现项驱动。每一项都必须以用户可观察的行为、稳定复现步骤和目标设备证据描述；不得把单次操作失误、未验证猜测或历史横屏证据伪装成缺陷。

本计划不追溯修改 Gate 3 的验收结论。Gate 3 已明确记录其缺少两条独立 portrait 运行期记录；后续实现或发布前的 AVD 验收遵守 `docs/verification/AVD_MATRIX.md` 的双竖屏基线。

## 本轮手工输入基线

| 字段 | 固定值 |
|---|---|
| target head | `26bad358ab2ef4afac01b63b30e6c6c3e6de9c1c` 或测试时展示的实际构建 revision |
| device | `Tsuyomi_API29`，API 29，`1080×2400` portrait，420 dpi |
| profile | forced Standard |
| font scale | 1.0；发现文字、布局或焦点问题时重测 2.0 |
| app | `org.tsuyomi.android`，debug `0.2.0` |
| fixture | `/sdcard/Download/wenku8-fixture.hxp`，仅作脱敏验收数据 |

## 手工验收流

1. 从空书架进入“浏览”，选择“导入内容源包”，在 DocumentsUI 选择 `wenku8-fixture.hxp`；核对安装说明并确认安装。
2. 选择“进入内容源”，搜索 `fixture`，打开 `雾港纪事`，检查搜索结果、详情、目录与章节 `第一章 雾中的灯塔` 的可见性、触控和返回栈。
3. 在书籍详情执行本地加入、标签/评分/集合操作；在页面中观察反馈、禁用状态、文本换行、滚动、底部导航与返回行为。
4. 在“更多”中检查数据迁移与备份入口；取消 DocumentsUI，再次进入，确认应用没有误报成功、没有卡死或丢失当前路由。
5. 在“设置”强制切换 Standard 与 E-ink，再切回 Standard；确认已保存主题/动态色偏好恢复，E-ink 不夸大硬件刷新能力。
6. 旋转到横屏、回到竖屏，并将字体缩放设为 2.0；检查文字裁切/重叠、焦点可达性、系统栏与底部导航。横屏仅为附加覆盖，不能替代本表中的手机竖屏基线。

不要输入真实 Wenku8 凭证、验证码或私有内容；受控 WebView 的登录/验证仅检查取消、返回和错误恢复路径。

## 发现项登记与准入

每条收到的发现项都追加到下表，并在进入实现前补全严重度、受影响用户契约和回归层级。只有 `READY` 项可进入 Gate 4 实现计划。

| ID | 状态 | 设备/构建 | 复现步骤 | 实际结果 | 预期结果 | 证据 | 严重度 | 回归层级 |
|---|---|---|---|---|---|---|---|---|
| G4-UX-001 | READY | API 29 phone portrait / `0.2.0`；用户实机流与代码路径复核 | 从搜索结果打开 `雾港纪事`；加入书架后从本地书架打开同一书；点击“前往浏览/打开来源” | 搜索进入 source-owned `source/detail`，书架进入 Room-owned `library/book/{sourceId}/{remoteBookId}`；标题、操作和返回栈分裂。本地页动作只切换/恢复 Browse 根栈，实际进入来源浏览页，且可能恢复到进入来源前的旧 Browse 页面，不保证打开当前书；详情→目录→章节→Reader 增加重复层级 | 同一 `BookIdentity` 无论从搜索、书架、历史或远程收藏进入，都落到一个 canonical book detail。页面先显示本地缓存/书架状态，再按来源可用性增强元数据和目录；来源不可用时本地详情仍可用。目标写明“查看本书来源详情”时必须打开该书，不能恢复无关 Browse 历史；主要阅读动作不得要求经过重复详情页 | 用户在固定 phone portrait 基线稳定复现；`MainActivity.Routes.Detail`、`Routes.LocalBook`、`LocalBookDetailsScreen.onOpenSource`；`GATE_3.md` 294、301–305、428–430 | P1 | route/navigation instrumentation；Room/source race and dormant-source tests；统一 screen semantics/goldens；两套 portrait AVD 用户流 |
| G4-UX-002 | READY | API 29 phone portrait / `0.2.0`；用户实机流与代码路径复核 | 创建手动集合；将来源书加入本地书架；分别检查本地书籍详情和“管理本地集合” | 可以创建/删除手动集合，但没有任何可发现路径把已入书架的书加入或移出集合。数据库已有 `addManualMembership` / `removeManualMembership`，UI 没有调用入口 | 本地书籍详情提供可访问的“管理所属集合”，显示当前 membership 并允许多选保存；集合详情或编辑态也应提供从书架选择/移除书籍的对向入口。保存必须有持久结果反馈，智能集合不显示手动 membership 编辑 | 用户在固定 phone portrait 基线发现；`LocalBookDetailsScreen` 无 collection 参数/动作，`CollectionManagerScreen` 只管理集合本身，`RoomLibraryRepository` 已有 membership API；违反 `GATE_3.md` 305、438 | P1 | repository membership contracts；book/collection UI instrumentation；TalkBack/DPAD；两套 portrait AVD 双向发现流 |
| G4-UX-003 | READY | API 29 phone portrait / `0.2.0`；用户实机流与代码路径复核 | 在本地书籍详情输入标签并点击“保存标签” | 点击后没有 loading、禁用、成功、失败、标准化结果或可访问公告；即使 Room 写入成功，用户也无法判断操作是否发生，失败同样不可见 | 保存期间防止重复提交；成功后以持久 inline 状态显示“已保存”并回显 Room 标准化后的标签；失败显示安全、可重试错误。TalkBack 使用 live region；E-ink 不依赖短暂 Snackbar 或动画 | 用户在固定 phone portrait 基线稳定观察；`MainActivity.onSaveTags` 启动协程后没有 outcome state，`LocalBookDetailsScreen` 只保留无状态按钮；违反 `GATE_3.md` 296 与 `EINK.md` 65–66 | P1 | repository success/failure；Compose mutation-state tests；semantics/live-region；两套 portrait AVD 保存/失败流 |

### G4-UX-001 设计约束

- 使用稳定身份路由 `book/{sourceId}/{remoteBookId}` 作为唯一书籍详情入口；搜索、书架、历史和远程收藏只传稳定身份，不各自拥有详情页。
- host 组合一个详情状态：Room 中的本地书架、评分、标签、集合、进度与 reconciliation 是本地真值；已验证来源只增量提供可刷新的简介、状态和目录，不得让网络或扩展阻塞本地内容。
- dormant source 在同一页面内降级，保留本地信息与阅读进度，并把来源相关动作禁用/解释；不能跳到另一种“本地详情页”。
- `继续阅读` / `开始阅读` 是详情页主动作。目录可作为同页章节区或一个明确的次级目的地，但不得再经过第二个书籍详情；Back 必须回到调用方列表并保留其查询、滚动和筛选状态。
- source/local 的安全与生命周期所有权继续分离在状态层和 controller 层，不用重复页面或重复 route 表达内部边界。

## Gate 4 导航与操作逻辑审阅准入

Gate 4 的最终审阅必须把产品操作逻辑与代码正确性作为两个独立准入面。代码测试通过不能替代真实任务流审阅；reviewer 必须实际执行下面的 route/task matrix，并对每一次点击的目标、返回结果、状态恢复和动作反馈给出结论。

### 导航不变量

1. 同一领域对象只有一个 canonical detail route。不同入口可以携带来源上下文，但不能生成互不一致的详情页或动作集合。
2. 动作标签必须准确描述目的地。“查看本书来源详情”打开稳定身份对应的书；“浏览来源”才允许进入来源根页。任何跨 root 的精确动作不得通过 `saveState` / `restoreState` 恢复无关历史页面。
3. 系统 Back 按实际访问历史返回；应用栏 Up 按当前信息层级返回；底部导航切换独立根栈。三者不得互相冒充，也不得产生循环、跳过调用方或意外退出。
4. 从搜索结果或书架项目到开始/继续阅读最多经过一个统一详情；长目录可以是一个明确次级目的地，但不能再经过第二个书籍详情。
5. 返回调用方列表时保留查询词、筛选、集合、选中项、滚动或 E-ink 页码；来源更新、进程恢复和显示 profile 切换不能把用户送到另一个根页。
6. 每个用户可变更动作必须有 `idle → working → success/error` 的可观察状态。重要结果使用持久 inline/dialog 反馈和 TalkBack live region；不得只依赖无响应按钮、短暂 Snackbar、颜色或动画。
7. 双向关系必须可发现：书籍可以管理所属手动集合，手动集合也可以选择书籍。智能集合只解释规则结果，不伪装成可手动修改 membership。
8. 每个页面都要审计主动作、次动作、空态动作、错误恢复、取消、重复点击、跨 root 跳转和 Back/Up；不存在无目标按钮、死路、依赖旧栈偶然状态的跳转或只能靠猜测发现的核心操作。

### 必须执行的任务流

- 搜索 → 统一书籍详情 → 加入书架 → 管理集合/标签/评分 → 继续阅读 → Back 返回原搜索结果。
- 书架/手动集合/智能集合 → 同一书籍详情 → 查看本书来源信息或目录 → Back 返回原列表和原位置。
- 来源不可用 → 本地书籍详情降级 → 可执行本地动作 → 来源动作禁用并解释 → Back 不切换 root。
- Browse 根页、搜索、详情、目录、Reader 间连续 Back/Up；随后切换 Library/Browse 底部项，验证两套根栈各自恢复且精确动作不消费旧根栈。
- 每个创建、保存、加入、移除和重试动作分别验证成功、失败、取消、重复点击、进程重建及 `font_scale = 2.0`；Standard 与 E-ink 共用同一操作语义。

最终 review 记录必须包含逐步点击序列、每步预期/实际 route、Back/Up 结果、恢复的页面状态、Standard 与 E-ink portrait 截图证据，以及未通过项对应的回归层级。仅检查 composable、controller、repository 或测试覆盖率不足以批准 Gate 4。

状态：`NEW`（待复现）→ `TRIAGED`（已归类）→ `READY`（可实现）→ `FIXED`（修复和回归证明完成）→ `DECLINED`（不属于产品缺陷，说明理由）。

报告时至少提供：所在页面、操作顺序、是否可稳定复现、截图或录屏时间点，以及期望行为。可直接自然语言描述；维护者负责转写为表中的完整条目。