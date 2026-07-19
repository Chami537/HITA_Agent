package cn.limpu.hita.ui.design

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.limpu.hita.R
import com.limpu.style.ThemeTools

/**
 * 女神异闻录（P5）风格的组件级造型工具。
 *
 * 视觉语言：高对比红/黑/白、斜切面板、错位硬阴影、粗描边。
 * 与赛博（霓虹光晕 + 流光）和毛玻璃（模糊透明）相反：
 * P5 不用任何模糊与柔光，阴影是一块错位的实心色块。
 * 所有函数在非 P5 风格下均为无操作。
 */

/** 当前是否为 P5 风格 */
@Composable
fun hitaIsPersona(): Boolean = HitaTheme.preferenceStyle == ThemeTools.STYLE.P5

/** P5 标题字体：得意黑（Smiley Sans，OFL-1.1）。非 P5 风格返回 null（用系统默认字体） */
private val personaTitleFontFamily = FontFamily(Font(R.font.p5_title, FontWeight.Black))

@Composable
fun hitaPersonaTitleFont(): FontFamily? = if (hitaIsPersona()) personaTitleFontFamily else null

/** P5 斜切面板：右上 + 左下斜切（与赛博的左上 + 右下相反，形成视觉区分） */
fun personaSlashShape(cut: Dp): Shape = CutCornerShape(topEnd = cut, bottomStart = cut)

/**
 * P5 卡片修饰：错位硬阴影（实心色块向右下凸出）+ 粗黑/白描边。
 * 不画底色（卡片容器自身提供不透明底色），不做任何模糊。
 *
 * @param tint 错位色块颜色，默认主题红；课程卡等场景传入课程色
 */
@Composable
fun Modifier.hitaPersonaCardModifier(
    shape: Shape,
    tint: Color = MaterialTheme.colorScheme.primary,
): Modifier {
    if (!hitaIsPersona()) return this
    val isDark = HitaTheme.isDark
    val edgeColor = if (isDark) Color.White.copy(alpha = 0.88f) else Color(0xFF101010)
    return this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val shift = 5.dp.toPx()
        val blockColor = tint.copy(alpha = 0.92f)
        val stroke = Stroke(width = 2.2.dp.toPx())
        onDrawWithContent {
            // 错位色块（P5 标志性硬阴影，凸出卡片右下）
            translate(left = shift, top = shift) {
                drawOutline(outline = outline, color = blockColor)
            }
            drawContent()
            drawOutline(outline = outline, color = edgeColor, style = stroke)
        }
    }
}

/**
 * 课程块的 P5 错位底板色。
 *
 * 浅色页面统一用黑色压住彩色卡片；OLED 深色页面不能继续用黑色（会消失），
 * 因此红色卡片配警示黄，其余亮色卡片配怪盗红。颜色仍只来自 P5 固定色组。
 */
internal fun personaCourseShadowColor(courseColor: Color, isDark: Boolean): Color {
    if (!isDark) return Color(0xFF101010)
    val isRedFamily = courseColor.red > 0.55f &&
        courseColor.red > courseColor.green * 1.55f &&
        courseColor.red > courseColor.blue * 1.25f
    return if (isRedFamily) Color(0xFFFFD400) else Color(0xFFE60012)
}
