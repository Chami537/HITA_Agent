package cn.limpu.hita.data.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class CourseSelectionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getStringExtra(CourseSelectionForegroundService.EXTRA_JOB_ID)
            ?.takeIf(String::isNotBlank)
            ?: return
        ContextCompat.startForegroundService(
            context.applicationContext,
            CourseSelectionForegroundService.executeIntent(context, jobId)
        )
    }
}
