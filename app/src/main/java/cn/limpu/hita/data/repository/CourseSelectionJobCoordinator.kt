package cn.limpu.hita.data.repository

import android.content.Context
import androidx.core.content.ContextCompat
import cn.limpu.hita.R
import cn.limpu.hita.data.model.eas.CourseSelectionJob
import cn.limpu.hita.data.model.eas.CourseSelectionJobStatus
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenSelectionPool
import cn.limpu.hita.data.model.eas.TermItem
import cn.limpu.hita.data.work.AlarmRecoveryAction
import cn.limpu.hita.data.work.CourseSelectionAlarmPolicy
import cn.limpu.hita.data.work.CourseSelectionAlarmScheduler
import cn.limpu.hita.data.work.CourseSelectionForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class CourseSelectionJobCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val easRepository: EASRepository,
    private val alarmScheduler: CourseSelectionAlarmScheduler
) {
    private val appContext = context.applicationContext
    private val store = CourseSelectionJobStore(appContext)
    private val executor = CourseSelectionExecutor(easRepository)
    private val executionMutex = Mutex()
    private val stateLock = Any()

    val jobs: StateFlow<List<CourseSelectionJob>> = store.jobs

    fun createImmediate(
        term: TermItem,
        pool: ShenzhenSelectionPool,
        courses: List<ShenzhenCourseCatalogItem>
    ): CourseSelectionJob {
        val nowMillis = System.currentTimeMillis()
        val job = createJob(term, pool, courses, nowMillis, nowMillis)
        synchronized(stateLock) {
            store.create(job)
            try {
                ContextCompat.startForegroundService(
                    appContext,
                    CourseSelectionForegroundService.executeIntent(appContext, job.id)
                )
            } catch (error: RuntimeException) {
                store.update(job.copy(
                    status = CourseSelectionJobStatus.FAILED,
                    message = appContext.getString(R.string.course_selection_unknown_result_recovery)
                ))
                throw error
            }
        }
        return job
    }

    fun createScheduled(
        term: TermItem,
        pool: ShenzhenSelectionPool,
        courses: List<ShenzhenCourseCatalogItem>,
        scheduledAtMillis: Long
    ): CourseSelectionJob {
        val nowMillis = System.currentTimeMillis()
        require(
            scheduledAtMillis >= nowMillis &&
                scheduledAtMillis - nowMillis in
                CourseSelectionJobPolicy.MIN_SCHEDULE_DELAY_MS..CourseSelectionJobPolicy.MAX_SCHEDULE_AHEAD_MS
        ) {
            appContext.getString(R.string.course_selection_schedule_range_error)
        }
        val job = createJob(term, pool, courses, scheduledAtMillis, nowMillis)
        synchronized(stateLock) {
            store.create(job)
            try {
                alarmScheduler.schedule(job)
            } catch (error: RuntimeException) {
                store.update(job.copy(
                    status = CourseSelectionJobStatus.FAILED,
                    message = appContext.getString(
                        R.string.course_selection_exact_alarm_permission_explanation
                    )
                ))
                throw error
            }
        }
        return job
    }

    fun cancel(jobId: String): Boolean = synchronized(stateLock) {
        val job = store.get(jobId) ?: return@synchronized false
        if (job.status != CourseSelectionJobStatus.WAITING) return@synchronized false
        store.cancel(jobId)
        alarmScheduler.cancel(jobId)
        true
    }

    suspend fun execute(jobId: String) {
        executionMutex.withLock {
            val runningJob = claimWaitingJob(jobId) ?: return@withLock
            try {
                val terminalJob = executor.execute(runningJob) { progress ->
                    store.update(progress)
                }
                store.update(terminalJob.copy(message = terminalMessage(terminalJob.status)))
            } catch (_: CourseSelectionCredentialScopeMismatchException) {
                terminalizeDifferentAccount(jobId)
            } catch (cancellation: CancellationException) {
                withContext(NonCancellable) {
                    markRunningJobUnknown(jobId)
                }
                throw cancellation
            } catch (_: Exception) {
                markRunningJobUnknown(jobId)
            }
        }
    }

    suspend fun confirm(jobId: String) {
        executionMutex.withLock {
            val job = store.get(jobId) ?: return@withLock
            if (job.status == CourseSelectionJobStatus.WAITING ||
                job.status == CourseSelectionJobStatus.RUNNING ||
                job.status == CourseSelectionJobStatus.CANCELLED
            ) {
                return@withLock
            }
            try {
                val confirmedJob = executor.confirm(job)
                store.update(confirmedJob.copy(message = terminalMessage(confirmedJob.status)))
            } catch (_: CourseSelectionCredentialScopeMismatchException) {
                terminalizeDifferentAccount(jobId)
            }
        }
    }

    internal fun recoverAfterBoot(nowMillis: Long = System.currentTimeMillis()) {
        synchronized(stateLock) {
            store.recoverInterrupted()
            store.waitingJobs().forEach { job ->
                when (CourseSelectionAlarmPolicy.recoveryAction(job, nowMillis)) {
                    AlarmRecoveryAction.RESCHEDULE -> try {
                        alarmScheduler.schedule(job)
                    } catch (_: RuntimeException) {
                        store.update(job.copy(
                            status = CourseSelectionJobStatus.FAILED,
                            message = appContext.getString(
                                R.string.course_selection_exact_alarm_permission_explanation
                            )
                        ))
                    }
                    AlarmRecoveryAction.EXPIRE -> store.update(job.copy(
                        status = CourseSelectionJobStatus.FAILED,
                        message = appContext.getString(R.string.course_selection_job_expired)
                    ))
                    AlarmRecoveryAction.IGNORE -> Unit
                }
            }
        }
    }

    private fun createJob(
        term: TermItem,
        pool: ShenzhenSelectionPool,
        courses: List<ShenzhenCourseCatalogItem>,
        scheduledAtMillis: Long,
        createdAtMillis: Long
    ) = CourseSelectionJob(
        id = UUID.randomUUID().toString(),
        termId = term.id,
        termYearCode = term.yearCode,
        termCode = term.termCode,
        scheduledAtMillis = scheduledAtMillis,
        createdAtMillis = createdAtMillis,
        status = CourseSelectionJobStatus.WAITING,
        courses = CourseSelectionJobPolicy.buildCourses(courses, pool),
        credentialScopeGeneration = easRepository.currentCourseSelectionCredentialScopeGeneration()
    )

    private fun claimWaitingJob(jobId: String): CourseSelectionJob? = synchronized(stateLock) {
        val job = store.get(jobId) ?: return@synchronized null
        if (job.status != CourseSelectionJobStatus.WAITING) return@synchronized null
        store.update(job.copy(
            status = CourseSelectionJobStatus.RUNNING,
            message = appContext.getString(R.string.course_selection_notification_running)
        ))
    }

    private fun markRunningJobUnknown(jobId: String) {
        synchronized(stateLock) {
            val job = store.get(jobId) ?: return@synchronized
            if (job.status != CourseSelectionJobStatus.RUNNING) return@synchronized
            store.update(job.copy(
                status = CourseSelectionJobStatus.FAILED,
                message = appContext.getString(R.string.course_selection_unknown_result_recovery)
            ))
        }
    }

    private fun terminalizeDifferentAccount(jobId: String) {
        synchronized(stateLock) {
            val job = store.get(jobId) ?: return@synchronized
            store.update(CourseSelectionCredentialScopePolicy.terminalize(job))
        }
    }

    private fun terminalMessage(status: CourseSelectionJobStatus): String = appContext.getString(
        when (status) {
            CourseSelectionJobStatus.COMPLETED -> R.string.course_selection_notification_completed
            CourseSelectionJobStatus.NEEDS_CONFIRMATION ->
                R.string.course_selection_notification_needs_confirmation
            else -> R.string.course_selection_notification_failed
        }
    )
}
