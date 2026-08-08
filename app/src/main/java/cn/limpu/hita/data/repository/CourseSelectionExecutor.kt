package cn.limpu.hita.data.repository

import cn.limpu.hita.data.model.eas.CourseSelectionCourseResult
import cn.limpu.hita.data.model.eas.CourseSelectionCourseStatus
import cn.limpu.hita.data.model.eas.CourseSelectionJob
import cn.limpu.hita.data.model.eas.CourseSelectionJobCourse
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

internal class CourseSelectionExecutor(
    private val gateway: ShenzhenCourseSelectionGateway,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun execute(job: CourseSelectionJob): CourseSelectionJob = coroutineScope {
        val distinctCourses = job.courses.distinctBy { it.courseId }
        require(distinctCourses.size <= CourseSelectionJobPolicy.MAX_COURSES) {
            "At most ${CourseSelectionJobPolicy.MAX_COURSES} distinct courses may be submitted"
        }
        val owner = Any()
        try {
            gateway.beginExecution(job, owner)
            val semaphore = Semaphore(CourseSelectionJobPolicy.MAX_CONCURRENCY)
            val submitted = distinctCourses.map { course ->
                async {
                    semaphore.withPermit {
                        gateway.submitOnce(job, course)
                    }
                }
            }.awaitAll()

            confirmWithinExecution(job.copy(results = submitted))
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
