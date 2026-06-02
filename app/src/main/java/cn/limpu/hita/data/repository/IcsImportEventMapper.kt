package cn.limpu.hita.data.repository

import cn.limpu.hita.data.model.timetable.EventItem
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import java.sql.Timestamp
import java.util.UUID

object IcsImportEventMapper {
    private val PERIOD_LINE_REGEX = Regex("""^第\s*\d+(?:\s*[-~到]\s*\d+)?\s*节$""")

    fun map(
        event: VEvent,
        timetableId: String,
        subjectId: String = "",
        type: EventItem.TYPE = EventItem.TYPE.OTHER,
        startTimeOverride: Long? = null,
        endTimeOverride: Long? = null
    ): EventItem? {
        val startTime = startTimeOverride ?: event.startDate?.date?.time ?: return null
        val endTime = endTimeOverride ?: event.endDate?.date?.time ?: return null
        if (endTime <= startTime) return null

        val descriptionLines = extractDescriptionLines(event)
        val resolvedType = resolveType(event) ?: type
        val item = EventItem()
        item.id = UUID.randomUUID().toString()
        item.timetableId = timetableId
        item.name = resolveName(event, descriptionLines)
        item.place = resolvePlace(event, descriptionLines)
        item.teacher = resolveTeacher(event)
        item.from = Timestamp(startTime)
        item.to = Timestamp(endTime)
        item.subjectId = subjectId
        item.fromNumber = 0
        item.lastNumber = 0
        item.type = resolvedType
        item.source = EventItem.SOURCE_ICS_IMPORT
        item.color = -1
        return item
    }

    private fun resolveName(event: VEvent, descriptionLines: List<String>): String {
        getXProperty(event, "X-HITA-COURSE-NAME")?.let { return it }
        val summary = event.summary?.value?.trim().orEmpty()
        if (summary.isNotEmpty()) return summary
        val fromDescription = descriptionLines.firstOrNull { !isPeriodLine(it) }
        if (!fromDescription.isNullOrBlank()) return fromDescription
        return event.uid?.value?.trim().orEmpty().ifBlank { "未知课程" }
    }

    private fun resolvePlace(event: VEvent, descriptionLines: List<String>): String {
        getXProperty(event, "X-HITA-CLASSROOM")?.let { return it }
        val location = event.location?.value?.trim().orEmpty()
        if (location.isNotEmpty()) return location
        return descriptionLines
            .asReversed()
            .firstOrNull { !isPeriodLine(it) }
            .orEmpty()
    }

    private fun resolveTeacher(event: VEvent): String {
        return getXProperty(event, "X-HITA-TEACHER").orEmpty()
    }

    private fun resolveType(event: VEvent): EventItem.TYPE? {
        val typeStr = getXProperty(event, "X-HITA-TYPE") ?: return null
        return when (typeStr.lowercase()) {
            "class" -> EventItem.TYPE.CLASS
            "exam" -> EventItem.TYPE.EXAM
            "other" -> EventItem.TYPE.OTHER
            "tag" -> EventItem.TYPE.TAG
            else -> null
        }
    }

    private fun extractDescriptionLines(event: VEvent): List<String> {
        return event.description?.value
            ?.replace("\\n", "\n")
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    }

    private fun isPeriodLine(line: String): Boolean {
        return PERIOD_LINE_REGEX.matches(line)
    }

    internal fun getXProperty(event: VEvent, name: String): String? {
        val props = event.properties ?: return null
        for (entry in props) {
            val property = entry as? Property ?: continue
            if (property.name.equals(name, ignoreCase = true)) {
                return property.value?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        return null
    }
}
