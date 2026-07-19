package cn.limpu.hita.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.limpu.hita.R
import com.limpu.style.ThemeTools
import kotlin.math.min
import kotlin.random.Random

/**
 * 水墨宣纸（Sumi-e + 宣纸）风格的组件级造型工具。
 *
 * 视觉语言：暖白宣纸、纸纤维纹理、墨色晕染边缘、朱砂印泥点缀。
 * 东方美学的核心是「留白」与「墨分五色」：不用硬边、不用高饱和，
 * 边缘只做柔和的墨色洇开。所有函数在非水墨风格下均为无操作。
 */

/** 当前是否为水墨风格 */
@Composable
fun hitaIsSumi(): Boolean = HitaTheme.preferenceStyle == ThemeTools.STYLE.SUMI

/** 水墨标题字体：马善政毛笔楷体（OFL-1.1）。非水墨风格返回 null（用系统默认字体） */
private val sumiTitleFontFamily = FontFamily(Font(R.font.sumi_title, FontWeight.Bold))

@Composable
fun hitaSumiTitleFont(): FontFamily? = if (hitaIsSumi()) sumiTitleFontFamily else null

/** 宣纸卡片形状：近似方裁的微圆角 */
fun sumiCardShape(radius: Dp = 3.dp): Shape = RoundedCornerShape(radius)

/**
 * 宣纸卡片：纸纤维纹理 + 墨色晕染边缘 + 极轻的暖灰纸影。
 *
 * @param inkTint 晕染墨色（默认主题墨；课程卡传课程色，做「彩墨」晕染）
 * @param withBamboo 是否在卡片里画一枝淡墨修竹（课程卡专用，绿卡配竹）
 */
@Composable
fun Modifier.hitaSumiCardModifier(
    shape: Shape,
    elevation: Dp = 4.dp,
    inkTint: Color? = null,
    withBamboo: Boolean = false,
): Modifier {
    if (!hitaIsSumi()) return this
    val isDark = HitaTheme.isDark
    val scheme = MaterialTheme.colorScheme
    val ink = inkTint ?: scheme.primary
    val shadowElevation = min(elevation.value * 0.5f, 3f).dp
    return this
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = Color(0xFF4A4438).copy(alpha = if (isDark) 0.10f else 0.08f),
            spotColor = Color(0xFF4A4438).copy(alpha = if (isDark) 0.14f else 0.10f)
        )
        .clip(shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            // 纸纤维：细碎纤维点与短丝（固定种子）
            val rnd = Random(11)
            val speckles = List(46) {
                Triple(rnd.nextFloat(), rnd.nextFloat(), 0.3f + rnd.nextFloat() * 0.6f)
            }
            val fibers = List(7) {
                val fx = rnd.nextFloat()
                val fy = rnd.nextFloat()
                val angle = rnd.nextFloat() * 3.1416f
                val len = (4f + rnd.nextFloat() * 7f)
                floatArrayOf(fx, fy, angle, len)
            }
            val fiberColor = ink.copy(alpha = if (isDark) 0.05f else 0.045f)
            // 晕染边缘：三层由宽到细、由淡到浓的墨色洇开
            val bleed1 = Stroke(width = 3.5.dp.toPx())
            val bleed2 = Stroke(width = 2.0.dp.toPx())
            val bleed3 = Stroke(width = 0.8.dp.toPx())
            val bleedColor1 = ink.copy(alpha = if (isDark) 0.06f else 0.05f)
            val bleedColor2 = ink.copy(alpha = if (isDark) 0.10f else 0.09f)
            val bleedColor3 = ink.copy(alpha = if (isDark) 0.26f else 0.32f)
            // 修竹丛：三竿 + 竹节 + 五簇介字叶（足够高才画，小卡留白）
            val bambooInk = ink.copy(alpha = if (isDark) 0.62f else 0.58f)
            val drawBamboo = withBamboo && size.height >= 60.dp.toPx()
            val stalkStroke = Stroke(width = 2.2.dp.toPx())
            val stalk2Stroke = Stroke(width = 1.6.dp.toPx())
            val stalk3Stroke = Stroke(width = 1.3.dp.toPx())
            val leafPaths = if (drawBamboo) buildSumiBamboo(size.width, size.height) else emptyList()
            onDrawWithContent {
                drawContent()
                speckles.forEach { (fx, fy, r) ->
                    drawCircle(fiberColor, radius = r.dp.toPx(), center = Offset(size.width * fx, size.height * fy))
                }
                fibers.forEach { (fx, fy, angle, len) ->
                    val cx = size.width * fx
                    val cy = size.height * fy
                    val dx = kotlin.math.cos(angle) * len.dp.toPx()
                    val dy = kotlin.math.sin(angle) * len.dp.toPx()
                    drawLine(fiberColor, Offset(cx - dx / 2, cy - dy / 2), Offset(cx + dx / 2, cy + dy / 2), strokeWidth = 0.5.dp.toPx())
                }
                drawOutline(outline = outline, brush = SolidColor(bleedColor1), style = bleed1)
                drawOutline(outline = outline, brush = SolidColor(bleedColor2), style = bleed2)
                drawOutline(outline = outline, brush = SolidColor(bleedColor3), style = bleed3)
                if (drawBamboo) {
                    val w = size.width
                    val h = size.height
                    // 三竿修竹，疏密有致
                    drawLine(bambooInk, Offset(w * 0.88f, h * 0.99f), Offset(w * 0.84f, h * 0.30f), strokeWidth = stalkStroke.width)
                    drawLine(bambooInk, Offset(w * 0.94f, h * 0.99f), Offset(w * 0.92f, h * 0.42f), strokeWidth = stalk2Stroke.width)
                    drawLine(bambooInk, Offset(w * 0.99f, h * 0.99f), Offset(w * 0.97f, h * 0.58f), strokeWidth = stalk3Stroke.width)
                    // 竹节
                    drawLine(bambooInk, Offset(w * 0.845f, h * 0.52f), Offset(w * 0.885f, h * 0.52f), strokeWidth = stalk3Stroke.width)
                    drawLine(bambooInk, Offset(w * 0.860f, h * 0.72f), Offset(w * 0.900f, h * 0.72f), strokeWidth = stalk3Stroke.width)
                    drawLine(bambooInk, Offset(w * 0.915f, h * 0.60f), Offset(w * 0.955f, h * 0.60f), strokeWidth = stalk3Stroke.width)
                    drawLine(bambooInk, Offset(w * 0.925f, h * 0.80f), Offset(w * 0.965f, h * 0.80f), strokeWidth = stalk3Stroke.width)
                    drawLine(bambooInk, Offset(w * 0.965f, h * 0.74f), Offset(w * 1.005f, h * 0.74f), strokeWidth = stalk3Stroke.width)
                    // 叶
                    leafPaths.forEach { drawPath(it, color = bambooInk) }
                }
            }
        }
}

/** 生成三簇介字竹叶的 Path（以竿节点为基部，细长弧形叶片） */
private fun buildSumiBamboo(w: Float, h: Float): List<Path> {
    val leaves = mutableListOf<Path>()
    fun leaf(bx: Float, by: Float, angleDeg: Float, len: Float, wid: Float): Path {
        val rad = Math.toRadians(angleDeg.toDouble())
        val dirX = kotlin.math.cos(rad).toFloat()
        val dirY = kotlin.math.sin(rad).toFloat()
        val tipX = bx + dirX * len
        val tipY = by + dirY * len
        val perpX = -dirY
        val perpY = dirX
        return Path().apply {
            moveTo(bx, by)
            quadraticTo(
                bx + dirX * len * 0.5f + perpX * wid,
                by + dirY * len * 0.5f + perpY * wid,
                tipX, tipY
            )
            quadraticTo(
                bx + dirX * len * 0.5f - perpX * wid,
                by + dirY * len * 0.5f - perpY * wid,
                bx, by
            )
            close()
        }
    }
    // 主竿顶簇（三叶，介字形）
    leaves += leaf(w * 0.845f, h * 0.34f, 195f, 19f, 3.0f)
    leaves += leaf(w * 0.845f, h * 0.34f, 225f, 18f, 2.8f)
    leaves += leaf(w * 0.845f, h * 0.34f, 162f, 16f, 2.5f)
    // 主竿中簇（三叶）
    leaves += leaf(w * 0.860f, h * 0.54f, 192f, 17f, 2.7f)
    leaves += leaf(w * 0.860f, h * 0.54f, 222f, 16f, 2.4f)
    leaves += leaf(w * 0.860f, h * 0.54f, 165f, 14f, 2.1f)
    // 二竿顶簇（两叶）
    leaves += leaf(w * 0.925f, h * 0.46f, 196f, 16f, 2.4f)
    leaves += leaf(w * 0.925f, h * 0.46f, 226f, 15f, 2.1f)
    // 二竿中簇（两叶）
    leaves += leaf(w * 0.935f, h * 0.64f, 194f, 14f, 2.1f)
    leaves += leaf(w * 0.935f, h * 0.64f, 168f, 13f, 1.9f)
    // 三竿簇（两叶）
    leaves += leaf(w * 0.975f, h * 0.62f, 200f, 13f, 1.9f)
    leaves += leaf(w * 0.975f, h * 0.62f, 170f, 12f, 1.7f)
    return leaves
}

/**
 * 宣纸背景：纸纤维 + 淡墨云纹 + 底部远山（水墨剪影）+ 朱砂圆日。
 * 大面留白，远山只在画面最下方淡淡铺开，一轮朱砂悬于山脊之上。
 */
@Composable
fun Modifier.hitaSumiBackground(): Modifier {
    if (!hitaIsSumi()) return this
    val isDark = HitaTheme.isDark
    val ink = MaterialTheme.colorScheme.primary
    val vermilion = MaterialTheme.colorScheme.secondary
    return this.drawWithCache {
        val w = size.width
        val h = size.height
        // 纸纤维
        val rnd = Random(23)
        val speckles = List(420) {
            Triple(rnd.nextFloat() * w, rnd.nextFloat() * h, 0.3f + rnd.nextFloat() * 0.7f)
        }
        val fiberColor = ink.copy(alpha = if (isDark) 0.040f else 0.050f)
        // 淡墨云纹（纸面霉斑/云影，极淡）
        val cloud1 = Brush.radialGradient(
            colors = listOf(ink.copy(alpha = if (isDark) 0.05f else 0.04f), Color.Transparent),
            center = Offset(w * 0.20f, h * 0.18f), radius = w * 0.55f
        )
        val cloud2 = Brush.radialGradient(
            colors = listOf(ink.copy(alpha = if (isDark) 0.04f else 0.035f), Color.Transparent),
            center = Offset(w * 0.82f, h * 0.42f), radius = w * 0.50f
        )
        // 远山两层：后山淡、前山稍浓，都是洇开的墨
        val backMountain = Path().apply {
            moveTo(0f, h * 0.84f)
            cubicTo(w * 0.15f, h * 0.75f, w * 0.26f, h * 0.81f, w * 0.38f, h * 0.77f)
            cubicTo(w * 0.52f, h * 0.72f, w * 0.62f, h * 0.80f, w * 0.75f, h * 0.75f)
            cubicTo(w * 0.86f, h * 0.71f, w * 0.94f, h * 0.77f, w, h * 0.74f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        val frontMountain = Path().apply {
            moveTo(0f, h * 0.91f)
            cubicTo(w * 0.18f, h * 0.85f, w * 0.30f, h * 0.91f, w * 0.45f, h * 0.87f)
            cubicTo(w * 0.60f, h * 0.83f, w * 0.72f, h * 0.90f, w * 0.85f, h * 0.86f)
            cubicTo(w * 0.93f, h * 0.84f, w * 0.97f, h * 0.88f, w, h * 0.87f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        val backInk = Brush.verticalGradient(
            colors = listOf(ink.copy(alpha = if (isDark) 0.13f else 0.12f), Color.Transparent),
            startY = h * 0.70f,
            endY = h
        )
        val frontInk = Brush.verticalGradient(
            colors = listOf(ink.copy(alpha = if (isDark) 0.20f else 0.18f), Color.Transparent),
            startY = h * 0.80f,
            endY = h
        )
        // 朱砂圆日：悬在后山山脊之上，东方画的印红
        val sunColor = vermilion.copy(alpha = if (isDark) 0.85f else 0.78f)
        val sunCenter = Offset(w * 0.74f, h * 0.66f)
        val sunRadius = w * 0.042f
        onDrawWithContent {
            drawRect(brush = cloud1)
            drawRect(brush = cloud2)
            speckles.forEach { (x, y, r) -> drawCircle(fiberColor, r.dp.toPx(), Offset(x, y)) }
            drawCircle(sunColor, radius = sunRadius, center = sunCenter)
            drawPath(backMountain, brush = backInk)
            drawPath(frontMountain, brush = frontInk)
            drawContent()
        }
    }
}

/**
 * 飞白笔触线：加在标题类文字下方，像一笔带飞白的行书笔画。
 * 非水墨风格无操作。
 */
@Composable
fun Modifier.hitaSumiBrushUnderline(
    color: Color = MaterialTheme.colorScheme.primary,
): Modifier {
    if (!hitaIsSumi()) return this
    val ink = color.copy(alpha = 0.55f)
    return this.drawWithCache {
        val y = size.height - 1.5.dp.toPx()
        val rnd = Random(5)
        val strokes = mutableListOf<FloatArray>()
        var x = 0f
        val maxX = size.width * 0.85f
        while (x < maxX) {
            val seg = (7f + rnd.nextFloat() * 13f).dp.toPx()
            val wStroke = (1.2f + rnd.nextFloat() * 1.8f).dp.toPx()
            val off = (rnd.nextFloat() - 0.5f) * 2.2f.dp.toPx()
            strokes.add(floatArrayOf(x, y + off, (x + seg).coerceAtMost(maxX), wStroke))
            x += seg + (2.5f + rnd.nextFloat() * 5f).dp.toPx()
        }
        onDrawWithContent {
            drawContent()
            strokes.forEach { (sx, sy, ex, sw) ->
                drawLine(ink, Offset(sx, sy), Offset(ex, sy), strokeWidth = sw)
            }
        }
    }
}
