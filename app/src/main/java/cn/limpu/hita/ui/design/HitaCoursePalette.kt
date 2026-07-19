package cn.limpu.hita.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.limpu.style.ThemeTools
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 课程色是主题系统的一部分，而不是直接把数据库颜色原样画到界面上。
 *
 * 经典风格用数据库颜色映射到当前基础色板；特殊风格只按课程身份分配自己的固定
 * 色板，完全忽略数据库中的任意 RGB 色，避免青绿色等旧颜色污染风格。
 */
@Composable
fun hitaCourseColor(courseKey: String, storedColor: Int): Color {
    val style = currentCourseThemeStyle()
    val preset = HitaTheme.colorPreset
    val isDark = HitaTheme.isDark
    return remember(style, preset, isDark, courseKey, storedColor) {
        resolveHitaCourseColor(style, preset, isDark, courseKey, storedColor)
    }
}

internal fun resolveHitaCourseColor(
    style: HitaThemeStyle,
    preset: ThemeTools.COLOR_PRESET,
    isDark: Boolean,
    courseKey: String,
    storedColor: Int,
): Color {
    val palette = hitaCoursePaletteFor(style = style, preset = preset, isDark = isDark)
    val slot = if (style == HitaThemeStyle.Classic) {
        closestClassicPaletteSlot(
            courseKey = courseKey,
            storedColor = storedColor,
            palette = palette,
        )
    } else {
        // 特殊风格的课程配色只由课程身份决定，完全不受数据库任意色污染。
        Math.floorMod(courseKey.hashCode(), palette.size)
    }
    return palette[slot]
}

/** 当前主题的完整课程色板，可用于设置页的非交互预览。 */
@Composable
fun hitaCoursePalette(): List<Color> {
    val style = currentCourseThemeStyle()
    val preset = HitaTheme.colorPreset
    val isDark = HitaTheme.isDark
    return remember(style, preset, isDark) {
        hitaCoursePaletteFor(style = style, preset = preset, isDark = isDark)
    }
}

@Composable
private fun currentCourseThemeStyle(): HitaThemeStyle = when (HitaTheme.preferenceStyle) {
    ThemeTools.STYLE.CLASSIC -> HitaThemeStyle.Classic
    ThemeTools.STYLE.APPLE_GLASS -> HitaThemeStyle.AppleGlass
    ThemeTools.STYLE.CYBER -> HitaThemeStyle.Cyber
    ThemeTools.STYLE.SORA_CLOUD -> HitaThemeStyle.SoraCloud
    ThemeTools.STYLE.P5 -> HitaThemeStyle.Persona5
    ThemeTools.STYLE.DEEP_SPACE -> HitaThemeStyle.DeepSpace
    ThemeTools.STYLE.SUMI -> HitaThemeStyle.Sumi
}

/** 给经典风格的预设选择器生成真实的课程气泡色板预览。 */
fun hitaCoursePaletteFor(
    style: HitaThemeStyle,
    preset: ThemeTools.COLOR_PRESET,
    isDark: Boolean,
): List<Color> {
    return when (style) {
        HitaThemeStyle.Classic -> classicCoursePalette(preset, isDark)
        HitaThemeStyle.AppleGlass -> if (isDark) {
            colors(0xFFFF453A, 0xFFFFD60A, 0xFF30D158, 0xFF64D2FF, 0xFF0A84FF, 0xFFBF5AF2)
        } else {
            colors(0xFFFF3B30, 0xFFFF9F0A, 0xFF34C759, 0xFF00C7BE, 0xFF007AFF, 0xFFAF52DE)
        }
        HitaThemeStyle.Cyber -> if (isDark) {
            colors(0xFFFF2E88, 0xFFFFCA28, 0xFFA3FF12, 0xFF365CFF, 0xFF00A8FF, 0xFFE936FF)
        } else {
            colors(0xFFD41464, 0xFF9A6500, 0xFF4F8500, 0xFF2347D8, 0xFF006FBB, 0xFFB419C6)
        }
        // 日映构成只从画布已有的朱红、赭黄、群青中派生课程色。深浅模式共用
        // 同一套印刷色，避免课程卡突然出现与日轮和斜面无关的紫灰色。
        HitaThemeStyle.SoraCloud -> colors(
            0xFFC94B38, // 朱红
            0xFFD2A23C, // 赭黄
            0xFF315D6D, // 群青
            0xFFA84938, // 砖红
            0xFF486F7A, // 暮蓝
            0xFFB8773D, // 陶土
        )
        // P5 的黑色是轮廓和版式骨架，不拿来当课程填充色。否则某些课程会退化成
        // 一整块暗褐黑，只剩一条红边。品牌色板在深浅模式保持一致，切换模式只改变
        // 页面承载背景和卡片的结构色。
        HitaThemeStyle.Persona5 -> colors(
            0xFFE60012, // 怪盗红
            0xFFFF3347, // 信号红
            0xFFFFD400, // 警示黄
            0xFFF5F1E8, // 拼贴纸白
            0xFFB50020, // 深绯红
            0xFFFF6675, // 高光红
        )
        // 深空课程色取自星野：星砂金、星云紫、玫瑰星云，全部哑光，不出现电光蓝
        HitaThemeStyle.DeepSpace -> colors(
            0xFFE3C878, // 星砂金
            0xFF9B8EC4, // 星云紫
            0xFFC48A9B, // 玫瑰星云
            0xFFD9A95E, // 暖沙金
            0xFF7FA0BD, // 柔星蓝
            0xFF8A94AE, // 陨石灰蓝
        )
        // 水墨课程色即传统彩墨：朱砂、赭石、黛青、藤黄、松烟、胭脂
        HitaThemeStyle.Sumi -> colors(
            0xFFA63B2A, // 朱砂
            0xFFB8773D, // 赭石
            0xFF4E5A65, // 黛青
            0xFFC9A227, // 藤黄
            0xFF3F5F54, // 松烟墨绿
            0xFF8C4A5A, // 胭脂
        )
    }
}

private fun classicCoursePalette(
    preset: ThemeTools.COLOR_PRESET,
    isDark: Boolean,
): List<Color> = when (preset) {
    ThemeTools.COLOR_PRESET.CLASSIC -> if (isDark) {
        colors(0xFFFF7380, 0xFFE8B65B, 0xFF55C79A, 0xFF48B8D0, 0xFF6EA1FF, 0xFFB38AF2)
    } else {
        colors(0xFFD95867, 0xFFB87D23, 0xFF258667, 0xFF167D99, 0xFF386FD6, 0xFF7A50BC)
    }
    ThemeTools.COLOR_PRESET.FRESH -> if (isDark) {
        colors(0xFFE29A86, 0xFFC8BF68, 0xFF69C793, 0xFF55C2B5, 0xFF65AFC0, 0xFFA69BC4)
    } else {
        colors(0xFFB56E5D, 0xFF8A8437, 0xFF378B5D, 0xFF208679, 0xFF397B8B, 0xFF74698F)
    }
    ThemeTools.COLOR_PRESET.FOCUS -> if (isDark) {
        colors(0xFFD29AAB, 0xFFC9B66C, 0xFF82B29B, 0xFF6AAAB4, 0xFF79A9D8, 0xFFA794C3)
    } else {
        colors(0xFF9B5D70, 0xFF88752F, 0xFF4E7965, 0xFF3E7580, 0xFF315E8A, 0xFF69557E)
    }
    ThemeTools.COLOR_PRESET.HIGH_CONTRAST -> if (isDark) {
        colors(0xFFFF4B55, 0xFFFFFF00, 0xFF42E65D, 0xFF00E5FF, 0xFF4D8DFF, 0xFFE56BFF)
    } else {
        colors(0xFFB00020, 0xFF8A5A00, 0xFF006B2E, 0xFF006A78, 0xFF0033CC, 0xFF6A1B9A)
    }
}

private fun closestClassicPaletteSlot(
    courseKey: String,
    storedColor: Int,
    palette: List<Color>,
): Int {
    if (storedColor == 0) return Math.floorMod(courseKey.hashCode(), palette.size)
    val source = hueAndSaturation(
        red = ((storedColor shr 16) and 0xFF) / 255f,
        green = ((storedColor shr 8) and 0xFF) / 255f,
        blue = (storedColor and 0xFF) / 255f,
    )
    if (source.second < 0.08f) return Math.floorMod(courseKey.hashCode(), palette.size)
    return palette.indices.minByOrNull { index ->
        val color = palette[index]
        val candidateHue = hueAndSaturation(color.red, color.green, color.blue).first
        val rawDistance = abs(source.first - candidateHue)
        minOf(rawDistance, 360f - rawDistance)
    } ?: 0
}

private fun hueAndSaturation(red: Float, green: Float, blue: Float): Pair<Float, Float> {
    val maximum = max(red, max(green, blue))
    val minimum = min(red, min(green, blue))
    val delta = maximum - minimum
    val saturation = if (maximum == 0f) 0f else delta / maximum
    if (delta == 0f) return 0f to saturation
    val rawHue = when (maximum) {
        red -> 60f * (((green - blue) / delta) % 6f)
        green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }
    return (if (rawHue < 0f) rawHue + 360f else rawHue) to saturation
}

internal fun hitaColorToArgb(color: Color): Int {
    val alpha = (color.alpha * 255f).roundToInt().coerceIn(0, 255)
    val red = (color.red * 255f).roundToInt().coerceIn(0, 255)
    val green = (color.green * 255f).roundToInt().coerceIn(0, 255)
    val blue = (color.blue * 255f).roundToInt().coerceIn(0, 255)
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}

private fun colors(vararg values: Long): List<Color> = values.map(::Color)
