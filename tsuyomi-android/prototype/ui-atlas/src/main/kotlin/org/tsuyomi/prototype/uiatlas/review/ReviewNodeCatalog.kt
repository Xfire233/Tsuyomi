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
)

object ReviewNodeCatalog {
    const val VERSION = 2

    val nodes: List<ReviewNode> = listOf(
        node("L01", "混合书架", ReviewNodeFamily.LIBRARY, AtlasRoute.LIBRARY,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.EMPTY, AtlasPageState.ERROR, AtlasPageState.OFFLINE, AtlasPageState.SELECTION, AtlasPageState.MUTATION, AtlasPageState.MODAL),
            "切换全部、继续、最近、稍后再读、休眠、收藏夹与镜像", "切换三种布局并长按选择", "从 AppBar 创建并打开系统节点、收藏夹、镜像和书籍",
            visual = listOf("手机三列、宽屏自适应且卡片等高", "长标题和无封面 fallback 可进入完整内容", "节点与书籍同流但可快速区分，且只有一个创建入口"),
            human = listOf("信息密度与扫描速度", "创建和长按选择的可发现性", "E-ink 分页节奏")),
        node("L02", "书架系统视图", ReviewNodeFamily.LIBRARY, AtlasRoute.LIBRARY,
            states(AtlasPageState.CONTENT, AtlasPageState.EMPTY, AtlasPageState.SELECTION, AtlasPageState.MUTATION),
            "进入继续、最近、稍后再读和休眠视图", "隐藏并重建系统节点", "修改稍后再读 membership 并验证其他节点保持自动",
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
            "切换本地和来源", "搜索、排序和切换布局", "编辑本地标签并尝试操作来源标签",
            visual = listOf("所有权不纵向混排", "compact chip 无计数而 list row 有计数", "来源操作清晰只读"),
            human = listOf("上百标签时的扫描效率", "所有权能否不逐字阅读即识别")),
        node("L08", "网站镜像", ReviewNodeFamily.LIBRARY, AtlasRoute.LIBRARY_MIRROR,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.EMPTY, AtlasPageState.ERROR, AtlasPageState.MUTATION, AtlasPageState.MODAL),
            "查看默认网站结构", "显式创建本地整理并展开节点", "执行校准、冻结、网站操作和 unresolved 场景",
            visual = listOf("创建前不显示虚假的本地区", "网站结构与本地整理边界明确", "本地动作不伪装成远端写入"),
            human = listOf("本地整理不会修改网站是否真正易懂", "远端操作的风险感")),
        node("B01", "书籍详情", ReviewNodeFamily.BOOK_READER, AtlasRoute.BOOK_DETAIL,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.ERROR, AtlasPageState.OFFLINE, AtlasPageState.MUTATION, AtlasPageState.UNRESOLVED, AtlasPageState.SELECTION, AtlasPageState.MODAL),
            "评分、添加标签、稍后再读和缓存", "运行刷新、离线、错误与 unresolved", "操作章节选择、排序、过滤、跳转并进入 Reader 后返回",
            visual = listOf("评分固定在 header", "标签、添加和稍后再读几何一致", "完整章节已整合且 FAB 不遮挡内容"),
            human = listOf("主操作优先级与页面密度", "评分可发现性", "章节扫描效率和 FAB 干扰程度")),
        node("B02", "Reader 基础阅读", ReviewNodeFamily.BOOK_READER, AtlasRoute.BOOK_READER,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.ERROR, AtlasPageState.OFFLINE),
            "中心点击显示 chrome", "前后翻页并切换滚动和分页", "操作目录、搜索、书签、immersive、文本和图片章节",
            visual = listOf("正文不被栏遮挡", "点击区域、章节 ticks 和当前位置可见", "E-ink 无动态装饰"),
            human = listOf("连续阅读舒适度", "点击区域误触与阅读节奏", "chrome 是否打断注意力")),
        node("B03", "Reader 跳转与设置", ReviewNodeFamily.BOOK_READER, ReviewNodeKind.HIGH_RISK, AtlasRoute.BOOK_READER,
            states(AtlasPageState.CONTENT, AtlasPageState.MODAL),
            "执行 seek hold、move、cancel、commit 和 return origin", "Standard 设置从 partial 拉到 full 并用 scrim 和 Back 关闭", "E-ink 打开全屏设置、遍历全部分组并返回",
            visual = listOf("preview 与 commit 分离且只提交一次", "Standard 首屏四项完整", "E-ink 为不透明页面且无 sheet 手势"),
            human = listOf("seek 信心和取消安全感", "真实设备触感与动画舒适度", "E-ink 残影、刷新和音量键")),
        node("S01", "浏览", ReviewNodeFamily.SOURCE, AtlasRoute.BROWSE,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.EMPTY, AtlasPageState.ERROR, AtlasPageState.MUTATION),
            "搜索、导入、安装或更新来源", "打开来源、网站收藏和验证", "检查空态和错误态",
            visual = listOf("已安装和可安装来源使用一致层级", "品牌 fallback 可识别且动作样式统一"),
            human = listOf("来源可信度", "安装动作风险感", "卡片信息密度")),
        node("S02", "聚合搜索", ReviewNodeFamily.SOURCE, AtlasRoute.SEARCH,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.EMPTY, AtlasPageState.ERROR, AtlasPageState.OFFLINE, AtlasPageState.MODAL),
            "输入但不提交并确认结果不变", "点击唯一 Search 并观察本地与远端增量", "制造单来源失败、去重、空态和离线",
            visual = listOf("只有一个提交动作和一个聚合进度", "无多 lane、高级筛选或教学文字", "重复结果合并清楚"),
            human = listOf("去重是否符合预期", "在线结果标识是否足够", "等待过程是否可信")),
        node("S03", "网站收藏", ReviewNodeFamily.SOURCE, AtlasRoute.BROWSE_SOURCE_REMOTE_LIBRARY,
            states(AtlasPageState.LOADING, AtlasPageState.CONTENT, AtlasPageState.EMPTY, AtlasPageState.ERROR, AtlasPageState.SELECTION, AtlasPageState.MUTATION, AtlasPageState.MODAL),
            "刷新、全部复制和选择部分", "操作目标 picker 与单项动作", "比较 Standard swipe 和可见等价动作并遍历 capability gate",
            visual = listOf("trailing 对齐和 selection bar 明确", "E-ink 间距与分页可读", "无重复来源 banner"),
            human = listOf("复制到本地是否会被误解为修改网站", "批量操作信心", "swipe 可发现性")),
        node("S04", "登录验证", ReviewNodeFamily.SOURCE, AtlasRoute.SOURCE_VERIFICATION,
            states(AtlasPageState.CONTENT, AtlasPageState.ERROR),
            "完成、取消、错误和 Back", "确认宿主状态反馈", "生产阶段补测真实 WebView 返回",
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
            "在滚动、选择和编辑状态测试 Up、系统 Back、根切换、旋转、分屏、后台和进程重启",
            visual = listOf("无错误转场、跳顶和焦点漂移"),
            human = listOf("Back 是否令人意外", "恢复位置是否符合任务语义")),
        cross("X02", "Modal、键盘与无障碍",
            "遍历 modal、键盘开闭、DPAD、TalkBack、Switch Access、危险操作默认焦点和 dismiss",
            visual = listOf("safe area、fontScale 2.0、焦点环、遮罩和 E-ink 全屏容器正确"),
            human = listOf("朗读是否简洁", "焦点顺序是否自然", "所有手势是否有等价动作")),
        cross("X03", "Standard 与 E-ink 对等",
            "在两种 profile 执行相同任务并比较分页、选择、工作、modal、错误和设置",
            visual = listOf("功能对等且 E-ink 无不必要动画、透明叠层或颜色独占语义"),
            human = listOf("真实面板残影、刷新等待感、阅读疲劳和物理按键")),
        cross("X04", "长内容与自适应",
            "测试长中文、日文、Latin、emoji、RTL、无封面、fontScale 1.3/2.0、窄宽屏、分屏和 cutout",
            visual = listOf("无裁切和不可达动作，长标题可进入完整内容"),
            human = listOf("扫描节奏和文字压迫感")),
        cross("X05", "失败与等待",
            "运行 slow、offline、recoverable-error、cancelled、unresolved、retry 和进程中断恢复",
            visual = listOf("working、error 和 result 不重复，E-ink 结果持续且 retry 不制造并发"),
            human = listOf("等待是否焦虑", "状态是否可信", "文案是否过度解释")),
        cross("X06", "审阅系统自身",
            "暂停自动操作、人工接管、修改 AI 草稿、切路由、关闭面板、杀进程、重新打开、导出并切 buildId",
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
