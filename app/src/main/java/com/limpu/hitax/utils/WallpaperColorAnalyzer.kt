package com.limpu.hitax.utils

import android.graphics.Bitmap
import android.graphics.Color

object WallpaperColorAnalyzer {

    private const val THRESHOLD = 128.0
    private const val SAMPLE_SIZE = 10

    /**
     * 心理学亮度公式：人眼对绿色最敏感、蓝色最迟钝。
     * Y = 0.299R + 0.587G + 0.114B
     */
    fun luminance(r: Int, g: Int, b: Int): Double =
        0.299 * r + 0.587 * g + 0.114 * b

    /**
     * 对 bitmap 的指定矩形区域降采样到 [sampleSize]×[sampleSize]，
     * 取像素平均色 → 算亮度 → 返回推荐文字色。
     *
     * @return [Color.WHITE] 浅底 → 白字 / 暗灰 #333 深底 → 黑字
     */
    fun sampleRegion(
        bitmap: Bitmap,
        leftRatio: Float,
        topRatio: Float,
        widthRatio: Float,
        heightRatio: Float,
        sampleSize: Int = SAMPLE_SIZE
    ): Int {
        val bw = bitmap.width
        val bh = bitmap.height
        val w = (bw * widthRatio).toInt().coerceAtLeast(1)
        val h = (bh * heightRatio).toInt().coerceAtLeast(1)
        val x = ((bw - w) * leftRatio).toInt().coerceIn(0, bw - 1)
        val y = ((bh - h) * topRatio).toInt().coerceIn(0, bh - 1)
        val rw = w.coerceAtMost(bw - x)
        val rh = h.coerceAtMost(bh - y)

        val region = Bitmap.createBitmap(bitmap, x, y, rw, rh)
        val scaled = Bitmap.createScaledBitmap(region, sampleSize, sampleSize, true)
        val pixels = IntArray(sampleSize * sampleSize)
        scaled.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)
        scaled.recycle()
        region.recycle()

        var sumR = 0L; var sumG = 0L; var sumB = 0L
        for (p in pixels) {
            sumR += Color.red(p)
            sumG += Color.green(p)
            sumB += Color.blue(p)
        }
        val n = pixels.size
        val yVal = luminance(
            (sumR / n).toInt(),
            (sumG / n).toInt(),
            (sumB / n).toInt()
        )
        return if (yVal >= THRESHOLD) Color.rgb(0x33, 0x33, 0x33) else Color.WHITE
    }
}
