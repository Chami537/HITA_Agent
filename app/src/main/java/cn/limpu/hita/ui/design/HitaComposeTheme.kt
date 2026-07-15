package cn.limpu.hita.ui.design

import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.limpu.hita.R
import com.limpu.style.ThemeTools

enum class HitaThemeStyle {
    Classic,
    Fresh,
    Focus,
    HighContrast,
    AppleGlass
}

@Immutable
data class HitaThemeState(
    val mode: ThemeTools.MODE,
    val preferenceStyle: ThemeTools.STYLE,
    val style: HitaThemeStyle,
    val isDark: Boolean,
)

@Immutable
data class HitaSpacing(
    val none: Dp,
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val xxl: Dp,
    val xxxl: Dp,
    val xxxxl: Dp
)

@Immutable
data class HitaRadius(
    val none: Dp,
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val full: Dp
)

@Immutable
data class HitaComponentSize(
    val toolbarHeight: Dp,
    val bottomNavigationHeight: Dp,
    val timetableLabelWidth: Dp,
    val timetableDateHeight: Dp,
    val timelineWidth: Dp
)

@Immutable
data class HitaDesignTokens(
    val spacing: HitaSpacing,
    val radius: HitaRadius,
    val componentSize: HitaComponentSize
)

private val LocalHitaDesignTokens = staticCompositionLocalOf {
    HitaDesignTokens(
        spacing = HitaSpacing(
            none = 0.dp,
            xs = 4.dp,
            sm = 8.dp,
            md = 12.dp,
            lg = 16.dp,
            xl = 24.dp,
            xxl = 32.dp,
            xxxl = 48.dp,
            xxxxl = 64.dp
        ),
        radius = HitaRadius(
            none = 0.dp,
            xs = 4.dp,
            sm = 8.dp,
            md = 12.dp,
            lg = 16.dp,
            xl = 24.dp,
            full = 999.dp
        ),
        componentSize = HitaComponentSize(
            toolbarHeight = 56.dp,
            bottomNavigationHeight = 80.dp,
            timetableLabelWidth = 28.dp,
            timetableDateHeight = 24.dp,
            timelineWidth = 2.dp
        )
    )
}

private val LocalHitaThemeState = staticCompositionLocalOf {
    HitaThemeState(
        mode = ThemeTools.MODE.FOLLOW,
        preferenceStyle = ThemeTools.STYLE.CLASSIC,
        style = HitaThemeStyle.Classic,
        isDark = false,
    )
}

object HitaTheme {
    val tokens: HitaDesignTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalHitaDesignTokens.current

    val mode: ThemeTools.MODE
        @Composable
        @ReadOnlyComposable
        get() = LocalHitaThemeState.current.mode

    val preferenceStyle: ThemeTools.STYLE
        @Composable
        @ReadOnlyComposable
        get() = LocalHitaThemeState.current.preferenceStyle

    val style: HitaThemeStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalHitaThemeState.current.style

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalHitaThemeState.current.isDark
}

@Composable
fun HitaComposeTheme(
    style: HitaThemeStyle? = null,
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(context) { ThemeTools.getThemePreferences(context) }
    var themeMode by remember(preferences) { mutableStateOf(ThemeTools.getThemeMode(context)) }
    var preferenceStyle by remember(preferences) { mutableStateOf(ThemeTools.getThemeStyle(context)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                ThemeTools.KEY_MODE -> themeMode = ThemeTools.getThemeMode(context)
                ThemeTools.KEY_STYLE -> preferenceStyle = ThemeTools.getThemeStyle(context)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val tokens = hitaDesignTokens()
    val actualDarkTheme = darkTheme ?: when (themeMode) {
        ThemeTools.MODE.DARK -> true
        ThemeTools.MODE.LIGHT -> false
        ThemeTools.MODE.FOLLOW -> isSystemInDarkTheme()
    }
    val actualStyle = style ?: preferenceStyle.toHitaThemeStyle()
    val themeState = HitaThemeState(
        mode = themeMode,
        preferenceStyle = preferenceStyle,
        style = actualStyle,
        isDark = actualDarkTheme,
    )
    CompositionLocalProvider(
        LocalHitaDesignTokens provides tokens,
        LocalHitaThemeState provides themeState,
    ) {
        MaterialTheme(
            colorScheme = hitaColorScheme(style = actualStyle, darkTheme = actualDarkTheme),
            typography = Typography(),
            shapes = Shapes(),
            content = content
        )
    }
}

private fun ThemeTools.STYLE.toHitaThemeStyle(): HitaThemeStyle {
    return when (this) {
        ThemeTools.STYLE.CLASSIC -> HitaThemeStyle.Classic
        ThemeTools.STYLE.FRESH -> HitaThemeStyle.Fresh
        ThemeTools.STYLE.FOCUS -> HitaThemeStyle.Focus
        ThemeTools.STYLE.HIGH_CONTRAST -> HitaThemeStyle.HighContrast
        ThemeTools.STYLE.APPLE_GLASS -> HitaThemeStyle.AppleGlass
    }
}

@Composable
private fun hitaDesignTokens(): HitaDesignTokens {
    return HitaDesignTokens(
        spacing = HitaSpacing(
            none = dimensionResource(R.dimen.ds_space_none),
            xs = dimensionResource(R.dimen.ds_space_xs),
            sm = dimensionResource(R.dimen.ds_space_sm),
            md = dimensionResource(R.dimen.ds_space_md),
            lg = dimensionResource(R.dimen.ds_space_lg),
            xl = dimensionResource(R.dimen.ds_space_xl),
            xxl = dimensionResource(R.dimen.ds_space_2xl),
            xxxl = dimensionResource(R.dimen.ds_space_3xl),
            xxxxl = dimensionResource(R.dimen.ds_space_4xl)
        ),
        radius = HitaRadius(
            none = dimensionResource(R.dimen.ds_radius_none),
            xs = dimensionResource(R.dimen.ds_radius_xs),
            sm = dimensionResource(R.dimen.ds_radius_sm),
            md = dimensionResource(R.dimen.ds_radius_md),
            lg = dimensionResource(R.dimen.ds_radius_lg),
            xl = dimensionResource(R.dimen.ds_radius_xl),
            full = dimensionResource(R.dimen.ds_radius_full)
        ),
        componentSize = HitaComponentSize(
            toolbarHeight = dimensionResource(R.dimen.ds_toolbar_height),
            bottomNavigationHeight = dimensionResource(R.dimen.ds_bottom_navigation_height),
            timetableLabelWidth = dimensionResource(R.dimen.ds_timetable_label_width),
            timetableDateHeight = dimensionResource(R.dimen.ds_timetable_date_height),
            timelineWidth = dimensionResource(R.dimen.ds_timeline_width)
        )
    )
}

@Composable
private fun hitaColorScheme(
    style: HitaThemeStyle,
    darkTheme: Boolean
): ColorScheme {
    val base = if (darkTheme) {
        darkColorScheme(
            primary = colorResource(R.color.ds_color_brand),
            onPrimary = colorResource(R.color.ds_color_brand_on),
            primaryContainer = colorResource(R.color.ds_color_brand_container),
            onPrimaryContainer = colorResource(R.color.ds_color_brand_on_container),
            secondary = colorResource(R.color.ds_color_accent),
            tertiary = colorResource(R.color.ds_color_info),
            background = colorResource(R.color.ds_color_background),
            surface = colorResource(R.color.ds_color_surface),
            onSurface = colorResource(R.color.ds_color_text_primary),
            onSurfaceVariant = colorResource(R.color.ds_color_text_secondary),
            outline = colorResource(R.color.ds_color_outline),
            outlineVariant = colorResource(R.color.ds_color_outline_subtle),
            error = colorResource(R.color.ds_color_error),
            errorContainer = colorResource(R.color.ds_color_error_container)
        )
    } else {
        lightColorScheme(
            primary = colorResource(R.color.ds_color_brand),
            onPrimary = colorResource(R.color.ds_color_brand_on),
            primaryContainer = colorResource(R.color.ds_color_brand_container),
            onPrimaryContainer = colorResource(R.color.ds_color_brand_on_container),
            secondary = colorResource(R.color.ds_color_accent),
            tertiary = colorResource(R.color.ds_color_info),
            background = colorResource(R.color.ds_color_background),
            surface = colorResource(R.color.ds_color_surface),
            onSurface = colorResource(R.color.ds_color_text_primary),
            onSurfaceVariant = colorResource(R.color.ds_color_text_secondary),
            outline = colorResource(R.color.ds_color_outline),
            outlineVariant = colorResource(R.color.ds_color_outline_subtle),
            error = colorResource(R.color.ds_color_error),
            errorContainer = colorResource(R.color.ds_color_error_container)
        )
    }

    return when (style) {
        HitaThemeStyle.Classic -> base
        HitaThemeStyle.Fresh -> if (darkTheme) {
            base.copy(
                primary = Color(0xFF79D5C0),
                onPrimary = Color(0xFF00382F),
                primaryContainer = Color(0xFF135348),
                onPrimaryContainer = Color(0xFFB8F1E2),
                secondary = Color(0xFFB6CCAC),
                tertiary = Color(0xFFFFB59F),
                background = Color(0xFF101512),
                surface = Color(0xFF171D19),
                surfaceVariant = Color(0xFF26312B),
                onSurface = Color(0xFFE1E9E3),
                onSurfaceVariant = Color(0xFFBCC9C1),
                outline = Color(0xFF87948D),
                outlineVariant = Color(0xFF3D4942),
            )
        } else {
            base.copy(
                primary = Color(0xFF126B5D),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFCDE9DF),
                onPrimaryContainer = Color(0xFF0B3D35),
                secondary = Color(0xFF52634C),
                tertiary = Color(0xFF8A4F3D),
                background = Color(0xFFF6FAF7),
                surface = Color.White,
                surfaceVariant = Color(0xFFE5EEE8),
                onSurface = Color(0xFF17201B),
                onSurfaceVariant = Color(0xFF4A5750),
                outline = Color(0xFF727E77),
                outlineVariant = Color(0xFFC1CBC5),
            )
        }
        HitaThemeStyle.Focus -> if (darkTheme) {
            base.copy(
                primary = Color(0xFFA3C9F0),
                onPrimary = Color(0xFF003258),
                primaryContainer = Color(0xFF244B70),
                onPrimaryContainer = Color(0xFFD2E7FF),
                secondary = Color(0xFFD8BFD4),
                tertiary = Color(0xFFE0C36D),
                background = Color(0xFF111416),
                surface = Color(0xFF191C1F),
                surfaceVariant = Color(0xFF2D3034),
                onSurface = Color(0xFFE4E7EA),
                onSurfaceVariant = Color(0xFFC3C7CC),
                outline = Color(0xFF8D9197),
                outlineVariant = Color(0xFF43474C),
            )
        } else {
            base.copy(
                primary = Color(0xFF315E8A),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFD5E8FA),
                onPrimaryContainer = Color(0xFF173B5E),
                secondary = Color(0xFF765574),
                tertiary = Color(0xFF766028),
                background = Color(0xFFF8F8F6),
                surface = Color.White,
                surfaceVariant = Color(0xFFE9EBED),
                onSurface = Color(0xFF1A1C1E),
                onSurfaceVariant = Color(0xFF4D5155),
                outline = Color(0xFF74787D),
                outlineVariant = Color(0xFFC5C8CC),
            )
        }
        HitaThemeStyle.HighContrast -> base.copy(
            primary = if (darkTheme) Color(0xFFFFFF00) else Color(0xFF0033CC),
            onPrimary = if (darkTheme) Color.Black else Color.White,
            primaryContainer = if (darkTheme) Color(0xFF3B3B00) else Color(0xFFDCE5FF),
            onPrimaryContainer = if (darkTheme) Color.White else Color.Black,
            surface = if (darkTheme) Color.Black else Color.White,
            background = if (darkTheme) Color.Black else Color.White,
            surfaceVariant = if (darkTheme) Color(0xFF151515) else Color(0xFFF0F0F0),
            onSurface = if (darkTheme) Color.White else Color.Black,
            onSurfaceVariant = if (darkTheme) Color.White else Color.Black,
            outline = if (darkTheme) Color.White else Color.Black,
            outlineVariant = if (darkTheme) Color(0xFFBDBDBD) else Color(0xFF333333),
        )
        HitaThemeStyle.AppleGlass -> base.copy(
            primary = Color(0xFF007AFF),
            onPrimary = Color.White,
            onPrimaryContainer = if (darkTheme) Color(0xFFD6EAFF) else Color(0xFF00315C),
            background = if (darkTheme) Color(0xFF090B10) else Color(0xFFF4F8FC),
            surface = if (darkTheme) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.42f),
            surfaceVariant = if (darkTheme) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.30f),
            primaryContainer = if (darkTheme) Color(0xFF0A84FF).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.34f),
            onSurface = if (darkTheme) Color(0xFFF5F7FA) else Color(0xFF101318),
            onSurfaceVariant = if (darkTheme) Color(0xFFCCD2DA) else Color(0xFF505761),
            outline = Color.White.copy(alpha = if (darkTheme) 0.34f else 0.72f),
            outlineVariant = Color.White.copy(alpha = if (darkTheme) 0.18f else 0.48f)
        )
    }
}
