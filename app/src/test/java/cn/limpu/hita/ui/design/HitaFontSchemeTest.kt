package cn.limpu.hita.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HitaFontSchemeTest {
    @Test
    fun everyThemeStyleResolvesToAStableFontScheme() {
        val schemes = HitaThemeStyle.entries.map(::hitaFontSchemeFor)

        assertEquals(HitaThemeStyle.entries.size, schemes.map { it.key }.distinct().size)
        schemes.forEach { scheme ->
            assertNotNull(scheme.primary)
            assertNotNull(scheme.display)
        }
    }

    @Test
    fun specialThemesKeepTheirBundledFontIdentity() {
        assertEquals("p5", hitaFontSchemeFor(HitaThemeStyle.Persona5).key)
        assertEquals("sumi", hitaFontSchemeFor(HitaThemeStyle.Sumi).key)
    }

    @Test
    fun typographyUsesTheSchemeForAllMaterialTextRoles() {
        val scheme = hitaFontSchemeFor(HitaThemeStyle.Cyber)
        val typography = hitaTypographyFor(scheme)

        listOf(
            typography.displayLarge,
            typography.headlineMedium,
            typography.titleMedium,
            typography.bodyLarge,
            typography.labelLarge,
        ).forEach { style ->
            assertTrue(style.fontFamily === scheme.primary || style.fontFamily === scheme.display)
        }
    }
}
