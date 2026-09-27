package cn.limpu.hita.data.source.preference

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 每个校区独立的拉取节流：last_fetch_day_<campus> / parser_version_<campus>。
 * 旧版无校区后缀的 Shenzhen key 不迁移，升级后首次会多拉一次，可接受。
 */
class CampusNoticePreferenceSource(context: Context, campus: String) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyDay = "${KEY_LAST_FETCH_DAY}_$campus"
    private val keyVersion = "${KEY_PARSER_VERSION}_$campus"

    var lastFetchDay: String
        get() = prefs.getString(keyDay, "").orEmpty()
        set(value) {
            prefs.edit().putString(keyDay, value).apply()
        }

    fun isFetchedToday(): Boolean = lastFetchDay == todayShanghai()

    var parserVersion: Int
        get() = prefs.getInt(keyVersion, 0)
        set(value) {
            prefs.edit().putInt(keyVersion, value).apply()
        }

    fun markFetchedToday(parserVersion: Int) {
        lastFetchDay = todayShanghai()
        this.parserVersion = parserVersion
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
