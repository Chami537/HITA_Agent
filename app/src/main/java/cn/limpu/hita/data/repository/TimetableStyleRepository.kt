package cn.limpu.hita.data.repository

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import javax.inject.Inject
import javax.inject.Singleton
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.limpu.component.data.booleanLiveData
import com.limpu.component.data.intLiveData
import com.limpu.component.data.stringLiveData
import cn.limpu.hita.ui.main.timetable.TimetableStyleSheet
import cn.limpu.hita.ui.main.timetable.CourseBubbleStyle

private const val SP_NAME = "timetable_style"
const val KEY_START_DATE = "start_date"
const val KEY_DRAW_BG_LINE = "draw_bg_line"
const val KEY_COLOR_ENABLE = "color_enable"
const val KEY_FADE_ENABLE = "fade_enable"
const val KEY_LABEL_PERIOD = "label_period"
const val KEY_WALLPAPER_PATH = "wallpaper_path"
const val KEY_WALLPAPER_SCRIM = "wallpaper_scrim"
const val KEY_CARD_OPACITY = "card_opacity"
const val KEY_COURSE_BUBBLE_STYLE = "course_bubble_style"

@Singleton
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
    val courseBubbleStyleLiveData = timetableStyleSP.stringLiveData(
        KEY_COURSE_BUBBLE_STYLE,
        CourseBubbleStyle.SOLID.storageValue
    )
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
            sheet.value = sheet.value?.copy(startTime = start)
        }
        sheet.addSource(drawBGLinesLiveData) { draw ->
            sheet.value = sheet.value?.copy(drawBGLine = draw)
        }
        sheet.addSource(colorEnableLiveData) { draw ->
            sheet.value = sheet.value?.copy(isColorEnabled = draw)
        }
        sheet.addSource(fadeEnableLiveData) { fade ->
            sheet.value = sheet.value?.copy(isFadeEnabled = fade)
        }
        sheet.addSource(periodLabelLiveData) { enabled ->
            sheet.value = sheet.value?.copy(usePeriodLabel = enabled)
        }
        sheet.addSource(cardOpacityLiveData) { opacity ->
            sheet.value = sheet.value?.withCardOpacity(opacity)
        }
        sheet.addSource(courseBubbleStyleLiveData) { bubbleStyle ->
            sheet.value = sheet.value?.copy(
                courseBubbleStyle = CourseBubbleStyle.fromStorage(bubbleStyle)
            )
        }
        return sheet
    }
}
