package cn.limpu.hita.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPSILON = 1e-4f

private const val BAR_WIDTH = 400f
private const val PADDING = 4f
private const val TAB_COUNT = 4

/** density 按 1 处理，此时 dp 与像素同为 [DockMetrics.LENS_GROWTH_DP]。 */
private const val LENS_GROWTH_PX = DockMetrics.LENS_GROWTH_DP

class DockMetricsTest {

    // ── 橡皮筋 ────────────────────────────────────────────────

    @Test
    fun rubberBandIsNeutralFromOrigin() {
        assertEquals(0f, dockRubberBand(overshoot = 0f, limit = 0.35f), EPSILON)
    }

    @Test
    fun rubberBandGrowsWithOvershoot() {
        val near = dockRubberBand(overshoot = 0.1f, limit = 0.35f)
        val far = dockRubberBand(overshoot = 1f, limit = 0.35f)
        assertTrue(near > 0f)
        assertTrue(far > near)
    }

    @Test
    fun rubberBandStaysUnderItsLimit() {
        val limit = 0.35f
        val pulled = dockRubberBand(overshoot = 1000f, limit = limit)
        assertTrue(pulled < limit)
        assertTrue(pulled > limit - 0.01f)
    }

    @Test
    fun rubberBandIsSymmetric() {
        val forward = dockRubberBand(overshoot = 0.3f, limit = 0.5f)
        val backward = dockRubberBand(overshoot = -0.3f, limit = 0.5f)
        assertEquals(forward, -backward, EPSILON)
    }

    @Test
    fun rubberBandIgnoresNonPositiveLimit() {
        assertEquals(0f, dockRubberBand(overshoot = 1f, limit = 0f), EPSILON)
        assertEquals(0f, dockRubberBand(overshoot = 1f, limit = -1f), EPSILON)
    }

    // ── lens 矩形 ─────────────────────────────────────────────

    @Test
    fun lensAtRestFillsExactlyOneItem() {
        val box = dockLensRect(
            position = 0f,
            velocity = 0f,
            extent = 100f,
            height = 50f,
            lift = 0f,
            growthPx = LENS_GROWTH_PX,
        )
        assertEquals(0f, box.left, EPSILON)
        assertEquals(0f, box.top, EPSILON)
        assertEquals(100f, box.width, EPSILON)
        assertEquals(50f, box.height, EPSILON)
    }

    @Test
    fun lensStretchesAndFlattensWithSpeed() {
        val box = dockLensRect(
            position = 0f,
            velocity = DockMetrics.JELLY_SPEED,
            extent = 100f,
            height = 50f,
            lift = 0f,
            growthPx = LENS_GROWTH_PX,
        )
        assertEquals(125f, box.width, EPSILON)
        assertEquals(43.75f, box.height, EPSILON)
        assertEquals(-12.5f, box.left, EPSILON)
        assertEquals(3.125f, box.top, EPSILON)
    }

    @Test
    fun lensStretchSaturatesAtJellySpeed() {
        val atSpeed = lensAtVelocity(DockMetrics.JELLY_SPEED)
        val beyond = lensAtVelocity(DockMetrics.JELLY_SPEED * 10f)
        assertEquals(atSpeed.width, beyond.width, EPSILON)
        assertEquals(atSpeed.height, beyond.height, EPSILON)
    }

    private fun lensAtVelocity(velocity: Float) = dockLensRect(
        position = 0f,
        velocity = velocity,
        extent = 100f,
        height = 50f,
        lift = 0f,
        growthPx = LENS_GROWTH_PX,
    )

    @Test
    fun lensGrowsWhileLifted() {
        val box = dockLensRect(
            position = 0f,
            velocity = 0f,
            extent = 100f,
            height = 50f,
            lift = 1f,
            growthPx = LENS_GROWTH_PX,
        )
        assertEquals(128f, box.width, EPSILON)
        assertEquals(78f, box.height, EPSILON)
        assertEquals(-14f, box.left, EPSILON)
        assertEquals(-14f, box.top, EPSILON)
    }

    @Test
    fun liftedLensOvershootsTheBar() {
        // 抣掉上下各一份 padding 后才是栏内高度，lens 抬起后必须高过栏体本身。
        val innerHeight = DockMetrics.BAR_HEIGHT - DockMetrics.BAR_PADDING * 2f
        val box = dockLensRect(
            position = 0f,
            velocity = 0f,
            extent = 100f,
            height = innerHeight,
            lift = 1f,
            growthPx = LENS_GROWTH_PX,
        )
        assertTrue(box.height > DockMetrics.BAR_HEIGHT)
    }

    @Test
    fun lensStaysCentredOnItsItem() {
        val extent = 100f
        for (position in listOf(0f, 1.5f, 3f)) {
            for (velocity in listOf(0f, 4f, 20f)) {
                for (lift in listOf(0f, 0.5f, 1f)) {
                    val box = dockLensRect(
                        position,
                        velocity,
                        extent,
                        50f,
                        lift,
                        LENS_GROWTH_PX,
                    )
                    assertEquals((position + 0.5f) * extent, box.left + box.width / 2f, EPSILON)
                }
            }
        }
    }

    @Test
    fun lensOverlapsOnlyTheItemItCovers() {
        val extent = 100f
        fun lensAt(position: Float) = dockLensRect(
            position, 0f, extent, 50f, 0f, LENS_GROWTH_PX,
        )
        assertTrue(lensAt(0f).overlaps(extent, 50f))
        // 停在左邻项上时，lens 的右边缘与该项的左边缘只相接，不算相交。
        assertFalse(lensAt(-1f).overlaps(extent, 50f))
        assertFalse(lensAt(1f).overlaps(extent, 50f))
    }

    // ── 浮点索引换算 ───────────────────────────────────────────

    private fun centreOf(index: Int): Float {
        val extent = (BAR_WIDTH - PADDING * 2f) / TAB_COUNT
        return PADDING + extent * (index + 0.5f)
    }

    @Test
    fun positionAtItemCentreIsAWholeIndex() {
        val lastIndex = (TAB_COUNT - 1).toFloat()
        for (index in 0 until TAB_COUNT) {
            val position = dockPositionAt(
                dx = centreOf(index),
                barWidth = BAR_WIDTH,
                count = TAB_COUNT,
                padding = PADDING,
                lastIndex = lastIndex,
                overdrag = DockMetrics.OVERDRAG,
            )
            assertEquals(index.toFloat(), position, EPSILON)
        }
    }

    @Test
    fun positionBeyondStartIsRubberBanded() {
        val position = dockPositionAt(
            dx = -200f,
            barWidth = BAR_WIDTH,
            count = 4,
            padding = PADDING,
            lastIndex = 3f,
            overdrag = DockMetrics.OVERDRAG,
        )
        assertTrue(position < 0f)
        assertTrue(position > -DockMetrics.OVERDRAG)
    }

    @Test
    fun positionBeyondEndIsRubberBanded() {
        val lastIndex = 3f
        val position = dockPositionAt(
            dx = 900f,
            barWidth = BAR_WIDTH,
            count = 4,
            padding = PADDING,
            lastIndex = lastIndex,
            overdrag = DockMetrics.OVERDRAG,
        )
        assertTrue(position > lastIndex)
        assertTrue(position < lastIndex + DockMetrics.OVERDRAG)
    }

    @Test
    fun positionFallsBackWhenThereIsNoRoom() {
        assertEquals(
            0f,
            dockPositionAt(50f, BAR_WIDTH, 0, PADDING, 0f, DockMetrics.OVERDRAG),
            EPSILON,
        )
        assertEquals(
            0f,
            dockPositionAt(50f, 2f, 4, PADDING, 3f, DockMetrics.OVERDRAG),
            EPSILON,
        )
    }

    // ── 甩动落点 ──────────────────────────────────────────────

    @Test
    fun flingWithoutSpeedStaysOnThePressedItem() {
        assertEquals(2, dockFlingIndex(2f, 0f, 100f, 2, 4, 0.1f))
    }

    @Test
    fun flingAdvancesAtMostOneItem() {
        assertEquals(3, dockFlingIndex(2f, 10_000f, 100f, 2, 4, 0.1f))
        assertEquals(1, dockFlingIndex(2f, -10_000f, 100f, 2, 4, 0.1f))
    }

    @Test
    fun flingProjectsVelocityOverTheWindow() {
        // lead = -500 / 100 * 0.1 = -0.5，四舍五入后仍落在原项。
        assertEquals(2, dockFlingIndex(2f, -500f, 100f, 2, 4, 0.1f))
        // lead = -1500 / 100 * 0.1 = -1.5，被压在「最多一项」的边界上。
        assertEquals(1, dockFlingIndex(2f, -1500f, 100f, 2, 4, 0.1f))
    }

    @Test
    fun flingStaysInsideTheBar() {
        assertEquals(0, dockFlingIndex(0f, -10_000f, 100f, 0, 4, 0.1f))
        assertEquals(4, dockFlingIndex(4f, 10_000f, 100f, 4, 4, 0.1f))
    }

    @Test
    fun flingFallsBackWhenTheExtentCollapses() {
        assertEquals(2, dockFlingIndex(2f, 1000f, 0f, 2, 4, 0.1f))
    }

    // ── 尺寸 ─────────────────────────────────────────────────

    @Test
    fun barHeightFollowsLabelScaling() {
        assertEquals(62f, dockBarHeight(DockMetrics.LABEL_SIZE), EPSILON)
        assertEquals(65f, dockBarHeight(13f), EPSILON)
    }

    @Test
    fun barWidthIsCappedUntilThereAreEnoughTabs() {
        assertEquals(80f, dockBarMaxWidth(1)!!, EPSILON)
        assertEquals(296f, dockBarMaxWidth(4)!!, EPSILON)
        assertNull(dockBarMaxWidth(DockMetrics.FULL_WIDTH_COUNT))
        assertNull(dockBarMaxWidth(6))
    }

    // ── 按下缩放 ───────────────────────────────────────────

    @Test
    fun pressScaleIsNeutralAtRest() {
        assertEquals(1f, dockPressScale(swell = 0f, maxDimensionPx = 400f, maxGrowthPx = 48f), EPSILON)
    }

    @Test
    fun pressScaleIsCappedByGrowthOnWideBars() {
        // 48 / 400 小于比例上限 0.25，因此由 dp 上限决定。
        assertEquals(1.12f, dockPressScale(1f, 400f, 48f), EPSILON)
    }

    @Test
    fun pressScaleIsCappedByRatioOnNarrowBars() {
        // 48 / 80 = 0.6 超过比例上限，被压到 0.25。
        assertEquals(1.25f, dockPressScale(1f, 80f, 48f), EPSILON)
    }

    @Test
    fun pressScaleFallsBackWhenThereIsNoSize() {
        assertEquals(1f, dockPressScale(1f, 0f, 48f), EPSILON)
    }

    @Test
    fun pressScaleStaysModestSoLabelsStayReadable() {
        // 5 项、411dp 屏宽、密度 3：栏宽 (411-42)dp = 1107px，膨胀上限 16dp = 48px。
        // 栏体一拉伸，标签就会跟着发糊，所以膨胀量必须压在这个区间里。
        val scale = dockPressScale(swell = 1f, maxDimensionPx = 1107f, maxGrowthPx = 48f)
        assertTrue(scale > 1.03f)
        assertTrue(scale < 1.07f)
    }
}
