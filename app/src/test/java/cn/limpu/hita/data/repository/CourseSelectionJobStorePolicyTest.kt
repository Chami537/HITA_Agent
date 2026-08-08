package cn.limpu.hita.data.repository

import cn.limpu.hita.data.model.eas.CourseSelectionJob
import cn.limpu.hita.data.model.eas.CourseSelectionJobCourse
import cn.limpu.hita.data.model.eas.CourseSelectionCourseResult
import cn.limpu.hita.data.model.eas.CourseSelectionCourseStatus
import cn.limpu.hita.data.model.eas.CourseSelectionJobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CourseSelectionJobStorePolicyTest {
    @Test
    fun `running task becomes failed unknown after process recovery`() {
        val recovered = CourseSelectionJobStorePolicy.recover(
            listOf(job(status = CourseSelectionJobStatus.RUNNING)),
            nowMillis = 2_000L
        )

        assertEquals(CourseSelectionJobStatus.FAILED, recovered.single().status)
        assertTrue(recovered.single().message.contains("结果未知"))
    }

    @Test
    fun `store keeps all active and latest twenty terminal jobs`() {
        val jobs = listOf(job(id = "waiting", status = CourseSelectionJobStatus.WAITING)) +
            (1..25).map { job(id = "done-$it", createdAtMillis = it.toLong(), status = CourseSelectionJobStatus.COMPLETED) }

        val pruned = CourseSelectionJobStorePolicy.prune(jobs)

        assertEquals(21, pruned.size)
        assertTrue(pruned.any { it.id == "waiting" })
        assertEquals((6..25).map { "done-$it" }.toSet(), pruned.filter { it.status == CourseSelectionJobStatus.COMPLETED }.map { it.id }.toSet())
    }

    @Test
    fun `job payload round trips without credentials`() {
        val encoded = CourseSelectionJobCodec.encode(listOf(job(status = CourseSelectionJobStatus.WAITING)))

        assertFalse(encoded.contains("SESSION="))
        assertFalse(encoded.contains("EASToken"))
        assertFalse(encoded.contains("username", ignoreCase = true))
        assertFalse(encoded.contains("password", ignoreCase = true))
        assertEquals(listOf(job(status = CourseSelectionJobStatus.WAITING)), CourseSelectionJobCodec.decode(encoded))
    }

    @Test
    fun `credential-like job and result messages are omitted from persisted payload`() {
        val job = job(
            status = CourseSelectionJobStatus.FAILED,
            message = "Cookie: SESSION=super-secret",
            results = listOf(result(message = "password=also-secret"))
        )

        val encoded = CourseSelectionJobCodec.encode(listOf(job))
        val decoded = CourseSelectionJobCodec.decode(encoded).single()

        assertFalse(encoded.contains("super-secret"))
        assertFalse(encoded.contains("also-secret"))
        assertEquals("", decoded.message)
        assertEquals("", decoded.results.single().message)
    }

    @Test
    fun `unicode separated credential messages are omitted from persisted payload`() {
        val secrets = listOf(
            "nbsp-secret",
            "narrow-secret",
            "thin-secret",
            "em-secret",
            "format-secret"
        )
        val job = job(
            status = CourseSelectionJobStatus.FAILED,
            message = "cOoKiE\u00a0=\u00a0${secrets[0]}",
            results = listOf(
                result(message = "SESSION\u202f=\u202f${secrets[1]}"),
                result(message = "ToKeN\u2009=\u2009${secrets[2]}"),
                result(message = "PASSWORD\u2003=\u2003${secrets[3]}"),
                result(message = "UsEr\u200bNaMe\u200c=\u2060${secrets[4]}")
            )
        )

        val encoded = CourseSelectionJobCodec.encode(listOf(job))
        val decoded = CourseSelectionJobCodec.decode(encoded).single()

        secrets.forEach { secret -> assertFalse(encoded.contains(secret)) }
        assertEquals("", decoded.message)
        assertTrue(decoded.results.all { it.message.isEmpty() })
    }

    @Test
    fun `format code points cannot conceal credential separators or keys`() {
        val supplementaryFormat = "\udb40\udc01"
        val secrets = listOf(
            "bearer-format-secret",
            "token-supplementary-secret",
            "cookie-supplementary-secret",
            "mixed-format-secret"
        )
        val job = job(
            status = CourseSelectionJobStatus.FAILED,
            message = "Bearer\u200b${secrets[0]}",
            results = listOf(
                result(message = "to${supplementaryFormat}ken=${secrets[1]}"),
                result(message = "co${supplementaryFormat}okie\u202f=\u202f${secrets[2]}"),
                result(message = "BeArEr${supplementaryFormat}\u2009${secrets[3]}")
            )
        )

        val encoded = CourseSelectionJobCodec.encode(listOf(job))
        val decoded = CourseSelectionJobCodec.decode(encoded).single()

        secrets.forEach { secret -> assertFalse(encoded.contains(secret)) }
        assertEquals("", decoded.message)
        assertTrue(decoded.results.all { it.message.isEmpty() })
    }

    @Test
    fun `safe Chinese recovery text remains byte for byte unchanged`() {
        val safeMessage = "应用恢复时发现任务正在运行，提交结果未知，未重试。"
        val decoded = CourseSelectionJobCodec.decode(CourseSelectionJobCodec.encode(listOf(job(
            status = CourseSelectionJobStatus.FAILED,
            message = safeMessage
        )))).single()

        assertEquals(safeMessage, decoded.message)
    }

    @Test
    fun `safe recovery message survives payload round trip unchanged`() {
        val recovered = CourseSelectionJobStorePolicy.recover(
            listOf(job(status = CourseSelectionJobStatus.RUNNING)),
            nowMillis = 2_000L
        ).single()

        assertEquals(
            recovered.message,
            CourseSelectionJobCodec.decode(CourseSelectionJobCodec.encode(listOf(recovered))).single().message
        )
    }

    @Test
    fun `malformed missing unsupported and null payload entries decode empty`() {
        val payloads = listOf(
            "{",
            "{\"jobs\":[]}",
            "{\"version\":2,\"jobs\":[]}",
            "{\"version\":1,\"jobs\":[null]}"
        )

        payloads.forEach { payload ->
            assertEquals(emptyList<CourseSelectionJob>(), CourseSelectionJobCodec.decode(payload))
        }
    }

    @Test
    fun `structurally invalid job result and enum payload decode empty`() {
        val encoded = CourseSelectionJobCodec.encode(
            listOf(job(status = CourseSelectionJobStatus.FAILED, results = listOf(result())))
        )

        assertEquals(emptyList<CourseSelectionJob>(), CourseSelectionJobCodec.decode(encoded.replace(
            "\"status\":\"FAILED\"",
            "\"status\":null"
        )))
        assertEquals(emptyList<CourseSelectionJob>(), CourseSelectionJobCodec.decode(encoded.replace(
            "\"submittedAtMillis\":10",
            "\"submittedAtMillis\":null"
        )))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate active fingerprint is rejected`() {
        val newJob = job(id = "new", status = CourseSelectionJobStatus.WAITING)
        val existingEquivalentWaitingJob = job(id = "existing", status = CourseSelectionJobStatus.WAITING)

        CourseSelectionJobStorePolicy.requireUnique(newJob, listOf(existingEquivalentWaitingJob))
    }

    @Test
    fun `terminal duplicate fingerprint is allowed`() {
        val newJob = job(id = "new", status = CourseSelectionJobStatus.WAITING)
        val completedEquivalentJob = job(id = "complete", status = CourseSelectionJobStatus.COMPLETED)

        CourseSelectionJobStorePolicy.requireUnique(newJob, listOf(completedEquivalentJob))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `updating active job to another active fingerprint is rejected`() {
        val current = listOf(
            job(id = "first", status = CourseSelectionJobStatus.WAITING),
            job(id = "second", status = CourseSelectionJobStatus.WAITING, scheduledAtMillis = 1_800_000_001_000L)
        )
        val update = current[1].copy(scheduledAtMillis = current[0].scheduledAtMillis)

        CourseSelectionJobStorePolicy.requireUniqueForUpdate(update, current)
    }

    @Test
    fun `persistence seam commits before publishing and stops on commit failure`() {
        val events = mutableListOf<String>()
        CourseSelectionJobStorePersistence.commitThenPublish(
            snapshot = emptyList(),
            commit = { events += "commit" },
            publish = { events += "publish" }
        )
        assertEquals(listOf("commit", "publish"), events)

        var published = false
        try {
            CourseSelectionJobStorePersistence.commitThenPublish(
                snapshot = emptyList(),
                commit = { throw IllegalStateException("disk full") },
                publish = { published = true }
            )
            fail("Commit failure must escape")
        } catch (_: IllegalStateException) {
        }
        assertFalse(published)
    }

    private fun job(
        id: String = "job-1",
        createdAtMillis: Long = 1_000L,
        status: CourseSelectionJobStatus,
        scheduledAtMillis: Long = 1_800_000_000_000L,
        results: List<CourseSelectionCourseResult> = emptyList(),
        message: String = ""
    ) = CourseSelectionJob(
        id = id,
        termId = "term-1",
        termYearCode = "2026-2027",
        termCode = "1",
        scheduledAtMillis = scheduledAtMillis,
        createdAtMillis = createdAtMillis,
        status = status,
        courses = listOf(
            CourseSelectionJobCourse(
                requestId = "request-1",
                taskId = "task-1",
                courseId = "course-1",
                courseCode = "COMP1001",
                courseName = "Course One",
                teacher = "Teacher",
                poolCode = "xx-b-b"
            )
        ),
        results = results,
        message = message
    )

    private fun result(message: String = "safe result") = CourseSelectionCourseResult(
        courseId = "course-1",
        status = CourseSelectionCourseStatus.UNKNOWN,
        message = message,
        submittedAtMillis = 10L
    )
}
