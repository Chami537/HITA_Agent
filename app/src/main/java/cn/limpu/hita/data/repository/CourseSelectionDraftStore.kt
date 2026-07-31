package cn.limpu.hita.data.repository

import android.content.Context
import androidx.core.content.edit
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCourseMeeting
import com.google.gson.Gson

data class CourseSelectionDraft(
    val ownerKey: String,
    val termId: String,
    val courses: List<ShenzhenCourseCatalogItem> = emptyList(),
    val projectionEnabled: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val courseIds: Set<String>
        get() = courses.mapTo(linkedSetOf()) { it.taskId.ifBlank { it.id } }
}

internal class CourseSelectionDraftStore(context: Context) {
    private data class Payload(
        val version: Int = 1,
        val drafts: List<CourseSelectionDraft> = emptyList()
    )

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val lock = Any()

    fun draft(ownerKey: String, termId: String): CourseSelectionDraft = synchronized(lock) {
        payload().drafts.firstOrNull { it.ownerKey == ownerKey && it.termId == termId }
            ?: CourseSelectionDraft(ownerKey, termId)
    }

    fun setCourse(
        ownerKey: String,
        termId: String,
        course: ShenzhenCourseCatalogItem,
        included: Boolean
    ): CourseSelectionDraft = synchronized(lock) {
        val payload = payload()
        val previous = payload.drafts.firstOrNull { it.ownerKey == ownerKey && it.termId == termId }
            ?: CourseSelectionDraft(ownerKey, termId)
        val id = course.taskId.ifBlank { course.id }
        val courses = previous.courses.filterNot { it.taskId.ifBlank { it.id } == id }.toMutableList()
        if (included) courses += course
        val updated = previous.copy(courses = courses, updatedAtMillis = System.currentTimeMillis())
        persist(payload.replace(updated))
        updated
    }

    fun setProjectionEnabled(ownerKey: String, termId: String, enabled: Boolean): CourseSelectionDraft =
        synchronized(lock) {
            val payload = payload()
            val updated = draft(ownerKey, termId).copy(
                projectionEnabled = enabled,
                updatedAtMillis = System.currentTimeMillis()
            )
            persist(payload.replace(updated))
            updated
        }

    fun clear(ownerKey: String, termId: String): CourseSelectionDraft = synchronized(lock) {
        val payload = payload()
        val previous = draft(ownerKey, termId)
        val updated = previous.copy(
            courses = emptyList(),
            updatedAtMillis = System.currentTimeMillis()
        )
        persist(payload.replace(updated))
        updated
    }

    private fun Payload.replace(draft: CourseSelectionDraft): Payload = copy(
        drafts = drafts.filterNot { it.ownerKey == draft.ownerKey && it.termId == draft.termId } + draft
    )

    private fun payload(): Payload = preferences.getString(KEY_PAYLOAD, null)
        ?.let { runCatching { gson.fromJson(it, Payload::class.java) }.getOrNull() }
        ?.takeIf { it.version == 1 }
        ?: Payload()

    private fun persist(payload: Payload) {
        preferences.edit { putString(KEY_PAYLOAD, gson.toJson(payload)) }
    }

    companion object {
        private const val PREFERENCES = "course_selection_drafts"
        private const val KEY_PAYLOAD = "payload_v1"
    }
}

internal object CoursePlanProjectionPolicy {
    fun projectableCourses(
        courses: List<ShenzhenCourseCatalogItem>,
        schedulePeriodCount: Int
    ): List<ShenzhenCourseCatalogItem> = courses
        .distinctBy { it.taskId.ifBlank { it.id } }
        .filter { course ->
            course.meetings.any { meeting ->
                meeting.isStructurallyComplete() &&
                    meeting.beginPeriod in 1..schedulePeriodCount &&
                    meeting.endPeriod in 1..schedulePeriodCount
            }
        }
}

object CoursePlanConflictEngine {
    fun conflictDescription(
        course: ShenzhenCourseCatalogItem,
        effectiveCourses: List<ShenzhenCourseCatalogItem>
    ): String? {
        if (course.hasConflict) {
            return course.conflictDescription.ifBlank { "与已选课程时间冲突" }
        }
        val courseId = course.taskId.ifBlank { course.id }
        val conflict = effectiveCourses.asSequence()
            .filter { it.taskId.ifBlank { it.id } != courseId }
            .mapNotNull { other -> firstIntersection(course.meetings, other.meetings)?.let { other to it } }
            .firstOrNull() ?: return null
        val (other, intersection) = conflict
        val weeks = intersection.weeks.joinToString("、")
        return "与 ${other.courseName} 冲突：第${weeks}周，周${weekdayName(intersection.weekday)}，" +
            "第${intersection.beginPeriod}-${intersection.endPeriod}节"
    }

    private data class Intersection(
        val weeks: List<Int>,
        val weekday: Int,
        val beginPeriod: Int,
        val endPeriod: Int
    )

    private fun firstIntersection(
        left: List<ShenzhenCourseMeeting>,
        right: List<ShenzhenCourseMeeting>
    ): Intersection? {
        left.forEach { a ->
            right.forEach { b ->
                if (a.weekday != b.weekday || a.beginPeriod > b.endPeriod || b.beginPeriod > a.endPeriod) {
                    return@forEach
                }
                val weeks = a.weeks.toSet().intersect(b.weeks.toSet()).sorted()
                if (weeks.isNotEmpty()) {
                    return Intersection(
                        weeks,
                        a.weekday,
                        maxOf(a.beginPeriod, b.beginPeriod),
                        minOf(a.endPeriod, b.endPeriod)
                    )
                }
            }
        }
        return null
    }

    private fun weekdayName(weekday: Int): String = "一二三四五六日".getOrNull(weekday - 1)?.toString()
        ?: weekday.toString()
}
