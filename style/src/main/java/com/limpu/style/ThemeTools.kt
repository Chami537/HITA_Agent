package com.limpu.style

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

object ThemeTools {
    enum class MODE { DARK, LIGHT, FOLLOW }
    enum class STYLE { CLASSIC, APPLE_GLASS, CYBER, SORA_CLOUD, P5, DEEP_SPACE, SUMI }
    enum class COLOR_PRESET { CLASSIC, FRESH, FOCUS, HIGH_CONTRAST }

    const val PREFERENCE_NAME = "theme"
    const val KEY_MODE = "mode"
    const val KEY_STYLE = "style"
    const val KEY_COLOR_PRESET = "color_preset"

    fun getThemePreferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): MODE {
        return when (getThemePreferences(context).getString(KEY_MODE, "follow")) {
            "dark" -> MODE.DARK
            "light" -> MODE.LIGHT
            else -> MODE.FOLLOW
        }
    }

    fun setThemeMode(context: Context, mode: MODE) {
        val str = when (mode) {
            MODE.DARK -> "dark"
            MODE.LIGHT -> "light"
            else -> "follow"
        }
        getThemePreferences(context).edit().putString(KEY_MODE, str).apply()
    }

    fun getThemeStyle(context: Context): STYLE {
        val preferences = getThemePreferences(context)
        return when (val stored = preferences.getString(KEY_STYLE, "classic")) {
            // 旧版把基础色板存成了“风格”。首次读取时无损迁移到独立色板设置。
            "fresh", "focus", "high_contrast" -> {
                val preset = when (stored) {
                    "fresh" -> "fresh"
                    "focus" -> "focus"
                    else -> "high_contrast"
                }
                preferences.edit()
                    .putString(KEY_STYLE, "classic")
                    .putString(KEY_COLOR_PRESET, preset)
                    .apply()
                STYLE.CLASSIC
            }
            "apple_glass" -> STYLE.APPLE_GLASS
            "cyber" -> STYLE.CYBER
            "sora_cloud" -> STYLE.SORA_CLOUD
            "p5" -> STYLE.P5
            "deep_space" -> STYLE.DEEP_SPACE
            "sumi" -> STYLE.SUMI
            else -> STYLE.CLASSIC
        }
    }

    fun setThemeStyle(context: Context, style: STYLE) {
        val str = when (style) {
            STYLE.CLASSIC -> "classic"
            STYLE.APPLE_GLASS -> "apple_glass"
            STYLE.CYBER -> "cyber"
            STYLE.SORA_CLOUD -> "sora_cloud"
            STYLE.P5 -> "p5"
            STYLE.DEEP_SPACE -> "deep_space"
            STYLE.SUMI -> "sumi"
        }
        getThemePreferences(context)
            .edit()
            .putString(KEY_STYLE, str)
            .apply()
    }

    fun getColorPreset(context: Context): COLOR_PRESET {
        return when (getThemePreferences(context).getString(KEY_COLOR_PRESET, "classic")) {
            "fresh" -> COLOR_PRESET.FRESH
            "focus" -> COLOR_PRESET.FOCUS
            "high_contrast" -> COLOR_PRESET.HIGH_CONTRAST
            else -> COLOR_PRESET.CLASSIC
        }
    }

    fun setColorPreset(context: Context, preset: COLOR_PRESET) {
        val value = when (preset) {
            COLOR_PRESET.CLASSIC -> "classic"
            COLOR_PRESET.FRESH -> "fresh"
            COLOR_PRESET.FOCUS -> "focus"
            COLOR_PRESET.HIGH_CONTRAST -> "high_contrast"
        }
        getThemePreferences(context)
            .edit()
            .putString(KEY_COLOR_PRESET, value)
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
