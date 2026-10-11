package cn.limpu.hita.data.repository

import android.app.Application
import cn.limpu.hita.data.AppDatabase
import cn.limpu.hita.data.model.timetable.EventItem
import cn.limpu.hita.feature.timetableshare.protocol.ShareMode
import cn.limpu.hita.feature.timetableshare.protocol.SharedTimetable
import cn.limpu.hita.feature.timetableshare.protocol.TimetableShareCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.CalScale
import net.fortuna.ical4j.model.Dur
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.Location
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.util.UidGenerator
import net.fortuna.ical4j.model.property.Version
import net.fortuna.ical4j.model.property.XProperty
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Callable
import javax.inject.Inject

/** 一条待写出的日历事件；与 ical4j 解耦，好友快照与本地课表共用。 */
data class IcsExportEvent(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val place: String?,
    val teacher: String?,
    /** 正数表示「第 N 周」；否则描述里写「未知周次」。 */
    val weekNumber: Int,
    /** class/exam/other，小写，写入 X-HITA-TYPE 供回导。 */
    val typeName: String,
) {
    val courseLike: Boolean get() = typeName == "class" || typeName == "exam"
}

/** ical4j 日历文件写出。属性与既有单课表导出保持一致，保证回导兼容。 */
object TimetableIcsWriter {
    fun write(directory: File, fileName: String, events: List<IcsExportEvent>): File {
        val calendar = Calendar()
        calendar.properties.add(ProdId("-//StupidTree//HITA//EN"))
        calendar.properties.add(Version.VERSION_2_0)
        calendar.properties.add(CalScale.GREGORIAN)
        for (item in events) {
            val start = DateTime(item.startMillis)
            start.isUtc = true
            val end = DateTime(item.endMillis)
            end.isUtc = true
            val event = VEvent(start, end, item.title)

            val place = item.place?.trim().orEmpty()
            if (place.isNotEmpty()) event.properties.add(Location(place))

            val weekText = if (item.weekNumber > 0) "第${item.weekNumber}周" else "未知周次"
            val description = buildString {
                if (!item.teacher.isNullOrBlank()) appendLine("教师：${item.teacher}")
                appendLine("周次：$weekText")
                appendLine("来源：HITA Android")
            }.trim()
            event.properties.add(Description(description))

            event.properties.add(XProperty("X-HITA-SCHEMA", "1.0"))
            event.properties.add(XProperty("X-HITA-SOURCE", "android"))
            event.properties.add(XProperty("X-HITA-COURSE-NAME", item.title))
            if (!item.teacher.isNullOrBlank()) event.properties.add(XProperty("X-HITA-TEACHER", item.teacher))
            if (place.isNotEmpty()) event.properties.add(XProperty("X-HITA-CLASSROOM", place))
            if (item.weekNumber > 0) event.properties.add(XProperty("X-HITA-WEEKS", item.weekNumber.toString()))
            event.properties.add(XProperty("X-HITA-TYPE", item.typeName))
            event.properties.add(Uid(UidGenerator("hita").generateUid().value))

            val alarm = VAlarm(Dur(0, 0, -10, 0))
            alarm.properties.add(Summary(if (item.courseLike) "课程提醒" else "事件提醒"))
            alarm.properties.add(Action.DISPLAY)
            alarm.properties.add(Description(item.title + if (item.courseLike) "马上就要开始啦！" else "马上开始啦！"))
            event.alarms.add(alarm)
            calendar.components.add(event)
        }
        calendar.validate()

        check(directory.isDirectory || directory.mkdirs()) { "cannot create ICS export directory" }
        // Names are user-controlled and need not be unique. Never overwrite an earlier
        // export (including one still held by another app's granted content URI).
        val stem = fileName.removeSuffix(".ics").replace(Regex("[\\\\/\\p{Cntrl}:*?\"<>|]"), "_")
        val prefix = stem.substring(0, stem.offsetByCodePoints(0, minOf(60, stem.codePointCount(0, stem.length))))
            .padEnd(3, '_')
        val file = File.createTempFile("$prefix-", ".ics", directory)
        try {
            FileOutputStream(file).use { net.fortuna.ical4j.data.CalendarOutputter().output(calendar, it) }
            return file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }
}

/** Keep bulk tokens out of Binder when the complete text exceeds the message budget. */
object TimetableTokenText {
    fun inlineText(tokens: List<Pair<String, String>>): String? {
        val length = tokens.sumOf { (name, token) ->
            token.length.toLong() + if (name.isBlank()) 0L else name.length.toLong() + 3L
        } + (tokens.size - 1).coerceAtLeast(0).toLong() * 2L
        if (length > cn.limpu.hita.feature.timetableshare.protocol.ShareLimits.MESSAGE_CHARS) return null
        return buildString { appendTokens(this, tokens) }
    }

    fun write(directory: File, tokens: List<Pair<String, String>>): File {
        val file = File.createTempFile("hita-timetable-tokens-", ".txt", directory)
        try {
            file.bufferedWriter(Charsets.UTF_8).use { appendTokens(it, tokens) }
            return file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    private fun appendTokens(output: Appendable, tokens: List<Pair<String, String>>) {
        tokens.forEachIndexed { index, (name, token) ->
            if (index > 0) output.append("\n\n")
            if (name.isNotBlank()) output.append('《').append(name).append("》\n")
            output.append(token)
        }
    }
}

/** 好友快照 → 日历事件。完整模式带课程名与地点；忙闲模式导出为「昵称·忙碌」占用块。 */
object FriendIcsMapper {
    private const val WEEK_MILLIS = 7L * 24 * 3600 * 1000

    fun map(snapshot: SharedTimetable): List<IcsExportEvent> {
        return snapshot.occurrences.sortedBy { it.startMillis }.map { occurrence ->
            val weekNumber = weekOf(snapshot, occurrence.startMillis)
            if (snapshot.mode == ShareMode.FULL) {
                val title = occurrence.courseIndex?.let { snapshot.courses.getOrNull(it) }
                    ?: snapshot.term.timetableName.ifBlank { snapshot.nickname }
                IcsExportEvent(title, occurrence.startMillis, occurrence.endMillis,
                    occurrence.place, null, weekNumber, "class")
            } else {
                IcsExportEvent("${snapshot.nickname}·忙碌", occurrence.startMillis, occurrence.endMillis,
                    null, null, weekNumber, "other")
            }
        }
    }

    private fun weekOf(snapshot: SharedTimetable, startMillis: Long): Int {
        val offset = startMillis - snapshot.term.startMillis
        if (offset < 0) return -1
        return (offset / WEEK_MILLIS).toInt() + 1
    }
}

/** 多课表导出：我的课表/组合课表走本地库，好友课表走已存快照。 */
class TimetableExportRepository @Inject constructor(
    private val db: AppDatabase,
    private val application: Application,
) {
    private val codec = TimetableShareCodec()

    /** 我的课表 → 每份一个 ICS 文件，文件名沿用既有格式。 */
    suspend fun mineIcs(timetableIds: List<String>): List<File> = withContext(Dispatchers.IO) {
        timetableIds.map { id ->
            val (name, events) = db.runInTransaction(Callable {
                val timetable = db.timetableDao().getTimetableByIdSync(id)
                    ?: throw IllegalStateException("timetable $id missing")
                val mapped = db.eventItemDao().getEventsOfTimetableSync(id).map { event ->
                    IcsExportEvent(event.name, event.from.time, event.to.time, event.place,
                        event.teacher, timetable.getWeekNumber(event.from.time), event.type.name.lowercase())
                }
                (timetable.name ?: "课表") to mapped
            })
            TimetableIcsWriter.write(icsDir(), "HITSZ助手：$name.ics", events)
        }
    }

    /** 组合课表整体导出：组内成员事件合并为一个 ICS。 */
    suspend fun groupIcs(groupId: String): File = withContext(Dispatchers.IO) {
        val (name, events) = db.runInTransaction(Callable {
            val group = db.timetableGroupDao().getGroupByIdSync(groupId)
                ?: throw IllegalStateException("group $groupId missing")
            val members = db.timetableDao().getTimetablesSync().filter { it.groupId == groupId }
            val byId = members.associateBy { it.id }
            val mapped = members.flatMap { member ->
                db.eventItemDao().getEventsOfTimetableSync(member.id).map { event ->
                    IcsExportEvent(event.name, event.from.time, event.to.time, event.place,
                        event.teacher, byId[event.timetableId]?.getWeekNumber(event.from.time) ?: -1,
                        event.type.name.lowercase())
                }
            }.sortedBy { it.startMillis }
            group.name to mapped
        })
        TimetableIcsWriter.write(icsDir(), "HITSZ助手：$name.ics", events)
    }

    /** 好友课表 → 每份一个 ICS 文件，从快照的绝对时间直接展开。 */
    suspend fun friendIcs(shareIds: List<String>): List<File> = withContext(Dispatchers.IO) {
        shareIds.map { shareId ->
            val entity = db.friendTimetableDao().getSync(shareId)
                ?: throw IllegalStateException("friend $shareId missing")
            val snapshot = codec.decodeJson(entity.snapshotJson)
            if (!entity.matchesSnapshot(snapshot, codec)) {
                throw cn.limpu.hita.feature.timetableshare.protocol.ShareFormatException(
                    cn.limpu.hita.feature.timetableshare.protocol.ShareError.INVALID_FIELDS)
            }
            val displayName = entity.remark?.takeIf { it.isNotBlank() } ?: entity.nickname
            TimetableIcsWriter.write(icsDir(), "HITA好友课表：$displayName.ics", FriendIcsMapper.map(snapshot))
        }
    }

    private fun icsDir(): File = application.getExternalFilesDir("ics") ?: application.filesDir
}
