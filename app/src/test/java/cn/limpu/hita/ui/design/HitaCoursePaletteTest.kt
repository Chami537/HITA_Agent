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
    fun personaPaletteKeepsBrandColorsAcrossDisplayModes() {
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
        assertTrue(light.contains(androidx.compose.ui.graphics.Color(0xFFE60012)))
        assertTrue(light.contains(androidx.compose.ui.graphics.Color(0xFFFFD400)))
        assertTrue(light.contains(androidx.compose.ui.graphics.Color(0xFFF5F1E8)))
        assertTrue(
            "black and gray are structural colors, not course fills",
            light.none { color -> color.red < 0.45f && color.green < 0.45f && color.blue < 0.45f },
        )
    }

    @Test
    fun personaCourseShadowStaysVisibleInLightAndDarkModes() {
        val red = androidx.compose.ui.graphics.Color(0xFFE60012)
        val paper = androidx.compose.ui.graphics.Color(0xFFF5F1E8)

        assertEquals(
            androidx.compose.ui.graphics.Color(0xFF101010),
            personaCourseShadowColor(red, isDark = false),
        )
        assertEquals(
            androidx.compose.ui.graphics.Color(0xFFFFD400),
            personaCourseShadowColor(red, isDark = true),
        )
        assertEquals(
            androidx.compose.ui.graphics.Color(0xFFE60012),
            personaCourseShadowColor(paper, isDark = true),
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
}
