# Shenzhen Selection Opening-Time Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Synchronize Shenzhen's official selection opening time, show it in the available-course browser, and prefill scheduled submission while still requiring manual confirmation.

**Architecture:** Parse the known `ktxkkssj`/`ksrq` fields into a typed opening-time value with deterministic course → page/pool → selection-rule precedence. Carry the resolved metadata through the existing catalog page and compute a pure scheduling suggestion for the Compose UI. Reuse the current read-only refresh, permission, AlarmManager, confirmation, and one-shot submission paths without adding an endpoint or persisting mutable official metadata into jobs.

**Tech Stack:** Kotlin, Gson, `java.time`, Android LiveData/ViewModel, Jetpack Compose Material 3, JUnit 4, Gradle.

## Global Constraints

- Do not change the existing 500 ms minimum schedule delay or 24-hour maximum (`500L` and `86_400_000L`).
- Do not change concurrency, retry, AlarmManager, notification, confirmation, or one-shot POST behavior.
- Do not automatically create, confirm, reschedule, or submit a selection job.
- Do not use `xksj` as an opening time; it remains an already-selected action timestamp.
- Do not add a speculative network endpoint. Parse only responses HITA already requests.
- Opening-time absence or malformed values must not fail course browsing.
- Existing jobs retain their user-confirmed `scheduledAtMillis` and are never mutated by later metadata refreshes.
- Whole-school read-only catalog UI must not show Shenzhen selection opening time.
- No real course-selection submission during tests.
- Preserve all pre-existing dirty-worktree changes. The term-list fix overlaps files in this plan; use selective staging and verify every task commit excludes old term-list hunks.

---

### Task 1: Model and parse official opening time

**Files:**

- Modify: `app/src/main/java/cn/limpu/hita/data/model/eas/ShenzhenCourseCatalog.kt`
- Modify: `app/src/main/java/cn/limpu/hita/data/source/web/eas/ShenzhenCourseCatalogParser.kt`
- Test: `app/src/test/java/cn/limpu/hita/data/source/web/eas/ShenzhenCourseCatalogParserTest.kt`

**Interfaces:**

- Produces:

```kotlin
enum class ShenzhenSelectionOpenTimeSource {
    COURSE,
    POOL_OR_PAGE,
    SELECTION_RULE
}

data class ShenzhenSelectionOpenTime(
    val rawValue: String,
    val epochMillis: Long,
    val source: ShenzhenSelectionOpenTimeSource
)
```

- Extends `ShenzhenCourseCatalogItem` with:

```kotlin
val selectionOpenTime: ShenzhenSelectionOpenTime? = null
```

- Extends `ShenzhenCourseCatalogPage` with:

```kotlin
val selectionOpenTime: ShenzhenSelectionOpenTime? = null
```

- Extends `ShenzhenSelectionPool` with:

```kotlin
val selectionOpenTime: ShenzhenSelectionOpenTime? = null
```

- Produces parser interfaces:

```kotlin
fun parseSelectionOpenTime(body: String): ShenzhenSelectionOpenTime?

fun parsePage(
    body: String,
    source: ShenzhenCourseCatalogSource,
    studentType: String,
    selectionPoolName: String = "",
    fallbackOpenTime: ShenzhenSelectionOpenTime? = null
): ShenzhenCourseCatalogPage?
```

- [ ] **Step 1: Add failing parser tests for field aliases, formats, and precedence**

Add focused tests with these exact expectations:

```kotlin
@Test
fun `selection opening time parses zoned and Shenzhen local values`() {
    val zoned = ShenzhenCourseCatalogParser.parseSelectionOpenTime(
        """{"ktxkkssj":"2026-08-10T09:30:15+08:00"}"""
    )
    val local = ShenzhenCourseCatalogParser.parseSelectionOpenTime(
        """{"ksrq":"2026-08-10 09:30:15"}"""
    )

    assertEquals(1_786_325_415_000L, zoned?.epochMillis)
    assertEquals(1_786_325_415_000L, local?.epochMillis)
    assertEquals("2026-08-10T09:30:15+08:00", zoned?.rawValue)
}
```

Add alias/malformed coverage:

```kotlin
@Test
fun `selection opening time prefers ktxkkssj and ignores malformed values`() {
    val preferred = ShenzhenCourseCatalogParser.parseSelectionOpenTime(
        """{"ktxkkssj":"2026-08-10 10:00:00","ksrq":"2026-08-10 11:00:00"}"""
    )

    assertEquals("2026-08-10 10:00:00", preferred?.rawValue)
    assertNull(ShenzhenCourseCatalogParser.parseSelectionOpenTime("""{"ksrq":"not-a-time"}"""))
}
```

Add page precedence coverage using one available-course row:

```kotlin
@Test
fun `available page resolves course then page then selection rule opening time`() {
    val coursePage = ShenzhenCourseCatalogParser.parsePage(
        body = """{"ksrq":"2026-08-10 09:00:00","kxrwList":{"list":[{"id":"request","kcdm":"CS101","kcmc":"Course","ktxkkssj":"2026-08-10 08:00:00"}]}}""",
        source = ShenzhenCourseCatalogSource.AVAILABLE,
        studentType = "1"
    )
    assertEquals(ShenzhenSelectionOpenTimeSource.COURSE, coursePage?.items?.single()?.selectionOpenTime?.source)

    val pageFallback = ShenzhenCourseCatalogParser.parsePage(
        body = """{"ktxkkssj":"2026-08-10 09:00:00","kxrwList":{"list":[{"id":"request","kcdm":"CS101","kcmc":"Course"}]}}""",
        source = ShenzhenCourseCatalogSource.AVAILABLE,
        studentType = "1"
    )
    assertEquals(ShenzhenSelectionOpenTimeSource.POOL_OR_PAGE, pageFallback?.items?.single()?.selectionOpenTime?.source)

    val ruleFallback = ShenzhenCourseCatalogParser.parsePage(
        body = """{"xkgzszOne":{"ksrq":"2026-08-10 10:00:00"},"kxrwList":{"list":[{"id":"request","kcdm":"CS101","kcmc":"Course"}]}}""",
        source = ShenzhenCourseCatalogSource.AVAILABLE,
        studentType = "1"
    )
    assertEquals(ShenzhenSelectionOpenTimeSource.SELECTION_RULE, ruleFallback?.items?.single()?.selectionOpenTime?.source)
}
```

Add explicit regression coverage:

```kotlin
@Test
fun `xksj remains excluded from selection opening time`() {
    val page = ShenzhenCourseCatalogParser.parsePage(
        body = """{"kxrwList":{"list":[{"id":"request","kcdm":"CS101","kcmc":"Course","xksj":"2026-08-10 10:00:00"}]}}""",
        source = ShenzhenCourseCatalogSource.AVAILABLE,
        studentType = "1"
    )

    assertNull(page?.items?.single()?.selectionOpenTime)
}
```

- [ ] **Step 2: Run the parser tests to prove RED**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "cn.limpu.hita.data.source.web.eas.ShenzhenCourseCatalogParserTest" --console=plain
```

Expected: compilation fails because the opening-time types, properties, and parser interfaces do not exist.

- [ ] **Step 3: Add the typed model fields**

Add the enum/data class exactly as defined in Interfaces. Add nullable properties with defaults so existing constructors and fixtures remain source-compatible.

- [ ] **Step 4: Implement deterministic timestamp parsing**

In `ShenzhenCourseCatalogParser`, add:

```kotlin
private val SHENZHEN_ZONE = ZoneId.of("Asia/Shanghai")

private fun parseOpenTime(
    row: JsonObject?,
    source: ShenzhenSelectionOpenTimeSource
): ShenzhenSelectionOpenTime? {
    if (row == null) return null
    val raw = first(row, "ktxkkssj", "KTXKKSSJ", "ksrq", "KSRQ")
    if (raw.isBlank()) return null
    val normalized = raw.replace(' ', 'T')
    val epochMillis = runCatching {
        OffsetDateTime.parse(normalized).toInstant().toEpochMilli()
    }.recoverCatching {
        LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(SHENZHEN_ZONE)
            .toInstant()
            .toEpochMilli()
    }.getOrNull() ?: return null
    return ShenzhenSelectionOpenTime(raw, epochMillis, source)
}
```

Import `LocalDateTime`, `OffsetDateTime`, `ZoneId`, and `DateTimeFormatter` from `java.time`.

Implement `parseSelectionOpenTime(body)` so it checks the parsed payload/root as `POOL_OR_PAGE` first, then nested `xkgzszOne` as `SELECTION_RULE`. Support root, `content`, or `data` wrapper objects without recursively scanning course arrays.

- [ ] **Step 5: Resolve page and course precedence**

In `parsePage`, calculate context time in this order:

```kotlin
val contextOpenTime =
    parseOpenTime(pageObject, ShenzhenSelectionOpenTimeSource.POOL_OR_PAGE)
        ?: parseOpenTime(root, ShenzhenSelectionOpenTimeSource.POOL_OR_PAGE)
        ?: parseOpenTime(
            pageObject.get("xkgzszOne")?.takeIf { it.isJsonObject }?.asJsonObject,
            ShenzhenSelectionOpenTimeSource.SELECTION_RULE
        )
        ?: parseOpenTime(
            root.get("xkgzszOne")?.takeIf { it.isJsonObject }?.asJsonObject,
            ShenzhenSelectionOpenTimeSource.SELECTION_RULE
        )
        ?: fallbackOpenTime
```

For each course item set:

```kotlin
selectionOpenTime =
    parseOpenTime(row, ShenzhenSelectionOpenTimeSource.COURSE) ?: contextOpenTime
```

Set `ShenzhenCourseCatalogPage.selectionOpenTime = contextOpenTime`.

In `parseSelectionPools`, populate each pool's nullable opening time from the row's direct fields and then a nested `xkgzszOne` fallback when present.

- [ ] **Step 6: Run focused parser tests to prove GREEN**

Run the Step 2 command. Expected: all `ShenzhenCourseCatalogParserTest` tests pass.

- [ ] **Step 7: Selectively commit only Task 1 hunks**

Run `git diff --check`, inspect all three files, and stage only opening-time model/parser/test hunks. Confirm the cached diff does not include the pre-existing term-list merge/filter changes. Commit:

```text
feat: parse Shenzhen selection opening time
```

---

### Task 2: Merge initialization metadata into the existing catalog refresh

**Files:**

- Modify: `app/src/main/java/cn/limpu/hita/data/source/web/eas/EASWebSource.kt`
- Test: `app/src/test/java/cn/limpu/hita/data/source/web/eas/ShenzhenCourseCatalogParserTest.kt`

**Interfaces:**

- Consumes `ShenzhenCourseCatalogParser.parseSelectionOpenTime(body: String)` from Task 1.
- Consumes `parsePage(..., fallbackOpenTime: ShenzhenSelectionOpenTime?)` from Task 1.
- Produces no new endpoint and no repository signature change; `queryShenzhenAvailableCourses` still returns `LiveData<DataState<ShenzhenCourseCatalogPage>>`.

- [ ] **Step 1: Add a failing fallback-injection parser test**

Add:

```kotlin
@Test
fun `available page uses initialization opening time when final response omits it`() {
    val initialization = ShenzhenCourseCatalogParser.parseSelectionOpenTime(
        """{"xkgzszOne":{"ktxkkssj":"2026-08-10 09:30:00"}}"""
    )
    val page = ShenzhenCourseCatalogParser.parsePage(
        body = """{"kxrwList":{"list":[{"id":"request","kcdm":"CS101","kcmc":"Course"}]}}""",
        source = ShenzhenCourseCatalogSource.AVAILABLE,
        studentType = "1",
        fallbackOpenTime = initialization
    )

    assertEquals(initialization, page?.selectionOpenTime)
    assertEquals(initialization, page?.items?.single()?.selectionOpenTime)
}
```

- [ ] **Step 2: Run the focused test**

Run the Task 1 focused parser command. Expected: RED if Task 1 did not fully wire `fallbackOpenTime`; otherwise this is a regression lock and must pass before transport wiring.

- [ ] **Step 3: Capture initialization metadata in the existing request path**

In `queryShenzhenCourseCatalog`, declare:

```kotlin
var initializationOpenTime: ShenzhenSelectionOpenTime? = null
```

After the existing AVAILABLE-source `queryYxkc` authentication/status checks, set:

```kotlin
initializationOpenTime =
    ShenzhenCourseCatalogParser.parseSelectionOpenTime(initialization.body())
```

Pass it only to the existing final parser call:

```kotlin
fallbackOpenTime = initializationOpenTime
```

Do not add a request, move authentication checks, change `jwFormPost`, or alter any form/retry parameters.

- [ ] **Step 4: Verify parser and transport compilation**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "cn.limpu.hita.data.source.web.eas.ShenzhenCourseCatalogParserTest" :app:assembleDebug --console=plain
```

Expected: parser tests and Debug build pass.

- [ ] **Step 5: Selectively commit only Task 2 hunks**

Inspect cached `EASWebSource.kt` and parser-test diffs. Confirm term-list and unrelated catalog changes remain unstaged. Commit:

```text
feat: sync Shenzhen selection opening metadata
```

---

### Task 3: Compute the official scheduling suggestion without changing limits

**Files:**

- Modify: `app/src/main/java/cn/limpu/hita/ui/eas/catalog/ShenzhenCourseCatalogViewModel.kt`
- Test: `app/src/test/java/cn/limpu/hita/ui/eas/catalog/ShenzhenCourseSelectionUiPolicyTest.kt`

**Interfaces:**

- Produces:

```kotlin
sealed interface CourseSelectionSchedulePrefill {
    data object Manual : CourseSelectionSchedulePrefill
    data class Official(val scheduledAtMillis: Long) : CourseSelectionSchedulePrefill
    data class TooFar(val officialAtMillis: Long) : CourseSelectionSchedulePrefill
}
```

- Adds to `ShenzhenCourseSelectionUiPolicy`:

```kotlin
fun earliestOfficialOpenTime(
    courses: List<ShenzhenCourseCatalogItem>,
    fallback: ShenzhenSelectionOpenTime? = null
): ShenzhenSelectionOpenTime?

fun schedulePrefill(
    now: Long,
    courses: List<ShenzhenCourseCatalogItem>
): CourseSelectionSchedulePrefill
```

- [ ] **Step 1: Add failing policy tests**

Add test helpers that create AVAILABLE courses with nullable `selectionOpenTime`. Add these cases:

```kotlin
@Test
fun `official schedule prefill uses earliest valid selected course time`() {
    val courses = listOf(
        availableCourse(openAt = 10_000L),
        availableCourse(requestId = "request-b", openAt = 8_000L)
    )

    assertEquals(
        CourseSelectionSchedulePrefill.Official(8_000L),
        ShenzhenCourseSelectionUiPolicy.schedulePrefill(now = 1_000L, courses = courses)
    )
}

@Test
fun `missing or past official time keeps manual scheduling`() {
    assertEquals(
        CourseSelectionSchedulePrefill.Manual,
        ShenzhenCourseSelectionUiPolicy.schedulePrefill(now = 1_000L, courses = emptyList())
    )
    assertEquals(
        CourseSelectionSchedulePrefill.Manual,
        ShenzhenCourseSelectionUiPolicy.schedulePrefill(
            now = 1_000L,
            courses = listOf(availableCourse(openAt = 999L))
        )
    )
}

@Test
fun `official time beyond twenty four hours is reported without changing limit`() {
    assertEquals(
        CourseSelectionSchedulePrefill.TooFar(86_401_001L),
        ShenzhenCourseSelectionUiPolicy.schedulePrefill(
            now = 1_000L,
            courses = listOf(availableCourse(openAt = 86_401_001L))
        )
    )
}
```

The `availableCourse(openAt)` helper creates `ShenzhenSelectionOpenTime` with source `COURSE`; use unique request IDs where multiple courses appear.

- [ ] **Step 2: Run policy tests to prove RED**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "cn.limpu.hita.ui.eas.catalog.ShenzhenCourseSelectionUiPolicyTest" --console=plain
```

Expected: compilation fails because `CourseSelectionSchedulePrefill` and policy methods do not exist.

- [ ] **Step 3: Implement the minimal pure policy**

`earliestOfficialOpenTime` returns the minimum `epochMillis` across course values plus the optional fallback. `schedulePrefill` calls the existing `validateSchedule(now, earliest.epochMillis)`:

```kotlin
return when (validateSchedule(now, earliest.epochMillis)) {
    CourseSelectionScheduleValidation.VALID ->
        CourseSelectionSchedulePrefill.Official(earliest.epochMillis)
    CourseSelectionScheduleValidation.TOO_FAR ->
        CourseSelectionSchedulePrefill.TooFar(earliest.epochMillis)
    CourseSelectionScheduleValidation.TOO_SOON ->
        CourseSelectionSchedulePrefill.Manual
}
```

Do not edit `CourseSelectionJobPolicy` constants.

- [ ] **Step 4: Run policy tests to prove GREEN**

Run the Step 2 command. Expected: all policy tests pass.

- [ ] **Step 5: Commit Task 3**

Run `git diff --check`, stage only the policy and test files, and commit:

```text
feat: derive Shenzhen official schedule prefill
```

---

### Task 4: Display opening status and prefill the existing confirmation flow

**Files:**

- Modify: `app/src/main/java/cn/limpu/hita/ui/eas/catalog/ShenzhenCourseCatalogActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**

- Consumes `page.selectionOpenTime`, `item.selectionOpenTime`, `earliestOfficialOpenTime`, and `schedulePrefill` from Tasks 1 and 3.
- Changes private composable signatures only:

```kotlin
SelectionScheduleDateDialog(
    initialSelectionMillis: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
)

SelectionScheduleTimeDialog(
    dateMillis: Long,
    initialSelectionMillis: Long,
    officialPrefill: Boolean,
    courseCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
)
```

- [ ] **Step 1: Add exact UI copy**

Add these resources:

```xml
<string name="course_selection_official_open_time">官方开放时间 · %1$s</string>
<string name="course_selection_official_open_time_unavailable">官方开放时间 · 暂未同步</string>
<string name="course_selection_official_open_status_future">待开放</string>
<string name="course_selection_official_open_status_open">已开放</string>
<string name="course_selection_official_prefill_note">已按所选课程中最早的官方开放时间预填，请确认后创建任务。</string>
<string name="course_selection_official_time_too_far_title">暂不可创建定时任务</string>
<string name="course_selection_official_time_too_far_message">官方开放时间为 %1$s，距离现在超过24小时。当前仅展示时间，不改变既有定时范围。</string>
```

- [ ] **Step 2: Add opening-time status inside the shared list**

Compute:

```kotlin
val catalogOpenTime = ShenzhenCourseSelectionUiPolicy.earliestOfficialOpenTime(
    courses = page?.items.orEmpty(),
    fallback = page?.selectionOpenTime
)
```

For `AVAILABLE`, insert one list item after `CatalogFilters` and before recommendation:

- Show formatted time plus `待开放` when `epochMillis > System.currentTimeMillis()`.
- Show formatted time plus `已开放` otherwise.
- Show the unavailable string when null.
- Keep it within the existing shared `LazyColumn` so it collapses with filters.
- Do not render it for `SCHOOL`.

- [ ] **Step 3: Centralize opening of the schedule picker**

Add Compose state:

```kotlin
var scheduleInitialMillis by remember { mutableStateOf<Long?>(null) }
var officialPrefillApplied by remember { mutableStateOf(false) }
var officialTimeTooFarMillis by remember { mutableStateOf<Long?>(null) }
```

Create a local lambda that runs only after notification/exact-alarm permission gates:

```kotlin
val openScheduleConfirmation = {
    when (val prefill = ShenzhenCourseSelectionUiPolicy.schedulePrefill(
        now = System.currentTimeMillis(),
        courses = selectedSubmissionCourses
    )) {
        CourseSelectionSchedulePrefill.Manual -> {
            scheduleInitialMillis = defaultSelectionScheduleMillis()
            officialPrefillApplied = false
            showScheduleDatePicker = true
        }
        is CourseSelectionSchedulePrefill.Official -> {
            scheduleInitialMillis = prefill.scheduledAtMillis
            officialPrefillApplied = true
            showScheduleDatePicker = true
        }
        is CourseSelectionSchedulePrefill.TooFar -> {
            officialPrefillApplied = false
            officialTimeTooFarMillis = prefill.officialAtMillis
        }
    }
}
```

Call this lambda from both the direct `onSchedule` success branch and the existing notification-permission `LaunchedEffect` after exact-alarm permission succeeds. Preserve all permission ordering.

- [ ] **Step 4: Prefill both date and time without auto-confirmation**

Define `openScheduleConfirmation` before the existing notification-permission `LaunchedEffect`, then pass `scheduleInitialMillis ?: defaultSelectionScheduleMillis()` into the date dialog and time dialog. Pass `officialPrefillApplied` to `SelectionScheduleTimeDialog`. Initialize hour, minute, and seconds from `initialSelectionMillis`, not `now + 1 minute`.

Display `course_selection_official_prefill_note` only when `officialPrefill` is true. The final button still calls the existing validation and `onCreateScheduledSelection` only after the user confirms.

On date-dialog dismissal, time-dialog dismissal, and successful confirmation, set `scheduleInitialMillis = null` and `officialPrefillApplied = false`. Do not call `onCreateScheduledSelection` from any effect or prefill branch.

- [ ] **Step 5: Show the over-24-hour guidance without opening a picker**

When `officialTimeTooFarMillis` is non-null, show an `AlertDialog` with the exact title/message resources, formatted timestamp, and one dismiss button. Dismissal only clears state. It must not open a picker or create a job.

- [ ] **Step 6: Run focused and full local verification**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "cn.limpu.hita.data.source.web.eas.ShenzhenCourseCatalogParserTest" --tests "cn.limpu.hita.ui.eas.catalog.ShenzhenCourseSelectionUiPolicyTest" --console=plain
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug --rerun-tasks --console=plain
git diff --check
```

Expected: all tests pass, Debug APK builds, and whitespace check is clean.

- [ ] **Step 7: Selectively commit Task 4**

Inspect the cached Activity diff and confirm the pre-existing `TermUtils.courseSelectionTerms(...)` hunk is absent. Commit only UI and string changes:

```text
feat: prefill Shenzhen schedule from official time
```

---

### Task 5: Final review and deferred device verification

- [ ] Generate a whole-change review package from the pre-Task-1 base through Task 4.
- [ ] Have a fresh senior reviewer check spec compliance, timestamp semantics, stale-state behavior, UI confirmation boundaries, and absence of submission/parameter changes.
- [ ] Resolve Critical or Important findings through the reviewed fix loop.
- [ ] Confirm the final working tree still contains every preserved pre-existing term-list change and unrelated untracked file.
- [ ] Record that ADB/device verification is deferred because the phone is disconnected.
- [ ] When ADB is later available, install the Debug APK and verify opening-time display, refresh synchronization, official-time prefill, over-24-hour guidance, and manual fallback without confirming or sending a real selection POST.
