package com.zongting.zongting.ui.player.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * The animated vinyl record that visualises playback state.
 *
 * Spins clockwise at 36°/sec when [isPlaying] is true and freezes at the
 * current angle on pause. The disk is drawn entirely with native Canvas
 * commands (radial gradient edges, concentric grooves, centre hole with
 * CLEAR xfermode to punch through) — see the inline Canvas block for
 * the math.
 *
 * Originally a `private fun` in PlayerScreen.kt.
 */
@Composable
fun VinylRecord(
    albumArtUrl: Any?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader
) {
    val context = LocalContext.current
    val cachedLoader = remember { imageLoader }

    var rotation by remember { mutableFloatStateOf(0f) }
    // 暂停时冻结在当前角度
    val pausedRotation = remember(rotation, isPlaying) { rotation }
    val displayRotation = if (isPlaying) rotation else pausedRotation

    // 匀速旋转：每秒转 360/10 = 36 度
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastTime = System.currentTimeMillis()
        while (coroutineContext.isActive) {
            val now = System.currentTimeMillis()
            val deltaMs = (now - lastTime).coerceAtLeast(0)
            rotation = (rotation + 360f * deltaMs / 10000f) % 360f
            lastTime = now
            delay(16)
        }
    }

    // 异步加载封面：图片加载前先显示纯色唱片，旋转不等待
    val loadedBmp = produceState<android.graphics.Bitmap?>(null, albumArtUrl, cachedLoader) {
        if (albumArtUrl == null) {
            value = null
            return@produceState
        }
        try {
            val request = coil.request.ImageRequest.Builder(context)
                .data(albumArtUrl)
                .allowHardware(false)
                .crossfade(true)
                .build()
            val result = cachedLoader.execute(request)
            value = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            value = null
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val outerR = size.minDimension / 2
            val artR = outerR - outerR * 0.02f   // 封面半径，边缘缩窄为原来的1/3（原6%→现2%）
            val labelR = outerR * 0.36f
            val holeR = outerR * 0.08f  // 中心孔直径翻倍（原0.04→现0.08）

            val nc = drawContext.canvas.nativeCanvas

            // 用 saveLayer 把唱片内容画到离屏缓冲，再用 CLEAR 挖空中心孔
            nc.saveLayer(0f, 0f, size.width, size.height, null)

            // 限制所有绘制只在唱片圆形区域内，唱片外保持透明（露出页面背景）
            val vinylClip = android.graphics.Path().apply {
                addCircle(cx, cy, outerR, android.graphics.Path.Direction.CW)
            }
            nc.clipPath(vinylClip)

            // --- 第1层：金属质感边缘：白色高光 -> 银灰 -> 黑色（径向渐变，左上光源） ---
            val lightX = cx - outerR * 0.3f   // 光源偏左上
            val lightY = cy - outerR * 0.3f
            val edgePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.FILL
                shader = android.graphics.RadialGradient(
                    lightX, lightY, outerR,
                    intArrayOf(
                        android.graphics.Color.parseColor("#F5F5F5"),  // 高光白
                        android.graphics.Color.parseColor("#BDBDBD"),  // 银灰
                        android.graphics.Color.parseColor("#1A1A1A"),  // 暗面
                        android.graphics.Color.parseColor("#0D0D0D")   // 边缘黑
                    ),
                    floatArrayOf(0f, 0.35f, 0.7f, 1f),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            nc.drawCircle(cx, cy, outerR, edgePaint)

            // --- 旋转的唱片主体 ---
            nc.save()
            nc.rotate(displayRotation, cx, cy)

            // 封面图片：裁剪为圆形铺满整张唱片
            val bmp = loadedBmp.value
            if (bmp != null && !bmp.isRecycled) {
                val path = android.graphics.Path().apply {
                    addCircle(cx, cy, artR, android.graphics.Path.Direction.CW)
                }
                nc.clipPath(path)
                val src = android.graphics.Rect(0, 0, bmp.width, bmp.height)
                val dstRect = android.graphics.Rect(
                    (cx - artR).toInt(), (cy - artR).toInt(),
                    (cx + artR).toInt(), (cy + artR).toInt()
                )
                nc.drawBitmap(bmp, src, dstRect, android.graphics.Paint().apply { isAntiAlias = true })
            } else {
                nc.drawCircle(cx, cy, artR, android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.parseColor("#1A1A1A")
                })
            }

            // 唱片纹理：同心圆细线叠加在封面上（模拟凹槽）
            nc.save()
            nc.clipRect((cx - artR).toFloat(), (cy - artR).toFloat(),
                        (cx + artR).toFloat(), (cy + artR).toFloat())
            val groovePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1.2f
                color = android.graphics.Color.parseColor("#40000000")
            }
            val grooveStep = (artR - labelR - artR * 0.04f) / 16
            var g = labelR + artR * 0.04f
            while (g <= artR - artR * 0.02f) {
                nc.drawCircle(cx, cy, g, groovePaint)
                g += grooveStep
            }
            nc.restore()

            // 封面之上的光泽高光（半透明渐变）
            val hlX = cx - artR * 0.25f
            val hlY = cy - artR * 0.25f
            val highlightPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.FILL
                shader = android.graphics.RadialGradient(
                    hlX, hlY, artR * 0.9f,
                    android.graphics.Color.parseColor("#25FFFFFF"),
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            nc.drawCircle(cx, cy, artR, highlightPaint)

            // 封面中心孔（用 CLEAR 挖空，透明穿透显示页面背景）
            val holePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            }
            nc.drawCircle(cx, cy, holeR, holePaint)

            // 中心孔金属光泽描边（孔外圈加一圈金属灰高光）
            nc.drawCircle(cx, cy, holeR * 1.4f, android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = (holeR * 0.3f).coerceAtLeast(2f)
                color = android.graphics.Color.parseColor("#C0C0C0")
            })
            nc.drawCircle(cx, cy, holeR * 1.4f, android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1f
                color = android.graphics.Color.parseColor("#707070")
            })

            // 唱片外圈描边（银灰色描边，贴合金属边缘内侧）
            nc.drawCircle(cx, cy, artR, android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2f
                color = android.graphics.Color.parseColor("#AAAAAA")
            })

            // 中心标签区域（无封面时为深色纯圆，有封面时画一小圈深色衬托中心孔）
            nc.drawCircle(cx, cy, labelR, android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1f
                color = android.graphics.Color.parseColor("#60FFFFFF")
            })

            nc.restore() // 恢复clip + 提交saveLayer（中心孔CLEAR穿透显示页面背景）
        }
    }
}
