package cn.limpu.hita.ui.main.timetable

import android.view.Gravity
import androidx.compose.runtime.Immutable
import cn.limpu.hita.data.model.timetable.TimeInDay

enum class CourseBubbleStyle(val storageValue: String) {
    SOLID("solid"),
    TONAL("tonal"),
    OUTLINE("outline");

    companion object {
        fun fromStorage(value: String?): CourseBubbleStyle =
            entries.firstOrNull { it.storageValue == value } ?: SOLID
    }
}

@Immutable
data class TimetableStyleSheet(
    val isColorEnabled: Boolean = true,
    val isFadeEnabled: Boolean = true,
    val cardTitleColor: String = "auto",
    val subTitleColor: String = "auto",
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
    val courseBubbleStyle: CourseBubbleStyle = CourseBubbleStyle.SOLID,
) {
    val cardOpacity: Int get() = storedCardOpacity.coerceIn(20, 100)
    val startHour: Int get() = startTime / 100
    val startMinute: Int get() = startTime % 100

    fun withCardOpacity(value: Int): TimetableStyleSheet =
        copy(storedCardOpacity = value.coerceIn(20, 100))

    fun getStartTimeObject(): TimeInDay = TimeInDay(startHour, startMinute)
}
