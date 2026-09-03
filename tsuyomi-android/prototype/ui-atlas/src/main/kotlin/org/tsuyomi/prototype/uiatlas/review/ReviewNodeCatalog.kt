/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.review

import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute

enum class ReviewNodeFamily {
    LIBRARY,
    BOOK_READER,
    SOURCE,
    MORE,
    CROSS_CUTTING,
}

enum class ReviewNodeKind {
    SURFACE,
    HIGH_RISK,
    CROSS_CUTTING,
}

enum class ReviewEvidenceStage {
    ATLAS_UI,
    ACTUAL_ONLINE_SCENARIO,
}

data class ReviewNode(
    val id: String,
    val title: String,
    val family: ReviewNodeFamily,
    val kind: ReviewNodeKind,
    val route: AtlasRoute?,
    val requiredStates: Set<AtlasPageState>,
    val operations: List<String>,
    val visualChecks: List<String>,
    val humanOnlyChecks: List<String>,
) {
    val evidenceStage: ReviewEvidenceStage
        get() = when (family) {
            ReviewNodeFamily.SOURCE,
            ReviewNodeFamily.CROSS_CUTTING,
            -> ReviewEvidenceStage.ACTUAL_ONLINE_SCENARIO
            ReviewNodeFamily.LIBRARY,
            ReviewNodeFamily.BOOK_READER,
            ReviewNodeFamily.MORE,
            -> ReviewEvidenceStage.ATLAS_UI
        }
}

object ReviewNodeCatalog {
    const val VERSION = 6

    val nodes: List<ReviewNode> = listOf(
        node("L01", "混合书架", ReviewNodeFamily.LIBRARY, AtlasRoute.LIBRARY,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.EMPTY, AtlasPageState.ERROR, AtlasPageState.OFFLINE, AtlasPageState.SELECTION, AtlasPageState.MUTATION, AtlasPageState.MODAL),
            "从 production root 验证 Atlas AppBar、快捷书架和三列封面流由真实 Room/Navigation 驱动", "切换全部、继续、最近、稍后再读、追更、收藏夹与镜像并切换三种布局", "使用快捷书架 + 创建；拖动单本和多选批次进入快捷书架根、现有收藏夹与直接书籍快捷项；在未锁定滚动模式和锁定固定模式分别重排快捷项、移除快捷项并验证所有目标等价；打开同步、搜索、排序、标签和书籍行菜单并确认对应触发身份",
            "拖动后立即验证布局匹配预览、批次数量、源项降权、目标高亮、插入间隙和结果说明；向下滚动使未锁定快捷栏收为 48dp chevron，并分别通过点击、反向滚动和拖拽悬停展开；锁定后完整快捷栏固定在 AppBar 下并持续接受根插入、收藏夹、直接书籍、重排与移除；中途解锁保持当前书籍锚点到下一次向下滚动；离开并返回根书架时验证快捷栏、根投影和已解析封面同时稳定恢复；进入子节点时验证计数和内容只属于当前投影",
            "从 Search/Home 无封面摘要进入 canonical Detail，验证 Detail 返回的真实封面在本地 pin/read-later 后写入 Library；对既有无封面条目打开 Detail 后返回并保存进度，封面必须修复且不再被清空",
            visual = listOf("production 与 Atlas 的 AppBar、快捷书架、三列封面流和动作位置一致", "静止态快捷书架保持 Atlas 80×116dp、76dp 封面/图标区和单行标题，不得因拖放增加常驻数量/说明行或改用另一套卡片语法", "拖动预览在手势成立后立即跟随指针且不遮挡目标；根插入位从零宽横向展开并通过 stable-key placement 动画把相邻快捷项连续挤开；收藏夹目标使用 1.05 缩放、primary 容器/2dp 描边和 8dp 阴影且离开后完整复原；单本封面、批次数量和落点效果文案与实际结果一致", "锁定只固定完整快捷栏的位置，不能改变任何快捷项操作或放置目标；未锁定离屏后只显示居中的 48dp chevron，完整栏从点击、反向滚动或拖拽悬停以同一 220ms 顶锚动画展开；解锁中途不得使书籍网格跳动；reduced motion 立即替换", "不得出现 interim 本地藏书标题、混合系统节点卡、AppBar + 或首字封面", "三点菜单使用统一 tonal Material 容器、48dp 条目；正常空间从按钮下方展开，受限空间仅允许 Material 安全视口 fallback，不漂移到父布局原点"),
            human = listOf("信息密度与扫描速度", "快捷书架创建、展开、锁定固定语义和长按选择的可发现性", "固定栏在大书架拖放时是否始终可靠可达", "锁定与未锁定的操作等价性", "菜单动作层级与锚点是否自然", "E-ink 分页节奏")),
        node("L02", "书架系统视图", ReviewNodeFamily.LIBRARY, AtlasRoute.LIBRARY,
            states(AtlasPageState.CONTENT, AtlasPageState.EMPTY, AtlasPageState.SELECTION, AtlasPageState.MUTATION),
            "进入继续、最近、稍后再读和休眠视图", "隐藏并重建系统节点", "修改稍后再读 membership 并验证其他节点保持自动",
            "验证最近阅读只包含持久进度，并按 progress.updatedAt 从新到旧排列；未开始书籍不得进入",
            visual = listOf("空态原因和恢复动作明确", "自动节点无虚假编辑入口"),
            human = listOf("系统节点与普通收藏夹的心理模型", "自动与手工 membership 是否无需教程即可理解")),
        node("L03", "历史", ReviewNodeFamily.LIBRARY, AtlasRoute.LIBRARY_HISTORY,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.EMPTY, AtlasPageState.ERROR, AtlasPageState.SELECTION, AtlasPageState.MUTATION, AtlasPageState.MODAL),
            "打开并删除单项", "清空后取消，再次清空并确认", "检查七天边界以及 Back 后的位置恢复",
            visual = listOf("相对时间和精确时间分组清晰", "危险操作层级正确且不暗示清除阅读进度"),
            human = listOf("时间表达是否自然", "清空操作是否制造不必要焦虑")),
        node("L04", "追更", ReviewNodeFamily.LIBRARY, AtlasRoute.LIBRARY_UPDATES,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.EMPTY, AtlasPageState.ERROR, AtlasPageState.OFFLINE, AtlasPageState.SELECTION, AtlasPageState.MUTATION, AtlasPageState.MODAL),
            "运行 slow、success、offline 和 error", "观察部分结果并确认已看过", "切换选择与布局",
            visual = listOf("只有一个工作指示且新项目增量出现", "日期、章节和动作对齐", "E-ink 使用静态工作 glyph"),
            human = listOf("已看过的语义", "大量更新时的扫描效率", "等待反馈是否可信")),
        node("L05", "收藏夹", ReviewNodeFamily.LIBRARY, AtlasRoute.LIBRARY_COLLECTION,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.EMPTY, AtlasPageState.SELECTION, AtlasPageState.MUTATION, AtlasPageState.MODAL),
            "进入根和子收藏夹", "创建、编辑、选择、移动 membership 与删除", "尝试创建第三层并切换三种布局",
            visual = listOf("子收藏夹和书籍作为 peers", "最大两层明确且创建管理动作不重复"),
            human = listOf("是否符合文件夹心智模型", "进入目标收藏夹的步骤成本")),
        node("L06", "智能规则", ReviewNodeFamily.LIBRARY, AtlasRoute.LIBRARY_COLLECTION_RULE,
            states(AtlasPageState.CONTENT, AtlasPageState.MUTATION, AtlasPageState.MODAL),
            "操作 picker、dropdown、checkbox、range 和自由文本", "制造非法规则并修正", "带未保存修改返回并检查草稿恢复",
            visual = listOf("内置数据使用选择器且错误就地显示", "上限、适用范围和保存状态可见"),
            human = listOf("用户能否预测规则结果", "错误文案是否帮助修正而非暴露内部实现")),
        node("L07", "标签", ReviewNodeFamily.LIBRARY, AtlasRoute.LIBRARY_TAGS,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.EMPTY, AtlasPageState.MUTATION, AtlasPageState.MODAL),
            "切换本地和来源", "搜索、排序和切换布局", "编辑本地标签并通过对应稳定触发身份打开每个标签行的锚定三点菜单；尝试操作来源标签",
            visual = listOf("所有权不纵向混排", "compact chip 无计数而 list row 有计数", "来源操作清晰只读", "标签行菜单在正常空间位于对应按钮下方；受限空间仅允许 Material 安全视口 fallback，删除项使用错误色并继续要求确认"),
            human = listOf("上百标签时的扫描效率", "所有权能否不逐字阅读即识别", "行级菜单是否与目标标签保持明确归属")),
        node("L08", "网站镜像", ReviewNodeFamily.LIBRARY, AtlasRoute.LIBRARY_MIRROR,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.EMPTY, AtlasPageState.ERROR, AtlasPageState.MUTATION, AtlasPageState.MODAL),
            "查看默认网站结构", "显式创建本地整理并展开节点", "执行校准、冻结、网站操作和 unresolved 场景",
            visual = listOf("创建前不显示虚假的本地区", "网站结构与本地整理边界明确", "本地动作不伪装成远端写入"),
            human = listOf("本地整理不会修改网站是否真正易懂", "远端操作的风险感")),
        node("B01", "书籍详情", ReviewNodeFamily.BOOK_READER, AtlasRoute.BOOK_DETAIL,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.ERROR, AtlasPageState.OFFLINE, AtlasPageState.MUTATION, AtlasPageState.UNRESOLVED, AtlasPageState.SELECTION, AtlasPageState.MODAL),
            "验证 135×180dp 封面在标准短标题状态与固定动作行下边缘对齐，并验证长标题末尾的标准 Material 3 Badge、作者、进度和评分", "验证评分下方固定且图标/颜色分离的书架与稍后再读状态按钮；未入库时直接点稍后再读必须原子本地入库并选中", "验证稍后再读切换时图标、字体字重、按钮颜色和无障碍状态一起改变，且两动作不占标签栏或 FAB", "验证长简介默认三行且展开全文贴在第三行末尾，再展开/收起", "评分、加号标签、缓存、刷新、离线、错误与 unresolved；使用三角 disclosure 展开/收起分卷目录", "操作真实未读过滤、独立排序方向图标和右上角二级动作，选择任意章节并确认 Reader 精确打开该章后返回；从 Library、Search 和 Source Home 进入同一 canonical Detail",
            "验证第一颗可见评分星形与标题、作者和进度的身份文字列起始边缘一致，同时五颗星各保留至少 48dp 触控目标和选择语义",
            "验证 Detail 的权威 summary 升级同一 BookIdentity 的 Library 元数据；加入书架、稍后再读和后续进度保存均保留 cover/author/canonical 字段",
            visual = listOf("Standard 封面为 135×180dp；标准短标题状态下封面与固定动作行下边缘对齐；连载中或已完结使用标准 Material 3 Badge，紧随完整标题末尾并与当前标题行视觉居中；长标题自然换行且状态不重叠、不导致标题截断", "作者、进度、评分后固定显示书架与稍后再读动作；书架使用 shelf 图标与 primary 色系，稍后再读使用 outline/filled bookmark 与 tertiary 选中色系", "稍后再读选中时图标、文字字重、容器色和内容色同时改变；未入库状态仍可操作", "标签区只有实际标签和行内加号；加入书架与稍后再读不占标签栏或 FAB，继续阅读/滚动导航保留为唯一主 FAB", "溢出简介默认三行且展开全文位于第三行末尾", "全文目录按来源分卷且卷可独立展开，当前卷或首卷默认展开", "卷行使用收起向右、展开向下的实心三角 disclosure，目录头排序保留带箭杆方向图标，两者轮廓不得复用", "全文目录、总章数、真实未读筛选和排序整合在单一无边框目录头", "已读浅灰常规字重且未读深色加粗带前导圆点", "下载图标与当前章节标记层级明确", "移出书架与网站动作只在 overflow", "FAB 不遮挡内容", "不存在本地详情中间页"),
            human = listOf("长标题与 inline 状态是否易扫读", "固定书架/稍后再读动作是否自然且状态差异无需试错", "上方身份区信息密度是否平衡", "三行简介是否足够且展开动作是否自然", "主操作优先级与页面密度", "数百章按分卷扫描效率", "卷展开与目录排序能否无需试错即区分", "评分可发现性", "章节状态与排序方向能否一眼识别", "二级动作是否无重复且作用域明确")),
        node("B02", "Reader 基础阅读", ReviewNodeFamily.BOOK_READER, AtlasRoute.BOOK_READER,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.ERROR, AtlasPageState.OFFLINE),
            "中心点击无波纹地双向显示或隐藏 chrome，并验证 chrome 可见时正文中央目标仍可收起；连续滚动拖拽不得误触 toggle", "使用左右点击区和分页键逐页移动，从章末继续前进进入紧邻下一章，再用上章、目录、设置和下章执行显式章节动作", "验证分页器按实际视口连续装入多个短段落、在测量行边界拆分长段落，并分别验证分页正文和连续滚动末尾完整避开常驻阅读信息/进度栏；遍历纯文本、纯插图、图文混排、回复流、搜索、书签和大图，验证每张插图独立加载、缓存、失败和有界重试",
            visual = listOf("140ms 淡入位移不打断正文", "正文仅避让常驻阅读信息/进度栏，顶部与展开式底部命令 chrome 继续覆盖且不触发重排", "连续滚动末块可完整滚到信息栏上方", "分页正文按视口填充而非每块独占一页，且不被信息栏裁切", "纯插图章节不被误判 wrong-page，图片按原比例呈现且单图失败不替换整章", "顶部仅返回、标题、书签和搜索", "底部 Material Slider、章节位置和四个动作层级清楚", "E-ink 冻结页面无变化"),
            human = listOf("连续阅读舒适度", "点击区滚动仲裁与误触", "章末连续翻页是否自然", "信息栏是否遮挡阅读终点", "插图清晰度、加载反馈与重试成本", "图文和回复流是否仍像一个阅读器", "chrome 是否打断注意力")),
        node("B03", "Reader 跳转与设置", ReviewNodeFamily.BOOK_READER, ReviewNodeKind.HIGH_RISK, AtlasRoute.BOOK_READER,
            states(AtlasPageState.CONTENT, AtlasPageState.MODAL),
            "直接点按 Material Slider 轨道或拖动都预览，并以最终目标只提交一次语义章节", "验证连续滚动保持连续 seek，分页和双页只提供实际页或跨页的离散 stop", "打开目录并确认默认部分态把当前章节置于相邻章节中间；以显式 展开全部 章 action 或上拉进入同一 Sheet 的全高目录，再选择非当前章节并确认精确进入所选章", "在目录、书签、搜索三页签间切换", "Standard 设置部分态在首屏同时显示四条加长 Slider 的排版快捷项和完整 2×2 快速动作网格；触发全高后观察一次同 Sheet 重组动画：四条排版控件留在上方并展开为完整行，快速 Chip 收起消失，对应设置迁移到下方页面、导航和设备完整控件；完整态向下拖动直接关闭，Back 反向恢复快速层",
            visual = listOf("track tap 与 drag preview 都和 commit 分离且只提交一次", "连续 seek 与离散页/跨页 stop 的模式差异明确", "目录从底部打开；部分态当前章节位于邻近章节中间，完整目录仅在显式展开后显示", "Standard 初始部分态无需滚动即可看到四条排版快捷项和两行四个有边界动作；Slider 使用扣除紧凑标签和值栏后的全部剩余宽度", "全高重组时排版控件保持空间连续并展开，快速网格以共享 200ms 以内 ease-out 动画收起，下方完整分组展开；reduced motion 立即完成", "稳定全高态只有上方四条完整排版行和下方页面/导航/设备控件，不同时出现快速 Chip；Back 恢复快速层且值不丢失", "全高下拉直接关闭且层级明确", "双页窗口约束明确"),
            human = listOf("seek 信心和取消安全感", "四页等短章节的离散目标是否不再误导", "附近章节跳转与全目录展开的成本", "2×2 Chip 网格是否清晰、不过重且首屏完整", "上拉扩展与全高下拉关闭是否符合直觉", "真实设备触感与动画舒适度", "设置密度与可理解性")),
        node("S01", "浏览", ReviewNodeFamily.SOURCE, AtlasRoute.BROWSE,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.EMPTY, AtlasPageState.ERROR, AtlasPageState.MUTATION),
            "从来源卡主 surface 一次点按进入 Home 并自动加载首屏", "点按四个等宽主 tab 和左右滑动 Pager，验证首次页面请求一次、已缓存页面零请求恢复且每页滚动位置独立", "推荐页直接显示源站首页按源顺序提供的当季新番、新书风云榜和本周会员推荐榜，不显示总推荐等指标筛选胶囊", "点按推荐分栏后的这本轻小说真厉害年度专题卡，验证只请求一次专题、显示文库与单行本两个源顺序分栏且不显示主 tabs；Toolbar Up 与 Android Back 都零请求恢复推荐页原滚动锚点", "展开 Tag 胶囊并观察标准动效，再选择另一项，验证面板关闭且恰好一次 scoped replacement；展开排序胶囊并选择另一项，验证相同行为或匹配缓存恢复；reduced motion 立即替换", "在 Tag 面板滚动到顶部或底部后继续同一拖动/甩动，验证手势仍由面板消费且父目录位置不变", "快速继续切换时旧请求被取消或失效；replacement/refresh 保留目录并显示 inline working/error", "向下滚动目录时验证共享 FAB 提供末尾；向上滚动时验证提供顶部；停止后保持最新目标", "滚动接近目录末尾并自动追加下一 cursor；验证 footer working/error-retry、Search、网站书架、登录验证和刷新保持可达", "选择真实网格条目进入 canonical Detail；离开后返回，验证已完成/进行中的请求不重复且封面命中 host cache",
            visual = listOf("Standard AppBar 只有单行来源标题、Search 和 scoped overflow；没有永久副标题或横向快捷操作带", "四个主 tabs 在整行内等宽分配，首尾 cell 关于屏幕中心镜像；filter bar、展开面板、section 标题和封面网格统一使用左右各 16dp gutter", "主 tabs 与 HorizontalPager 位置一致，切换不闪白、不回顶、不串用另一页滚动位置", "推荐页按源站顺序显示多个全宽标题加封面网格，不出现总推荐/总收藏/月推荐等 toplist 筛选", "年度专题使用推荐内容后的单个全宽 Material 导航卡而非第五主 tab；专题页 AppBar 标题切换、主 tabs 隐藏，并以两个全宽部门标题配自适应封面网格", "分类页由标准 Material 3 Surface、FilterChip、按钮与 IconButton 组成一个宽 Tag 胶囊和一个紧凑排序胶囊；选中项优先可见且 48dp 触控边界明确", "胶囊下方只展开当前筛选器的有界选项面板；每个换行的 FilterChip 组在可用宽度内水平居中且左右余量镜像，不满整行的尾行也居中而非贴左；展开/收起及箭头旋转使用不超过 200ms 的单一 ease-out 动效，reduced motion 立即完成；没有草稿、取消、查看结果或 modal filter sheet", "每个来源 section 使用全宽标题加自适应封面主导网格；标题和可选作者位于稳定元数据栏，禁止横向书单或一行一本列表", "replacement loading/error 保留当前目录并使用 scoped indicator；自动 append 保留已加载卡片并只在 footer 显示进度或重试", "共享控件为稳定尺寸的标准 Material 3 icon-only FAB；向下滚动显示末尾，向上滚动显示顶部，停止不反跳且不伸缩说明文字", "首屏封面自动加载且回访稳定", "宽屏仅增加网格列数，不切换到另一套 side rail 交互", "缺少 Home 能力时仍保留 Search/网站书架/登录验证"),
            human = listOf("一次点击意图是否明确", "初始视口中 chrome 与真实目录内容的比例", "四栏及目录左右留白是否严格对称", "tab 点按和左右滑动是否一致且不误触纵向网格", "缓存页面返回是否流畅可信", "推荐页源站分栏是否易于扫描", "年度专题卡是否足够可发现但不过度抢占推荐内容；专题返回是否保持上下文", "双胶囊的层级、展开范围、行内标签居中对称、动效、边界滚动隔离与即时提交是否清楚", "icon-only FAB 的方向切换是否符合滚动意图且不抖动", "自动续页是否无感且失败可恢复", "大量封面扫描效率", "刷新/登录/网站书架的作用域是否清楚")),
        node("S02", "聚合搜索", ReviewNodeFamily.SOURCE, AtlasRoute.SEARCH,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.EMPTY, AtlasPageState.ERROR, AtlasPageState.OFFLINE, AtlasPageState.MODAL),
            "输入但不提交并确认结果不变", "点击唯一 Search 并观察本地与真实线上结果增量", "制造线上单来源失败、去重、空态和离线；再用签名 fixture 重放",
            visual = listOf("只有一个提交动作和一个聚合进度", "无多 lane、高级筛选或教学文字", "重复结果合并清楚"),
            human = listOf("去重是否符合预期", "在线结果标识是否足够", "等待过程是否可信")),
        node("S03", "网站收藏", ReviewNodeFamily.SOURCE, AtlasRoute.BROWSE_SOURCE_REMOTE_LIBRARY,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.EMPTY, AtlasPageState.ERROR, AtlasPageState.SELECTION, AtlasPageState.MUTATION, AtlasPageState.MODAL),
            "显式刷新真实线上收藏并复制全部或选择项到本地", "确认 route entry、登录返回和恢复均不自动拉取，复制产生零网站写请求", "比较 Standard swipe 和可见等价动作并遍历 read capability、credential 与 source gate",
            visual = listOf("trailing 对齐和 selection bar 明确", "E-ink 间距与分页可读", "无重复来源 banner"),
            human = listOf("复制到本地是否会被误解为修改网站", "批量操作信心", "swipe 可发现性")),
        node("S04", "登录验证", ReviewNodeFamily.SOURCE, AtlasRoute.SOURCE_VERIFICATION,
            states(AtlasPageState.CONTENT, AtlasPageState.ERROR),
            "在真实 WebView 完成、取消、错误、Back、同来源重入，并从暂停的搜索、详情、目录和章节操作依次显式选择打开对应页面和使用当前页面", "确认重入在首个页面加载前恢复同一来源/声明 origin 的 cookie+exact-UA 会话；打开动作加载精确暂停 GET，只允许同 origin HTTPS 顶层重定向保留来源绑定，后续非重定向导航使绑定失效；当前页以原请求精确匹配一次、以最终页面 URL 作为响应元数据，经签名 parser 验证后返回，且不自动重试、导入、刷新或写网站", "验证 session 过期后的显式登录恢复、cookie+exact-UA handoff、bounded live retry，以及 raw snapshot 不持久化/不记录/不自动 replay 的边界",
            visual = listOf("宿主提示与网站区域边界明确", "来源身份仅在必要处出现", "状态和退出动作清晰"),
            human = listOf("登录信任感和钓鱼感", "凭据边界", "WebView Back 语义")),
        node("M01", "更多主页", ReviewNodeFamily.MORE, AtlasRoute.MORE,
            states(AtlasPageState.CONTENT),
            "逐项进入显示、阅读、数据、帮助和关于", "逐项返回并检查恢复",
            visual = listOf("分组紧凑、标题准确且无假入口", "宽屏内容不过宽"),
            human = listOf("信息架构和命名的可发现性")),
        node("M02", "显示设置", ReviewNodeFamily.MORE, AtlasRoute.MORE_DISPLAY,
            states(AtlasPageState.CONTENT, AtlasPageState.MUTATION, AtlasPageState.MODAL),
            "切换 profile、theme、dynamic color 和 E-ink redraw", "检查 unknown-newer、reset 和写入失败重试", "重启后比较 persisted 与 effective",
            visual = listOf("依赖、禁用和错误 banner 明确", "stored 与 effective 不混淆", "fontScale 2.0 可达"),
            human = listOf("profile 与 theme 边界", "重置风险", "设置是否过度技术化")),
        node("M03", "Reader 默认设置", ReviewNodeFamily.MORE, AtlasRoute.MORE_READER,
            states(AtlasPageState.CONTENT, AtlasPageState.MUTATION),
            "遍历排版、页面、导航和设备", "制造依赖组合并重启检查", "查看 E-ink 有效约束",
            visual = listOf("分组紧凑且无非法组合", "禁用原因不过度重复", "fontScale 2.0 无不可达项"),
            human = listOf("功能完整度与认知负担", "高级设置层级")),
        node("M04", "数据", ReviewNodeFamily.MORE, AtlasRoute.MORE_DATA,
            states(AtlasPageState.CONTENT, AtlasPageState.MODAL),
            "查看首次介绍", "执行 Tsuyomi/Hikari 导入、picker cancel 和导出选项", "打开报告并制造 cancel 或 error",
            visual = listOf("导入、导出和报告入口独立", "包含与排除项明确", "cancel 无成功假象"),
            human = listOf("隐私预期和迁移信心", "不可逆风险", "导入影响是否清楚")),
        node("M05", "导入报告", ReviewNodeFamily.MORE, AtlasRoute.MORE_DATA_REPORT,
            states(AtlasPageState.CONTENT, AtlasPageState.MODAL),
            "展开并折叠超过 50 条警告", "打开恢复 modal", "未解决时尝试导航并完成恢复",
            visual = listOf("计数、冲突、警告和结果层级清晰", "敏感内容脱敏且恢复 gate 可见"),
            human = listOf("用户能否据此决定下一步", "错误数量的压迫感")),
        node("M06", "帮助", ReviewNodeFamily.MORE, AtlasRoute.MORE_HELP,
            states(AtlasPageState.CONTENT, AtlasPageState.MODAL),
            "搜索并展开折叠帮助项", "重播五个 introduction", "开关全局介绍、重置 seen versions 并跳转界面重置",
            visual = listOf("搜索和 accordion 避免长列表", "介绍内容与实际首次入口一致"),
            human = listOf("帮助能否快速回答问题", "是否用教程掩盖本应自解释的 UI")),
        node("M07", "关于", ReviewNodeFamily.MORE, AtlasRoute.MORE_ABOUT,
            states(AtlasPageState.CONTENT, AtlasPageState.MODAL),
            "查看版本和许可证", "返回并检查状态恢复",
            visual = listOf("无假条目且长许可证可阅读", "版本信息稳定"),
            human = listOf("合法性和产品信任感")),
        cross("X01", "导航与状态恢复",
            "在生产包按 20-surface inventory 对照 Atlas、活动合同和真实状态所有权，记录并修正 Atlas 缺口；再测试滚动、选择和编辑状态下的 Up、系统 Back、根切换、旋转、分屏、后台和进程重启",
            visual = listOf("无错误转场、跳顶和焦点漂移；生产页不保留 Atlas fixture-only 控件、占位状态或原型命名"),
            human = listOf("Back 是否令人意外", "恢复位置是否符合任务语义", "Atlas 与真实数据结合后任务层级是否仍然自然")),
        cross("X02", "Modal、键盘与无障碍",
            "在生产包遍历 modal、键盘开闭、DPAD、TalkBack、Switch Access、危险操作默认焦点和 dismiss",
            visual = listOf("safe area、fontScale 2.0、焦点环、遮罩和 E-ink 全屏容器正确"),
            human = listOf("朗读是否简洁", "焦点顺序是否自然", "所有手势是否有等价动作")),
        cross("X03", "Standard 与 E-ink 对等",
            "E-ink restoration 后在 Standard 与真实 E-ink 设备执行相同生产任务并比较分页、选择、工作、modal、错误和设置",
            visual = listOf("功能对等且 E-ink 无不必要动画、透明叠层或颜色独占语义"),
            human = listOf("真实面板残影、刷新等待感、阅读疲劳和物理按键")),
        cross("X04", "长内容与自适应",
            "在生产包测试长中文、日文、Latin、emoji、RTL、无封面、fontScale 1.3/2.0、窄宽屏、分屏和 cutout",
            visual = listOf("无裁切和不可达动作，长标题可进入完整内容"),
            human = listOf("扫描节奏和文字压迫感")),
        cross("X05", "失败与等待",
            "对真实线上服务运行 slow、offline、recoverable-error、cancelled、unresolved、retry 和进程中断恢复；再用签名 fixture 重放",
            visual = listOf("working、error 和 result 不重复，E-ink 结果持续且 retry 不制造并发"),
            human = listOf("等待是否焦虑", "状态是否可信", "文案是否过度解释")),
        cross("X06", "审阅系统自身",
            "在真实审阅批次暂停自动操作、人工接管、修改 AI 草稿、切路由、关闭面板、杀进程、重新打开、导出、核对生产证据并切 buildId",
            visual = listOf("当前节点、来源、保存状态和最终 verdict 明确"),
            human = listOf("评论是否丢失", "AI 是否越权批准", "能否从任意节点继续")),
    )

    val byId: Map<String, ReviewNode> = nodes.associateBy(ReviewNode::id)

    private val defaultByRoute: Map<AtlasRoute, String> = mapOf(
        AtlasRoute.LIBRARY to "L01",
        AtlasRoute.LIBRARY_SYSTEM to "L02",
        AtlasRoute.LIBRARY_HISTORY to "L03",
        AtlasRoute.LIBRARY_UPDATES to "L04",
        AtlasRoute.LIBRARY_COLLECTION to "L05",
        AtlasRoute.LIBRARY_COLLECTION_CHILD to "L05",
        AtlasRoute.LIBRARY_COLLECTION_GRANDCHILD to "L05",
        AtlasRoute.LIBRARY_COLLECTION_RULE to "L06",
        AtlasRoute.LIBRARY_TAGS to "L07",
        AtlasRoute.LIBRARY_MIRROR to "L08",
        AtlasRoute.LIBRARY_MIRROR_FOLDER to "L08",
        AtlasRoute.LIBRARY_MIRROR_SUBFOLDER to "L08",
        AtlasRoute.BOOK_DETAIL to "B01",
        AtlasRoute.BOOK_READER to "B02",
        AtlasRoute.BROWSE to "S01",
        AtlasRoute.SEARCH to "S02",
        AtlasRoute.BROWSE_SOURCE_REMOTE_LIBRARY to "S03",
        AtlasRoute.SOURCE_VERIFICATION to "S04",
        AtlasRoute.MORE to "M01",
        AtlasRoute.MORE_DISPLAY to "M02",
        AtlasRoute.MORE_READER to "M03",
        AtlasRoute.MORE_DATA to "M04",
        AtlasRoute.MORE_DATA_REPORT to "M05",
        AtlasRoute.MORE_HELP to "M06",
        AtlasRoute.MORE_ABOUT to "M07",
    )

    init {
        require(nodes.map(ReviewNode::id).distinct().size == nodes.size) { "review node ids must be unique" }
        require(defaultByRoute.keys == AtlasRoute.entries.toSet()) { "every AtlasRoute must have a default review node" }
    }

    fun resolve(context: AtlasContext): ReviewNode {
        if (context.route == AtlasRoute.LIBRARY_SYSTEM ||
            (context.route == AtlasRoute.LIBRARY && context.libraryView in systemViews)
        ) return byId.getValue("L02")
        if (context.route == AtlasRoute.BOOK_READER &&
            (context.state == AtlasPageState.MODAL || context.readerSeekPreview != null)
        ) return byId.getValue("B03")
        return byId.getValue(defaultByRoute.getValue(context.route))
    }

    fun defaultForRoutePath(route: String): ReviewNode? =
        AtlasRoute.parse(route)?.let { byId.getValue(defaultByRoute.getValue(it)) }

    private val systemViews = setOf(
        AtlasLibraryView.CONTINUE,
        AtlasLibraryView.RECENT,
        AtlasLibraryView.READ_LATER,
        AtlasLibraryView.DORMANT,
    )

    private fun node(
        id: String,
        title: String,
        family: ReviewNodeFamily,
        route: AtlasRoute,
        requiredStates: Set<AtlasPageState>,
        vararg operations: String,
        visual: List<String>,
        human: List<String>,
    ): ReviewNode = node(id, title, family, ReviewNodeKind.SURFACE, route, requiredStates, *operations, visual = visual, human = human)

    private fun node(
        id: String,
        title: String,
        family: ReviewNodeFamily,
        kind: ReviewNodeKind,
        route: AtlasRoute,
        requiredStates: Set<AtlasPageState>,
        vararg operations: String,
        visual: List<String>,
        human: List<String>,
    ): ReviewNode = ReviewNode(id, title, family, kind, route, requiredStates, operations.toList(), visual, human)

    private fun cross(
        id: String,
        title: String,
        operation: String,
        visual: List<String>,
        human: List<String>,
    ): ReviewNode = ReviewNode(
        id = id,
        title = title,
        family = ReviewNodeFamily.CROSS_CUTTING,
        kind = ReviewNodeKind.CROSS_CUTTING,
        route = null,
        requiredStates = emptySet(),
        operations = listOf(operation),
        visualChecks = visual,
        humanOnlyChecks = human,
    )

    private fun states(vararg states: AtlasPageState): Set<AtlasPageState> = states.toSet()
}
