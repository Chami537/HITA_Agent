package com.limpu.hitax.ui.main.timetable

import android.graphics.Color

object ColorContrast {

    /**
     * 心理学亮度公式，权重视觉感知修正。
     * Y = 0.299R + 0.587G + 0.114B，范围 0–255。
     */
    fun luminance(color: Int): Double {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return 0.299 * r + 0.587 * g + 0.114 * b
    }

    /**
     * 阈值 128：亮底 → 暗灰 #333，暗底 → 白色。
     */
    fun contrastText(color: Int): Int =
        if (luminance(color) >= 128) Color.rgb(0x33, 0x33, 0x33)
        else Color.WHITE
}
