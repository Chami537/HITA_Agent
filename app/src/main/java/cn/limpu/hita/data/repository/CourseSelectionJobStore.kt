package cn.limpu.hita.data.repository

import android.content.Context
import cn.limpu.hita.data.model.eas.CourseSelectionCourseResult
import cn.limpu.hita.data.model.eas.CourseSelectionCourseStatus
import cn.limpu.hita.data.model.eas.CourseSelectionJob
import cn.limpu.hita.data.model.eas.CourseSelectionJobCourse
import cn.limpu.hita.data.model.eas.CourseSelectionJobStatus
import cn.limpu.hita.data.model.eas.CourseSelectionMessageSanitizer
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists Shenzhen course-selection job metadata and results only.
 *
 * Authentication and web-session state deliberately stay outside this store. A process restart
 * turns an in-flight job into a terminal unknown result; it never resumes a submission.
 */
class CourseSelectionJobStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE
    )
    private val _jobs = MutableStateFlow(emptyList<CourseSelectionJob>())

    val jobs: StateFlow<List<CourseSelectionJob>> = _jobs.asStateFlow()

    init {
        synchronized(processLock) {
            publishPersisted(load())
        }
        recoverInterrupted()
    }

    fun get(id: String): CourseSelectionJob? = _jobs.value.firstOrNull { it.id == id }

    fun create(job: CourseSelectionJob): CourseSelectionJob = mutate { current ->
        CourseSelectionJobStorePolicy.requireUnique(job, current)
        check(current.none { it.id == job.id }) { "Course selection job already exists: ${job.id}" }
        current + job
    }.first { it.id == job.id }

    fun update(job: CourseSelectionJob): CourseSelectionJob = mutate { current ->
        CourseSelectionJobStorePolicy.requireUniqueForUpdate(job, current)
        current.map { existing -> if (existing.id == job.id) job else existing }
    }.first { it.id == job.id }

    fun cancel(id: String): CourseSelectionJob? = synchronized(processLock) {
        val current = load()
        val job = current.firstOrNull { it.id == id } ?: return@synchronized null
        if (job.status != CourseSelectionJobStatus.WAITING) {
            return@synchronized job
        }
        val cancelled = job.copy(status = CourseSelectionJobStatus.CANCELLED, message = "已取消")
        val next = CourseSelectionJobStorePolicy.prune(
            current.map { existing -> if (existing.id == id) cancelled else existing }
        )
        publishPersisted(next)
        cancelled
    }

    fun waitingJobs(): List<CourseSelectionJob> = _jobs.value.filter {
        it.status == CourseSelectionJobStatus.WAITING
    }

    fun recoverInterrupted(): List<CourseSelectionJob> = mutate { current ->
        CourseSelectionJobStorePolicy.recover(current, System.currentTimeMillis())
    }

    private fun mutate(transform: (List<CourseSelectionJob>) -> List<CourseSelectionJob>): List<CourseSelectionJob> =
        synchronized(processLock) {
            val next = CourseSelectionJobStorePolicy.prune(transform(load()))
            publishPersisted(next)
        }

    private fun load(): List<CourseSelectionJob> = CourseSelectionJobCodec.decode(
        preferences.getString(KEY_PAYLOAD, null)
    ).let(CourseSelectionJobStorePolicy::prune)

    private fun persist(jobs: List<CourseSelectionJob>) {
        check(preferences.edit().putString(KEY_PAYLOAD, CourseSelectionJobCodec.encode(jobs)).commit()) {
            "Unable to persist Shenzhen course selection jobs"
        }
    }

    private fun publishPersisted(jobs: List<CourseSelectionJob>): List<CourseSelectionJob> =
        CourseSelectionJobStorePersistence.commitThenPublish(
            snapshot = jobs,
            commit = ::persist,
            publish = { _jobs.value = it }
        )

    private companion object {
        const val PREFERENCES = "shenzhen_course_selection_jobs"
        const val KEY_PAYLOAD = "payload_v1"
        val processLock = Any()
    }
}

object CourseSelectionJobStorePolicy {
    private const val MAX_TERMINAL_JOBS = 20

    fun recover(jobs: List<CourseSelectionJob>, nowMillis: Long): List<CourseSelectionJob> = jobs.map { job ->
        if (job.status == CourseSelectionJobStatus.RUNNING) {
            job.copy(
                status = CourseSelectionJobStatus.FAILED,
                message = "应用在 $nowMillis 恢复时发现任务正在运行，提交结果未知，未重试。"
            )
        } else {
            job
        }
    }

    fun prune(jobs: List<CourseSelectionJob>): List<CourseSelectionJob> {
        val active = jobs.filter(::isActive)
        val terminal = jobs.filterNot(::isActive)
            .sortedByDescending(CourseSelectionJob::createdAtMillis)
            .take(MAX_TERMINAL_JOBS)
        return active + terminal
    }

    fun requireUnique(newJob: CourseSelectionJob, jobs: List<CourseSelectionJob>) {
        if (!isActive(newJob)) return
        val fingerprint = CourseSelectionJobPolicy.fingerprint(newJob.scheduledAtMillis, newJob.courses)
        require(jobs.none { existing ->
            isActive(existing) &&
                CourseSelectionJobPolicy.fingerprint(existing.scheduledAtMillis, existing.courses) == fingerprint
        }) { "An active course selection job with the same fingerprint already exists" }
    }

    fun requireUniqueForUpdate(updatedJob: CourseSelectionJob, jobs: List<CourseSelectionJob>) {
        check(jobs.any { it.id == updatedJob.id }) {
            "Course selection job does not exist: ${updatedJob.id}"
        }
        requireUnique(updatedJob, jobs.filterNot { it.id == updatedJob.id })
    }

    private fun isActive(job: CourseSelectionJob): Boolean =
        job.status == CourseSelectionJobStatus.WAITING || job.status == CourseSelectionJobStatus.RUNNING
}

object CourseSelectionJobCodec {
    private data class Payload(
        val version: Int = VERSION,
        val jobs: List<JobPayload>
    )

    private data class JobPayload(
        val id: String,
        val termId: String,
        val termYearCode: String,
        val termCode: String,
        val scheduledAtMillis: Long,
        val createdAtMillis: Long,
        val status: String,
        val courses: List<CoursePayload>,
        val results: List<ResultPayload>,
        val message: String,
        val credentialScopeGeneration: Long
    )

    private data class CoursePayload(
        val requestId: String,
        val taskId: String,
        val courseId: String,
        val courseCode: String,
        val courseName: String,
        val teacher: String,
        val poolCode: String
    )

    private data class ResultPayload(
        val courseId: String,
        val status: String,
        val message: String,
        val submittedAtMillis: Long,
        val confirmedAtMillis: Long?
    )

    private val gson = Gson()

    fun encode(jobs: List<CourseSelectionJob>): String = gson.toJson(
        Payload(version = VERSION, jobs = jobs.map(::toPayload))
    )

    fun decode(encoded: String?): List<CourseSelectionJob> = runCatching {
        val root = encoded?.let { JsonParser().parse(it) }?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?: return emptyList()
        if (root.requiredVersion() != VERSION) return emptyList()
        val jobs = root.requiredArray("jobs") ?: return emptyList()
        jobs.map { decodeJob(it) ?: return emptyList() }.also { decoded ->
            if (decoded.map(CourseSelectionJob::id).distinct().size != decoded.size) return emptyList()
        }
    }.getOrDefault(emptyList())

    private fun toPayload(job: CourseSelectionJob) = JobPayload(
        id = job.id,
        termId = job.termId,
        termYearCode = job.termYearCode,
        termCode = job.termCode,
        scheduledAtMillis = job.scheduledAtMillis,
        createdAtMillis = job.createdAtMillis,
        status = job.status.name,
        courses = job.courses.map { course ->
            CoursePayload(
                requestId = course.requestId,
                taskId = course.taskId,
                courseId = course.courseId,
                courseCode = course.courseCode,
                courseName = course.courseName,
                teacher = course.teacher,
                poolCode = course.poolCode
            )
        },
        results = job.results.map { result ->
            ResultPayload(
                courseId = result.courseId,
                status = result.status.name,
                message = CourseSelectionMessageSanitizer.sanitize(result.message),
                submittedAtMillis = result.submittedAtMillis,
                confirmedAtMillis = result.confirmedAtMillis
            )
        },
        message = CourseSelectionMessageSanitizer.sanitize(job.message),
        credentialScopeGeneration = job.credentialScopeGeneration
    )

    private fun decodeJob(element: JsonElement): CourseSelectionJob? {
        val job = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return null
        val courses = job.requiredArray("courses")?.map(::decodeCourse) ?: return null
        val results = job.requiredArray("results")?.map(::decodeResult) ?: return null
        if (courses.any { it == null } || results.any { it == null }) return null
        return CourseSelectionJob(
            id = job.requiredString("id", nonBlank = true) ?: return null,
            termId = job.requiredString("termId", nonBlank = true) ?: return null,
            termYearCode = job.requiredString("termYearCode", nonBlank = true) ?: return null,
            termCode = job.requiredString("termCode", nonBlank = true) ?: return null,
            scheduledAtMillis = job.requiredTimestamp("scheduledAtMillis") ?: return null,
            createdAtMillis = job.requiredTimestamp("createdAtMillis") ?: return null,
            status = job.requiredEnum<CourseSelectionJobStatus>("status") ?: return null,
            courses = courses.filterNotNull(),
            results = results.filterNotNull(),
            message = CourseSelectionMessageSanitizer.sanitize(
                job.requiredString("message") ?: return null
            ),
            credentialScopeGeneration = job.optionalCredentialScopeGeneration() ?: return null
        )
    }

    private fun decodeCourse(element: JsonElement): CourseSelectionJobCourse? {
        val course = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return null
        return CourseSelectionJobCourse(
            requestId = course.requiredString("requestId", nonBlank = true) ?: return null,
            taskId = course.requiredString("taskId", nonBlank = true) ?: return null,
            courseId = course.requiredString("courseId", nonBlank = true) ?: return null,
            courseCode = course.requiredString("courseCode") ?: return null,
            courseName = course.requiredString("courseName") ?: return null,
            teacher = course.requiredString("teacher") ?: return null,
            poolCode = course.requiredString("poolCode", nonBlank = true) ?: return null
        )
    }

    private fun decodeResult(element: JsonElement): CourseSelectionCourseResult? {
        val result = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return null
        val confirmedAt = result.optionalTimestamp("confirmedAtMillis") ?: return null
        return CourseSelectionCourseResult(
            courseId = result.requiredString("courseId", nonBlank = true) ?: return null,
            status = result.requiredEnum<CourseSelectionCourseStatus>("status") ?: return null,
            message = CourseSelectionMessageSanitizer.sanitize(
                result.requiredString("message") ?: return null
            ),
            submittedAtMillis = result.requiredTimestamp("submittedAtMillis") ?: return null,
            confirmedAtMillis = confirmedAt.value
        )
    }

    private fun JsonObject.requiredVersion(): Int? {
        val version = get("version")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asString
            ?: return null
        return version.takeIf { it.matches(Regex("-?\\d+")) }?.toIntOrNull()
    }

    private fun JsonObject.requiredArray(name: String) = get(name)?.takeIf(JsonElement::isJsonArray)?.asJsonArray

    private fun JsonObject.requiredString(name: String, nonBlank: Boolean = false): String? {
        val value = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?: return null
        return value.takeIf { !nonBlank || it.isNotBlank() }
    }

    private fun JsonObject.requiredTimestamp(name: String): Long? = get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.asString
        ?.takeIf { it.matches(Regex("-?\\d+")) }
        ?.toLongOrNull()
        ?.takeIf { it >= 0L }

    private fun JsonObject.optionalCredentialScopeGeneration(): Long? {
        if (!has("credentialScopeGeneration")) return 0L
        return requiredTimestamp("credentialScopeGeneration")
    }

    private fun JsonObject.optionalTimestamp(name: String): OptionalTimestamp? {
        if (!has(name)) return OptionalTimestamp(null)
        if (get(name).isJsonNull) return OptionalTimestamp(null)
        return requiredTimestamp(name)?.let(::OptionalTimestamp)
    }

    private data class OptionalTimestamp(val value: Long?)

    private inline fun <reified T : Enum<T>> JsonObject.requiredEnum(name: String): T? =
        requiredString(name, nonBlank = true)?.let { value ->
            enumValues<T>().firstOrNull { it.name == value }
        }

    private const val VERSION = 1
}

internal object CourseSelectionJobStorePersistence {
    fun commitThenPublish(
        snapshot: List<CourseSelectionJob>,
        commit: (List<CourseSelectionJob>) -> Unit,
        publish: (List<CourseSelectionJob>) -> Unit
    ): List<CourseSelectionJob> {
        val sanitizedSnapshot = snapshot.map(CourseSelectionMessageSanitizer::sanitize)
        commit(sanitizedSnapshot)
        publish(sanitizedSnapshot)
        return sanitizedSnapshot
    }
}
