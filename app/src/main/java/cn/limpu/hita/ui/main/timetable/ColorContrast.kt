package cn.limpu.hita.ui.main.timetable

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

    /** 按 WCAG 相对亮度选择黑/白文字，取对比度更高的一侧。 */
    fun contrastText(color: Int): Int {
        val background = relativeLuminance(color)
        val contrastWithWhite = 1.05 / (background + 0.05)
        val contrastWithBlack = (background + 0.05) / 0.05
        return if (contrastWithBlack >= contrastWithWhite) Color.BLACK else Color.WHITE
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) {
                normalized / 12.92
            } else {
                Math.pow((normalized + 0.055) / 1.055, 2.4)
            }
        }
        return 0.2126 * channel(Color.red(color)) +
                0.7152 * channel(Color.green(color)) +
                0.0722 * channel(Color.blue(color))
    }
}
