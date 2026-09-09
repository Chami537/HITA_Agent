package cn.limpu.hita.data.analytics

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class UsageAnalyticsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        UsageAnalyticsClient.initialize(applicationContext)
        if (!UsageAnalyticsClient.isEnabled(applicationContext)) return Result.success()
        return try { if (UsageAnalyticsClient.drain()) Result.success() else Result.retry() } catch (_: Exception) { Result.retry() }
    }
}
