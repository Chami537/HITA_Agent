package cn.limpu.hita.ui.design

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 课表课程色分配器：为当前显示目标内的全部课程稳定分配色板槽位。
 *
 * 规则：
 * - 输入按 courseKey 排序后依次占槽，结果与输入顺序无关；
 * - 每门课先取 storedColor 的色相最近邻槽位作为倾向（用户手选色因此优先保留）；
 * - 倾向槽已被先序课程占用时，沿色板环找最近空槽，保证课程数 ≤ 色板大小时同屏两两不同色；
 * - 课程数超过色板时确定性复用倾向槽（最少碰撞，不随机）。
 *
 * 纯 Kotlin 实现，不依赖 Android/Compose，JVM 直接可测。
 */
object TimetableColorAssigner {

    /**
     * @param courseKeys 课程键（subjectId，或课名兜底）
     * @param storedColors courseKey → 数据库存储色（0 或低饱和表示无倾向）
     * @param paletteArgb 当前主题色板（ARGB）
     * @return courseKey → 槽位 index
     */
    fun assign(
        courseKeys: Collection<String>,
        storedColors: Map<String, Int>,
        paletteArgb: IntArray,
    ): Map<String, Int> {
        if (courseKeys.isEmpty() || paletteArgb.isEmpty()) return emptyMap()
        val sorted = courseKeys.toSet().sorted()
        val result = LinkedHashMap<String, Int>(sorted.size)
        val used = HashSet<Int>(sorted.size)
        for (key in sorted) {
            val preferred = preferredSlot(key, storedColors[key] ?: 0, paletteArgb)
            val slot = if (preferred in used) nearestFreeSlot(preferred, used, paletteArgb.size) else preferred
            result[key] = slot
            used += slot
        }
        return result
    }

    private fun preferredSlot(courseKey: String, storedColor: Int, paletteArgb: IntArray): Int {
        if (storedColor != 0) {
            val (hue, saturation) = hueAndSaturation(storedColor)
            if (saturation >= 0.08f) {
                return paletteArgb.indices.minByOrNull { index ->
                    hueDistance(hue, hueAndSaturation(paletteArgb[index]).first)
                } ?: 0
            }
        }
        return Math.floorMod(courseKey.hashCode(), paletteArgb.size)
    }

    private fun nearestFreeSlot(preferred: Int, used: Set<Int>, size: Int): Int {
        for (distance in 1 until size) {
            val backward = Math.floorMod(preferred - distance, size)
            if (backward !in used) return backward
            val forward = (preferred + distance) % size
            if (forward !in used) return forward
        }
        // 全部占用：确定性复用倾向槽。
        return preferred
    }

    internal fun hueDistance(a: Float, b: Float): Float {
        val raw = abs(a - b)
        return min(raw, 360f - raw)
    }

    /** RGB(0-255) → (hue 0-360, saturation 0-1)。 */
    internal fun hueAndSaturation(argb: Int): Pair<Float, Float> {
        val red = ((argb shr 16) and 0xFF) / 255f
        val green = ((argb shr 8) and 0xFF) / 255f
        val blue = (argb and 0xFF) / 255f
        val maximum = max(red, max(green, blue))
        val minimum = min(red, min(green, blue))
        val delta = maximum - minimum
        val saturation = if (maximum == 0f) 0f else delta / maximum
        if (delta == 0f) return 0f to saturation
        val hue = when (maximum) {
            red -> 60f * (((green - blue) / delta) % 6f)
            green -> 60f * (((blue - red) / delta) + 2f)
            else -> 60f * (((red - green) / delta) + 4f)
        }
        return ((hue % 360f) + 360f) % 360f to saturation
    }
}
