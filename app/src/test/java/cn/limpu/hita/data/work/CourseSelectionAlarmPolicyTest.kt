package cn.limpu.hita.data.work

import cn.limpu.hita.data.model.eas.CourseSelectionJob
import cn.limpu.hita.data.model.eas.CourseSelectionJobCourse
import cn.limpu.hita.data.model.eas.CourseSelectionJobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CourseSelectionAlarmPolicyTest {
    @Test
    fun `future waiting job is rescheduled after boot`() {
        assertEquals(
            AlarmRecoveryAction.RESCHEDULE,
            CourseSelectionAlarmPolicy.recoveryAction(waitingJob(at = 10_000L), nowMillis = 5_000L)
        )
    }

    @Test
    fun `expired waiting job is failed and never replayed`() {
        assertEquals(
            AlarmRecoveryAction.EXPIRE,
            CourseSelectionAlarmPolicy.recoveryAction(waitingJob(at = 4_999L), nowMillis = 5_000L)
        )
    }

    @Test
    fun `non-waiting job is ignored during boot recovery`() {
        assertEquals(
            AlarmRecoveryAction.IGNORE,
            CourseSelectionAlarmPolicy.recoveryAction(
                waitingJob(at = 10_000L).copy(status = CourseSelectionJobStatus.RUNNING),
                nowMillis = 5_000L
            )
        )
    }

    @Test
    fun `request code is stable for the same job id`() {
        assertEquals(
            CourseSelectionAlarmPolicy.requestCode("job-1"),
            CourseSelectionAlarmPolicy.requestCode("job-1")
        )
    }

    @Test
    fun `hash colliding job ids have distinct stable alarm identities`() {
        val firstId = "Aa"
        val secondId = "BB"
        assertEquals(firstId.hashCode(), secondId.hashCode())

        val firstIdentity = CourseSelectionAlarmPolicy.alarmIdentity(firstId)
        assertEquals(firstIdentity, CourseSelectionAlarmPolicy.alarmIdentity(firstId))
        assertNotEquals(firstIdentity, CourseSelectionAlarmPolicy.alarmIdentity(secondId))
    }

    private fun waitingJob(at: Long) = CourseSelectionJob(
        id = "job-1",
        termId = "2026-2027-1",
        termYearCode = "2026-2027",
        termCode = "1",
        scheduledAtMillis = at,
        createdAtMillis = 1_000L,
        status = CourseSelectionJobStatus.WAITING,
        courses = listOf(
            CourseSelectionJobCourse(
                requestId = "request-1",
                taskId = "task-1",
                courseId = "course-1",
                courseCode = "COMP1001",
                courseName = "Course One",
                teacher = "Teacher",
                poolCode = "xx-b-b"
            )
        )
    )
}
