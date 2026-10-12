package cn.limpu.hita.ui.main

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * 悬浮底栏的几何常量与纯数学。
 *
 * 这里刻意不引用 Android 或 Compose 类型，弹簧几何因此能在纯 JVM 单测里直接跑。
 * 需要真机重标的常量都在注释里标了量纲。
 */
internal object DockMetrics {

    /** 栏体基准高度（dp）；实际高度还要加上标签字体缩放带来的增量，见 [dockBarHeight]。 */
    const val BAR_HEIGHT = 54f

    /** 栏体内边距（dp），同时也是 [dockPositionAt] 换算浮点索引时的基准。 */
    const val BAR_PADDING = 4f

    /** 左右外边距（dp），同时也是距底边的最小距离。 */
    const val EDGE_MARGIN = 21f

    /** 栏体上方留给阴影的空白（dp）。 */
    const val SHADOW_ROOM = 8f

    /** 单项宽度上限（dp）；超过后不再拉宽，改为铺满可用宽度。 */
    const val MAX_ITEM_EXTENT = 56f

    /** 达到该标签数后栏体铺满可用宽度。 */
    const val FULL_WIDTH_COUNT = 6

    const val ICON_SIZE = 20f
    const val LABEL_GAP = 2f
    const val LABEL_INSET = 2f

    /** 标签基准字号（sp）。 */
    const val LABEL_SIZE = 10f

    /** 标签缩放下限（sp），窄屏下不会比它更小。 */
    const val MIN_LABEL_SIZE = 9f

    /**
     * 按下时栏体的膨胀比例，以及膨胀量的 dp 上限。
     *
     * 一旦放大，栏体就会明显拉伸，标签跟着发糊，得不偿失。
     */
    const val PRESS_GROWTH = 1f / 8f
    const val MAX_PRESS_GROWTH_DP = 16f

    /**
     * lens 抬起时相对栏内高度的外扩量（dp），按 2×lift 作用于宽高。
     *
     * 按 density 正确换算后，lens 抬起时会高过栏体一个头，不必再放大——
     * 之前看不出效果是把 dp 当成像素用所致。
     */
    const val LENS_GROWTH_DP = 14f

    /** lens 抬起时其上内容的放大比例。 */
    const val LENS_MAGNIFY = 0.12f

    /** 果冻拉伸的速度标度，单位为「项/秒」——lens 位置本身就是浮点索引。 */
    const val JELLY_SPEED = 8f

    /** 速度打满时 lens 的最大横向拉伸比例。 */
    const val JELLY_STRETCH = 0.25f

    /** 越界拖拽的橡皮筋上界，单位为「项」。 */
    const val OVERDRAG = 0.35f

    /** 横向拖出栏体时的橡皮筋上界，取栏体短边的该比例。 */
    const val PULL_LIMIT = 7f / 32f

    /** 拖出栏体时栏体的横向拉伸比例。 */
    const val PULL_STRETCH = 0.5f

    /** 甩动落点的速度投影窗口（秒）。 */
    const val FLING_PROJECTION_SECONDS = 0.1f
}

/** lens 的矩形，以所属项的左上角为原点。单位为像素。 */
internal data class DockLensBox(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * 与 Compose `Rect.overlaps` 一致的严格相交判定：仅边缘相接不算相交。
 *
 * lens 停在某一项上时，它恰好铺满该项，与相邻项只相接不相交，相邻项因此不会被染色。
 */
internal fun DockLensBox.overlaps(areaWidth: Float, areaHeight: Float): Boolean =
    (left < areaWidth) && (right > 0f) && (top < areaHeight) && (bottom > 0f)

/**
 * iOS 式渐近橡皮筋：越界越远，回拉越强，输出绝对值恒小于 [limit]。
 *
 * `0.55` 是曲率系数。
 */
internal fun dockRubberBand(overshoot: Float, limit: Float): Float {
    if (overshoot == 0f || limit <= 0f) return 0f
    val pull = 1f - 1f / (abs(overshoot) * 0.55f / limit + 1f)
    return limit * pull * sign(overshoot)
}

/**
 * lens 相对所属项的矩形。
 *
 * [position] 以「项」为单位，[velocity] 为项/秒，二者推出横向果冻拉伸；
 * [lift] 与 [growthPx] 推出整体膨胀。
 *
 * [extent]、[height]、[growthPx] 均为像素——前两者来自布局，后者由调用方按 density 换算，
 * 否则同一份 dp 值在不同密度下会得到完全不同的膨胀量。
 */
internal fun dockLensRect(
    position: Float,
    velocity: Float,
    extent: Float,
    height: Float,
    lift: Float,
    growthPx: Float,
): DockLensBox {
    val stretch =
        (abs(velocity) / DockMetrics.JELLY_SPEED).coerceAtMost(1f) * DockMetrics.JELLY_STRETCH
    val growth = growthPx * 2f * lift
    val width = (extent + growth) * (1f + stretch)
    val lensHeight = (height + growth) * (1f - stretch / 2f)
    return DockLensBox(
        left = (position + 0.5f) * extent - width / 2f,
        top = (height - lensHeight) / 2f,
        width = width,
        height = lensHeight,
    )
}

/** 把栏内局部 x 换算成浮点索引；越界部分走橡皮筋，因此不会越过首尾项太远。 */
internal fun dockPositionAt(
    dx: Float,
    barWidth: Float,
    count: Int,
    padding: Float,
    lastIndex: Float,
    overdrag: Float,
): Float {
    if (count <= 0) return 0f
    val extent = (barWidth - padding * 2f) / count
    if (extent <= 0f) return 0f
    val position = (dx - padding) / extent - 0.5f
    return when {
        position < 0f -> dockRubberBand(position, overdrag)
        position > lastIndex -> lastIndex + dockRubberBand(position - lastIndex, overdrag)
        else -> position
    }
}

/** 甩动松手后的落点：按横向速度投影，最多越过手指所在项一项。 */
internal fun dockFlingIndex(
    lensTarget: Float,
    velocityX: Float,
    extent: Float,
    pressedIndex: Int,
    lastIndex: Int,
    projectionSeconds: Float,
): Int {
    if (extent <= 0f || lastIndex < 0) return pressedIndex.coerceIn(0, maxOf(lastIndex, 0))
    val lead = velocityX / extent * projectionSeconds
    return (lensTarget + lead)
        .roundToInt()
        .coerceIn(pressedIndex - 1, pressedIndex + 1)
        .coerceIn(0, lastIndex)
}

/** 栏体高度（dp）：基准高度加上标签字体缩放增量。 */
internal fun dockBarHeight(scaledLabelSize: Float): Float =
    DockMetrics.BAR_HEIGHT + (scaledLabelSize - DockMetrics.LABEL_SIZE)

/** 栏体宽度上限（dp）。返回 null 表示标签数已足够多，应当铺满可用宽度。 */
internal fun dockBarMaxWidth(count: Int): Float? =
    if (count >= DockMetrics.FULL_WIDTH_COUNT) {
        null
    } else {
        count * DockMetrics.MAX_ITEM_EXTENT + DockMetrics.BAR_PADDING * 2f
    }

/**
 * 按下时栏体的缩放。膨胀量取「比例」与「dp 上限」中的小者，因此上限要先换算成像素：
 * `GraphicsLayerScope.size` 量的是像素，不是 dp。
 */
internal fun dockPressScale(
    swell: Float,
    maxDimensionPx: Float,
    maxGrowthPx: Float,
): Float {
    if (maxDimensionPx <= 0f) return 1f
    return 1f + swell * min(DockMetrics.PRESS_GROWTH * 2f, maxGrowthPx / maxDimensionPx)
}
