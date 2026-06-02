package cn.limpu.hita.data.repository

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import javax.inject.Inject
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.limpu.component.data.booleanLiveData
import com.limpu.component.data.intLiveData
import com.limpu.component.data.stringLiveData
import cn.limpu.hita.ui.main.timetable.TimetableStyleSheet

private const val SP_NAME = "timetable_style"
const val KEY_START_DATE = "start_date"
const val KEY_DRAW_BG_LINE = "draw_bg_line"
const val KEY_COLOR_ENABLE = "color_enable"
const val KEY_FADE_ENABLE = "fade_enable"
const val KEY_LABEL_PERIOD = "label_period"
const val KEY_WALLPAPER_PATH = "wallpaper_path"
const val KEY_WALLPAPER_SCRIM = "wallpaper_scrim"
const val KEY_CARD_OPACITY = "card_opacity"

class TimetableStyleRepository @Inject constructor(application: Application) {
    private val timetableStyleSP: SharedPreferences = application.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
    val startTimeLiveData = timetableStyleSP.intLiveData(KEY_START_DATE, 830)
    val drawBGLinesLiveData = timetableStyleSP.booleanLiveData(KEY_DRAW_BG_LINE, true)
    val colorEnableLiveData = timetableStyleSP.booleanLiveData(KEY_COLOR_ENABLE, true)
    val fadeEnableLiveData = timetableStyleSP.booleanLiveData(KEY_FADE_ENABLE, true)
    val periodLabelLiveData = timetableStyleSP.booleanLiveData(KEY_LABEL_PERIOD, false)
    val wallpaperPathLiveData = timetableStyleSP.stringLiveData(KEY_WALLPAPER_PATH, "")
    val wallpaperScrimLiveData = timetableStyleSP.intLiveData(KEY_WALLPAPER_SCRIM, 30)
    val cardOpacityLiveData = timetableStyleSP.intLiveData(KEY_CARD_OPACITY, 85)
    val wallpaperDateColorLiveData = MutableLiveData(Color.WHITE)
    val wallpaperLabelColorLiveData = MutableLiveData(Color.WHITE)


    fun putData(key: String, value: Int) {
        timetableStyleSP.edit().putInt(key, value).apply()
    }

    fun putData(key: String, value: Boolean) {
        timetableStyleSP.edit().putBoolean(key, value).apply()
    }

    fun putData(key: String, value: String) {
        timetableStyleSP.edit().putString(key, value).apply()
    }

    fun getStyleSheetLiveData(): MediatorLiveData<TimetableStyleSheet> {
        val sheet = MediatorLiveData<TimetableStyleSheet>()
        sheet.value = TimetableStyleSheet()
        sheet.addSource(startTimeLiveData) { start ->
            val ts = sheet.value
            ts?.let {
                it.startTime = start
                sheet.value = it
            }
        }
        sheet.addSource(drawBGLinesLiveData) { draw ->
            val ts = sheet.value
            ts?.let {
                it.drawBGLine = draw
                sheet.value = it
            }
        }
        sheet.addSource(colorEnableLiveData) { draw ->
            val ts = sheet.value
            ts?.let {
                it.isColorEnabled = draw
                sheet.value = it
            }
        }
        sheet.addSource(fadeEnableLiveData) { fade ->
            val ts = sheet.value
            ts?.let {
                it.isFadeEnabled = fade
                sheet.value = it
            }
        }
        sheet.addSource(periodLabelLiveData) { enabled ->
            val ts = sheet.value
            ts?.let {
                it.usePeriodLabel = enabled
                sheet.value = it
            }
        }
        sheet.addSource(cardOpacityLiveData) { opacity ->
            val ts = sheet.value
            ts?.let {
                it.cardOpacity = opacity
                sheet.value = it
            }
        }
        return sheet
    }
}
