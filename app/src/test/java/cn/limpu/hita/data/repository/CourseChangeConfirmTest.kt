package cn.limpu.hita.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CourseChangeConfirmTest {

    @Test
    fun fingerprintDiffersWhenNewValueChanges() {
        val samePlace = CourseChangeConfirm.fingerprint(
            "code:MATH", PendingChangeKind.PLACE, "1|1|2", "A楼", "B楼"
        )
        val laterPlace = CourseChangeConfirm.fingerprint(
            "code:MATH", PendingChangeKind.PLACE, "1|1|2", "A楼", "C楼"
        )
        val teacher = CourseChangeConfirm.fingerprint(
            "code:MATH", PendingChangeKind.TEACHER, "1|1|2", "张三", "李四"
        )
        assertNotEquals(samePlace, laterPlace)
        assertNotEquals(samePlace, teacher)
        assertEquals(
            samePlace,
            CourseChangeConfirm.fingerprint("code:MATH", PendingChangeKind.PLACE, "1|1|2", "A楼", "B楼"),
        )
    }

    @Test
    fun applyRowsKeepsIgnoredPlaceAndAdoptsTime() {
        val local = MergeCourse(
            subjectId = "s1",
            name = "高等数学",
            code = "MATH",
            lessons = listOf(lesson(8 * 60, "正心楼 101")),
        )
        val incoming = MergeCourse(
            subjectId = "s1",
            name = "高等数学",
            code = "MATH",
            lessons = listOf(lesson(10 * 60, "诚意楼 202")),
        )
        val plan = TimetableRefreshMergePolicy.plan(listOf(local), listOf(incoming), emptyMap(), 1_000L)
        val update = plan.pendingUpdates.single()
        val timeRow = update.rows.single { it.kind == PendingChangeKind.TIME }
        val patched = CourseChangeConfirm.applyRows(
            local = local,
            incoming = incoming,
            adopted = setOf(timeRow.fingerprint),
            rows = update.rows,
        )!!
        assertEquals("正心楼 101", patched.lessons.single().place)
        val calendar = Calendar.getInstance().apply { timeInMillis = patched.lessons.single().fromMillis }
        assertEquals(10, calendar.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun applyRowsSkipsUnselectedAddedCourse() {
        val incoming = MergeCourse("s2", "大学物理", null, listOf(lesson(8 * 60, "A")))
        val update = CourseChangeConfirm.addedCourse(incoming)
        assertNull(
            CourseChangeConfirm.applyRows(
                local = null,
                incoming = incoming,
                adopted = emptySet(),
                rows = update.rows,
            )
        )
        val added = CourseChangeConfirm.applyRows(
            local = null,
            incoming = incoming,
            adopted = setOf(update.rows.single().fingerprint),
            rows = update.rows,
        )
        assertEquals("大学物理", added?.name)
        assertTrue(added!!.lessons.isNotEmpty())
    }

    private fun lesson(clockMinutes: Int, place: String): MergeLesson {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(2026, Calendar.SEPTEMBER, 7, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        val from = calendar.timeInMillis + clockMinutes * 60_000L
        return MergeLesson(
            name = "课",
            place = place,
            teacher = "张三",
            fromMillis = from,
            toMillis = from + 45 * 60_000L,
            fromNumber = 1,
            lastNumber = 2,
            weekOfTerm = 1,
        )
    }
}
