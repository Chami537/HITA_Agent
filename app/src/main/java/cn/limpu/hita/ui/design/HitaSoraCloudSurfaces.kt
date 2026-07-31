package cn.limpu.hita.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.limpu.style.ThemeTools

/**
 * “日映构成”组件语言。
 *
 * 它以日系构成主义海报为骨架，并用和纸颗粒与不均匀笔触补充印象主义的手作感：
 * 暖白纸、墨黑网格、朱红日轮、群青斜面、芥子黄小色块；硬边、非对称、无玻璃模糊。
 * 保留 SORA_CLOUD 的存储名是为了兼容已经选择该风格的用户。
 */

val SoraPaper = Color(0xFFFFF8E8)
val SoraAgedPaper = Color(0xFFF3EBD9)
val SoraInk = Color(0xFF25221F)
val SoraVermilion = Color(0xFFC94B38)
val SoraIndigo = Color(0xFF315D6D)
val SoraOchre = Color(0xFFD2A23C)

/** 特殊主题自己的课程块结构，避免只靠透明度伪装成三种样式。 */
enum class HitaCourseBubbleTreatment {
    SOLID,
    TONAL,
    OUTLINE,
}

@Composable
fun hitaIsSoraCloud(): Boolean = HitaTheme.preferenceStyle == ThemeTools.STYLE.SORA_CLOUD

@Composable
fun hitaSoraTitleFont(): FontFamily? = if (hitaIsSoraCloud()) FontFamily.Serif else null

/** 非对称日系海报切角：右上大切角、左下小切角。 */
fun soraCloudCardShape(radius: Dp): Shape = CutCornerShape(
    topEnd = radius,
    bottomStart = radius / 3,
)

@Composable
fun hitaSoraCloudCardShape(roundedRadius: Dp, fallback: Dp = roundedRadius): Shape {
    return if (hitaIsSoraCloud()) soraCloudCardShape(roundedRadius) else RoundedCornerShape(fallback)
}

@Composable
fun hitaSoraCloudBorder(
    width: Dp = 1.2.dp,
    alpha: Float = 0.82f,
    color: Color = MaterialTheme.colorScheme.outline,
): BorderStroke? {
    return if (hitaIsSoraCloud()) BorderStroke(width, color.copy(alpha = alpha)) else null
}

@Composable
fun hitaSoraCloudContainerColor(
    base: Color = MaterialTheme.colorScheme.surface,
    alpha: Float = 0.96f,
): Color {
    if (!hitaIsSoraCloud()) return base
    return if (HitaTheme.isDark) {
        Color(0xFF25221D).copy(alpha = alpha.coerceIn(0.90f, 1f))
    } else {
        SoraPaper.copy(alpha = alpha.coerceIn(0.92f, 1f))
    }
}

@Composable
fun hitaSoraCloudCardColors(
    containerColor: Color = MaterialTheme.colorScheme.surface,
    glassAlpha: Float = 0.96f,
): CardColors = CardDefaults.cardColors(
    containerColor = hitaSoraCloudContainerColor(containerColor, glassAlpha)
)

/**
 * 通用卡片：错位群青/课程色底板、墨色硬描边、朱红装订线与微小和纸颗粒。
 * 这是结构变化，不依赖单纯换色。
 */
@Composable
fun Modifier.hitaSoraCloudCardModifier(
    shape: Shape,
    elevation: Dp = 6.dp,
    highlightTint: Color? = null,
    reflectionTint: Color? = null,
): Modifier {
    if (!hitaIsSoraCloud()) {
        return this.background(MaterialTheme.colorScheme.surfaceVariant, shape).clip(shape)
    }
    val isDark = HitaTheme.isDark
    val underlay = highlightTint ?: if (isDark) Color(0xFF6F8F92) else SoraIndigo
    val accent = reflectionTint ?: if (isDark) Color(0xFFE16C58) else SoraVermilion
    val edge = if (isDark) Color(0xFFF4ECD9) else SoraInk
    val grain = edge.copy(alpha = if (isDark) 0.07f else 0.055f)
    return this
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val shiftX = minOf(4.dp.toPx(), elevation.toPx() * 0.55f)
            val shiftY = minOf(5.dp.toPx(), elevation.toPx() * 0.70f)
            val edgeStroke = Stroke(width = 1.25.dp.toPx())
            val bindingStroke = 3.dp.toPx()
            val dots = List(18) { index ->
                Offset(
                    x = size.width * (((index * 47) % 97) / 97f),
                    y = size.height * (((index * 71 + 13) % 101) / 101f),
                )
            }
            onDrawWithContent {
                translate(shiftX, shiftY) {
                    drawOutline(outline, color = underlay.copy(alpha = if (isDark) 0.66f else 0.82f))
                }
                drawContent()
                drawLine(
                    color = accent,
                    start = Offset(8.dp.toPx(), 3.dp.toPx()),
                    end = Offset(8.dp.toPx(), size.height - 3.dp.toPx()),
                    strokeWidth = bindingStroke,
                )
                dots.forEachIndexed { index, point ->
                    drawCircle(grain, radius = if (index % 3 == 0) 0.7.dp.toPx() else 0.42.dp.toPx(), center = point)
                }
                drawOutline(outline, color = edge.copy(alpha = 0.88f), style = edgeStroke)
            }
        }
        .clip(shape)
}

/**
 * 课程色块：课程色错位底板 + 和纸正文面 + 装订色条，像一张小型日系票券。
 * 课程色承担识别，不再整块覆盖正文面，避免多个大色面与全屏构图相互争抢。
 */
@Composable
fun Modifier.hitaSoraCloudTintedModifier(
    shape: Shape,
    tint: Color,
    isMuted: Boolean = false,
    opacity: Float = 1f,
    treatment: HitaCourseBubbleTreatment = HitaCourseBubbleTreatment.SOLID,
): Modifier {
    if (!hitaIsSoraCloud()) return this.background(MaterialTheme.colorScheme.surfaceVariant, shape).clip(shape)
    val isDark = HitaTheme.isDark
    val opacityScale = opacity.coerceIn(0.2f, 1f)
    val ink = if (isDark) Color(0xFFF4ECD9) else SoraInk
    val paper = if (isDark) Color(0xFF292621) else SoraPaper
    val accentAlpha = if (isMuted) 0.30f else (0.72f + 0.24f * opacityScale).coerceAtMost(0.96f)
    val paperAlpha = if (isMuted) 0.68f else (0.88f + 0.10f * opacityScale).coerceAtMost(0.98f)
    return this
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val shiftX = 4.dp.toPx()
            val shiftY = 4.dp.toPx()
            val edgeStroke = Stroke(width = 1.2.dp.toPx())
            val slash = Path().apply {
                moveTo(size.width * 0.66f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height * 0.28f)
                lineTo(size.width * 0.82f, size.height * 0.18f)
                close()
            }
            onDrawWithContent {
                when (treatment) {
                    HitaCourseBubbleTreatment.SOLID -> {
                        // 实色：强色海报块，墨色/纸白硬底板和纸片切角。
                        translate(shiftX, shiftY) {
                            drawOutline(outline, color = ink.copy(alpha = if (isMuted) 0.24f else 0.76f))
                        }
                        drawOutline(
                            outline,
                            color = tint.copy(alpha = if (isMuted) 0.46f else (0.84f + 0.14f * opacityScale)),
                        )
                        drawPath(slash, color = paper.copy(alpha = if (isMuted) 0.12f else 0.34f))
                        drawLine(
                            color = paper.copy(alpha = if (isMuted) 0.36f else 0.90f),
                            start = Offset(6.dp.toPx(), size.height * 0.08f),
                            end = Offset(6.dp.toPx(), size.height * 0.92f),
                            strokeWidth = 3.2.dp.toPx(),
                        )
                    }

                    HitaCourseBubbleTreatment.TONAL -> {
                        // 柔和：和纸正文面，课程色只作为错位底板和装订色条。
                        translate(shiftX, shiftY) {
                            drawOutline(outline, color = tint.copy(alpha = accentAlpha))
                        }
                        drawOutline(outline, color = paper.copy(alpha = paperAlpha))
                        drawPath(slash, color = tint.copy(alpha = if (isMuted) 0.10f else 0.20f))
                        drawLine(
                            color = tint.copy(alpha = if (isMuted) 0.42f else 0.96f),
                            start = Offset(6.dp.toPx(), size.height * 0.08f),
                            end = Offset(6.dp.toPx(), size.height * 0.92f),
                            strokeWidth = 3.dp.toPx(),
                        )
                        drawLine(
                            color = SoraOchre.copy(alpha = if (isMuted) 0.30f else 0.88f),
                            start = Offset(6.dp.toPx(), size.height * 0.48f),
                            end = Offset(6.dp.toPx(), size.height * 0.62f),
                            strokeWidth = 3.dp.toPx(),
                        )
                    }

                    HitaCourseBubbleTreatment.OUTLINE -> {
                        // 描边：不画错位底板，以半透明纸面和双色硬边保持复杂背景上的识读。
                        drawOutline(
                            outline,
                            color = paper.copy(alpha = if (isMuted) 0.36f else if (isDark) 0.58f else 0.66f),
                        )
                        drawPath(slash, color = tint.copy(alpha = if (isMuted) 0.08f else 0.15f))
                        drawLine(
                            color = tint.copy(alpha = if (isMuted) 0.38f else 0.92f),
                            start = Offset(5.dp.toPx(), size.height * 0.12f),
                            end = Offset(5.dp.toPx(), size.height * 0.38f),
                            strokeWidth = 2.2.dp.toPx(),
                        )
                    }
                }
                drawContent()
                when (treatment) {
                    HitaCourseBubbleTreatment.OUTLINE -> {
                        drawOutline(
                            outline,
                            color = tint.copy(alpha = if (isMuted) 0.48f else 0.96f),
                            style = Stroke(width = 2.4.dp.toPx()),
                        )
                        drawOutline(
                            outline,
                            color = ink.copy(alpha = if (isMuted) 0.40f else 0.74f),
                            style = Stroke(width = 0.7.dp.toPx()),
                        )
                    }

                    else -> drawOutline(outline, color = ink.copy(alpha = 0.90f), style = edgeStroke)
                }
            }
        }
        .clip(shape)
}

internal fun soraCloudCourseContentColor(isDark: Boolean): Color =
    if (isDark) Color(0xFFF4ECD9) else SoraInk

/**
 * 全屏日系构成主义画布：和纸底、日轮、斜向群青块、墨色模数网格和稀疏笔触。
 * 所有几何按画布比例计算，在手机和平板上保持同一构图关系。
 */
@Composable
fun Modifier.hitaSoraCloudBackground(cloudiness: Float = 0.6f): Modifier {
    if (!hitaIsSoraCloud()) return this
    val isDark = HitaTheme.isDark
    return this.drawWithCache {
        val paper = if (isDark) Color(0xFF181714) else SoraAgedPaper
        val paperLight = if (isDark) Color(0xFF25221D) else SoraPaper
        val ink = if (isDark) Color(0xFFF4ECD9) else SoraInk
        val red = if (isDark) Color(0xFFD65D49) else SoraVermilion
        val indigo = if (isDark) Color(0xFF385E68) else SoraIndigo
        val ochre = if (isDark) Color(0xFFC69A43) else SoraOchre
        val grainPoints = List(86) { index ->
            Offset(
                x = size.width * (((index * 61 + 7) % 103) / 103f),
                y = size.height * (((index * 43 + 19) % 107) / 107f),
            )
        }
        val indigoPlane = Path().apply {
            moveTo(-size.width * 0.08f, size.height * 0.62f)
            lineTo(size.width * 0.58f, size.height * 0.43f)
            lineTo(size.width * 1.08f, size.height * 0.78f)
            lineTo(size.width * 0.20f, size.height * 0.94f)
            close()
        }
        val paperCut = Path().apply {
            moveTo(size.width * 0.56f, size.height * 0.13f)
            lineTo(size.width, size.height * 0.19f)
            lineTo(size.width, size.height * 0.34f)
            lineTo(size.width * 0.46f, size.height * 0.27f)
            close()
        }
        onDrawWithContent {
            drawRect(paper)
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(paperLight.copy(alpha = 0.42f), Color.Transparent, paperLight.copy(alpha = 0.20f))
                )
            )

            // 不居中的日轮是画面的视觉锚点。
            drawCircle(
                color = red.copy(alpha = if (isDark) 0.72f else 0.88f),
                radius = size.width * 0.235f,
                center = Offset(size.width * 0.78f, size.height * 0.115f),
            )
            drawPath(indigoPlane, color = indigo.copy(alpha = if (isDark) 0.62f else 0.78f))
            drawPath(paperCut, color = paperLight.copy(alpha = if (isDark) 0.26f else 0.68f))
            drawRect(
                color = ochre.copy(alpha = 0.86f),
                topLeft = Offset(size.width * 0.08f, size.height * 0.22f),
                size = Size(size.width * 0.12f, size.height * 0.055f),
            )

            // 构成主义模数线：少量、不抢内容。
            val gridAlpha = if (isDark) 0.075f else 0.10f
            val step = 52.dp.toPx()
            var x = size.width * 0.08f
            while (x < size.width) {
                drawLine(ink.copy(alpha = gridAlpha), Offset(x, 0f), Offset(x, size.height), 0.65.dp.toPx())
                x += step
            }
            var y = size.height * 0.18f
            while (y < size.height) {
                drawLine(ink.copy(alpha = gridAlpha), Offset(0f, y), Offset(size.width, y), 0.65.dp.toPx())
                y += step
            }

            // 不完全平行的墨色笔触，为几何骨架加入手工温度。
            val brushAlpha = (0.08f + cloudiness.coerceIn(0f, 1f) * 0.05f)
            repeat(4) { index ->
                val y0 = size.height * (0.34f + index * 0.012f)
                drawLine(
                    color = ink.copy(alpha = brushAlpha - index * 0.012f),
                    start = Offset(-size.width * 0.05f, y0),
                    end = Offset(size.width * 0.42f, y0 - size.height * 0.035f),
                    strokeWidth = (5 - index).dp.toPx(),
                )
            }
            grainPoints.forEachIndexed { index, point ->
                drawCircle(
                    color = ink.copy(alpha = if (isDark) 0.045f else 0.052f),
                    radius = if (index % 5 == 0) 0.85.dp.toPx() else 0.45.dp.toPx(),
                    center = point,
                )
            }
            drawContent()
        }
    }
}

/** 顶栏是一条不对称和纸横幅，带朱红基线与群青角标。 */
@Composable
fun Modifier.hitaSoraCloudTopBarModifier(
    shape: Shape = CutCornerShape(bottomEnd = 22.dp),
): Modifier {
    if (!hitaIsSoraCloud()) return this
    val isDark = HitaTheme.isDark
    val base = if (isDark) Color(0xFF25221D) else SoraPaper
    val edge = if (isDark) Color(0xFFF4ECD9) else SoraInk
    return this
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val corner = Path().apply {
                moveTo(size.width * 0.84f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height)
                lineTo(size.width * 0.94f, size.height)
                close()
            }
            onDrawWithContent {
                drawOutline(outline, color = base.copy(alpha = 0.94f))
                drawPath(corner, color = SoraIndigo.copy(alpha = if (isDark) 0.68f else 0.88f))
                drawContent()
                drawLine(
                    SoraVermilion.copy(alpha = 0.92f),
                    Offset(0f, size.height - 1.5.dp.toPx()),
                    Offset(size.width * 0.72f, size.height - 1.5.dp.toPx()),
                    3.dp.toPx(),
                )
                drawOutline(outline, color = edge.copy(alpha = 0.42f), style = Stroke(0.8.dp.toPx()))
            }
        }
        .clip(shape)
}
