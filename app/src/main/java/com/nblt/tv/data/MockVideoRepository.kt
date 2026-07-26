package com.nblt.tv.data

import com.nblt.tv.model.VideoItem
import com.nblt.tv.util.FormatUtils

object MockVideoRepository {
    val videos = listOf(
        VideoItem(1, "BVMOCK000001", 0, "", "Compose TV 从入门到上手", "青空实验室", 128_000, 1122, "用一个电视端界面快速理解 Compose 在大屏上的布局、焦点和状态管理。", FormatUtils.accentFor(0)),
        VideoItem(2, "BVMOCK000002", 0, "", "周末游戏开发日志 04", "像素工坊", 83_000, 1456, "记录一个复古横版游戏从角色手感到关卡节奏的调整过程。", FormatUtils.accentFor(1)),
        VideoItem(3, "BVMOCK000003", 0, "", "客厅影音设备怎么选", "小陈评测", 312_000, 669, "围绕电视、音箱和盒子的实际体验，聊聊普通家庭更值得买什么。", FormatUtils.accentFor(2)),
        VideoItem(4, "BVMOCK000004", 0, "", "十分钟看懂 Kotlin 协程", "代码咖啡馆", 221_000, 635, "用可视化例子解释挂起、调度和结构化并发。", FormatUtils.accentFor(3)),
        VideoItem(5, "BVMOCK000005", 0, "", "城市夜景延时摄影合集", "光影漫游", 176_000, 478, "从天桥到河岸，收集了一组适合大屏观看的夜景延时镜头。", FormatUtils.accentFor(4)),
        VideoItem(6, "BVMOCK000006", 0, "", "手作机械键盘装配实录", "桌面研究所", 64_000, 927, "一次安静的装配过程，包括轴体、卫星轴和键帽搭配。", FormatUtils.accentFor(5)),
        VideoItem(7, "BVMOCK000007", 0, "", "Android TV 应用设计要点", "大屏设计社", 194_000, 830, "讨论电视端的信息密度、遥控器路径和焦点反馈。", FormatUtils.accentFor(6)),
        VideoItem(8, "BVMOCK000008", 0, "", "厨房新手也能做的晚餐", "认真吃饭", 447_000, 561, "三道简单家常菜，步骤少，失败率低。", FormatUtils.accentFor(7)),
        VideoItem(9, "BVMOCK000009", 0, "", "独立音乐现场精选", "耳机时间", 269_000, 1684, "几段小型 Livehouse 表演剪辑，适合放在电视上慢慢听。", FormatUtils.accentFor(8)),
        VideoItem(10, "BVMOCK000010", 0, "", "老番重温：那些经典镜头", "动画放映室", 395_000, 1211, "回看几部经典作品里的分镜、配乐和情绪推进。", FormatUtils.accentFor(9)),
        VideoItem(11, "BVMOCK000011", 0, "", "桌面收纳改造计划", "生活小修小补", 99_000, 767, "把凌乱桌面整理成更适合工作和娱乐的状态。", FormatUtils.accentFor(10)),
        VideoItem(12, "BVMOCK000012", 0, "", "从零搭建家庭媒体中心", "客厅玩家", 151_000, 1056, "第一期先做需求拆解和基础设备规划。", FormatUtils.accentFor(11))
    )
}
