package cn.limpu.hita.data.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import cn.limpu.hita.R
import cn.limpu.hita.data.model.eas.CourseSelectionJob
import cn.limpu.hita.data.model.eas.CourseSelectionJobStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseSelectionAlarmScheduler @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean = alarmManager != null &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms())

    fun schedule(job: CourseSelectionJob) {
        require(job.status == CourseSelectionJobStatus.WAITING)
        check(canScheduleExactAlarms()) {
            appContext.getString(R.string.course_selection_exact_alarm_permission_explanation)
        }
        try {
            alarmManager!!.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                job.scheduledAtMillis,
                checkNotNull(pendingIntent(job.id, PendingIntent.FLAG_UPDATE_CURRENT))
            )
        } catch (error: SecurityException) {
            throw IllegalStateException(
                appContext.getString(R.string.course_selection_exact_alarm_permission_explanation),
                error
            )
        }
    }

    fun cancel(jobId: String) {
        pendingIntent(jobId, PendingIntent.FLAG_NO_CREATE)?.let { pendingIntent ->
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun exactAlarmSettingsIntent(): Intent {
        val packageUri = Uri.parse("package:${appContext.packageName}")
        val exactAlarmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        }
        val resolvedIntent = exactAlarmIntent.takeIf {
            it.resolveActivity(appContext.packageManager) != null
        } ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        return resolvedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun pendingIntent(jobId: String, lookupFlag: Int): PendingIntent? {
        val intent = Intent(appContext, CourseSelectionAlarmReceiver::class.java).apply {
            data = Uri.parse(CourseSelectionAlarmPolicy.alarmIdentity(jobId))
            putExtra(CourseSelectionForegroundService.EXTRA_JOB_ID, jobId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            CourseSelectionAlarmPolicy.requestCode(jobId),
            intent,
            lookupFlag or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
