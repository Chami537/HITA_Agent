package cn.limpu.hita.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import cn.limpu.hita.R

data class HitaFontScheme(
    val key: String,
    val primary: FontFamily,
    val display: FontFamily = primary,
)

private val p5FontFamily = FontFamily(Font(R.font.p5_title, FontWeight.Black))
private val sumiFontFamily = FontFamily(Font(R.font.sumi_title, FontWeight.Bold))
private val interFontFamily = FontFamily(Font(R.font.inter))
private val orbitronFontFamily = FontFamily(Font(R.font.orbitron))
private val spaceGroteskFontFamily = FontFamily(Font(R.font.space_grotesk))

fun hitaFontSchemeFor(style: HitaThemeStyle): HitaFontScheme = when (style) {
    HitaThemeStyle.Classic -> HitaFontScheme("classic", FontFamily.SansSerif)
    HitaThemeStyle.AppleGlass -> HitaFontScheme("apple_glass", interFontFamily)
    HitaThemeStyle.Cyber -> HitaFontScheme("cyber", orbitronFontFamily)
    HitaThemeStyle.SoraCloud -> HitaFontScheme("sora_cloud", FontFamily.Serif)
    HitaThemeStyle.Persona5 -> HitaFontScheme("p5", p5FontFamily)
    HitaThemeStyle.DeepSpace -> HitaFontScheme("deep_space", spaceGroteskFontFamily)
    HitaThemeStyle.Sumi -> HitaFontScheme("sumi", sumiFontFamily)
}

@Composable
fun hitaActiveFontScheme(): HitaFontScheme = hitaFontSchemeFor(HitaTheme.style)
