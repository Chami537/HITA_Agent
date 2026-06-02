package cn.limpu.hita.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 课程提醒调度器
 */
object CourseReminderScheduler {
    private const val UNIQUE_PERIODIC_WORK_NAME = "course_reminder_periodic"
    private const val UNIQUE_NEXT_WORK_NAME = "course_reminder_next"

    /**
     * 启动课程提醒（每15分钟检查一次）
     */
    fun schedule(context: Context) {
        // 15分钟是 WorkManager 的最小间隔
        val request = PeriodicWorkRequestBuilder<CourseReminderWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        scheduleNextCheck(context, 0L)
    }

    fun scheduleNextCheck(context: Context, delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<CourseReminderWorker>()
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NEXT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * 取消课程提醒
     */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NEXT_WORK_NAME)
    }

    /**
     * 根据开关状态自动调度或取消
     */
    fun autoSchedule(context: Context) {
        val store = cn.limpu.hita.data.source.preference.CourseReminderStore(context.applicationContext)
        if (store.isEnabled()) {
            schedule(context)
        } else {
            cancel(context)
        }
    }
}
