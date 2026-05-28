package com.limpu.hitax.ui.design

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.limpu.hitax.R
import com.limpu.style.ThemeTools

enum class HitaThemeStyle {
    Classic,
    Fresh,
    Focus,
    HighContrast
}

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

object HitaTheme {
    val tokens: HitaDesignTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalHitaDesignTokens.current
}

@Composable
fun HitaComposeTheme(
    style: HitaThemeStyle? = null,
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val tokens = hitaDesignTokens()
    val actualDarkTheme = darkTheme ?: resolveDarkTheme(context)
    val themeStyle = style ?: ThemeTools.getThemeStyle(context).toHitaThemeStyle()
    CompositionLocalProvider(LocalHitaDesignTokens provides tokens) {
        MaterialTheme(
            colorScheme = hitaColorScheme(style = themeStyle, darkTheme = actualDarkTheme),
            typography = Typography(),
            shapes = Shapes(),
            content = content
        )
    }
}

@Composable
private fun resolveDarkTheme(context: Context): Boolean {
    return when (ThemeTools.getThemeMode(context)) {
        ThemeTools.MODE.DARK -> true
        ThemeTools.MODE.LIGHT -> false
        ThemeTools.MODE.FOLLOW -> isSystemInDarkTheme()
    }
}

private fun ThemeTools.STYLE.toHitaThemeStyle(): HitaThemeStyle {
    return when (this) {
        ThemeTools.STYLE.CLASSIC -> HitaThemeStyle.Classic
        ThemeTools.STYLE.FRESH -> HitaThemeStyle.Fresh
        ThemeTools.STYLE.FOCUS -> HitaThemeStyle.Focus
        ThemeTools.STYLE.HIGH_CONTRAST -> HitaThemeStyle.HighContrast
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
        HitaThemeStyle.Fresh -> base
        HitaThemeStyle.Focus -> base
        HitaThemeStyle.HighContrast -> base.copy(
            surface = if (darkTheme) Color.Black else Color.White,
            background = if (darkTheme) Color.Black else Color.White,
            onSurface = if (darkTheme) Color.White else Color.Black
        )
    }
}
