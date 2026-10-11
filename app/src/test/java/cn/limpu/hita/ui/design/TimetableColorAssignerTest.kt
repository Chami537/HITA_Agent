package cn.limpu.hita.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableColorAssignerTest {

    // 16 色板:色相环 22.5° 步进(仅取 ARGB 参与计算,数值与经典预设一致与否不影响算法)。
    private val palette16 = IntArray(16) { i ->
        val hue = i * 22.5f
        androidFreeHslToArgb(hue, 0.55f, 0.45f)
    }

    private fun androidFreeHslToArgb(hue: Float, saturation: Float, lightness: Float): Int {
        val c = (1f - kotlin.math.abs(2 * lightness - 1f)) * saturation
        val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
        val m = lightness - c / 2f
        val (r, g, b) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val ri = ((r + m) * 255).toInt().coerceIn(0, 255)
        val gi = ((g + m) * 255).toInt().coerceIn(0, 255)
        val bi = ((b + m) * 255).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    @Test
    fun `courses up to palette size never share a slot`() {
        val keys = (1..16).map { "course-$it" }
        val result = TimetableColorAssigner.assign(keys, emptyMap(), palette16)
        assertEquals(16, result.size)
        assertEquals(16, result.values.toSet().size)
    }

    @Test
    fun `stored color hue is preferred when slot is free`() {
        // 纯红色 storedColor 应落在色板 0° 槽(索引 0)。
        val result = TimetableColorAssigner.assign(listOf("a"), mapOf("a" to 0xFFD95867.toInt()), palette16)
        assertEquals(0, result["a"])
    }

    @Test
    fun `collision moves to nearest free slot deterministically`() {
        // 两门课都带红色倾向:第一门占 0 号槽,第二门让到相邻空槽。
        val result = TimetableColorAssigner.assign(
            listOf("a", "b"),
            mapOf("a" to 0xFFD95867.toInt(), "b" to 0xFFE05B6C.toInt()),
            palette16,
        )
        assertEquals(0, result["a"])
        assertNotEquals(0, result["b"])
        assertEquals(1, kotlin.math.abs(result.getValue("b") - 0).let { minOf(it, 16 - it) })
    }

    @Test
    fun `overflow reuses deterministically beyond palette size`() {
        val keys = (1..20).map { "course-$it" }
        val first = TimetableColorAssigner.assign(keys, emptyMap(), palette16)
        val second = TimetableColorAssigner.assign(keys.reversed(), emptyMap(), palette16)
        // 超池必然复用,但同一门课两次分配必须一致(与输入顺序无关)。
        assertEquals(first, second)
        assertEquals(20, first.size)
    }

    @Test
    fun `result is independent of input order`() {
        val keys = listOf("alpha", "beta", "gamma", "delta", "epsilon")
        val a = TimetableColorAssigner.assign(keys, emptyMap(), palette16)
        val b = TimetableColorAssigner.assign(keys.shuffled(kotlin.random.Random(41)), emptyMap(), palette16)
        assertEquals(a, b)
    }

    @Test
    fun `empty or blank inputs produce empty result`() {
        assertTrue(TimetableColorAssigner.assign(emptyList(), emptyMap(), palette16).isEmpty())
        assertTrue(TimetableColorAssigner.assign(listOf("a"), emptyMap(), intArrayOf()).isEmpty())
    }

    @Test
    fun `zero or gray stored color falls back to identity hash`() {
        // storedColor=0 与低饱和灰都不参与色相倾向,走 hash 槽位。
        val zero = TimetableColorAssigner.assign(listOf("x"), mapOf("x" to 0), palette16)
        val gray = TimetableColorAssigner.assign(listOf("x"), mapOf("x" to 0xFF808080.toInt()), palette16)
        assertEquals(Math.floorMod("x".hashCode(), 16), zero["x"])
        assertEquals(zero["x"], gray["x"])
    }
}
