package cn.limpu.hita.data.source.preference

import android.content.Context
import cn.limpu.hita.data.source.web.notice.CampusNoticeParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CampusNoticePreferenceSource(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lastFetchDay: String
        get() = prefs.getString(KEY_LAST_FETCH_DAY, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_LAST_FETCH_DAY, value).apply()
        }

    fun isFetchedToday(): Boolean = lastFetchDay == todayShanghai()

    var parserVersion: Int
        get() = prefs.getInt(KEY_PARSER_VERSION, 0)
        set(value) {
            prefs.edit().putInt(KEY_PARSER_VERSION, value).apply()
        }

    fun markFetchedToday() {
        lastFetchDay = todayShanghai()
        parserVersion = CampusNoticeParser.PARSER_VERSION
    }

    companion object {
        private const val PREFS_NAME = "campus_notice"
        private const val KEY_LAST_FETCH_DAY = "last_fetch_day"
        private const val KEY_PARSER_VERSION = "parser_version"

        fun todayShanghai(): String {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            format.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            return format.format(Date())
        }
    }
}
