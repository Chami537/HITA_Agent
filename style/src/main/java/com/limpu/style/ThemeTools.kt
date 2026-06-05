package com.limpu.style

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeTools {
    enum class MODE { DARK, LIGHT, FOLLOW }
    enum class STYLE { CLASSIC, FRESH, FOCUS, HIGH_CONTRAST, APPLE_GLASS }


    fun getThemeMode(context: Context): MODE {
        val pr = context.getSharedPreferences("theme", Context.MODE_PRIVATE)
        return when (pr.getString("mode", "follow")) {
            "dark" -> MODE.DARK
            "light" -> MODE.LIGHT
            else -> MODE.FOLLOW
        }
    }

    private fun setThemeMode(context: Context, mode: MODE) {
        val pr = context.getSharedPreferences("theme", Context.MODE_PRIVATE)
        val str = when (mode) {
            MODE.DARK -> "dark"
            MODE.LIGHT -> "light"
            else -> "follow"
        }
        pr.edit().putString("mode", str).apply()
    }

    fun getThemeStyle(context: Context): STYLE {
        val pr = context.getSharedPreferences("theme", Context.MODE_PRIVATE)
        return when (pr.getString("style", "classic")) {
            "fresh" -> STYLE.FRESH
            "focus" -> STYLE.FOCUS
            "high_contrast" -> STYLE.HIGH_CONTRAST
            "apple_glass" -> STYLE.APPLE_GLASS
            else -> STYLE.CLASSIC
        }
    }

    fun setThemeStyle(context: Context, style: STYLE) {
        val str = when (style) {
            STYLE.CLASSIC -> "classic"
            STYLE.FRESH -> "fresh"
            STYLE.FOCUS -> "focus"
            STYLE.HIGH_CONTRAST -> "high_contrast"
            STYLE.APPLE_GLASS -> "apple_glass"
        }
        context.getSharedPreferences("theme", Context.MODE_PRIVATE)
            .edit()
            .putString("style", str)
            .apply()
    }

    fun switchTheme(activity: Activity) {
        val newMode = when (getThemeMode(activity)) {
            MODE.DARK->MODE.LIGHT
            MODE.LIGHT->MODE.FOLLOW
            MODE.FOLLOW->MODE.DARK
        }
        setThemeMode(activity, newMode)
        val nightMode = when (newMode) {
            MODE.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            MODE.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MODE.FOLLOW -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
