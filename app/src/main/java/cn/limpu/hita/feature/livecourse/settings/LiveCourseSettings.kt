package cn.limpu.hita.feature.livecourse.settings

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Persistent user choices and system-settings routing for Live Course. */
class LiveCourseSettings(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Keeps a visible foreground guard active for devices which defer exact alarms.
     * This is opt-in because the guard has a persistent system notification.
     */
    fun isStrongReminderEnabled(): Boolean =
        preferences.getBoolean(KEY_STRONG_REMINDER_ENABLED, false)

    fun setStrongReminderEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_STRONG_REMINDER_ENABLED, enabled).apply()
    }

    /** Android 12+ requires this special access for course-state transitions to be timely. */
    fun canScheduleExactAlarms(): Boolean {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (alarmManager?.canScheduleExactAlarms() == true)
    }

    fun exactAlarmSettingsIntent(): Intent {
        val packageUri = Uri.parse("package:${appContext.packageName}")
        val exactAlarmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        }
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        return (exactAlarmIntent.takeIf { it.resolveActivity(appContext.packageManager) != null } ?: fallback)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun promotedNotificationSettingsIntent(): Intent {
        val packageUri = Uri.parse("package:${appContext.packageName}")
        val promotedIntent = Intent(ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS, packageUri)
        val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
        return (promotedIntent.takeIf { it.resolveActivity(appContext.packageManager) != null } ?: fallback)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        private const val PREFERENCES = "live_course"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_STRONG_REMINDER_ENABLED = "strong_reminder_enabled"

        // Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS is API 36. Keeping the public
        // action literal lets this module compile against API 35 and still route on Android 16.
        private const val ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS =
            "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"
    }
}
