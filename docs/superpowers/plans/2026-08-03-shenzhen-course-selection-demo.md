# Shenzhen Course Selection Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add real Shenzhen course submission, exact one-shot scheduling, task monitoring, cancellation, and read-only result confirmation to the existing HITA course catalog.

**Architecture:** A pure selection domain models jobs and per-course outcomes. `EASWebSource` performs exactly one POST per distinct course through a dedicated non-retrying request path, while a coroutine executor caps one job at ten concurrent courses and confirms accepted submissions with one read-only selected-course query. SharedPreferences persists jobs; AlarmManager wakes a non-exported receiver that starts a short-lived foreground service, and the existing catalog ViewModel and Compose screen expose creation and monitoring.

**Tech Stack:** Kotlin 2.2, Android SDK 35/minSdk 26, Hilt, Compose Material 3, coroutines, Jsoup, Gson, SharedPreferences, AlarmManager, foreground services, JUnit 4.

## Global Constraints

- Shenzhen campus only; do not alter Benbu or Weihai behavior.
- Maximum 20 distinct courses per job and maximum 10 concurrent course submissions.
- Each distinct course ID receives exactly one `/Xsxk/addGouwuche` POST per job; no POST retry after timeout, authentication failure, parse failure, or business failure.
- Scheduled time must be at least 500 milliseconds in the future and no more than 24 hours ahead.
- Use `AlarmManager.setExactAndAllowWhileIdle()`; do not silently fall back to WorkManager.
- Never persist Cookie, username, or password in course-selection jobs.
- A read-only selected-course query may confirm an uncertain result but must never trigger another submission.
- Preserve existing untracked `AGENTS.md`, `docs/superpowers/specs/2026-07-31-theme-font-system-design.md`, other user-owned plan files, and `graphify-out/`.
- New user-facing strings go in `app/src/main/res/values/strings.xml`.
- Automated tests must not contact the real teaching system.
- Per repository policy, show each task diff and ask the user before committing; never push without separate approval.

---

### Task 1: Course-selection domain model and policy

**Files:**
- Create: `app/src/main/java/cn/limpu/hita/data/model/eas/ShenzhenCourseSelection.kt`
- Create: `app/src/main/java/cn/limpu/hita/data/repository/CourseSelectionJobPolicy.kt`
- Test: `app/src/test/java/cn/limpu/hita/data/repository/CourseSelectionJobPolicyTest.kt`

**Interfaces:**
- Consumes: `ShenzhenCourseCatalogItem`, `TermItem`, and `ShenzhenSelectionPool`.
- Produces: `CourseSelectionJob`, `CourseSelectionJobCourse`, `CourseSelectionCourseResult`, `CourseSelectionJobStatus`, `CourseSelectionCourseStatus`, and `CourseSelectionJobPolicy` used by all later tasks.

- [ ] **Step 1: Write failing policy tests**

```kotlin
class CourseSelectionJobPolicyTest {
    @Test
    fun `build courses preserves order and removes duplicate request ids`() {
        val first = course(id = "task-a", requestId = "request-a")
        val duplicate = course(id = "task-a-copy", requestId = "request-a")
        val second = course(id = "task-b", requestId = "request-b")

        val result = CourseSelectionJobPolicy.buildCourses(
            listOf(first, duplicate, second),
            ShenzhenSelectionPool("xx-b-b", "限选课程池")
        )

        assertEquals(listOf("request-a", "request-b"), result.map { it.requestId })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `more than twenty distinct courses is rejected`() {
        CourseSelectionJobPolicy.buildCourses(
            (1..21).map { course("task-$it", "request-$it") },
            ShenzhenSelectionPool("xx-b-b", "限选课程池")
        )
    }

    @Test
    fun `same second and course set produces same fingerprint`() {
        val courses = listOf(jobCourse("b"), jobCourse("a"))
        assertEquals(
            CourseSelectionJobPolicy.fingerprint(1_800_000_000_100, courses),
            CourseSelectionJobPolicy.fingerprint(1_800_000_000_900, courses.reversed())
        )
    }
}
```

- [ ] **Step 2: Run the focused test and verify failure**

Run: `gradlew.bat :app:testDebugUnitTest --tests "cn.limpu.hita.data.repository.CourseSelectionJobPolicyTest"`

Expected: compilation failure because the course-selection types do not exist.

- [ ] **Step 3: Add immutable job and result types**

```kotlin
enum class CourseSelectionJobStatus { WAITING, RUNNING, COMPLETED, PARTIAL, FAILED, CANCELLED }
enum class CourseSelectionCourseStatus { CONFIRMED, UNCONFIRMED, BUSINESS_FAILURE, AUTH_REQUIRED, UNKNOWN }

data class CourseSelectionJobCourse(
    val requestId: String,
    val taskId: String,
    val courseId: String,
    val courseCode: String,
    val courseName: String,
    val teacher: String,
    val poolCode: String
)

data class CourseSelectionCourseResult(
    val courseId: String,
    val status: CourseSelectionCourseStatus,
    val message: String,
    val submittedAtMillis: Long,
    val confirmedAtMillis: Long? = null
)

data class CourseSelectionJob(
    val id: String,
    val termId: String,
    val termYearCode: String,
    val termCode: String,
    val scheduledAtMillis: Long,
    val createdAtMillis: Long,
    val status: CourseSelectionJobStatus,
    val courses: List<CourseSelectionJobCourse>,
    val results: List<CourseSelectionCourseResult> = emptyList(),
    val message: String = ""
)
```

Implement `CourseSelectionJobPolicy.buildCourses`, `fingerprint`, `aggregateStatus`, and constants `MAX_COURSES = 20`, `MAX_CONCURRENCY = 10`, `MIN_SCHEDULE_DELAY_MS = 500L`, and `MAX_SCHEDULE_AHEAD_MS = 86_400_000L`. Resolve the request ID as `selectionRequestId.ifBlank { taskId.ifBlank { id } }`.

- [ ] **Step 4: Run the policy tests**

Run: `gradlew.bat :app:testDebugUnitTest --tests "cn.limpu.hita.data.repository.CourseSelectionJobPolicyTest"`

Expected: PASS.

- [ ] **Step 5: Review checkpoint and conditional commit**

Show only the three Task 1 files, request commit approval, then commit after approval:

```powershell
git add app/src/main/java/cn/limpu/hita/data/model/eas/ShenzhenCourseSelection.kt `
        app/src/main/java/cn/limpu/hita/data/repository/CourseSelectionJobPolicy.kt `
        app/src/test/java/cn/limpu/hita/data/repository/CourseSelectionJobPolicyTest.kt
git commit -m "feat: add Shenzhen course selection domain"
```

### Task 2: Single-shot form builder and response classifier

**Files:**
- Create: `app/src/main/java/cn/limpu/hita/data/source/web/eas/ShenzhenCourseSelectionProtocol.kt`
- Test: `app/src/test/java/cn/limpu/hita/data/source/web/eas/ShenzhenCourseSelectionProtocolTest.kt`

**Interfaces:**
- Consumes: `EASToken`, `TermItem`, `CourseSelectionJobCourse`.
- Produces: `ShenzhenCourseSelectionForm.build(...)` and `ShenzhenCourseSelectionResponseParser.parse(...)` for the network source.

- [ ] **Step 1: Write failing protocol tests**

```kotlin
@Test
fun `form carries exact term pool and request id`() {
    val form = ShenzhenCourseSelectionForm.build(
        studentType = "1",
        termYearCode = "2026-2027",
        termCode = "1",
        poolCode = "xx-b-b",
        requestId = "request-123"
    )
    assertEquals("request-123", form["p_id"])
    assertEquals("2026-2027", form["p_xn"])
    assertEquals("1", form["p_xq"])
    assertEquals("2026-2027-1", form["p_xnxq"])
    assertEquals("xx-b-b", form["p_xkfsdm"])
    assertEquals("rwtjzyx", form["p_xktjz"])
}

@Test
fun `jg one is accepted but not yet confirmed`() {
    val result = ShenzhenCourseSelectionResponseParser.parse(
        200,
        "https://jw.hitsz.edu.cn/Xsxk/addGouwuche",
        """{"jg":"1","message":"成功"}"""
    )
    assertEquals(CourseSelectionCourseStatus.UNCONFIRMED, result.status)
}

@Test
fun `authentication html is never accepted`() {
    val result = ShenzhenCourseSelectionResponseParser.parse(
        200,
        "https://jw.hitsz.edu.cn/authentication/require",
        "<html><title>Loading...</title></html>"
    )
    assertEquals(CourseSelectionCourseStatus.AUTH_REQUIRED, result.status)
}
```

Add cases for `jg=-1`, malformed JSON, HTTP 500, and the Chinese authentication guidance JSON already recognized by `ShenzhenWebAuthenticationClassifier`.

- [ ] **Step 2: Run the focused test and verify failure**

Run: `gradlew.bat :app:testDebugUnitTest --tests "cn.limpu.hita.data.source.web.eas.ShenzhenCourseSelectionProtocolTest"`

Expected: compilation failure because the protocol types do not exist.

- [ ] **Step 3: Implement the pure protocol**

`ShenzhenCourseSelectionForm.build` must return all fields currently used by the Class prototype, including `cxsfmt`, `p_pylx`, `mxpylx`, `p_sfsyxkgwc`, `p_xktjz`, term fields, `p_xkfsdm`, `p_id`, conflict flags, page number, and page size. Do not read Android state in this object.

`ShenzhenCourseSelectionResponseParser.parse` must call `ShenzhenWebAuthenticationClassifier.isExpired` before JSON parsing. It returns `UNCONFIRMED` only for string or numeric `jg=1`, `BUSINESS_FAILURE` for `jg=-1`, `AUTH_REQUIRED` for expired responses, and `UNKNOWN` for every other shape.

- [ ] **Step 4: Run protocol tests**

Run: `gradlew.bat :app:testDebugUnitTest --tests "cn.limpu.hita.data.source.web.eas.ShenzhenCourseSelectionProtocolTest"`

Expected: PASS.

- [ ] **Step 5: Review checkpoint and conditional commit**

After user approval:

```powershell
git add app/src/main/java/cn/limpu/hita/data/source/web/eas/ShenzhenCourseSelectionProtocol.kt `
        app/src/test/java/cn/limpu/hita/data/source/web/eas/ShenzhenCourseSelectionProtocolTest.kt
git commit -m "feat: classify Shenzhen selection responses"
```

### Task 3: Non-retrying network gateway and concurrent executor

**Files:**
- Modify: `app/src/main/java/cn/limpu/hita/data/source/web/eas/EASWebSource.kt`
- Modify: `app/src/main/java/cn/limpu/hita/data/repository/EASRepository.kt`
- Create: `app/src/main/java/cn/limpu/hita/data/repository/CourseSelectionExecutor.kt`
- Test: `app/src/test/java/cn/limpu/hita/data/repository/CourseSelectionExecutorTest.kt`

**Interfaces:**
- Consumes: Task 1 models and Task 2 protocol.
- Produces: `ShenzhenCourseSelectionGateway`, repository gateway implementation, and `CourseSelectionExecutor.execute(job)` / `confirm(job)`.

- [ ] **Step 1: Write executor tests with a fake gateway**

```kotlin
@Test
fun `twenty distinct courses are each submitted exactly once`() = runBlocking {
    val fake = RecordingGateway(acceptedIds = (1..20).map { "request-$it" }.toSet())
    val executor = CourseSelectionExecutor(fake, nowMillis = { 1_000L })

    val completed = executor.execute(jobWithCourses(20))

    assertEquals((1..20).map { "request-$it" }.toSet(), fake.submittedIds.toSet())
    assertTrue(fake.submittedIds.groupingBy { it }.eachCount().values.all { it == 1 })
    assertEquals(1, fake.confirmCalls)
    assertEquals(CourseSelectionJobStatus.COMPLETED, completed.status)
}

@Test
fun `executor never exceeds ten concurrent submissions`() = runBlocking {
    val fake = RecordingGateway(trackConcurrency = true)
    CourseSelectionExecutor(fake).execute(jobWithCourses(20))
    assertTrue(fake.maximumConcurrency <= 10)
}

@Test
fun `timeout remains unknown and is not retried`() = runBlocking {
    val fake = RecordingGateway(unknownIds = setOf("request-1"))
    val result = CourseSelectionExecutor(fake).execute(jobWithCourses(1))
    assertEquals(1, fake.submittedIds.count { it == "request-1" })
    assertEquals(CourseSelectionCourseStatus.UNKNOWN, result.results.single().status)
}
```

- [ ] **Step 2: Run executor tests and verify failure**

Run: `gradlew.bat :app:testDebugUnitTest --tests "cn.limpu.hita.data.repository.CourseSelectionExecutorTest"`

Expected: compilation failure because the gateway and executor do not exist.

- [ ] **Step 3: Add the gateway contract and executor**

```kotlin
internal interface ShenzhenCourseSelectionGateway {
    suspend fun submitOnce(job: CourseSelectionJob, course: CourseSelectionJobCourse): CourseSelectionCourseResult
    suspend fun selectedRequestIds(job: CourseSelectionJob): Set<String>
}

internal class CourseSelectionExecutor(
    private val gateway: ShenzhenCourseSelectionGateway,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun execute(job: CourseSelectionJob): CourseSelectionJob
    suspend fun confirm(job: CourseSelectionJob): CourseSelectionJob
}
```

Implement `execute` with `coroutineScope`, `Semaphore(CourseSelectionJobPolicy.MAX_CONCURRENCY)`, and one `async` per distinct course. After all POST calls finish, call `selectedRequestIds` exactly once and promote accepted results to `CONFIRMED` when present. `confirm` performs only the selected-course query.

- [ ] **Step 4: Add dedicated EASWebSource single-shot methods**

Add:

```kotlin
@WorkerThread
fun submitShenzhenCourseOnce(
    token: EASToken,
    job: CourseSelectionJob,
    course: CourseSelectionJobCourse
): CourseSelectionCourseResult

@WorkerThread
fun getShenzhenSelectedRequestIdsOnce(token: EASToken, job: CourseSelectionJob): Set<String>
```

The POST method may perform a GET warmup first, but it must call `.execute()` for `/Xsxk/addGouwuche` exactly once. Build each request with a snapshot copy of the Cookie map. Merge response cookies inside `synchronized(token)`, then invoke the existing token-refresh callback. Do not call `jwFormPost`, because that helper can retry after authentication recovery.

Wrap blocking Jsoup calls in `withContext(Dispatchers.IO)` inside `EASRepository`, read the current token at execution time, reject non-Shenzhen or missing Web sessions, and implement `ShenzhenCourseSelectionGateway`.

`getShenzhenSelectedRequestIdsOnce` must return every non-blank identity available on each selected item (`selectionRequestId`, `taskId`, and `id`) so confirmation does not depend on one response-field variant.

- [ ] **Step 5: Run executor and protocol tests**

Run: `gradlew.bat :app:testDebugUnitTest --tests "cn.limpu.hita.data.repository.CourseSelectionExecutorTest" --tests "cn.limpu.hita.data.source.web.eas.ShenzhenCourseSelectionProtocolTest"`

Expected: PASS.

- [ ] **Step 6: Review checkpoint and conditional commit**

After user approval:

```powershell
git add app/src/main/java/cn/limpu/hita/data/source/web/eas/EASWebSource.kt `
        app/src/main/java/cn/limpu/hita/data/repository/EASRepository.kt `
        app/src/main/java/cn/limpu/hita/data/repository/CourseSelectionExecutor.kt `
        app/src/test/java/cn/limpu/hita/data/repository/CourseSelectionExecutorTest.kt
git commit -m "feat: submit Shenzhen courses once"
```

### Task 4: Persistent job store and recovery policy

**Files:**
- Create: `app/src/main/java/cn/limpu/hita/data/repository/CourseSelectionJobStore.kt`
- Test: `app/src/test/java/cn/limpu/hita/data/repository/CourseSelectionJobStorePolicyTest.kt`

**Interfaces:**
- Consumes: Task 1 models.
- Produces: `CourseSelectionJobStore.jobs`, `get`, `create`, `update`, `cancel`, `waitingJobs`, and `recoverInterrupted`; pure `CourseSelectionJobStorePolicy` for testable pruning and recovery.

- [ ] **Step 1: Write store-policy tests**

```kotlin
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
    val jobs = listOf(job(status = CourseSelectionJobStatus.WAITING)) +
        (1..25).map { job(id = "done-$it", createdAt = it.toLong(), status = CourseSelectionJobStatus.COMPLETED) }
    val pruned = CourseSelectionJobStorePolicy.prune(jobs)
    assertEquals(21, pruned.size)
    assertTrue(pruned.any { it.status == CourseSelectionJobStatus.WAITING })
}

@Test
fun `job payload round trips without credentials`() {
    val encoded = CourseSelectionJobCodec.encode(listOf(job(status = CourseSelectionJobStatus.WAITING)))
    assertFalse(encoded.contains("SESSION="))
    assertEquals(1, CourseSelectionJobCodec.decode(encoded).size)
}

@Test(expected = IllegalArgumentException::class)
fun `duplicate active fingerprint is rejected`() {
    CourseSelectionJobStorePolicy.requireUnique(newJob, listOf(existingEquivalentWaitingJob))
}
```

- [ ] **Step 2: Run the focused test and verify failure**

Run: `gradlew.bat :app:testDebugUnitTest --tests "cn.limpu.hita.data.repository.CourseSelectionJobStorePolicyTest"`

Expected: compilation failure because the store policy does not exist.

- [ ] **Step 3: Implement codec, policy, and synchronized store**

Use private SharedPreferences name `shenzhen_course_selection_jobs`, key `payload_v1`, Gson payload version `1`, and a process-local lock. Put JSON serialization in pure `CourseSelectionJobCodec.encode/decode` functions so the round-trip test does not require Android. Expose a `StateFlow<List<CourseSelectionJob>>` initialized from disk. Every mutation writes the complete pruned snapshot before publishing it.

Reject active duplicate fingerprints in `create`. `cancel` only changes `WAITING` jobs. `recoverInterrupted` converts `RUNNING` to `FAILED` without scheduling a new POST.

- [ ] **Step 4: Run store-policy tests**

Run: `gradlew.bat :app:testDebugUnitTest --tests "cn.limpu.hita.data.repository.CourseSelectionJobStorePolicyTest"`

Expected: PASS.

- [ ] **Step 5: Review checkpoint and conditional commit**

After user approval:

```powershell
git add app/src/main/java/cn/limpu/hita/data/repository/CourseSelectionJobStore.kt `
        app/src/test/java/cn/limpu/hita/data/repository/CourseSelectionJobStorePolicyTest.kt
git commit -m "feat: persist course selection jobs"
```

### Task 5: Alarm scheduling, service execution, notifications, and reboot recovery

**Files:**
- Create: `app/src/main/java/cn/limpu/hita/data/work/CourseSelectionAlarmPolicy.kt`
- Create: `app/src/main/java/cn/limpu/hita/data/work/CourseSelectionAlarmScheduler.kt`
- Create: `app/src/main/java/cn/limpu/hita/data/work/CourseSelectionAlarmReceiver.kt`
- Create: `app/src/main/java/cn/limpu/hita/data/work/CourseSelectionBootReceiver.kt`
- Create: `app/src/main/java/cn/limpu/hita/data/work/CourseSelectionForegroundService.kt`
- Create: `app/src/main/java/cn/limpu/hita/data/repository/CourseSelectionJobCoordinator.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/cn/limpu/hita/data/work/CourseSelectionAlarmPolicyTest.kt`

**Interfaces:**
- Consumes: job store, executor, and Task 1 timing limits.
- Produces: exact schedule/cancel, service action `ACTION_EXECUTE_JOB`, boot recovery, foreground notifications, and coordinator operations used by the ViewModel.

- [ ] **Step 1: Write alarm-policy tests**

```kotlin
@Test
fun `future waiting job is rescheduled after boot`() {
    assertEquals(
        AlarmRecoveryAction.RESCHEDULE,
        CourseSelectionAlarmPolicy.recoveryAction(waitingJob(at = 10_000L), nowMillis = 5_000L)
    )
}

@Test
fun `expired waiting job is failed and never replayed`() {
    assertEquals(
        AlarmRecoveryAction.EXPIRE,
        CourseSelectionAlarmPolicy.recoveryAction(waitingJob(at = 4_999L), nowMillis = 5_000L)
    )
}

@Test
fun `request code is stable for the same job id`() {
    assertEquals(
        CourseSelectionAlarmPolicy.requestCode("job-1"),
        CourseSelectionAlarmPolicy.requestCode("job-1")
    )
}
```

- [ ] **Step 2: Run the focused test and verify failure**

Run: `gradlew.bat :app:testDebugUnitTest --tests "cn.limpu.hita.data.work.CourseSelectionAlarmPolicyTest"`

Expected: compilation failure because the alarm policy does not exist.

- [ ] **Step 3: Implement scheduler and coordinator**

`CourseSelectionAlarmScheduler` is an injected singleton using application context. Implement:

```kotlin
fun canScheduleExactAlarms(): Boolean
fun schedule(job: CourseSelectionJob)
fun cancel(jobId: String)
fun exactAlarmSettingsIntent(): Intent
```

Use `AlarmManager.RTC_WAKEUP`, `setExactAndAllowWhileIdle`, and an immutable update-current PendingIntent containing only `jobId`. Reject unsupported permissions rather than enqueuing approximate work.

`CourseSelectionJobCoordinator` owns a `Mutex` so only one job executes at a time. It must atomically change `WAITING -> RUNNING`, call `CourseSelectionExecutor.execute`, persist the terminal result, and ignore duplicate service starts for non-waiting jobs. Its exact public surface is:

```kotlin
val jobs: StateFlow<List<CourseSelectionJob>>
fun createImmediate(term: TermItem, pool: ShenzhenSelectionPool, courses: List<ShenzhenCourseCatalogItem>): CourseSelectionJob
fun createScheduled(term: TermItem, pool: ShenzhenSelectionPool, courses: List<ShenzhenCourseCatalogItem>, scheduledAtMillis: Long): CourseSelectionJob
fun cancel(jobId: String): Boolean
suspend fun execute(jobId: String)
suspend fun confirm(jobId: String)
```

`createImmediate` persists a `WAITING` job and starts `CourseSelectionForegroundService` through application context. `createScheduled` validates the 500 millisecond/24 hour window, persists the job, then calls the exact scheduler. `cancel` updates the store and cancels its PendingIntent.

- [ ] **Step 4: Implement Android components and manifest declarations**

The alarm receiver calls `ContextCompat.startForegroundService` with `ACTION_EXECUTE_JOB` and the job ID. The Hilt-enabled service calls `startForeground` immediately, executes through an IO coroutine scope, updates the notification, and calls `stopSelf` in `finally`.

The boot receiver handles `Intent.ACTION_BOOT_COMPLETED`, runs store recovery asynchronously, reschedules only future `WAITING` jobs, and marks expired jobs failed without executing them.

Add manifest permissions:

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

Declare the alarm receiver, boot receiver, and `dataSync` foreground service with `android:exported="false"`. Give BootReceiver only the system `BOOT_COMPLETED` intent filter. Reuse the existing `POST_NOTIFICATIONS` permission.

- [ ] **Step 5: Add notification strings and run tests**

Add channel name, waiting/running/completed/failed messages, exact-alarm permission explanation, and unknown-result recovery text to `strings.xml`.

Run: `gradlew.bat :app:testDebugUnitTest --tests "cn.limpu.hita.data.work.CourseSelectionAlarmPolicyTest"`

Expected: PASS.

- [ ] **Step 6: Review checkpoint and conditional commit**

After user approval:

```powershell
git add app/src/main/java/cn/limpu/hita/data/work/CourseSelectionAlarmPolicy.kt `
        app/src/main/java/cn/limpu/hita/data/work/CourseSelectionAlarmScheduler.kt `
        app/src/main/java/cn/limpu/hita/data/work/CourseSelectionAlarmReceiver.kt `
        app/src/main/java/cn/limpu/hita/data/work/CourseSelectionBootReceiver.kt `
        app/src/main/java/cn/limpu/hita/data/work/CourseSelectionForegroundService.kt `
        app/src/main/java/cn/limpu/hita/data/repository/CourseSelectionJobCoordinator.kt `
        app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml `
        app/src/test/java/cn/limpu/hita/data/work/CourseSelectionAlarmPolicyTest.kt
git commit -m "feat: schedule exact course selection jobs"
```

### Task 6: ViewModel selection draft and task commands

**Files:**
- Modify: `app/src/main/java/cn/limpu/hita/ui/eas/catalog/ShenzhenCourseCatalogViewModel.kt`
- Test: `app/src/test/java/cn/limpu/hita/ui/eas/catalog/ShenzhenCourseSelectionUiPolicyTest.kt`

**Interfaces:**
- Consumes: `CourseSelectionJobCoordinator`, store jobs flow, current `TermItem`, selected pool, and catalog courses.
- Produces: selected request IDs, selected course list, active/recent jobs LiveData, permission-independent command validation, and methods used by the Activity.

- [ ] **Step 1: Write pure UI-policy tests**

```kotlin
@Test
fun `selection actions only appear for available source`() {
    assertTrue(ShenzhenCourseSelectionUiPolicy.canSelect(availableCourse()))
    assertFalse(ShenzhenCourseSelectionUiPolicy.canSelect(schoolCourse()))
}

@Test
fun `schedule validation preserves five hundred millisecond and twenty four hour limits`() {
    assertEquals(
        CourseSelectionScheduleValidation.TOO_SOON,
        ShenzhenCourseSelectionUiPolicy.validateSchedule(now = 1_000L, scheduled = 1_499L)
    )
    assertEquals(
        CourseSelectionScheduleValidation.VALID,
        ShenzhenCourseSelectionUiPolicy.validateSchedule(now = 1_000L, scheduled = 1_500L)
    )
}
```

- [ ] **Step 2: Run the focused test and verify failure**

Run: `gradlew.bat :app:testDebugUnitTest --tests "cn.limpu.hita.ui.eas.catalog.ShenzhenCourseSelectionUiPolicyTest"`

Expected: compilation failure because the UI policy does not exist.

- [ ] **Step 3: Add ViewModel state and commands**

Inject `CourseSelectionJobCoordinator`. Add `selectedForSubmissionLiveData`, `selectionJobsLiveData`, and one-shot error/event state. Implement:

```kotlin
fun toggleCourseForSubmission(course: ShenzhenCourseCatalogItem): Boolean
fun clearSubmissionDraft()
fun createImmediateSelectionJob(): CourseSelectionCommandResult
fun createScheduledSelectionJob(scheduledAtMillis: Long): CourseSelectionCommandResult
fun cancelSelectionJob(jobId: String): Boolean
fun confirmSelectionJob(jobId: String): Boolean
```

Course selection must use request IDs and preserve card order. Creating a job captures the selected term and pool, clears the draft only after successful job creation, and never calls Android permission APIs from the ViewModel.

- [ ] **Step 4: Run UI policy and ViewModel-adjacent unit tests**

Run: `gradlew.bat :app:testDebugUnitTest --tests "cn.limpu.hita.ui.eas.catalog.ShenzhenCourseSelectionUiPolicyTest"`

Expected: PASS.

- [ ] **Step 5: Review checkpoint and conditional commit**

After user approval:

```powershell
git add app/src/main/java/cn/limpu/hita/ui/eas/catalog/ShenzhenCourseCatalogViewModel.kt `
        app/src/test/java/cn/limpu/hita/ui/eas/catalog/ShenzhenCourseSelectionUiPolicyTest.kt
git commit -m "feat: manage Shenzhen selection jobs"
```

### Task 7: Compose catalog controls and monitoring UI

**Files:**
- Modify: `app/src/main/java/cn/limpu/hita/ui/eas/catalog/ShenzhenCourseCatalogActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: Task 6 ViewModel state and commands plus Task 5 exact-alarm capability/settings intent.
- Produces: selectable cards, bottom action bar, confirmation dialog, date/time picker, permission guidance, job monitor, cancellation, and read-only reconfirmation.

- [ ] **Step 1: Wire observable state without changing existing catalog filters**

Observe selected request IDs and jobs beside the existing course, term, source, draft, follow, and attachment state. Extend `ShenzhenCourseCatalogScreen` parameters with explicit callbacks rather than passing the ViewModel into child composables.

- [ ] **Step 2: Add selection controls to available course cards**

Extend `CourseCatalogCard` with:

```kotlin
selectable: Boolean,
selectedForSubmission: Boolean,
onToggleSubmission: () -> Unit
```

Show the control only for `ShenzhenCourseCatalogSource.AVAILABLE`. Keep existing course detail, follow, conflict, attachment, and planning actions unchanged.

- [ ] **Step 3: Add immediate and scheduled confirmation flows**

The bottom action bar displays selected count and buttons for immediate and scheduled submission. Before immediate submission, show course names and the real-side-effect warning. Before scheduling, check notification permission and `canScheduleExactAlarms`; open the system settings intent when exact alarms are unavailable.

Use Material date/time pickers and preserve seconds with an explicit seconds field or picker control. Validate through `ShenzhenCourseSelectionUiPolicy` before creating a job.

- [ ] **Step 4: Add job monitor and task actions**

Display active jobs first and the latest terminal jobs afterward. Each row shows schedule time, status, course count, and per-course result summary. Show cancel only for `WAITING`; show “重新查询结果” only when any result is `UNCONFIRMED` or `UNKNOWN`. Reconfirmation invokes only `confirmSelectionJob`.

- [ ] **Step 5: Add all UI strings and compile**

Run: `gradlew.bat :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL with no Kotlin compilation errors.

- [ ] **Step 6: Review checkpoint and conditional commit**

After user approval:

```powershell
git add app/src/main/java/cn/limpu/hita/ui/eas/catalog/ShenzhenCourseCatalogActivity.kt `
        app/src/main/res/values/strings.xml
git commit -m "feat: add Shenzhen selection controls"
```

### Task 8: Full verification and safe handoff

**Files:**
- Modify only if verification finds scoped defects in files already listed above.

**Interfaces:**
- Consumes: complete feature.
- Produces: verified Debug APK and evidence that no automated test reached production.

- [ ] **Step 1: Run all app unit tests**

Run: `gradlew.bat :app:testDebugUnitTest`

Expected: BUILD SUCCESSFUL and all tests pass.

- [ ] **Step 2: Build the Debug APK**

Run: `gradlew.bat :app:assembleDebug`

Expected: BUILD SUCCESSFUL and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 3: Inspect the scoped diff and manifest**

Run:

```powershell
git diff --check
git status --short
git diff --stat
rg -n "SCHEDULE_EXACT_ALARM|RECEIVE_BOOT_COMPLETED|FOREGROUND_SERVICE_DATA_SYNC" app/src/main/AndroidManifest.xml
rg -n "retry|repeat|while|for \(" app/src/main/java/cn/limpu/hita/data/source/web/eas/ShenzhenCourseSelectionProtocol.kt app/src/main/java/cn/limpu/hita/data/repository/CourseSelectionExecutor.kt
```

Confirm the gateway has exactly one POST execution path per course and no production URL appears in test fakes.

- [ ] **Step 4: Perform a local lifecycle smoke test without submitting**

Install or launch the Debug APK only if a device/emulator is available. Verify the catalog loads, courses can be selected, permission guidance opens, a future task can be created and cancelled before execution, and reboot recovery logic can be inspected without allowing an alarm to fire. Do not trigger a real POST during automated verification.

- [ ] **Step 5: Present final diff and request commit approval**

Summarize changed files, unit/build results, APK path, and remaining manual real-course test. Only after explicit approval, commit any remaining uncommitted implementation changes with a conventional commit message. Do not push.
