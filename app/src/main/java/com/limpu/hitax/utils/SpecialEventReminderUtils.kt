package com.limpu.hitax.utils

import com.limpu.hitax.data.model.timetable.EventItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object SpecialEventReminderUtils {
    fun isExamEvent(event: EventItem): Boolean {
        if (event.type == EventItem.TYPE.EXAM) return true
        return event.name.trimStart().startsWith("[考试]")
    }

    fun findUpcomingExamEvent(events: List<EventItem>, now: Long = System.currentTimeMillis()): EventItem? {
        return events
            .asSequence()
            .filter { it.from.time > now && isExamEvent(it) }
            .minByOrNull { it.from.time }
    }

    fun findTimelineHighlightIndex(events: List<EventItem>, now: Long = System.currentTimeMillis()): Int {
        val currentIndex = events.indexOfFirst { now in it.from.time until it.to.time }
        if (currentIndex >= 0) return currentIndex

        val upcomingExamIndex = events
            .withIndex()
            .filter { (_, event) -> event.from.time > now && isExamEvent(event) }
            .minByOrNull { (_, event) -> event.from.time }
            ?.index
        if (upcomingExamIndex != null) return upcomingExamIndex

        return events.indexOfFirst { it.from.time > now }
    }

    fun isToday(timestamp: Long, now: Long = System.currentTimeMillis()): Boolean {
        val eventDay = Calendar.getInstance().apply { timeInMillis = timestamp }
        val today = Calendar.getInstance().apply { timeInMillis = now }
        return eventDay.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                eventDay.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    }

    fun formatExamDateTime(event: EventItem): String {
        val timeText = TimeTools.printTime(event.from.time) + "-" + TimeTools.printTime(event.to.time)
        if (isToday(event.from.time)) return timeText
        val dateText = SimpleDateFormat("MM/dd E", Locale.getDefault()).format(Date(event.from.time))
        return "$dateText $timeText"
    }

    fun formatExamName(event: EventItem): String {
        val name = event.name.trim()
        if (name.startsWith("[考试]")) return name
        return "[考试] $name"
    }

    fun formatExamReminderLine(event: EventItem): String {
        return "${formatExamCountdown(event)}  ${formatExamName(event)}"
    }

    fun formatExamCountdown(event: EventItem, now: Long = System.currentTimeMillis()): String {
        val eventDay = Calendar.getInstance().apply {
            timeInMillis = event.from.time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val days = ((eventDay.timeInMillis - today.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
        return when (days) {
            0 -> "今天考试"
            1 -> "明天考试"
            else -> "还有${days}天"
        }
    }
}
