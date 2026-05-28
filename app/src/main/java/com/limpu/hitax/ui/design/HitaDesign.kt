package com.limpu.hitax.ui.design

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.core.content.ContextCompat
import com.limpu.hitax.R

/**
 * Kotlin facade for the HITA design resources.
 *
 * Use this in custom Views and dynamic UI code instead of hard-coded dp/color
 * values. XML layouts should use the matching ds_* resources in
 * values/design_system.xml.
 */
object HitaDesign {
    object Color {
        @ColorRes val brand = R.color.ds_color_brand
        @ColorRes val brandOn = R.color.ds_color_brand_on
        @ColorRes val brandContainer = R.color.ds_color_brand_container
        @ColorRes val accent = R.color.ds_color_accent
        @ColorRes val info = R.color.ds_color_info
        @ColorRes val background = R.color.ds_color_background
        @ColorRes val surface = R.color.ds_color_surface
        @ColorRes val textPrimary = R.color.ds_color_text_primary
        @ColorRes val textSecondary = R.color.ds_color_text_secondary
        @ColorRes val outline = R.color.ds_color_outline
        @ColorRes val outlineSubtle = R.color.ds_color_outline_subtle
        @ColorRes val error = R.color.ds_color_error
    }

    object Space {
        @DimenRes val none = R.dimen.ds_space_none
        @DimenRes val xs = R.dimen.ds_space_xs
        @DimenRes val sm = R.dimen.ds_space_sm
        @DimenRes val md = R.dimen.ds_space_md
        @DimenRes val lg = R.dimen.ds_space_lg
        @DimenRes val xl = R.dimen.ds_space_xl
        @DimenRes val xxl = R.dimen.ds_space_2xl
        @DimenRes val xxxl = R.dimen.ds_space_3xl
    }

    object Radius {
        @DimenRes val none = R.dimen.ds_radius_none
        @DimenRes val xs = R.dimen.ds_radius_xs
        @DimenRes val sm = R.dimen.ds_radius_sm
        @DimenRes val md = R.dimen.ds_radius_md
        @DimenRes val lg = R.dimen.ds_radius_lg
        @DimenRes val xl = R.dimen.ds_radius_xl
        @DimenRes val full = R.dimen.ds_radius_full
    }

    object Component {
        @DimenRes val toolbarHeight = R.dimen.ds_toolbar_height
        @DimenRes val bottomNavigationHeight = R.dimen.ds_bottom_navigation_height
        @DimenRes val timetableLabelWidth = R.dimen.ds_timetable_label_width
        @DimenRes val timetableDateHeight = R.dimen.ds_timetable_date_height
        @DimenRes val timelineWidth = R.dimen.ds_timeline_width
    }
}

@ColorInt
fun Context.dsColor(@ColorRes colorRes: Int): Int = ContextCompat.getColor(this, colorRes)

fun Context.dsDimen(@DimenRes dimenRes: Int): Float = resources.getDimension(dimenRes)

fun Context.dsDimenPx(@DimenRes dimenRes: Int): Int = resources.getDimensionPixelSize(dimenRes)

@ColorInt
fun Context.dsThemeColor(@AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}
