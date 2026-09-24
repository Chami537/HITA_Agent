package cn.limpu.hita.data.work

import cn.limpu.hita.data.model.eas.CourseSelectionJob
import cn.limpu.hita.data.model.eas.CourseSelectionJobStatus
import java.net.URLEncoder

enum class AlarmRecoveryAction {
    RESCHEDULE,
    EXPIRE,
    IGNORE
}

object CourseSelectionAlarmPolicy {
    fun recoveryAction(job: CourseSelectionJob, nowMillis: Long): AlarmRecoveryAction = when {
        job.status != CourseSelectionJobStatus.WAITING -> AlarmRecoveryAction.IGNORE
        job.scheduledAtMillis > nowMillis -> AlarmRecoveryAction.RESCHEDULE
        else -> AlarmRecoveryAction.EXPIRE
    }

    fun requestCode(jobId: String): Int = jobId.hashCode()

    fun alarmIdentity(jobId: String): String =
        "hita://course-selection/alarm/${URLEncoder.encode(jobId, Charsets.UTF_8.name())}"
}
