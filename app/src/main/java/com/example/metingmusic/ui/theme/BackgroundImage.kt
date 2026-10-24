package com.example.metingmusic.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * 背景图的存取与取色。
 *
 * 用户从相册选的图会**复制一份到应用私有目录**，而不是记住相册的 Uri ——
 * 相册的读权限是临时的，重启后大概率失效，那时背景就会变空白。
 */
object BackgroundImage {

    private const val DIR_NAME = "backgrounds"
    private const val MAX_WIDTH = 1440

    /** 把选中的图片复制到私有目录，返回新文件路径。 */
    fun importFrom(context: Context, uri: Uri): String? = runCatching {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }

        // 先只读尺寸，算好采样率再真正解码，避免把 4000×3000 的原图整个读进内存
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > MAX_WIDTH) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        // 每次换背景都写新文件，旧的删掉，避免私有目录越攒越大
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "bg_${System.currentTimeMillis()}.jpg")
        FileOutputStream(target).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        target.absolutePath
    }.getOrNull()

    /**
     * 内存缓存。
     *
     * 磨砂玻璃需要把背景**再画一遍**（然后模糊），如果每次都重新解码，
     * 一次模糊就会多解一张 1440px 的图，内存和耗时都受不了。
     * 只缓存最近一张：用户不会同时用两张背景图。
     */
    @Volatile
    private var cachedPath: String? = null
    @Volatile
    private var cachedBitmap: Bitmap? = null

    /**
     * 读背景图。
     *
     * 只在路径变化时解码一次，其余情况直接命中内存缓存。
     */
    fun load(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        if (path == cachedPath) return cachedBitmap

        val file = File(path)
        if (!file.exists()) return null
        val bitmap = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > MAX_WIDTH) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()

        cachedPath = path
        cachedBitmap = bitmap
        return bitmap
    }

    /** 删掉所有背景图（恢复默认时用）。 */
    fun clear(context: Context) {
        File(context.filesDir, DIR_NAME).listFiles()?.forEach { it.delete() }
    }
}

/** 把 Bitmap 包成 Compose 的 ImageBitmap。 */
fun Bitmap.toImage(): androidx.compose.ui.graphics.ImageBitmap = asImageBitmap()

/**
 * 从图片里提取一个「能当主题色用」的颜色。
 *
 * 思路不是简单求平均（那样几乎总是得到一片灰），而是：
 * 1. 缩到 32×32 采样；
 * 2. 按「饱和度² × 亮度可用性」加权求平均，让鲜艳的颜色占更大权重；
 * 3. 最后把亮度夹到中间区间，保证用作主色时对比度够。
 *
 * 找不到有彩色（整张图偏灰）时返回 null，由调用方回退到默认色。
 */
fun extractAccentColor(bitmap: Bitmap): Int? {
    val size = 32
    val small = Bitmap.createScaledBitmap(bitmap, size, size, true)
    var rSum = 0.0
    var gSum = 0.0
    var bSum = 0.0
    var weightSum = 0.0

    for (x in 0 until size) {
        for (y in 0 until size) {
            val px = small.getPixel(x, y)
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF

            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val saturation = if (maxC == 0) 0f else (maxC - minC).toFloat() / maxC
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0

            // 近灰 / 死黑 / 死白 几乎不参与，避免把主题色拉成灰
            val usable = when {
                saturation < 0.12f -> 0.0
                luminance < 0.12 || luminance > 0.94 -> 0.0
                else -> 1.0
            }
            val weight = saturation * saturation * usable
            if (weight <= 0.0) continue

            rSum += r * weight
            gSum += g * weight
            bSum += b * weight
            weightSum += weight
        }
    }
    if (weightSum < 1.0) return null

    var r = (rSum / weightSum).toInt().coerceIn(0, 255)
    var g = (gSum / weightSum).toInt().coerceIn(0, 255)
    var b = (bSum / weightSum).toInt().coerceIn(0, 255)

    // 把亮度拉回中间区间：太暗当主色看不清，太亮和背景糊在一起
    val lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    val target = 0.55
    if (lum < 0.35 || lum > 0.78) {
        val scale = target / lum.coerceAtLeast(0.05)
        r = (r * scale).toInt().coerceIn(0, 255)
        g = (g * scale).toInt().coerceIn(0, 255)
        b = (b * scale).toInt().coerceIn(0, 255)
    }

    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/** 主色上面该用黑字还是白字。 */
fun Color.onColorFor(): Color = if (luminance() > 0.55f) Color(0xFF1A1A1A) else Color.White
