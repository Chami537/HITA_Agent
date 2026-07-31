package cn.limpu.hita.data.repository

import android.content.Context
import androidx.core.content.edit
import cn.limpu.hita.data.model.eas.EASToken
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogSource
import cn.limpu.hita.data.model.eas.ShenzhenCourseMeeting
import cn.limpu.hita.data.model.eas.TermItem
import cn.limpu.hita.data.model.timetable.TimeInDay
import cn.limpu.hita.data.model.timetable.TimePeriodInDay
import com.google.gson.Gson
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class FollowedTeachingSectionSnapshot(
    val id: String,
    val ownerKey: String,
    val termId: String,
    val yearCode: String,
    val termCode: String,
    val termName: String,
    val taskId: String,
    val courseCode: String,
    val courseName: String,
    val teacher: String,
    val credits: String,
    val offeringCollege: String,
    val campus: String,
    val classNumber: String,
    val rawSchedule: String,
    val meetings: List<ShenzhenCourseMeeting>,
    val termStartMillis: Long,
    val schedule: List<FollowedTimePeriod>,
    val followedAtMillis: Long,
    val refreshedAtMillis: Long? = null
) {
    fun asCatalogItem(): ShenzhenCourseCatalogItem = ShenzhenCourseCatalogItem(
        id = id,
        taskId = taskId,
        courseCode = courseCode,
        courseName = courseName,
        teacher = teacher,
        credits = credits,
        offeringCollege = offeringCollege,
        campus = campus,
        schedule = rawSchedule,
        classNumber = classNumber,
        meetings = meetings,
        source = ShenzhenCourseCatalogSource.SCHOOL
    )

    fun term(): TermItem = TermItem(yearCode, yearCode, termCode, termName)

    fun scheduleStructure(): List<TimePeriodInDay> = schedule.map {
        TimePeriodInDay(TimeInDay(it.fromHour, it.fromMinute), TimeInDay(it.toHour, it.toMinute))
    }

    companion object {
        fun create(
            ownerKey: String,
            term: TermItem,
            course: ShenzhenCourseCatalogItem,
            termStartMillis: Long,
            schedule: List<TimePeriodInDay>,
            followedAtMillis: Long = System.currentTimeMillis()
        ): FollowedTeachingSectionSnapshot {
            val stableId = course.taskId.ifBlank { course.id }
            return FollowedTeachingSectionSnapshot(
                id = stableId,
                ownerKey = ownerKey,
                termId = term.id,
                yearCode = term.yearCode,
                termCode = term.termCode,
                termName = term.termName,
                taskId = course.taskId,
                courseCode = course.courseCode,
                courseName = course.courseName,
                teacher = course.teacher,
                credits = course.credits,
                offeringCollege = course.offeringCollege,
                campus = course.campus,
                classNumber = course.classNumber,
                rawSchedule = course.schedule,
                meetings = course.meetings,
                termStartMillis = termStartMillis,
                schedule = schedule.map {
                    FollowedTimePeriod(
                        fromHour = it.from.hour,
                        fromMinute = it.from.minute,
                        toHour = it.to.hour,
                        toMinute = it.to.minute
                    )
                },
                followedAtMillis = followedAtMillis
            )
        }
    }
}

data class FollowedTimePeriod(
    val fromHour: Int,
    val fromMinute: Int,
    val toHour: Int,
    val toMinute: Int
)

internal class FollowedTeachingSectionStore(context: Context) {
    private data class Payload(val version: Int = 1, val snapshots: List<FollowedTeachingSectionSnapshot> = emptyList())

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val lock = Any()

    fun snapshots(ownerKey: String, termId: String? = null): List<FollowedTeachingSectionSnapshot> =
        synchronized(lock) {
            payload().snapshots.filter { snapshot ->
                snapshot.ownerKey == ownerKey && (termId == null || snapshot.termId == termId)
            }
        }

    fun save(snapshot: FollowedTeachingSectionSnapshot) = synchronized(lock) {
        val current = payload().snapshots.toMutableList()
        current.removeAll {
            it.ownerKey == snapshot.ownerKey && it.termId == snapshot.termId && it.id == snapshot.id
        }
        current += snapshot
        persist(Payload(snapshots = current))
    }

    fun remove(ownerKey: String, termId: String, id: String) = synchronized(lock) {
        val current = payload().snapshots.filterNot {
            it.ownerKey == ownerKey && it.termId == termId && it.id == id
        }
        persist(Payload(snapshots = current))
    }

    private fun payload(): Payload = preferences.getString(KEY_PAYLOAD, null)
        ?.let { json -> runCatching { gson.fromJson(json, Payload::class.java) }.getOrNull() }
        ?.takeIf { it.version == 1 }
        ?: Payload()

    private fun persist(payload: Payload) {
        preferences.edit { putString(KEY_PAYLOAD, gson.toJson(payload)) }
    }

    companion object {
        private const val PREFERENCES = "followed_teaching_sections"
        private const val KEY_PAYLOAD = "payload_v1"

        fun ownerKey(token: EASToken): String {
            val identity = listOf(
                token.campus.name,
                token.stuId.orEmpty(),
                token.username.orEmpty(),
                token.id.orEmpty()
            ).joinToString("::")
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest(identity.toByteArray(StandardCharsets.UTF_8))
            return bytes.take(12).joinToString("") { "%02x".format(it) }
        }
    }
}
