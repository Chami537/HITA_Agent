package cn.limpu.hita.feature.timetableshare.protocol

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** HITA2 wire format. Both academic and ICS imports arrive here as exact occurrences. */
internal object CompactShareJson {
    private const val MAX_WEEK = 53
    private const val MINUTE = 60_000L
    private const val DAY = 86_400_000L
    private data class Slot(val first: Int, val last: Int)
    private data class Group(val course: Int, val place: Int, val dow: Int, val first: Int, val last: Int)

    fun encode(snapshot: SharedTimetable): String {
        val full = snapshot.mode == ShareMode.FULL
        val termStart = localDate(snapshot.term.startMillis)
        val weekZero = termStart.minusDays((termStart.dayOfWeek.value - 1).toLong())
        val places = if (full) snapshot.occurrences.map { it.place.orEmpty() }.distinct().sorted() else emptyList()
        val placeIndexes = places.withIndex().associate { it.value to it.index }
        val groups = linkedMapOf<Group, Long>()
        val minuteEvents = JsonArray()
        val exactEvents = JsonArray()

        snapshot.occurrences.forEach { event ->
            val day = localDate(event.startMillis)
            val dayStart = midnight(day)
            val course = event.courseIndex ?: -1
            val place = if (full) placeIndexes.getValue(event.place.orEmpty()) else -1
            val week = (ChronoUnit.DAYS.between(weekZero, day) / 7).toInt()
            val slot = matchingSlot(event, dayStart, snapshot.periods)
            val group = if (slot != null && week in 0..MAX_WEEK)
                Group(course, place, day.dayOfWeek.value, slot.first, slot.last) else null
            val bit = if (group != null) 1L shl week else 0L
            if (group != null && (groups[group] ?: 0L) and bit == 0L) {
                groups[group] = (groups[group] ?: 0L) or bit
            } else {
                val startMinute = event.startMillis - dayStart
                val endMinute = event.endMillis - dayStart
                val dayOffset = ChronoUnit.DAYS.between(termStart, day)
                if (startMinute % MINUTE == 0L && endMinute % MINUTE == 0L &&
                    startMinute in 0L until DAY && endMinute in 1L..DAY && dayOffset in 0L..370L) {
                    minuteEvents.add(numbers(if (full) listOf(course.toLong(), place.toLong(), dayOffset,
                        startMinute / MINUTE, endMinute / MINUTE) else listOf(dayOffset, startMinute / MINUTE, endMinute / MINUTE)))
                } else {
                    exactEvents.add(numbers(if (full) listOf(course.toLong(), place.toLong(), event.startMillis,
                        event.endMillis) else listOf(event.startMillis, event.endMillis)))
                }
            }
        }

        val root = JsonObject().apply {
            addProperty("v", 2)
            addProperty("id", snapshot.shareId)
            addProperty("n", snapshot.nickname)
            addProperty("m", if (full) "full" else "busy")
            add("t", JsonArray().apply {
                add(snapshot.term.timetableName); add(snapshot.term.termName)
                add(snapshot.term.startMillis); add(snapshot.term.endMillis); add(snapshot.term.zoneId)
            })
            add("p", JsonArray().apply { snapshot.periods.forEach { add(numbers(listOf(it.startMinute.toLong(), it.endMinute.toLong()))) } })
            if (full) {
                add("c", JsonArray().apply { snapshot.courses.forEach { add(it) } })
                add("l", JsonArray().apply { places.forEach { add(it) } })
            }
            add("g", JsonArray().apply {
                groups.toList().sortedWith(compareBy({ it.first.course }, { it.first.place }, { it.first.dow },
                    { it.first.first }, { it.first.last })).forEach { (key, mask) ->
                    add(numbers(if (full) listOf(key.course.toLong(), key.place.toLong(), key.dow.toLong(),
                        key.first.toLong(), key.last.toLong(), mask) else listOf(key.dow.toLong(),
                        key.first.toLong(), key.last.toLong(), mask)))
                }
            })
            add("x", minuteEvents)
            add("z", exactEvents)
        }
        return GsonBuilder().disableHtmlEscaping().create().toJson(root)
    }

    fun decode(json: String): SharedTimetable {
        val obj = StrictShareJson.parseObject(json)
        val s = StrictShareJson
        val full = when (s.string(obj.get("m"))) { "full" -> true; "busy" -> false; else -> invalidFields() }
        val required = setOf("v", "id", "n", "m", "t", "p", "g", "x", "z") +
            if (full) setOf("c", "l") else emptySet()
        if (obj.keySet() != required || s.number(obj.get("v")) != 2L) invalidFields()
        val t = s.array(obj.get("t"), 5)
        val term = SharedTerm(s.string(t[0]), s.string(t[1]), s.number(t[2]), s.number(t[3]), s.string(t[4]))
        if (term.startMillis !in ShareLimits.MIN_TIME until ShareLimits.MAX_TIME || term.zoneId != "Asia/Shanghai") invalidFields()
        val termStart = localDate(term.startMillis)
        val weekZero = termStart.minusDays((termStart.dayOfWeek.value - 1).toLong())
        val periods = s.list(obj.get("p"), ShareLimits.PERIODS).map {
            val row = s.array(it, 2)
            SharedPeriod(s.integer(row[0]), s.integer(row[1]))
        }
        val courses = if (full) s.list(obj.get("c"), ShareLimits.COURSES).map(s::string) else emptyList()
        val places = if (full) s.list(obj.get("l"), ShareLimits.OCCURRENCES).map(s::string) else emptyList()
        val occurrences = ArrayList<SharedOccurrence>()
        fun add(course: Int, place: Int, start: Long, end: Long) {
            if (occurrences.size >= ShareLimits.OCCURRENCES) invalidFields()
            if (full && (course !in courses.indices || place !in places.indices)) invalidFields()
            occurrences.add(SharedOccurrence(start, end, if (full) course else null, if (full) places[place] else null))
        }
        fun fields(row: List<JsonElement>): Pair<Int, Int> = if (full) {
            s.integer(row[0]) to s.integer(row[1])
        } else -1 to -1
        val prefix = if (full) 2 else 0

        s.list(obj.get("g"), ShareLimits.OCCURRENCES).forEach {
            val row = s.array(it, prefix + 4)
            val (course, place) = fields(row)
            val dow = s.integer(row[prefix])
            val first = s.integer(row[prefix + 1])
            val last = s.integer(row[prefix + 2])
            val mask = s.number(row[prefix + 3])
            if (dow !in 1..7 || first !in periods.indices || last !in first until periods.size ||
                mask <= 0 || mask ushr (MAX_WEEK + 1) != 0L) invalidFields()
            for (week in 0..MAX_WEEK) if (mask and (1L shl week) != 0L) {
                val day = weekZero.plusDays(week * 7L + dow - 1L)
                val midnight = midnight(day)
                add(course, place, midnight + periods[first].startMinute * MINUTE,
                    midnight + periods[last].endMinute * MINUTE)
            }
        }
        s.list(obj.get("x"), ShareLimits.OCCURRENCES).forEach {
            val row = s.array(it, prefix + 3)
            val (course, place) = fields(row)
            val offset = s.integer(row[prefix])
            val startMinute = s.integer(row[prefix + 1])
            val endMinute = s.integer(row[prefix + 2])
            if (offset !in 0..370 || startMinute !in 0..1439 || endMinute !in startMinute + 1..1440) invalidFields()
            val midnight = midnight(termStart.plusDays(offset.toLong()))
            add(course, place, midnight + startMinute * MINUTE, midnight + endMinute * MINUTE)
        }
        s.list(obj.get("z"), ShareLimits.OCCURRENCES).forEach {
            val row = s.array(it, prefix + 2)
            val (course, place) = fields(row)
            add(course, place, s.number(row[prefix]), s.number(row[prefix + 1]))
        }
        return s.validate(SharedTimetable(s.string(obj.get("id")), s.string(obj.get("n")),
            if (full) ShareMode.FULL else ShareMode.BUSY, term, periods, courses, occurrences))
    }

    private fun matchingSlot(event: SharedOccurrence, dayStart: Long, periods: List<SharedPeriod>): Slot? {
        periods.forEachIndexed { first, start ->
            if (event.startMillis == dayStart + start.startMinute * MINUTE) {
                for (last in first until periods.size) {
                    if (event.endMillis == dayStart + periods[last].endMinute * MINUTE) return Slot(first, last)
                }
            }
        }
        return null
    }
    private fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ShareLimits.ZONE).toLocalDate()
    private fun midnight(date: LocalDate): Long = date.atStartOfDay(ShareLimits.ZONE).toInstant().toEpochMilli()
    private fun numbers(values: List<Long>) = JsonArray().apply { values.forEach { add(it) } }
}
