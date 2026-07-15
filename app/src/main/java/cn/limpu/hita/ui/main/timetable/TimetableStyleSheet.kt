package cn.limpu.hita.ui.main.timetable

import android.view.Gravity
import androidx.compose.runtime.Immutable
import cn.limpu.hita.data.model.timetable.TimeInDay

@Immutable
data class TimetableStyleSheet(
    val isColorEnabled: Boolean = true,
    val isFadeEnabled: Boolean = true,
    val cardTitleColor: String = "white",
    val subTitleColor: String = "white",
    val iconColor: String = "white",
    val isBoldText: Boolean = true,
    val drawBGLine: Boolean = true,
    val cardIconEnabled: Boolean = false,
    private val storedCardOpacity: Int = 95,
    val cardHeight: Int = 160,
    val usePeriodLabel: Boolean = false,
    val startTime: Int = 830,
    val endHour: Int = 23,
    val todayBGColor: Int = 0x10000000,
    val titleGravity: Int = Gravity.CENTER,
    val titleAlpha: Int = 100,
    val subtitleAlpha: Int = 60,
    val drawNowLine: Boolean = true,
) {
    val cardOpacity: Int get() = storedCardOpacity.coerceIn(20, 100)
    val startHour: Int get() = startTime / 100
    val startMinute: Int get() = startTime % 100

    fun withCardOpacity(value: Int): TimetableStyleSheet =
        copy(storedCardOpacity = value.coerceIn(20, 100))

    fun getStartTimeObject(): TimeInDay = TimeInDay(startHour, startMinute)
}
