package cn.limpu.hita.feature.livecourse.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.limpu.hita.feature.livecourse.domain.LiveCourse
import cn.limpu.hita.feature.livecourse.domain.LiveCourseState
import cn.limpu.hita.feature.livecourse.guard.LiveCourseGuardService
import cn.limpu.hita.feature.livecourse.notification.LiveCourseNotificationManager
import cn.limpu.hita.feature.livecourse.settings.LiveCourseSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Debug-only ADB hook for verifying that the device promotes a real ongoing course notification.
 * It deliberately has no production source-set counterpart.
 */
class LiveCourseDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_ENABLE_STRONG_REMINDER_DEBUG) {
            LiveCourseSettings(context).apply {
                setEnabled(true)
                setStrongReminderEnabled(true)
            }
            LiveCourseGuardService.start(context)
            return
        }
        if (intent.action != ACTION_SHOW_LIVE_COURSE_DEBUG &&
            intent.action != ACTION_SHOW_PRE_CLASS_DEBUG
        ) return

        // HApplication schedules a real timetable reconciliation as the process starts. Wait
        // for it to finish so its no-current-course cleanup cannot race this diagnostic post.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                delay(DEBUG_POST_DELAY_MILLIS)
                val now = Instant.now()
                val preClass = intent.action == ACTION_SHOW_PRE_CLASS_DEBUG
                val start = now.plus(DEBUG_PRE_CLASS_DURATION)
                val course = LiveCourse(
                    courseId = "live-course-adb-test",
                    courseName = "数据结构",
                    classroom = "T3-403",
                    startTime = if (preClass) start
                        else now.minus(DEBUG_COURSE_HALF_DURATION),
                    endTime = if (preClass) start.plus(DEBUG_COURSE_HALF_DURATION)
                        else now.plus(DEBUG_COURSE_HALF_DURATION),
                )
                val state = if (preClass) {
                    LiveCourseState.PreClass(course, Duration.between(now, start))
                } else {
                    LiveCourseState.InClass(
                        course = course,
                        elapsed = DEBUG_COURSE_HALF_DURATION,
                        remaining = DEBUG_COURSE_HALF_DURATION,
                        progress = 0.5f,
                    )
                }
                LiveCourseNotificationManager(context).show(state)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SHOW_LIVE_COURSE_DEBUG =
            "cn.limpu.hita.action.SHOW_LIVE_COURSE_DEBUG"
        const val ACTION_SHOW_PRE_CLASS_DEBUG =
            "cn.limpu.hita.action.SHOW_PRE_CLASS_DEBUG"
        const val ACTION_ENABLE_STRONG_REMINDER_DEBUG =
            "cn.limpu.hita.action.ENABLE_STRONG_REMINDER_DEBUG"
        private const val DEBUG_POST_DELAY_MILLIS = 1_500L
        private val DEBUG_COURSE_HALF_DURATION = Duration.ofMinutes(30)
        private val DEBUG_PRE_CLASS_DURATION = Duration.ofSeconds(80)
    }
}
