package cn.limpu.hita.feature.livecourse.scheduler

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import cn.limpu.hita.utils.LogUtils
import java.util.concurrent.TimeUnit

/** Persistent, inexact recovery path when an OEM delays alarms or kills the guard process. */
internal object LiveCourseWork {
    private const val PERIODIC_NAME = "live_course_reconcile_periodic"
    private const val TRANSITION_NAME = "live_course_next_transition"

    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<LiveCourseWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun scheduleTransition(context: Context, triggerAtMillis: Long) {
        val delay = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<LiveCourseWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            TRANSITION_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelTransition(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(TRANSITION_NAME)
    }

    fun cancel(context: Context) {
        cancelTransition(context)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
    }
}

class LiveCourseWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = try {
        LiveCourseScheduler(applicationContext).reconcile()
        Result.success()
    } catch (error: Exception) {
        LogUtils.e("Live Course work reconciliation failed", error)
        Result.retry()
    }
}
