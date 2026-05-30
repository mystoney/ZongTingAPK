package com.zongting.zongting.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 品牌色彩统一管理 — 换主题只需改这里
 *
 * 墨绿色系配色方案（当前）
 * 如需更换主题，请把所有 Color(...) 替换为目标色值即可
 */
object AppColors {

    // ══════════════════════════════════════════════
    // 主题色 — 换色调只需改这里
    // ══════════════════════════════════════════════

    /** 主色（墨绿） */
    val Primary = Color(0xFF2E7D32)

    /** 深主色（深墨绿） */
    val PrimaryVariant = Color(0xFF1B5E20)

    /** 副色（翠绿） */
    val Secondary = Color(0xFF4CAF50)

    /** 第三色（中绿） */
    val Tertiary = Color(0xFF388E3C)

    /** 浅副色（浅绿） */
    val Accent = Color(0xFF66BB6A)

    // ══════════════════════════════════════════════
    // 收藏/强调色
    // ══════════════════════════════════════════════

    /** 收藏图标颜色（使用主色） */
    val FavoriteActive = Primary

    /** 收藏图标未选中色 */
    val FavoriteInactive = Color.White

    // ══════════════════════════════════════════════
    // 背景色
    // ══════════════════════════════════════════════

    val Background = Color(0xFF121212)
    val Surface = Color(0xFF1E1E1E)
    val SurfaceVariant = Color(0xFF2A2A2A)

    // ══════════════════════════════════════════════
    // 文字/图标色
    // ══════════════════════════════════════════════

    val OnPrimary = Color.White
    val OnBackground = Color.White
    val OnSurface = Color.White
    val OnSurfaceVariant = Color(0xFFB0B0B0)

    /** 导航栏选中图标/文字色（白色） */
    val NavSelected = Color.White

    /** 导航栏未选中图标/文字色 */
    val NavUnselected = Color(0xFFB0B0B0)
}
