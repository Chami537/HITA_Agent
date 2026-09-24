package cn.limpu.hita.data.source.preference

import android.content.Context
import android.content.SharedPreferences

class BlogPreferenceSource(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lastBuildDate: String
        get() = prefs.getString(KEY_LAST_BUILD, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_LAST_BUILD, value).apply()
        }

    var hasBaseline: Boolean
        get() = prefs.getBoolean(KEY_HAS_BASELINE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_HAS_BASELINE, value).apply()
        }

    fun readGuids(): Set<String> = prefs.getStringSet(KEY_READ_GUIDS, emptySet()).orEmpty().toSet()

    fun tabClearedGuids(): Set<String> =
        prefs.getStringSet(KEY_TAB_CLEARED, emptySet()).orEmpty().toSet()

    fun replaceReadGuids(guids: Set<String>) {
        prefs.edit().putStringSet(KEY_READ_GUIDS, HashSet(guids)).apply()
    }

    fun addReadGuid(guid: String) {
        if (guid.isBlank()) return
        replaceReadGuids(readGuids() + guid)
    }

    fun replaceTabClearedGuids(guids: Set<String>) {
        prefs.edit().putStringSet(KEY_TAB_CLEARED, HashSet(guids)).apply()
    }

    companion object {
        private const val PREFS_NAME = "hoa_blog"
        private const val KEY_LAST_BUILD = "last_build_date"
        private const val KEY_HAS_BASELINE = "has_baseline"
        private const val KEY_READ_GUIDS = "read_guids"
        private const val KEY_TAB_CLEARED = "tab_cleared_guids"
    }
}
