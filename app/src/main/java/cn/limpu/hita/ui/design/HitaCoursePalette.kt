package cn.limpu.hita.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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
/** 周视图课程色分配结果；未提供（详情页等单课场景）时为 null，走现算路径。 */
val LocalCourseColorAssignments = staticCompositionLocalOf<Map<String, Int>?> { null }

@Composable
fun hitaCourseColor(courseKey: String, storedColor: Int, assignedSlot: Int? = null): Color {
    val style = currentCourseThemeStyle()
    val preset = HitaTheme.colorPreset
    val isDark = HitaTheme.isDark
    val contextSlot = assignedSlot ?: LocalCourseColorAssignments.current?.get(courseKey)
    return remember(style, preset, isDark, courseKey, storedColor, contextSlot) {
        resolveHitaCourseColor(style, preset, isDark, courseKey, storedColor, contextSlot)
    }
}

internal fun resolveHitaCourseColor(
    style: HitaThemeStyle,
    preset: ThemeTools.COLOR_PRESET,
    isDark: Boolean,
    courseKey: String,
    storedColor: Int,
    assignedSlot: Int? = null,
): Color {
    val palette = hitaCoursePaletteFor(style = style, preset = preset, isDark = isDark)
    // 周视图分配器产出优先;无分配(详情页等单课场景)走原现算路径。
    val slot = assignedSlot?.let { Math.floorMod(it, palette.size) } ?: if (style == HitaThemeStyle.Classic) {
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
        // 波普涂鸦的课程填充只使用一明一暗两档正红。黑色留给结构轮廓，避免课程卡
        // 混入偏粉的高光色或其他杂色。
        HitaThemeStyle.Persona5 -> colors(
            0xFFE60012, // 怪盗红
            0xFFB50020, // 深绯红
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
        // 水墨宣纸的课程卡统一使用松烟墨绿，让卡面与修竹纹样形成一体，
        // 不再因课程哈希落入朱砂、胭脂等彩墨色。
        HitaThemeStyle.Sumi -> colors(0xFF3F5F54)
    }
}

private fun classicCoursePalette(
    preset: ThemeTools.COLOR_PRESET,
    isDark: Boolean,
): List<Color> = when (preset) {
    // 16 色沿色相环均匀分布(22.5° 步进),深/浅各一套;
    // 保留原 6 色的代表色(浅:D95867/B87D23/258667/167D99/386FD6/7A50BC)以延续既有映射倾向。
    ThemeTools.COLOR_PRESET.CLASSIC -> if (isDark) {
        colors(
            0xFFFF7380, 0xFFE08A6E, 0xFFE8B65B, 0xFFD9C45C,
            0xFFA9C96B, 0xFF7FCC7F, 0xFF5FCB8E, 0xFF55C79A,
            0xFF4EC4C4, 0xFF48B8D0, 0xFF6EA1FF, 0xFF8D8DE8,
            0xFFB38AF2, 0xFFCE83E2, 0xFFE87BB8, 0xFFEE7D97,
        )
    } else {
        colors(
            0xFFD95867, 0xFFC75B41, 0xFFB87D23, 0xFF8A760F,
            0xFF648B1F, 0xFF2E8B3D, 0xFF1E7D4F, 0xFF258667,
            0xFF1F8A8A, 0xFF167D99, 0xFF386FD6, 0xFF4E5BC0,
            0xFF7A50BC, 0xFF9C46A8, 0xFFB8458F, 0xFFC74B6E,
        )
    }
    ThemeTools.COLOR_PRESET.FRESH -> if (isDark) {
        colors(
            0xFFCC8E8E, 0xFFCCA68E, 0xFFCCBD8E, 0xFFC5CC8E,
            0xFFADCC8E, 0xFF96CC8E, 0xFF8ECC9E, 0xFF8ECCB5,
            0xFF8ECCCC, 0xFF8EB5CC, 0xFF8E9ECC, 0xFF968ECC,
            0xFFAD8ECC, 0xFFC58ECC, 0xFFCC8EBD, 0xFFCC8EA6,
        )
    } else {
        colors(
            0xFF994848, 0xFF996648, 0xFF998448, 0xFF8E9948,
            0xFF709948, 0xFF529948, 0xFF48995C, 0xFF48997A,
            0xFF489999, 0xFF487A99, 0xFF485C99, 0xFF524899,
            0xFF704899, 0xFF8E4899, 0xFF994884, 0xFF994866,
        )
    }
    ThemeTools.COLOR_PRESET.FOCUS -> if (isDark) {
        colors(
            0xFFC18686, 0xFFC19C86, 0xFFC1B286, 0xFFB9C186,
            0xFFA3C186, 0xFF8DC186, 0xFF86C195, 0xFF86C1AB,
            0xFF86C1C1, 0xFF86ABC1, 0xFF8695C1, 0xFF8D86C1,
            0xFFA386C1, 0xFFB986C1, 0xFFC186B2, 0xFFC1869C,
        )
    } else {
        colors(
            0xFF854747, 0xFF855E47, 0xFF857547, 0xFF7D8547,
            0xFF668547, 0xFF4F8547, 0xFF478557, 0xFF47856E,
            0xFF478585, 0xFF476E85, 0xFF475785, 0xFF4F4785,
            0xFF664785, 0xFF7D4785, 0xFF854775, 0xFF85475E,
        )
    }
    ThemeTools.COLOR_PRESET.HIGH_CONTRAST -> if (isDark) {
        colors(
            0xFFF74545, 0xFFF78845, 0xFFF7CB45, 0xFFE1F745,
            0xFF9EF745, 0xFF5BF745, 0xFF45F772, 0xFF45F7B4,
            0xFF45F7F7, 0xFF45B4F7, 0xFF4572F7, 0xFF5B45F7,
            0xFF9E45F7, 0xFFE145F7, 0xFFF745CB, 0xFFF74588,
        )
    } else {
        colors(
            0xFFA30A0A, 0xFFA3440A, 0xFFA37D0A, 0xFF90A30A,
            0xFF57A30A, 0xFF1DA30A, 0xFF0AA331, 0xFF0AA36A,
            0xFF0AA3A3, 0xFF0A6AA3, 0xFF0A31A3, 0xFF1D0AA3,
            0xFF570AA3, 0xFF900AA3, 0xFFA30A7D, 0xFFA30A44,
        )
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
