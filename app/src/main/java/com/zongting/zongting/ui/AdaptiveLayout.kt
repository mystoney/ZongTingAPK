package com.zongting.zongting.ui

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

/**
 * 响应式布局工具，基于 WindowSizeClass 判断屏幕尺寸类型。
 *
 * 设计目标：1920×1080 / 280 DPI 横屏平板
 *
 * 尺寸换算（px / density = dp，density = DPI/160 = 280/160 = 1.75）：
 *   宽度：1920 / 1.75 = ~1097dp
 *   高度：1080 / 1.75 = ~617dp
 *   → 宽度 1097dp > 840dp，属于 WindowWidthSizeClass.Expanded
 *
 * 尺寸断点（Compose Material3 标准）：
 *   Compact    : < 600dp  → 手机竖屏 / 手机横屏
 *   Medium     : 600-840dp → 平板竖屏 / 小平板横屏
 *   Expanded   : > 840dp  → 平板横屏（我们主要适配的目标）
 *
 * 本项目策略：
 *   - Compact / Medium → 手机布局（现有代码不变）
 *   - Expanded         → 平板横屏布局（新增）
 */
object AdaptiveLayout {

    /**
     * 是否为平板横屏（Expanded）模式。
     * 1920×1080 / 280DPI → 宽度约 1097dp > 840dp → Expanded。
     */
    fun isExpanded(widthSizeClass: WindowWidthSizeClass) =
        widthSizeClass == WindowWidthSizeClass.Expanded

    /**
     * 是否为 Compact 模式（手机）。
     */
    fun isCompact(widthSizeClass: WindowWidthSizeClass) =
        widthSizeClass == WindowWidthSizeClass.Compact

    /**
     * 是否为 Medium 模式（中等尺寸）。
     */
    fun isMedium(widthSizeClass: WindowWidthSizeClass) =
        widthSizeClass == WindowWidthSizeClass.Medium

    /**
     * 是否为宽屏（Medium 或 Expanded）。
     * 用于判断是否可以使用分栏布局。
     */
    fun isWide(widthSizeClass: WindowWidthSizeClass) =
        widthSizeClass != WindowWidthSizeClass.Compact

    /**
     * 计算推荐列数（用于网格布局）。
     * 基于可用宽度（dp）动态计算。
     */
    fun recommendedGridColumns(widthDp: Float): Int = when {
        widthDp >= 1400 -> 4
        widthDp >= 1000 -> 3
        widthDp >= 600  -> 2
        else            -> 1
    }
}

/** 平板横屏内容区最大宽度（防止封面/文字在大屏上拉伸过度） */
const val PAD_MAX_CONTENT_WIDTH_DP = 1200

/** 平板横屏侧边 NavigationRail 宽度 */
const val PAD_NAV_RAIL_WIDTH_DP = 80

/** 平板横屏左侧面板宽度（播放页封面、排行榜列表等） */
const val PAD_LEFT_PANEL_WIDTH_DP = 420
