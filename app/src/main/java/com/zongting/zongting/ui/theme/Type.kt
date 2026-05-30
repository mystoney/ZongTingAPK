package com.zongting.zongting.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.sp
import com.zongting.zongting.R

// 霞鹜文楷字体家族：Regular(400) + Medium(500)
val LXGW_WenKai = FontFamily(
    Font(resId = R.font.lxgw_wen_kai_regular, weight = FontWeight.Normal),
    Font(resId = R.font.lxgw_wen_kai_medium, weight = FontWeight.Medium)
)

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = LXGW_WenKai, fontWeight = FontWeight.Medium, fontSize = 50.sp, lineHeight = 56.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = LXGW_WenKai, fontWeight = FontWeight.Medium, fontSize = 38.sp, lineHeight = 44.sp),
    displaySmall = TextStyle(fontFamily = LXGW_WenKai, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontFamily = LXGW_WenKai, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = LXGW_WenKai, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = LXGW_WenKai, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = LXGW_WenKai, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = LXGW_WenKai, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = LXGW_WenKai, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = LXGW_WenKai, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = LXGW_WenKai, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = LXGW_WenKai, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = LXGW_WenKai, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = LXGW_WenKai, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = LXGW_WenKai, fontWeight = FontWeight.Medium, fontSize = 9.sp, lineHeight = 13.sp, letterSpacing = 0.5.sp)
)
