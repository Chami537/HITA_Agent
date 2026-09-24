package cn.limpu.hita.data.repository

import cn.limpu.hita.data.model.eas.CourseSelectionCourseResult
import cn.limpu.hita.data.model.eas.CourseSelectionCourseStatus
import cn.limpu.hita.data.model.eas.CourseSelectionJob
import cn.limpu.hita.data.model.eas.CourseSelectionJobCourse
import cn.limpu.hita.data.model.eas.CourseSelectionJobStatus
import cn.limpu.hita.data.model.eas.EASToken
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal interface ShenzhenCourseSelectionGateway {
    suspend fun beginExecution(
        job: CourseSelectionJob,
        owner: Any
    ) = Unit

    suspend fun endExecution(
        job: CourseSelectionJob,
        owner: Any
    ) = Unit

    suspend fun submitOnce(
        job: CourseSelectionJob,
        course: CourseSelectionJobCourse
    ): CourseSelectionCourseResult

    suspend fun selectedRequestIds(job: CourseSelectionJob): Set<String>
}

internal class CourseSelectionCredentialScopeMismatchException : IllegalStateException(
    CourseSelectionCredentialScopePolicy.DIFFERENT_ACCOUNT_NO_REQUEST_MESSAGE
)

internal object CourseSelectionCredentialScopePolicy {
    const val DIFFERENT_ACCOUNT_NO_REQUEST_MESSAGE = "任务属于其他账号，未发送任何请求。"

    fun requireMatching(job: CourseSelectionJob, token: EASToken) {
        if (job.credentialScopeGeneration <= 0L ||
            token.sessionGeneration <= 0L ||
            job.credentialScopeGeneration != token.sessionGeneration ||
            !token.hasShenzhenWebSession()
        ) {
            throw CourseSelectionCredentialScopeMismatchException()
        }
    }

    fun terminalize(job: CourseSelectionJob): CourseSelectionJob = job.copy(
        status = CourseSelectionJobStatus.FAILED,
        message = DIFFERENT_ACCOUNT_NO_REQUEST_MESSAGE
    )
}

internal class CourseSelectionExecutor(
    private val gateway: ShenzhenCourseSelectionGateway,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun execute(
        job: CourseSelectionJob,
        persistProgress: (CourseSelectionJob) -> Unit = {}
    ): CourseSelectionJob = coroutineScope {
        val distinctCourses = job.courses.distinctBy { it.courseId }
        require(distinctCourses.size <= CourseSelectionJobPolicy.MAX_COURSES) {
            "At most ${CourseSelectionJobPolicy.MAX_COURSES} distinct courses may be submitted"
        }
        val progress = CourseSelectionExecutionProgress(job, nowMillis, persistProgress)
        val attemptedCourseIds = job.results.mapTo(hashSetOf()) { it.courseId }
        val owner = Any()
        try {
            gateway.beginExecution(job, owner)
            val semaphore = Semaphore(CourseSelectionJobPolicy.MAX_CONCURRENCY)
            distinctCourses.filterNot { it.courseId in attemptedCourseIds }.map { course ->
                async {
                    semaphore.withPermit {
                        progress.markAttempted(course)
                        progress.complete(gateway.submitOnce(job, course))
                    }
                }
            }.awaitAll()

            confirmWithinExecution(progress.snapshot())
        } finally {
            withContext(NonCancellable) {
                gateway.endExecution(job, owner)
            }
        }
    }

    suspend fun confirm(job: CourseSelectionJob): CourseSelectionJob {
        val owner = Any()
        return try {
            gateway.beginExecution(job, owner)
            confirmWithinExecution(job)
        } finally {
            withContext(NonCancellable) {
                gateway.endExecution(job, owner)
            }
        }
    }

    private suspend fun confirmWithinExecution(job: CourseSelectionJob): CourseSelectionJob {
        val selectedIds = gateway.selectedRequestIds(job)
        val courseById = job.courses.distinctBy { it.courseId }.associateBy { it.courseId }
        val confirmedAtMillis = nowMillis()
        val confirmedResults = job.results.map { result ->
            val course = courseById[result.courseId]
            if (course != null && course.identities().any(selectedIds::contains)) {
                result.copy(
                    status = CourseSelectionCourseStatus.CONFIRMED,
                    confirmedAtMillis = confirmedAtMillis
                )
            } else {
                result
            }
        }
        return job.copy(
            status = CourseSelectionJobPolicy.aggregateStatus(
                expectedCourseCount = courseById.size,
                results = confirmedResults
            ),
            results = confirmedResults
        )
    }

    private fun CourseSelectionJobCourse.identities(): Set<String> =
        setOf(requestId, taskId, courseId).filterTo(linkedSetOf()) { it.isNotBlank() }
}

private class CourseSelectionExecutionProgress(
    initialJob: CourseSelectionJob,
    private val nowMillis: () -> Long,
    private val persist: (CourseSelectionJob) -> Unit
) {
    private val lock = Any()
    private var currentJob = initialJob

    fun markAttempted(course: CourseSelectionJobCourse) {
        replaceResult(CourseSelectionCourseResult(
            courseId = course.courseId,
            status = CourseSelectionCourseStatus.UNKNOWN,
            message = "",
            submittedAtMillis = nowMillis()
        ))
    }

    fun complete(result: CourseSelectionCourseResult): CourseSelectionCourseResult {
        replaceResult(result)
        return result
    }

    fun snapshot(): CourseSelectionJob = synchronized(lock) { currentJob }

    private fun replaceResult(result: CourseSelectionCourseResult) {
        synchronized(lock) {
            val resultsByCourse = currentJob.results.associateByTo(linkedMapOf()) { it.courseId }
            resultsByCourse[result.courseId] = result
            val orderedResults = currentJob.courses.distinctBy { it.courseId }
                .mapNotNull { resultsByCourse[it.courseId] }
            currentJob = currentJob.copy(results = orderedResults)
            persist(currentJob)
        }
    }
}
