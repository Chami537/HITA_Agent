package cn.limpu.hita.ui.design

import com.limpu.style.ThemeTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HitaCoursePaletteTest {

    @Test
    fun classicPresetChangesCoursePalette() {
        val classic = hitaCoursePaletteFor(
            style = HitaThemeStyle.Classic,
            preset = ThemeTools.COLOR_PRESET.CLASSIC,
            isDark = false,
        )
        val fresh = hitaCoursePaletteFor(
            style = HitaThemeStyle.Classic,
            preset = ThemeTools.COLOR_PRESET.FRESH,
            isDark = false,
        )

        assertEquals(6, classic.size)
        assertEquals(6, fresh.size)
        assertNotEquals(classic, fresh)
    }

    @Test
    fun specialStylePaletteIgnoresClassicPreset() {
        HitaThemeStyle.entries
            .filterNot { it == HitaThemeStyle.Classic }
            .forEach { style ->
                val classicPreset = hitaCoursePaletteFor(
                    style = style,
                    preset = ThemeTools.COLOR_PRESET.CLASSIC,
                    isDark = false,
                )
                val highContrastPreset = hitaCoursePaletteFor(
                    style = style,
                    preset = ThemeTools.COLOR_PRESET.HIGH_CONTRAST,
                    isDark = false,
                )

                assertEquals("$style should have a fixed palette", classicPreset, highContrastPreset)
            }
    }

    @Test
    fun specialCourseColorIgnoresStoredRgb() {
        HitaThemeStyle.entries
            .filterNot { it == HitaThemeStyle.Classic }
            .forEach { style ->
                val fromTeal = resolveHitaCourseColor(
                    style = style,
                    preset = ThemeTools.COLOR_PRESET.CLASSIC,
                    isDark = true,
                    courseKey = "computer-design-lab",
                    storedColor = 0xFF009688.toInt(),
                )
                val fromGreen = resolveHitaCourseColor(
                    style = style,
                    preset = ThemeTools.COLOR_PRESET.FRESH,
                    isDark = true,
                    courseKey = "computer-design-lab",
                    storedColor = 0xFF00D06C.toInt(),
                )

                assertEquals("$style must ignore arbitrary RGB colors", fromTeal, fromGreen)
            }
    }

    @Test
    fun classicPaletteSwatchesRemainDirectlySelectable() {
        val palette = hitaCoursePaletteFor(
            style = HitaThemeStyle.Classic,
            preset = ThemeTools.COLOR_PRESET.CLASSIC,
            isDark = true,
        )

        palette.forEach { swatch ->
            val resolved = resolveHitaCourseColor(
                style = HitaThemeStyle.Classic,
                preset = ThemeTools.COLOR_PRESET.CLASSIC,
                isDark = true,
                courseKey = "course",
                storedColor = hitaColorToArgb(swatch),
            )
            assertEquals(swatch, resolved)
        }
    }

    @Test
    fun personaPaletteUsesOnlyBrightAndDeepRedAcrossDisplayModes() {
        val light = hitaCoursePaletteFor(
            style = HitaThemeStyle.Persona5,
            preset = ThemeTools.COLOR_PRESET.CLASSIC,
            isDark = false,
        )
        val dark = hitaCoursePaletteFor(
            style = HitaThemeStyle.Persona5,
            preset = ThemeTools.COLOR_PRESET.HIGH_CONTRAST,
            isDark = true,
        )

        assertEquals(light, dark)
        assertEquals(
            listOf(
                androidx.compose.ui.graphics.Color(0xFFE60012),
                androidx.compose.ui.graphics.Color(0xFFB50020),
            ),
            light,
        )
    }

    @Test
    fun personaCourseShadowUsesTheOtherRedStepInDarkMode() {
        val brightRed = androidx.compose.ui.graphics.Color(0xFFE60012)
        val deepRed = androidx.compose.ui.graphics.Color(0xFFB50020)

        assertEquals(
            androidx.compose.ui.graphics.Color(0xFF101010),
            personaCourseShadowColor(brightRed, isDark = false),
        )
        assertEquals(
            deepRed,
            personaCourseShadowColor(brightRed, isDark = true),
        )
        assertEquals(
            brightRed,
            personaCourseShadowColor(deepRed, isDark = true),
        )
    }

    @Test
    fun cyberCourseBlueSlotReplacesPurple() {
        val dark = hitaCoursePaletteFor(
            style = HitaThemeStyle.Cyber,
            preset = ThemeTools.COLOR_PRESET.CLASSIC,
            isDark = true,
        )
        val light = hitaCoursePaletteFor(
            style = HitaThemeStyle.Cyber,
            preset = ThemeTools.COLOR_PRESET.CLASSIC,
            isDark = false,
        )

        assertEquals(androidx.compose.ui.graphics.Color(0xFF00A8FF), dark[4])
        assertEquals(androidx.compose.ui.graphics.Color(0xFF006FBB), light[4])
    }

    @Test
    fun soraCoursePaletteMatchesPosterColorsWithoutPurple() {
        val light = hitaCoursePaletteFor(
            style = HitaThemeStyle.SoraCloud,
            preset = ThemeTools.COLOR_PRESET.CLASSIC,
            isDark = false,
        )
        val dark = hitaCoursePaletteFor(
            style = HitaThemeStyle.SoraCloud,
            preset = ThemeTools.COLOR_PRESET.HIGH_CONTRAST,
            isDark = true,
        )

        assertEquals(light, dark)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFC94B38), light[0])
        assertEquals(androidx.compose.ui.graphics.Color(0xFFD2A23C), light[1])
        assertEquals(androidx.compose.ui.graphics.Color(0xFF315D6D), light[2])
        assertTrue(
            "sunset poster course palette must not contain purple",
            light.none { color -> color.red > color.green && color.blue > color.green },
        )
    }

    @Test
    fun soraCourseContentUsesPaperAndInkContrast() {
        assertEquals(
            androidx.compose.ui.graphics.Color(0xFF25221F),
            soraCloudCourseContentColor(isDark = false),
        )
        assertEquals(
            androidx.compose.ui.graphics.Color(0xFFF4ECD9),
            soraCloudCourseContentColor(isDark = true),
        )
    }

    @Test
    fun sumiCourseBubblesAlwaysUseBambooGreen() {
        val expected = androidx.compose.ui.graphics.Color(0xFF3F5F54)
        val light = hitaCoursePaletteFor(
            style = HitaThemeStyle.Sumi,
            preset = ThemeTools.COLOR_PRESET.CLASSIC,
            isDark = false,
        )
        val dark = hitaCoursePaletteFor(
            style = HitaThemeStyle.Sumi,
            preset = ThemeTools.COLOR_PRESET.HIGH_CONTRAST,
            isDark = true,
        )

        assertEquals(listOf(expected), light)
        assertEquals(light, dark)
        listOf("计算机设计与实践", "软件构造实践", "高等数学").forEach { courseKey ->
            assertEquals(
                expected,
                resolveHitaCourseColor(
                    style = HitaThemeStyle.Sumi,
                    preset = ThemeTools.COLOR_PRESET.CLASSIC,
                    isDark = false,
                    courseKey = courseKey,
                    storedColor = 0xFF8C4A5A.toInt(),
                ),
            )
        }
    }
}
