/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.prototype.uiatlas.screens.reader

import org.tsuyomi.prototype.uiatlas.fixtures.SourceAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView

/** Complete deterministic documents used to review every host-owned Reader content primitive. */
internal object ReaderAtlasFixtures {
    fun documentFor(view: AtlasLibraryView, chapterNumber: Int): ReaderDocument = when (view) {
        AtlasLibraryView.CONTINUE -> mixedMediaDocument(chapterNumber)
        AtlasLibraryView.RECENT -> replyStreamDocument(chapterNumber)
        else -> proseDocument(chapterNumber)
    }

    fun previewSnippet(chapterNumber: Int): String = proseParagraphs[(chapterNumber - 1).mod(proseParagraphs.size)].take(56)

    private fun proseDocument(chapterNumber: Int): ReaderDocument {
        val chapter = SourceAtlasFixtures.chapters[(chapterNumber - 1).coerceIn(SourceAtlasFixtures.chapters.indices)]
        return ReaderDocument(
            id = "prose-$chapterNumber",
            title = chapter.title,
            kind = ReaderDocumentKind.PROSE,
            blocks = buildList {
                add(ReaderHeading("prose-title", chapter.title, level = 1))
                proseParagraphs.forEachIndexed { index, paragraph ->
                    when (index) {
                        3 -> add(ReaderHeading("prose-section-river", "一、河埠头的空船", level = 2))
                        8 -> add(
                            ReaderQuote(
                                id = "prose-watch-note",
                                content = listOf(
                                    ReaderInline.Plain("灯不是为了照亮整条路。它只负责告诉走到这里的人："),
                                    ReaderInline.Strong("门还开着"),
                                    ReaderInline.Plain("。"),
                                ),
                                attribution = "《纸灯巷守夜簿》第七码",
                            ),
                        )
                        11 -> add(ReaderDivider("prose-mid-divider"))
                        12 -> add(ReaderHeading("prose-section-letter", "二、没有寄出的回信", level = 2))
                        16 -> add(
                            ReaderListBlock(
                                id = "prose-letter-list",
                                ordered = false,
                                items = listOf(
                                    listOf(ReaderInline.Plain("旧渡口在退潮后会露出第七码头。")),
                                    listOf(ReaderInline.Plain("看见三短一长的灯号时，不要立即靠岸。")),
                                    listOf(ReaderInline.Plain("如果纸灯熄灭，沿石阶返回，不要回头。")),
                                ),
                            ),
                        )
                        19 -> add(ReaderHeading("prose-section-home", "三、灯下归人", level = 2))
                    }
                    add(
                        ReaderParagraph(
                            id = "prose-paragraph-${index + 1}",
                            content = when (index) {
                                2 -> listOf(
                                    ReaderInline.Plain("门楣下刻着两个极浅的字："),
                                    ReaderInline.Ruby("归舟", "guī zhōu"),
                                    ReaderInline.Plain("。许砚伸手摸过笔画，指腹沾了一层潮湿的石粉。"),
                                )
                                7 -> listOf(
                                    ReaderInline.Plain("更梆子里夹着一张窄纸，旧句已经被划去："),
                                    ReaderInline.Strike("今夜无船"),
                                    ReaderInline.Plain("，旁边新添了四个字："),
                                    ReaderInline.Emphasis("灯下等你"),
                                    ReaderInline.Plain("。"),
                                )
                                15 -> listOf(
                                    ReaderInline.Plain("信尾只有一串约定好的灯号："),
                                    ReaderInline.Code("··— / ·—· / —··"),
                                    ReaderInline.Plain("。许砚默念一遍，终于认出那是父亲教他的返航记号。"),
                                )
                                21 -> listOf(
                                    ReaderInline.Plain("关于旧河道的注记收录在"),
                                    ReaderInline.Link("《南河渡口沿革》", "tsuyomi://note/south-river-history"),
                                    ReaderInline.Plain("；他决定天亮后再去核对，而不是让答案替代眼前的人。"),
                                )
                                else -> listOf(ReaderInline.Plain(paragraph))
                            },
                        ),
                    )
                }
            },
        )
    }

    private fun mixedMediaDocument(chapterNumber: Int): ReaderDocument = ReaderDocument(
        id = "mixed-$chapterNumber",
        title = "第${chapterNumber}章 · 河图残卷",
        kind = ReaderDocumentKind.MIXED_MEDIA,
        blocks = listOf(
            ReaderHeading("mixed-title", "河图残卷", level = 1),
            paragraph("mixed-opening", "雨停后，木匣里的旧图重新显出颜色。许砚把它铺在柜台上，用四枚铜钱压住卷起的边角。纸面先浮出三条河道，然后是山脊、石阶和一盏悬在半空的纸灯。"),
            ReaderParagraph(
                "mixed-emphasis",
                listOf(
                    ReaderInline.Plain("他先辨认出"),
                    ReaderInline.Strong("三处渡口"),
                    ReaderInline.Plain("，随后看见角落写着"),
                    ReaderInline.Ruby("归舟", "guī zhōu"),
                    ReaderInline.Plain("二字。墨色新旧不一，至少经过两次补绘。"),
                ),
            ),
            ReaderImage("river-map", "河图残卷", "泛黄纸面上绘有三条河道、山脊、七码头石阶和一盏红色纸灯", "图一 · 木匣中的河道手绘图；缺损处由宿主以中性色标示", 1.45f),
            paragraph("mixed-map-reading", "图的北缘有一道水渍，恰好遮住通往城外的支流。许砚把灯移到侧面，凹下去的笔痕在斜光里连成另一条路线：它绕过旧桥，从芦苇荡后方进入七码头。"),
            ReaderQuote(
                "map-note",
                listOf(ReaderInline.Plain("潮生时看山，潮退时看灯。"), ReaderInline.Strike("切勿独行"), ReaderInline.Plain("若见双影，等第三声梆响。")),
                "残卷背面题记",
            ),
            ReaderHeading("mixed-clue-title", "校验记录", level = 2),
            ReaderListBlock(
                "clues",
                ordered = true,
                items = listOf(
                    listOf(ReaderInline.Plain("确认北侧渡口共有七码石阶，第三层刻有缺口。")),
                    listOf(ReaderInline.Plain("比对旧信中的灯号："), ReaderInline.Code("短、短、长 / 短、长、短")),
                    listOf(ReaderInline.Plain("检查图中两处被覆盖的年份，排除后人误抄。")),
                    listOf(ReaderInline.Plain("在天亮前返回纸灯巷，并留下可追溯的校验记录。")),
                ),
            ),
            ReaderImage("lantern-detail", "纸灯纹样细节", "放大的纸灯剪影中隐藏着水鸟、月牙与三枚方向标记", "图二 · 放大后的剪纸纹样；点按进入宿主大图查看", 0.78f),
            paragraph("mixed-image-analysis", "水鸟的喙朝向东南，翅尖却指向西岸。若把月牙视作退潮时刻，三枚方向标记正好对应图上的三处渡口。图像不是装饰，而是路线的一部分。"),
            ReaderTableBlock(
                "tide-table",
                headers = listOf("时刻", "水位", "河面记号", "可通行路线"),
                rows = listOf(
                    listOf("子时", "缓退", "石阶露出三层", "北岸旧桥"),
                    listOf("丑时", "最低", "双灯影重合", "芦苇荡支流"),
                    listOf("寅时", "回涨", "桥影指向东南", "七码头"),
                    listOf("卯时", "封航", "纸灯熄灭", "停止通行"),
                ),
            ),
            ReaderCodeBlock("signal", "灯号抄录", "··—  /  ·—·  /  —··\n北岸  /  等候  /  归航"),
            ReaderAttachment("attachment", "河图残卷题记.txt", "UTF-8 · 2.4 KB · SHA-256 已验证 · 已下载"),
            ReaderDivider("mixed-divider"),
            ReaderHeading("mixed-conclusion-title", "复原结果", level = 2),
            paragraph("mixed-conclusion-1", "许砚没有把缺失的河道直接补在原图上。他另取一张透明薄纸，只描出能够被笔痕、潮表和灯号共同证明的部分；无法确定的岔路仍保持空白。"),
            ReaderParagraph(
                "mixed-link",
                listOf(
                    ReaderInline.Plain("旧桥年代可继续查阅"),
                    ReaderInline.Link("《南河渡口沿革》", "tsuyomi://note/south-river"),
                    ReaderInline.Plain("。链接、附件和大图都由宿主处理，不把执行能力交给来源内容。"),
                ),
            ),
            paragraph("mixed-conclusion-2", "天亮前，薄纸上的路线终于接到纸灯巷后门。许砚卷好残图，听见巷外传来第三声梆响。有人踩着积水走近，脚步与守夜簿里记了十二年的那一行字完全相同。"),
        ),
    )

    private fun replyStreamDocument(chapterNumber: Int): ReaderDocument = ReaderDocument(
        id = "thread-$chapterNumber",
        title = "第${chapterNumber}话讨论 · 灯影从哪里来",
        kind = ReaderDocumentKind.REPLY_STREAM,
        blocks = listOf(
            ReaderHeading("thread-title", "灯影从哪里来", level = 1),
            paragraph("thread-summary", "本帖整理第十二章的灯影、潮汐和旧桥线索。回复按稳定楼层与 postId 排列；引用、图片、附件和编辑记录都属于正文阅读位置。"),
            post(
                id = "post-1082", floor = "楼主", author = "河岸听雨", time = "08-22 21:14", original = true,
                blocks = listOf(
                    paragraph("post-1082-body-1", "我把第十二章提到的三处灯影画在一起，发现它们并不是同一盏灯的倒影。第一处随水面移动，第二处固定在桥洞下，第三处只在退潮后出现。"),
                    ReaderImage("post-1082-image", "三处灯影对照", "三张河面灯影的并排对照图，分别标注水流方向、桥洞位置和拍摄时刻", "楼主上传 · 1920×1080 · 点按查看原图", 1.8f),
                    paragraph("post-1082-body-2", "如果把第三处视为隐藏渡口，那么残卷上缺失的路线就能闭合。我暂时没有找到能证明它通向七码头的直接文字。"),
                ),
            ),
            post(
                id = "post-1087", floor = "2楼", author = "雾都棋士", time = "08-22 21:37", original = false,
                blocks = listOf(
                    ReaderReplyReference("reply-1082", "post-1082", "楼主", "河岸听雨", "第三处只在退潮后出现……"),
                    ReaderParagraph(
                        "post-1087-body",
                        listOf(
                            ReaderInline.Plain("第二张图右下角的水纹方向相反，可能是另一条支流。"),
                            ReaderInline.Strong("建议对照第九章的图卷"),
                            ReaderInline.Plain("，尤其是纸灯下方那枚被裁掉一半的方向标。"),
                        ),
                    ),
                ),
            ),
            post(
                id = "post-1091", floor = "3楼", author = "纸上航线", time = "08-22 21:48", original = false,
                blocks = listOf(
                    ReaderReplyReference("reply-1087", "post-1087", "2楼", "雾都棋士", "可能是另一条支流……"),
                    ReaderQuote("post-1091-quote", listOf(ReaderInline.Plain("潮生时看山，潮退时看灯。")), "残卷背面"),
                    paragraph("post-1091-body", "题记把“山”和“灯”分在不同水位观察，说明路线至少有两套参照物。楼主的三张图拍摄时间不同，不能只按灯的位置叠加。"),
                ),
            ),
            post(
                id = "post-1095", floor = "4楼", author = "山中邮差", time = "08-22 22:03 · 已编辑", original = false,
                blocks = listOf(
                    paragraph("post-1095-body", "我找到了旧版 TXT。它的换行和新版网页不同，但宿主标准化后段落、引用和灯号顺序一致。下面是校对文件。"),
                    ReaderAttachment("post-1095-attachment", "第九章旧版校对记录.txt", "UTF-8 · 6.1 KB · 已下载"),
                ),
            ),
            post(
                id = "post-1104", floor = "5楼", author = "无声渡口", time = "08-22 22:26", original = false,
                blocks = listOf(
                    ReaderReplyReference("reply-1095", "post-1095", "4楼", "山中邮差", "宿主标准化后段落、引用和灯号顺序一致……"),
                    ReaderListBlock(
                        "post-1104-list",
                        ordered = false,
                        items = listOf(
                            listOf(ReaderInline.Plain("旧版把“七码头”写作“第七码头”。")),
                            listOf(ReaderInline.Plain("灯号之间原本有全角空格。")),
                            listOf(ReaderInline.Plain("图注缺少拍摄时刻，不应参与时间排序。")),
                        ),
                    ),
                ),
            ),
            post(
                id = "post-1110", floor = "6楼", author = "南河档案室", time = "08-22 22:41", original = false,
                blocks = listOf(
                    ReaderParagraph(
                        "post-1110-body",
                        listOf(
                            ReaderInline.Plain("档案索引里有一条相关记录："),
                            ReaderInline.Link("南河旧桥修缮表（民国二十六年）", "tsuyomi://archive/bridge-1937"),
                            ReaderInline.Plain("。其中的桥墩编号与图二一致，但开放方向仍需潮表佐证。"),
                        ),
                    ),
                    ReaderTableBlock(
                        "post-1110-table",
                        headers = listOf("桥墩", "旧编号", "残卷标记"),
                        rows = listOf(listOf("东一", "甲-3", "水鸟"), listOf("中二", "乙-1", "月牙"), listOf("西四", "丁-7", "纸灯")),
                    ),
                ),
            ),
            post(
                id = "post-1122", floor = "7楼", author = "河岸听雨", time = "08-22 23:08 · 楼主", original = true,
                blocks = listOf(
                    ReaderReplyReference("reply-1110", "post-1110", "6楼", "南河档案室", "桥墩编号与图二一致……"),
                    paragraph("post-1122-body-1", "感谢补充。我按潮表重新排列图片后，第三处灯影确实对应西四桥墩，而不是隐藏渡口本身。先前结论需要修正。"),
                    ReaderParagraph(
                        "post-1122-body-2",
                        listOf(
                            ReaderInline.Strike("第三处灯影就是七码头入口。"),
                            ReaderInline.Plain("更正：第三处灯影是确认西四桥墩的参照，入口仍要等第三声梆响后从芦苇荡支流进入。"),
                        ),
                    ),
                    ReaderCodeBlock("post-1122-signal", "最终灯号", "··— / ·—· / —··"),
                ),
            ),
        ),
    )

    private fun paragraph(id: String, text: String) = ReaderParagraph(id, listOf(ReaderInline.Plain(text)))

    private fun post(
        id: String,
        floor: String,
        author: String,
        time: String,
        original: Boolean,
        blocks: List<ReaderBlock>,
    ) = ReaderPost(id, floor, author, time, original, blocks)

    private val proseParagraphs = listOf(
        "雨从傍晚一直落到二更。纸灯巷的石板被洗得发亮，屋檐水沿着瓦当连成一排细线。许砚提着新换过灯芯的风灯，从巷口开始逐户查看门牌。守夜簿上记着今晚应亮二十四盏灯，可他数到尽头，只见二十三点暖黄。",
        "缺的那一盏属于巷尾的旧邮局。邮局停用十二年，木门上仍挂着褪色的铜牌。许砚每夜都会经过，却从没见门缝里透出光。今晚，门把手是温的，锁孔附近留着刚被雨水冲淡的泥痕。",
        "他没有立刻推门，而是绕到侧墙。墙根长着一排薄荷，只有正对后窗的一株被人踩弯。窗纸后传来纸张翻动的声音，慢而均匀，像有人在核对一封很长的信。",
        "河埠头离邮局不到百步。许砚赶到时，系船的铁环还在晃，水面却看不见船。雾从河心向岸边铺开，贴着石阶一层层抬高，直到第七层台阶完全消失。",
        "石阶旁放着一只旧木箱，箱盖用邮局的麻绳捆了三道。绳结不是本地常用的活扣，而是船员在风浪里固定货物的双套结。许砚解开第一道时，河对岸亮起一盏红灯。",
        "箱里没有货物，只有一叠被油纸包好的信。信封上的年份从十二年前一直排到上个月，收件人全是纸灯巷的住户，寄件地址却写着已经拆除的七码头。",
        "最上面一封写给许砚。笔迹瘦长，末尾习惯性地向上挑，和父亲留在守夜簿上的签名一样。他把信举到灯下，纸张没有霉斑，封口的浆糊甚至还带着淡淡米香。",
        "远处又响了一声梆。红灯从对岸移到河心，水面却没有船桨划过的波纹。许砚收好信，按照纸条上的新句走下石阶，每一步都先用更梆子探一探水下。",
        "第七码石阶下藏着一道横向凹槽，宽度刚好容一只脚。许砚侧身站稳，发现桥洞后方有一条被芦苇遮住的窄河。红灯就在窄河深处，忽明忽暗，像在回答岸上的梆声。",
        "他敲了两短一长。灯停了一瞬，随后回以一短一长一短。那是父亲教过他的旧航灯语：前方有障碍，等待引路。许砚握紧灯柄，没有继续向前。",
        "雾里传来铁链拖过船舷的声音。一条乌篷船慢慢显出轮廓，船头没有人，舱门却从里面推开半寸。许砚看见一只湿透的邮袋，袋口别着纸灯巷早已作废的投递牌。",
        "邮袋里的信按门牌顺序捆好，每一封都写着不同的迟到理由：洪水、封航、地址改名、收件人离开。最后一捆没有理由，只有同一句批注——等待守夜人确认灯还亮着。",
        "许砚回到邮局，把信一封封摊在长柜台上。屋里的人已经离开，煤油灯却仍燃着。账本翻在最后一页，日期是父亲失踪的那天，下面留着一行没有写完的投递记录。",
        "他拆开写给自己的信。父亲没有解释十二年的去向，只写了旧河道会在特定潮位重新出现，也写了守夜人的灯并非路标，而是确认岸上仍有人等候的回执。",
        "窗外的雨变小了。许砚把今晚的潮位、梆声和灯号逐项记入新页，不替未知的部分编造结论。他在“投递结果”一栏停了很久，最后写下“船已到岸，寄件人未确认”。",
        "信纸夹层里掉出一张窄地图。地图只画了旧桥到纸灯巷的半段路线，另一半用灯号代替。每组灯号后都标着一户门牌，仿佛整条巷子的灯能在夜里拼成一封完整回信。",
        "许砚先去敲开住在一号院的陶婆婆。她接过迟到十一年的信，没有问是谁送来的，只让他等一等。片刻后，院里那盏多年不用的青纱灯重新亮起。",
        "从一号院到巷尾，灯一盏接一盏被点亮。有人沉默地收信，有人读到一半便关上门，也有人说收件人已经不在，请许砚把信放在旧相框旁。每一次回应，他都如实记在守夜簿里。",
        "轮到自己的门牌时，许砚没有把父亲的信带进屋。他坐在门槛上读完第二遍，把那张半幅地图压在膝头。河面传来第三声梆响，远处的红灯开始向下游移动。",
        "他终于明白，船不会因为某个人追赶就停下。它只在潮位、灯号和岸上的回执同时满足时靠岸。十二年前缺失的不是路线，而是最后一盏确认有人等候的灯。",
        "许砚取下自家门前那盏蒙尘的纸灯，换油、剪芯、擦净灯罩。火苗第一次窜起时很不稳定，几次几乎被风吹灭。他用手护着，直到暖光落在门牌和守夜簿的新页上。",
        "天将亮时，乌篷船已经看不见了。邮局长柜台上多出一枚湿漉漉的七码头邮戳，日期停在今天。许砚把它收进木盒，在投递记录最后写下：纸灯巷二十四盏灯，全部回应。",
        "巷口卖早点的人推开窗，第一缕蒸汽混进尚未散尽的河雾。许砚合上守夜簿，听见身后有人踩过青石板。那脚步不急不缓，在他门前停住。",
        "“信收到了吗？”来人问。许砚没有立刻回头。他先看了一眼门上的纸灯，确认火苗安稳，才把手按在木盒上回答：“收到了。路也记下了。”",
    )
}
