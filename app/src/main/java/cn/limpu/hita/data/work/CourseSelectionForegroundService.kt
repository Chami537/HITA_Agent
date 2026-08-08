package cn.limpu.hita.data.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import cn.limpu.hita.R
import cn.limpu.hita.data.model.eas.CourseSelectionJobStatus
import cn.limpu.hita.data.repository.CourseSelectionJobCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel

internal data class CourseSelectionServiceStart(
    val startId: Int,
    val jobId: String?
)

internal class CourseSelectionServiceStartQueue(
    scope: CoroutineScope,
    private val handle: suspend (CourseSelectionServiceStart) -> Unit,
    private val onFinished: (Int) -> Unit
) {
    private val starts = Channel<CourseSelectionServiceStart>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (start in starts) {
                try {
                    handle(start)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Keep the worker alive so already-queued starts still receive cleanup.
                } finally {
                    onFinished(start.startId)
                }
            }
        }
    }

    fun enqueue(start: CourseSelectionServiceStart) {
        check(starts.trySend(start).isSuccess)
    }
}

@AndroidEntryPoint
class CourseSelectionForegroundService : Service() {
    @Inject
    lateinit var coordinator: CourseSelectionJobCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager
    private lateinit var startQueue: CourseSelectionServiceStartQueue

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        ensureNotificationChannel()
        startQueue = CourseSelectionServiceStartQueue(
            scope = serviceScope,
            handle = ::handleStart,
            onFinished = ::stopSelf
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            notification(R.string.course_selection_notification_waiting, ongoing = true)
        )
        val jobId = intent
            ?.takeIf { it.action == ACTION_EXECUTE_JOB }
            ?.getStringExtra(EXTRA_JOB_ID)
            ?.takeIf(String::isNotBlank)
        startQueue.enqueue(CourseSelectionServiceStart(startId, jobId))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun handleStart(start: CourseSelectionServiceStart) {
        val jobId = start.jobId ?: return
        try {
            updateNotification(R.string.course_selection_notification_running, ongoing = true)
            coordinator.execute(jobId)
        } finally {
            updateNotificationFor(jobId)
        }
    }

    private fun updateNotificationFor(jobId: String) {
        val status = coordinator.jobs.value.firstOrNull { it.id == jobId }?.status
        val message = when (status) {
            CourseSelectionJobStatus.WAITING -> R.string.course_selection_notification_waiting
            CourseSelectionJobStatus.RUNNING -> R.string.course_selection_notification_running
            CourseSelectionJobStatus.COMPLETED -> R.string.course_selection_notification_completed
            else -> R.string.course_selection_notification_failed
        }
        updateNotification(
            message,
            ongoing = status == CourseSelectionJobStatus.WAITING ||
                status == CourseSelectionJobStatus.RUNNING
        )
    }

    private fun updateNotification(message: Int, ongoing: Boolean) {
        notificationManager.notify(NOTIFICATION_ID, notification(message, ongoing))
    }

    private fun notification(message: Int, ongoing: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_access_alarm_24)
            .setContentTitle(getString(R.string.course_selection_notification_channel_name))
            .setContentText(getString(message))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .build()

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.course_selection_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    companion object {
        const val ACTION_EXECUTE_JOB = "cn.limpu.hita.action.EXECUTE_COURSE_SELECTION_JOB"
        internal const val EXTRA_JOB_ID = "jobId"
        private const val CHANNEL_ID = "shenzhen_course_selection"
        private const val NOTIFICATION_ID = 2505

        fun executeIntent(context: Context, jobId: String): Intent =
            Intent(context.applicationContext, CourseSelectionForegroundService::class.java).apply {
                action = ACTION_EXECUTE_JOB
                putExtra(EXTRA_JOB_ID, jobId)
            }
    }
}
