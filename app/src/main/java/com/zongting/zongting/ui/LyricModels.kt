package com.zongting.zongting.ui

// 歌词行数据类
data class LyricLine(
    val timestamp: Long, // 毫秒
    val text: String
)

// 歌词加载状态
sealed class LyricState {
    data object Idle : LyricState()
    data object Loading : LyricState()
    data class Success(val lyrics: List<LyricLine>) : LyricState()
    data class Error(val message: String) : LyricState()
}
