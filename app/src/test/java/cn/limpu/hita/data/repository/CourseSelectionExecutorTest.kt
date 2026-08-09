package cn.limpu.hita.data.repository

import cn.limpu.hita.data.model.eas.CourseSelectionCourseResult
import cn.limpu.hita.data.model.eas.CourseSelectionCourseStatus
import cn.limpu.hita.data.model.eas.CourseSelectionJob
import cn.limpu.hita.data.model.eas.CourseSelectionJobCourse
import cn.limpu.hita.data.model.eas.CourseSelectionJobStatus
import cn.limpu.hita.data.model.eas.EASToken
import cn.limpu.hita.data.source.web.eas.EASWebSource
import cn.limpu.hita.data.source.web.eas.ShenzhenCourseSelectionTransport
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CourseSelectionExecutorTest {
    @Test
    fun `twenty distinct courses are each submitted exactly once`() = runBlocking {
        val acceptedIds = (1..20).map { "request-$it" }.toSet()
        val fake = RecordingGateway(acceptedIds = acceptedIds)
        val executor = CourseSelectionExecutor(fake, nowMillis = { 1_000L })

        val completed = executor.execute(jobWithCourses(20))

        assertEquals(acceptedIds, fake.submittedIds.toSet())
        assertTrue(fake.submittedIds.groupingBy { it }.eachCount().values.all { it == 1 })
        assertEquals(1, fake.confirmCalls)
        assertEquals(CourseSelectionJobStatus.COMPLETED, completed.status)
        assertTrue(completed.results.all { it.confirmedAtMillis == 1_000L })
    }

    @Test
    fun `executor never exceeds ten concurrent submissions`() = runBlocking {
        val fake = RecordingGateway(trackConcurrency = true)

        CourseSelectionExecutor(fake).execute(jobWithCourses(20))

        assertEquals(CourseSelectionJobPolicy.MAX_CONCURRENCY, fake.maximumConcurrency)
    }

    @Test
    fun `timeout remains unknown and is not retried`() = runBlocking {
        val fake = RecordingGateway(unknownIds = setOf("request-1"))

        val result = CourseSelectionExecutor(fake).execute(jobWithCourses(1))

        assertEquals(1, fake.submittedIds.count { it == "request-1" })
        assertEquals(CourseSelectionCourseStatus.UNKNOWN, result.results.single().status)
    }

    @Test
    fun `attempt marker is persisted before post and final result immediately after`() = runBlocking {
        val snapshots = mutableListOf<CourseSelectionJob>()
        val persistedProgress = AtomicReference<CourseSelectionJob>()
        val fake = object : ShenzhenCourseSelectionGateway {
            override suspend fun submitOnce(
                job: CourseSelectionJob,
                course: CourseSelectionJobCourse
            ): CourseSelectionCourseResult {
                assertEquals(
                    CourseSelectionCourseStatus.UNKNOWN,
                    persistedProgress.get().results.single().status
                )
                return resultFor(course)
            }

            override suspend fun selectedRequestIds(job: CourseSelectionJob): Set<String> = emptySet()
        }

        CourseSelectionExecutor(fake, nowMillis = { 1_234L }).execute(
            jobWithCourses(1).copy(status = CourseSelectionJobStatus.RUNNING),
            persistProgress = { snapshot ->
                persistedProgress.set(snapshot)
                snapshots += snapshot
            }
        )

        assertEquals(2, snapshots.size)
        assertEquals(CourseSelectionCourseStatus.UNKNOWN, snapshots[0].results.single().status)
        assertEquals(1_234L, snapshots[0].results.single().submittedAtMillis)
        assertEquals(CourseSelectionCourseStatus.UNCONFIRMED, snapshots[1].results.single().status)
    }

    @Test
    fun `interruption after two posts survives restart and reconfirms without resubmission`() = runBlocking {
        val firstResultPersisted = CompletableDeferred<Unit>()
        val encodedProgress = AtomicReference<String>()
        val posts = AtomicInteger(0)
        val interruptedGateway = object : ShenzhenCourseSelectionGateway {
            override suspend fun submitOnce(
                job: CourseSelectionJob,
                course: CourseSelectionJobCourse
            ): CourseSelectionCourseResult {
                if (course.courseId == "course-2") {
                    firstResultPersisted.await()
                    posts.incrementAndGet()
                    throw SimulatedProcessInterruption()
                }
                posts.incrementAndGet()
                return resultFor(course)
            }

            override suspend fun selectedRequestIds(job: CourseSelectionJob): Set<String> =
                throw AssertionError("Interrupted execution must not reach confirmation")
        }
        val runningJob = jobWithCourses(2).copy(status = CourseSelectionJobStatus.RUNNING)

        val failure = runCatching {
            CourseSelectionExecutor(interruptedGateway, nowMillis = { 1_500L }).execute(
                runningJob,
                persistProgress = { snapshot ->
                    encodedProgress.set(CourseSelectionJobCodec.encode(listOf(snapshot)))
                    if (snapshot.results.any {
                            it.courseId == "course-1" &&
                                it.status == CourseSelectionCourseStatus.UNCONFIRMED
                        }
                    ) {
                        firstResultPersisted.complete(Unit)
                    }
                }
            )
        }.exceptionOrNull()

        assertTrue(failure is SimulatedProcessInterruption)
        assertEquals(2, posts.get())
        val restarted = CourseSelectionJobStorePolicy.recover(
            CourseSelectionJobCodec.decode(encodedProgress.get()),
            nowMillis = 2_000L
        ).single()
        assertEquals(CourseSelectionJobStatus.FAILED, restarted.status)
        assertEquals(
            CourseSelectionCourseStatus.UNCONFIRMED,
            restarted.results.single { it.courseId == "course-1" }.status
        )
        assertEquals(
            CourseSelectionCourseStatus.UNKNOWN,
            restarted.results.single { it.courseId == "course-2" }.status
        )

        val reconfirmGateway = RecordingGateway(
            acceptedIds = setOf("request-1", "request-2")
        )
        val reconfirmed = CourseSelectionExecutor(reconfirmGateway, nowMillis = { 3_000L })
            .confirm(restarted)

        assertTrue(reconfirmGateway.submittedIds.isEmpty())
        assertEquals(1, reconfirmGateway.confirmCalls)
        assertTrue(reconfirmed.results.all { it.status == CourseSelectionCourseStatus.CONFIRMED })
    }

    @Test
    fun `duplicate request ids are submitted only once`() = runBlocking {
        val original = course(1)
        val duplicate = original.copy(taskId = "duplicate-task", courseId = "duplicate-course")
        val fake = RecordingGateway(acceptedIds = setOf(original.requestId))

        val result = CourseSelectionExecutor(fake).execute(jobWithCourses(original, duplicate))

        assertEquals(listOf(original.requestId), fake.submittedIds)
        assertEquals(1, result.results.size)
        assertEquals(CourseSelectionJobStatus.COMPLETED, result.status)
    }

    @Test
    fun `different request ids sharing one course id are each submitted`() = runBlocking {
        val first = course(1)
        val alternative = first.copy(
            requestId = "request-2",
            taskId = "task-2",
            teacher = "Alternative Teacher"
        )
        val fake = RecordingGateway(acceptedIds = setOf(first.requestId, alternative.requestId))

        val result = CourseSelectionExecutor(fake).execute(jobWithCourses(first, alternative))

        assertEquals(setOf(first.requestId, alternative.requestId), fake.submittedIds.toSet())
        assertEquals(2, result.results.size)
        assertEquals(CourseSelectionJobStatus.COMPLETED, result.status)
    }

    @Test
    fun `one runtime submission failure does not cancel sibling courses`() = runBlocking {
        val submitted = Collections.synchronizedList(mutableListOf<String>())
        val gateway = object : ShenzhenCourseSelectionGateway {
            override suspend fun submitOnce(
                job: CourseSelectionJob,
                course: CourseSelectionJobCourse
            ): CourseSelectionCourseResult {
                submitted += course.requestId
                if (course.requestId == "request-1") throw IllegalStateException("broken response")
                return resultFor(course)
            }

            override suspend fun selectedRequestIds(job: CourseSelectionJob): Set<String> =
                setOf("request-2")
        }

        val result = CourseSelectionExecutor(gateway).execute(jobWithCourses(2))

        assertEquals(setOf("request-1", "request-2"), submitted.toSet())
        assertEquals(CourseSelectionCourseStatus.UNKNOWN, result.results.single {
            it.courseId == "course-1"
        }.status)
        assertEquals(CourseSelectionCourseStatus.CONFIRMED, result.results.single {
            it.courseId == "course-2"
        }.status)
    }

    @Test
    fun `confirmation transport failure remains pending confirmation`() = runBlocking {
        val gateway = object : ShenzhenCourseSelectionGateway {
            override suspend fun submitOnce(
                job: CourseSelectionJob,
                course: CourseSelectionJobCourse
            ): CourseSelectionCourseResult = resultFor(course)

            override suspend fun selectedRequestIds(job: CourseSelectionJob): Set<String> =
                throw IOException("confirmation unavailable")
        }

        val result = CourseSelectionExecutor(gateway).execute(jobWithCourses(1))

        assertEquals("NEEDS_CONFIRMATION", result.status.name)
        assertEquals(CourseSelectionCourseStatus.UNCONFIRMED, result.results.single().status)
    }

    @Test
    fun `definitive business failure stays failed when confirmation is unavailable`() = runBlocking {
        val gateway = object : ShenzhenCourseSelectionGateway {
            override suspend fun submitOnce(
                job: CourseSelectionJob,
                course: CourseSelectionJobCourse
            ): CourseSelectionCourseResult = resultFor(
                course,
                CourseSelectionCourseStatus.BUSINESS_FAILURE
            )

            override suspend fun selectedRequestIds(job: CourseSelectionJob): Set<String> =
                throw IOException("confirmation unavailable")
        }

        val result = CourseSelectionExecutor(gateway).execute(jobWithCourses(1))

        assertEquals(CourseSelectionJobStatus.FAILED, result.status)
        assertEquals(CourseSelectionCourseStatus.BUSINESS_FAILURE, result.results.single().status)
    }

    @Test
    fun `more than twenty distinct courses are rejected before network access`() = runBlocking {
        val fake = RecordingGateway()

        val failure = runCatching {
            CourseSelectionExecutor(fake).execute(jobWithCourses(21))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(fake.submittedIds.isEmpty())
        assertEquals(0, fake.confirmCalls)
    }

    @Test
    fun `durable credential scope survives restart but rejects logout and account switch`() {
        val originalScope = 8_135_021L
        val job = jobWithCourses(1).copy(credentialScopeGeneration = originalScope)
        val currentToken = AtomicReference(webToken(originalScope))
        val tokenStore = CourseSelectionExecutionTokenStore(currentToken::get)
        val restartOwner = Any()

        assertSame(currentToken.get(), tokenStore.begin(job, restartOwner))
        assertSame(currentToken.get(), tokenStore.requireToken(job))
        tokenStore.end(job.id, restartOwner)

        currentToken.set(EASToken())
        assertTrue(
            runCatching { tokenStore.begin(job, Any()) }.exceptionOrNull() is
                CourseSelectionCredentialScopeMismatchException
        )

        currentToken.set(webToken(originalScope + 1L))
        assertTrue(
            runCatching { tokenStore.begin(job, Any()) }.exceptionOrNull() is
                CourseSelectionCredentialScopeMismatchException
        )
    }

    @Test
    fun `different account rejects execution and reconfirmation before transport`() = runBlocking {
        val job = jobWithCourses(1).copy(
            status = CourseSelectionJobStatus.FAILED,
            credentialScopeGeneration = 101L,
            results = listOf(resultFor(course(1), CourseSelectionCourseStatus.UNKNOWN))
        )
        val tokenStore = CourseSelectionExecutionTokenStore { webToken(202L) }
        val posts = AtomicInteger(0)
        val queries = AtomicInteger(0)
        val gateway = object : ShenzhenCourseSelectionGateway {
            override suspend fun beginExecution(job: CourseSelectionJob, owner: Any) {
                tokenStore.begin(job, owner)
            }

            override suspend fun endExecution(job: CourseSelectionJob, owner: Any) {
                tokenStore.end(job.id, owner)
            }

            override suspend fun submitOnce(
                job: CourseSelectionJob,
                course: CourseSelectionJobCourse
            ): CourseSelectionCourseResult {
                posts.incrementAndGet()
                return resultFor(course)
            }

            override suspend fun selectedRequestIds(job: CourseSelectionJob): Set<String> {
                queries.incrementAndGet()
                return emptySet()
            }
        }
        val executor = CourseSelectionExecutor(gateway)

        assertTrue(
            runCatching { executor.execute(job.copy(status = CourseSelectionJobStatus.RUNNING)) }
                .exceptionOrNull() is CourseSelectionCredentialScopeMismatchException
        )
        assertTrue(
            runCatching { executor.confirm(job) }.exceptionOrNull() is
                CourseSelectionCredentialScopeMismatchException
        )
        assertEquals(0, posts.get())
        assertEquals(0, queries.get())

        val terminal = CourseSelectionCredentialScopePolicy.terminalize(job)
        assertEquals(CourseSelectionJobStatus.FAILED, terminal.status)
        assertEquals("任务属于其他账号，未发送任何请求。", terminal.message)
    }

    @Test
    fun `confirm performs one selected course query and no submissions`() = runBlocking {
        val fake = RecordingGateway(acceptedIds = setOf("request-1"))
        val submitted = jobWithCourses(1).copy(
            status = CourseSelectionJobStatus.RUNNING,
            results = listOf(resultFor(course(1)))
        )

        val confirmed = CourseSelectionExecutor(fake, nowMillis = { 2_000L }).confirm(submitted)

        assertTrue(fake.submittedIds.isEmpty())
        assertEquals(1, fake.confirmCalls)
        assertEquals(CourseSelectionCourseStatus.CONFIRMED, confirmed.results.single().status)
        assertEquals(2_000L, confirmed.results.single().confirmedAtMillis)
        assertEquals(CourseSelectionJobStatus.COMPLETED, confirmed.status)
    }

    @Test
    fun `confirmation accepts a selected task identity variant`() = runBlocking {
        val fake = RecordingGateway(acceptedIds = setOf("task-1"))
        val submitted = jobWithCourses(1).copy(results = listOf(resultFor(course(1))))

        val confirmed = CourseSelectionExecutor(fake).confirm(submitted)

        assertEquals(CourseSelectionCourseStatus.CONFIRMED, confirmed.results.single().status)
    }

    @Test
    fun `local successful submission sends exactly one post`() {
        val receivedPosts = AtomicInteger(0)
        LocalSelectionServer(
            mapOf(
                "/Xsxk/addGouwuche" to { request ->
                    request.requirePost(receivedPosts)
                    LocalResponse(200, """{"jg":1,"message":"accepted"}""")
                }
            )
        ).use { server ->
            val result = localWebSource(server).submitShenzhenCourseOnce(webToken(), jobWithCourses(1), course(1))

            assertEquals(1, receivedPosts.get())
            assertEquals(CourseSelectionCourseStatus.UNCONFIRMED, result.status)
        }
    }

    @Test
    fun `local authentication response sends exactly one post`() {
        val receivedPosts = AtomicInteger(0)
        LocalSelectionServer(
            mapOf(
                "/Xsxk/addGouwuche" to { request ->
                    request.requirePost(receivedPosts)
                    LocalResponse(200, "<html><title>统一身份认证</title></html>")
                }
            )
        ).use { server ->
            val result = localWebSource(server).submitShenzhenCourseOnce(webToken(), jobWithCourses(1), course(1))

            assertEquals(1, receivedPosts.get())
            assertEquals(CourseSelectionCourseStatus.AUTH_REQUIRED, result.status)
        }
    }

    @Test
    fun `malicious server message never reaches returned result published job or payload`() {
        val secret = "server-session-secret"
        val malicious = "Cookie\u202f=\u202fSESSION=$secret Authorization: Bearer hidden"
        val receivedPosts = AtomicInteger(0)
        LocalSelectionServer(
            mapOf(
                "/Xsxk/addGouwuche" to { request ->
                    request.requirePost(receivedPosts)
                    LocalResponse(200, """{"jg":-1,"message":"$malicious"}""")
                }
            )
        ).use { server ->
            val result = localWebSource(server)
                .submitShenzhenCourseOnce(webToken(), jobWithCourses(1), course(1))
            var jobsFlowValue = emptyList<CourseSelectionJob>()

            CourseSelectionJobStorePersistence.commitThenPublish(
                snapshot = listOf(jobWithCourses(1).copy(
                    status = CourseSelectionJobStatus.FAILED,
                    message = malicious,
                    results = listOf(result)
                )),
                commit = {},
                publish = { jobsFlowValue = it }
            )
            val encoded = CourseSelectionJobCodec.encode(jobsFlowValue)

            assertEquals("", result.message)
            assertEquals(1, receivedPosts.get())
            assertEquals("", jobsFlowValue.single().message)
            assertEquals("", jobsFlowValue.single().results.single().message)
            assertTrue(result.toString().contains(secret).not())
            assertTrue(jobsFlowValue.toString().contains(secret).not())
            assertTrue(encoded.contains(secret).not())
        }
    }

    @Test
    fun `local timeout sends exactly one post and remains unknown`() {
        val receivedPosts = AtomicInteger(0)
        LocalSelectionServer(
            mapOf(
                "/Xsxk/addGouwuche" to { request ->
                    request.requirePost(receivedPosts)
                    LocalResponse(200, """{"jg":1}""", delayMillis = 1_000L)
                }
            )
        ).use { server ->
            val result = localWebSource(server, timeoutMillis = 100)
                .submitShenzhenCourseOnce(webToken(), jobWithCourses(1), course(1))

            assertEquals(1, receivedPosts.get())
            assertEquals(CourseSelectionCourseStatus.UNKNOWN, result.status)
        }
    }

    @Test
    fun `local 307 redirect never follows with another post`() {
        val originalPosts = AtomicInteger(0)
        val redirectedPosts = AtomicInteger(0)
        LocalSelectionServer(
            mapOf(
                "/Xsxk/addGouwuche" to { request ->
                    request.requirePost(originalPosts)
                    LocalResponse(307, """{"jg":1}""", mapOf("Location" to "/redirect-target"))
                },
                "/redirect-target" to { request ->
                    request.requirePost(redirectedPosts)
                    LocalResponse(200, """{"jg":1}""")
                }
            )
        ).use { server ->
            val result = localWebSource(server).submitShenzhenCourseOnce(webToken(), jobWithCourses(1), course(1))

            assertEquals(1, originalPosts.get())
            assertEquals(0, redirectedPosts.get())
            assertEquals(CourseSelectionCourseStatus.AUTH_REQUIRED, result.status)
        }
    }

    @Test
    fun `transport cancellation is rethrown`() {
        val source = EASWebSource(
            courseSelectionTransport = ShenzhenCourseSelectionTransport {
                throw CancellationException("cancelled")
            }
        )

        try {
            source.submitShenzhenCourseOnce(webToken(), jobWithCourses(1), course(1))
            fail("CancellationException must escape the network boundary")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun `selected rows without raw server identity never confirm`() {
        val receivedQueries = AtomicInteger(0)
        LocalSelectionServer(
            mapOf(
                "/Xsxk/queryYxkc" to { request ->
                    request.requirePost(receivedQueries)
                    LocalResponse(
                        200,
                        """{"yxkcList":[{"kcdm":"COMP1001","kcmc":"Course A"}]}"""
                    )
                }
            )
        ).use { server ->
            val identities = localWebSource(server)
                .getShenzhenSelectedRequestIdsOnce(webToken(), jobWithCourses(1))

            assertEquals(1, receivedQueries.get())
            assertTrue(identities.isEmpty())
        }
    }

    @Test
    fun `malformed selected course response makes confirmation unavailable`() {
        LocalSelectionServer(
            mapOf(
                "/Xsxk/queryYxkc" to { request ->
                    request.requirePost(AtomicInteger())
                    LocalResponse(200, "{}")
                }
            )
        ).use { server ->
            val failure = runCatching {
                localWebSource(server).getShenzhenSelectedRequestIdsOnce(
                    webToken(),
                    jobWithCourses(1)
                )
            }.exceptionOrNull()

            assertTrue("failure=$failure", failure is IOException)
        }
    }

    @Test
    fun `duplicate course code rows expose only their raw identity variants`() {
        val receivedQueries = AtomicInteger(0)
        LocalSelectionServer(
            mapOf(
                "/Xsxk/queryYxkc" to { request ->
                    request.requirePost(receivedQueries)
                    LocalResponse(
                        200,
                        """{
                            "yxkcList":[
                                {"kcdm":"COMP1001","kcmc":"Course A"},
                                {"kcdm":"COMP1001","kcmc":"Course A","id":"RAW-ID-2"},
                                {
                                    "kcdm":"COMP1001","kcmc":"Course A",
                                    "rwid":"RAW-RWID-3","rwh":"RAW-RWH-3",
                                    "selectionRequestId":"RAW-SELECTION-3","taskId":"RAW-TASK-3"
                                }
                            ]
                        }"""
                    )
                }
            )
        ).use { server ->
            val identities = localWebSource(server)
                .getShenzhenSelectedRequestIdsOnce(webToken(), jobWithCourses(1))

            assertEquals(1, receivedQueries.get())
            assertEquals(
                setOf("RAW-ID-2", "RAW-RWID-3", "RAW-RWH-3", "RAW-SELECTION-3", "RAW-TASK-3"),
                identities
            )
            assertTrue(identities.none { it.startsWith("COMP1001-") })
        }
    }

    @Test
    fun `executor brackets submissions and confirmation in one gateway execution`() = runBlocking {
        val fake = RecordingGateway(acceptedIds = setOf("request-1"), requireExecution = true)

        CourseSelectionExecutor(fake).execute(jobWithCourses(1))

        assertEquals(1, fake.beginCalls)
        assertEquals(1, fake.endCalls)
        assertEquals(1, fake.confirmCalls)
    }

    @Test
    fun `one execution token preserves concurrent response cookie deltas`() = runBlocking {
        val requestsArrived = CountDownLatch(2)
        val receivedPosts = AtomicInteger(0)
        LocalSelectionServer(
            mapOf(
                "/Xsxk/addGouwuche" to { request ->
                    request.requirePost(receivedPosts)
                    requestsArrived.countDown()
                    assertTrue(requestsArrived.await(2, TimeUnit.SECONDS))
                    val cookieName = if (request.body.contains("request-1")) "course-one" else "course-two"
                    LocalResponse(
                        200,
                        """{"jg":1}""",
                        mapOf("Set-Cookie" to "$cookieName=received; Path=/")
                    )
                }
            )
        ).use { server ->
            val loads = AtomicInteger(0)
            val tokenStore = CourseSelectionExecutionTokenStore {
                loads.incrementAndGet()
                webToken()
            }
            val job = jobWithCourses(2)
            val owner = Any()
            val sharedToken = tokenStore.begin(job, owner)
            try {
                job.courses.map { selectedCourse ->
                    async(Dispatchers.IO) {
                        localWebSource(server).submitShenzhenCourseOnce(sharedToken, job, selectedCourse)
                    }
                }.awaitAll()

                assertSame(sharedToken, tokenStore.requireToken(job))
                assertEquals(2, loads.get())
                assertEquals(2, receivedPosts.get())
                assertEquals("received", sharedToken.webCookies["course-one"])
                assertEquals("received", sharedToken.webCookies["course-two"])
            } finally {
                tokenStore.end(job.id, owner)
            }
        }
    }

    @Test
    fun `external cancellation still completes execution cleanup`() = runBlocking {
        val fake = RecordingGateway(
            requireExecution = true,
            suspendCleanup = true,
            blockSubmission = true
        )

        val execution = launch {
            CourseSelectionExecutor(fake).execute(jobWithCourses(1))
        }
        fake.submissionStarted.await()
        execution.cancelAndJoin()

        assertEquals(1, fake.beginCalls)
        assertEquals(1, fake.endCalls)
        assertEquals(0, fake.confirmCalls)
    }

    @Test
    fun `cancellation after token insertion allows same job to start again`() = runBlocking {
        val tokenStore = CourseSelectionExecutionTokenStore(::webToken)
        val inserted = CompletableDeferred<Unit>()
        val job = jobWithCourses(1)
        val gateway = object : ShenzhenCourseSelectionGateway {
            override suspend fun beginExecution(
                job: CourseSelectionJob,
                owner: Any
            ) {
                tokenStore.begin(job, owner)
                inserted.complete(Unit)
                awaitCancellation()
            }

            override suspend fun endExecution(
                job: CourseSelectionJob,
                owner: Any
            ) {
                tokenStore.end(job.id, owner)
            }

            override suspend fun submitOnce(
                job: CourseSelectionJob,
                course: CourseSelectionJobCourse
            ): CourseSelectionCourseResult =
                throw AssertionError("Cancellation must prevent submission")

            override suspend fun selectedRequestIds(job: CourseSelectionJob): Set<String> =
                throw AssertionError("Cancellation must prevent confirmation")
        }

        val execution = launch {
            CourseSelectionExecutor(gateway).execute(job)
        }
        inserted.await()
        execution.cancelAndJoin()

        val restartOwner = Any()
        tokenStore.begin(job, restartOwner)
        tokenStore.end(job.id, restartOwner)
    }

    private class RecordingGateway(
        private val acceptedIds: Set<String> = emptySet(),
        private val unknownIds: Set<String> = emptySet(),
        private val trackConcurrency: Boolean = false,
        private val requireExecution: Boolean = false,
        private val suspendCleanup: Boolean = false,
        private val blockSubmission: Boolean = false
    ) : ShenzhenCourseSelectionGateway {
        val submittedIds: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private val activeSubmissions = AtomicInteger(0)
        private val maxSubmissions = AtomicInteger(0)
        private val selectedQueries = AtomicInteger(0)
        private val begins = AtomicInteger(0)
        private val ends = AtomicInteger(0)
        @Volatile private var executionOwner: Any? = null
        val submissionStarted = CompletableDeferred<Unit>()

        val maximumConcurrency: Int
            get() = maxSubmissions.get()

        val confirmCalls: Int
            get() = selectedQueries.get()

        val beginCalls: Int
            get() = begins.get()

        val endCalls: Int
            get() = ends.get()

        override suspend fun beginExecution(
            job: CourseSelectionJob,
            owner: Any
        ) {
            check(executionOwner == null)
            executionOwner = owner
            begins.incrementAndGet()
        }

        override suspend fun endExecution(
            job: CourseSelectionJob,
            owner: Any
        ) {
            if (suspendCleanup) delay(1L)
            check(executionOwner === owner)
            executionOwner = null
            ends.incrementAndGet()
        }

        override suspend fun submitOnce(
            job: CourseSelectionJob,
            course: CourseSelectionJobCourse
        ): CourseSelectionCourseResult {
            if (requireExecution) check(executionOwner != null)
            submittedIds += course.requestId
            if (blockSubmission) {
                submissionStarted.complete(Unit)
                awaitCancellation()
            }
            val active = activeSubmissions.incrementAndGet()
            maxSubmissions.updateAndGet { current -> maxOf(current, active) }
            try {
                if (trackConcurrency) delay(25L)
                return resultFor(
                    course,
                    if (course.requestId in unknownIds) {
                        CourseSelectionCourseStatus.UNKNOWN
                    } else {
                        CourseSelectionCourseStatus.UNCONFIRMED
                    }
                )
            } finally {
                activeSubmissions.decrementAndGet()
            }
        }

        override suspend fun selectedRequestIds(job: CourseSelectionJob): Set<String> {
            if (requireExecution) check(executionOwner != null)
            selectedQueries.incrementAndGet()
            return acceptedIds
        }
    }

    private class SimulatedProcessInterruption : Error()

    companion object {
        private fun localWebSource(
            server: LocalSelectionServer,
            timeoutMillis: Int = 1_000
        ) = EASWebSource(
            courseSelectionHostOverride = server.baseUrl,
            courseSelectionTimeoutMillis = timeoutMillis
        )

        private fun webToken(generation: Long = 1L) = EASToken().apply {
            campus = EASToken.Campus.SHENZHEN
            webCookies["JSESSIONID"] = "local-session"
            webCookies["route"] = "local-route"
            sessionGeneration = generation
        }

        private fun jobWithCourses(count: Int): CourseSelectionJob =
            jobWithCourses(*(1..count).map(::course).toTypedArray())

        private fun jobWithCourses(vararg courses: CourseSelectionJobCourse) = CourseSelectionJob(
            id = "job-1",
            termId = "term-1",
            termYearCode = "2026-2027",
            termCode = "1",
            scheduledAtMillis = 900L,
            createdAtMillis = 800L,
            status = CourseSelectionJobStatus.WAITING,
            courses = courses.toList(),
            credentialScopeGeneration = 1L
        )

        private fun course(index: Int) = CourseSelectionJobCourse(
            requestId = "request-$index",
            taskId = "task-$index",
            courseId = "course-$index",
            courseCode = "COMP$index",
            courseName = "Course $index",
            teacher = "Teacher $index",
            poolCode = "pool"
        )

        private fun resultFor(
            course: CourseSelectionJobCourse,
            status: CourseSelectionCourseStatus = CourseSelectionCourseStatus.UNCONFIRMED
        ) = CourseSelectionCourseResult(
            requestId = course.requestId,
            courseId = course.courseId,
            status = status,
            message = "",
            submittedAtMillis = 900L
        )
    }

    private class LocalSelectionServer(
        private val routes: Map<String, (LocalRequest) -> LocalResponse>
    ) : AutoCloseable {
        private val executor: ExecutorService = Executors.newCachedThreadPool()
        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private val failure = AtomicReference<Throwable?>()
        @Volatile private var running = true

        init {
            executor.execute {
                while (running) {
                    try {
                        val socket = server.accept()
                        executor.execute { handle(socket) }
                    } catch (_: SocketException) {
                        if (running) failure.compareAndSet(null, AssertionError("Local server socket failed"))
                    }
                }
            }
        }

        val baseUrl: String = "http://127.0.0.1:${server.localPort}"

        private fun handle(socket: Socket) {
            try {
                socket.use {
                    val reader = it.getInputStream().bufferedReader(Charsets.UTF_8)
                    val requestLine = reader.readLine() ?: return
                    val parts = requestLine.split(' ')
                    val method = parts.getOrElse(0) { "" }
                    val path = parts.getOrElse(1) { "" }.substringBefore('?')
                    var contentLength = 0
                    while (true) {
                        val header = reader.readLine() ?: break
                        if (header.isEmpty()) break
                        if (header.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = header.substringAfter(':').trim().toIntOrNull() ?: 0
                        }
                    }
                    val body = buildString(contentLength) {
                        repeat(contentLength) {
                            val next = reader.read()
                            if (next >= 0) append(next.toChar())
                        }
                    }
                    val response = routes[path]?.invoke(LocalRequest(method, path, body))
                        ?: LocalResponse(404, "not found")
                    if (response.delayMillis > 0) Thread.sleep(response.delayMillis)
                    writeResponse(it, response)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: java.io.IOException) {
                // Expected when the timeout test closes the client socket before the delayed response.
            } catch (error: Throwable) {
                failure.compareAndSet(null, error)
            }
        }

        private fun writeResponse(socket: Socket, response: LocalResponse) {
            val body = response.body.toByteArray(Charsets.UTF_8)
            val reason = when (response.statusCode) {
                200 -> "OK"
                307 -> "Temporary Redirect"
                404 -> "Not Found"
                else -> "Response"
            }
            val output = socket.getOutputStream()
            output.write("HTTP/1.1 ${response.statusCode} $reason\r\n".toByteArray())
            response.headers.forEach { (name, value) ->
                output.write("$name: $value\r\n".toByteArray())
            }
            output.write("Content-Type: application/json; charset=UTF-8\r\n".toByteArray())
            output.write("Content-Length: ${body.size}\r\n".toByteArray())
            output.write("Connection: close\r\n\r\n".toByteArray())
            output.write(body)
            output.flush()
        }

        override fun close() {
            running = false
            server.close()
            executor.shutdownNow()
            failure.get()?.let { throw AssertionError("Local server handler failed", it) }
        }
    }

    private data class LocalRequest(val method: String, val path: String, val body: String)

    private data class LocalResponse(
        val statusCode: Int,
        val body: String,
        val headers: Map<String, String> = emptyMap(),
        val delayMillis: Long = 0L
    )

    private fun LocalRequest.requirePost(counter: AtomicInteger) {
        assertEquals("POST", method)
        counter.incrementAndGet()
    }
}
