package cn.limpu.hita.data.source.preference

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private const val SP_NAME = "score_reminder"
private const val KEY_ENABLED = "enabled"
private const val KEY_KNOWN_SCORES = "known_scores"

class ScoreReminderStore constructor(context: Context) {
    private val preference: SharedPreferences =
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preference.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        preference.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getKnownScores(scopeKey: String): Set<String>? {
        val raw = preference.getString("${KEY_KNOWN_SCORES}_$scopeKey", null) ?: return null
        return runCatching {
            val type = object : TypeToken<Set<String>>() {}.type
            Gson().fromJson<Set<String>>(raw, type)
        }.getOrNull()
    }

    fun setKnownScores(scopeKey: String, values: Set<String>) {
        preference.edit().putString("${KEY_KNOWN_SCORES}_$scopeKey", Gson().toJson(values)).apply()
    }

    fun clearKnownScores() {
        val editor = preference.edit().remove(KEY_KNOWN_SCORES)
        preference.all.keys
            .filter { it.startsWith("${KEY_KNOWN_SCORES}_") }
            .forEach(editor::remove)
        editor.apply()
    }
}
