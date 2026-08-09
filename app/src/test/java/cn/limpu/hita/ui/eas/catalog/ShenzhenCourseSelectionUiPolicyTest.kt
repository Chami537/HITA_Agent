package cn.limpu.hita.ui.eas.catalog

import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogSource
import cn.limpu.hita.data.model.eas.CourseSelectionJobStatus
import cn.limpu.hita.data.model.eas.ShenzhenSelectionOpenTime
import cn.limpu.hita.data.model.eas.ShenzhenSelectionOpenTimeSource
import cn.limpu.hita.data.model.eas.ShenzhenSelectionPool
import cn.limpu.hita.data.model.eas.TermItem
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ShenzhenCourseSelectionUiPolicyTest {

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
        assertEquals(
            CourseSelectionScheduleValidation.VALID,
            ShenzhenCourseSelectionUiPolicy.validateSchedule(now = 1_000L, scheduled = 86_401_000L)
        )
        assertEquals(
            CourseSelectionScheduleValidation.TOO_FAR,
            ShenzhenCourseSelectionUiPolicy.validateSchedule(now = 1_000L, scheduled = 86_401_001L)
        )
    }

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

    @Test
    fun `Shenzhen early morning instant maps to the same DatePicker calendar date`() {
        val instantMillis = Instant.parse("2026-08-09T16:30:15Z").toEpochMilli()

        assertEquals(
            Instant.parse("2026-08-10T00:00:00Z").toEpochMilli(),
            ShenzhenCourseSelectionUiPolicy.datePickerUtcDateMillis(instantMillis)
        )
    }

    @Test
    fun `Shenzhen daytime instant round trips through DatePicker UTC date`() {
        val instantMillis = Instant.parse("2026-08-10T07:45:20Z").toEpochMilli()
        val dateMillis = ShenzhenCourseSelectionUiPolicy.datePickerUtcDateMillis(instantMillis)

        assertEquals(
            instantMillis,
            ShenzhenCourseSelectionUiPolicy.combineDatePickerDateAndShenzhenTime(
                dateMillis = dateMillis,
                hour = 15,
                minute = 45,
                second = 20
            )
        )
    }

    @Test
    fun `DatePicker bridge preserves selected seconds`() {
        val dateMillis = Instant.parse("2026-08-10T00:00:00Z").toEpochMilli()

        assertEquals(
            Instant.parse("2026-08-10T01:30:15Z").toEpochMilli(),
            ShenzhenCourseSelectionUiPolicy.combineDatePickerDateAndShenzhenTime(
                dateMillis = dateMillis,
                hour = 9,
                minute = 30,
                second = 15
            )
        )
    }

    @Test
    fun `filter shortcut appears only after a scrollable list leaves the top`() {
        assertFalse(
            ShenzhenCourseSelectionUiPolicy.shouldShowFilterShortcut(
                firstVisibleItemIndex = 0,
                canScrollBackward = false
            )
        )
        assertFalse(
            ShenzhenCourseSelectionUiPolicy.shouldShowFilterShortcut(
                firstVisibleItemIndex = 1,
                canScrollBackward = false
            )
        )
        assertTrue(
            ShenzhenCourseSelectionUiPolicy.shouldShowFilterShortcut(
                firstVisibleItemIndex = 1,
                canScrollBackward = true
            )
        )
    }

    @Test
    fun `selection rejects a blank request id instead of falling back to task or catalog id`() {
        val draft = ShenzhenCourseSelectionDraftState()

        assertFalse(draft.toggle(availableCourse(requestId = "", taskId = "task-id", id = "catalog-id")))
        assertTrue(draft.requestIds().isEmpty())
    }

    @Test
    fun `selected courses follow card order instead of toggle order`() {
        val firstCard = availableCourse(requestId = "request-a", id = "catalog-a")
        val secondCard = availableCourse(requestId = "request-b", id = "catalog-b")
        val draft = ShenzhenCourseSelectionDraftState()

        assertTrue(draft.toggle(secondCard))
        assertTrue(draft.toggle(firstCard))

        assertEquals(
            listOf("request-a", "request-b"),
            draft.selectedCourses(listOf(firstCard, secondCard)).map { it.selectionRequestId }
        )
    }

    @Test
    fun `catalog refresh rebinds selected draft to refreshed course metadata`() {
        val stale = availableCourse(requestId = "request-a", openAt = 10_000L)
        val refreshed = availableCourse(requestId = "request-a", openAt = 20_000L)
        val draft = ShenzhenCourseSelectionDraftState()

        assertTrue(draft.toggle(stale))

        assertSame(refreshed, draft.selectedCourses(listOf(refreshed)).single())
        assertSame(refreshed, draft.selectedCourses(emptyList()).single())
        assertEquals(setOf("request-a"), draft.requestIds())
    }

    @Test
    fun `term or pool change suppresses opening metadata until matching load completes`() {
        val initialQuery = catalogQuery(termCode = "1", poolCode = "pool-a")
        val initialLoading = ShenzhenCourseCatalogOpeningMetadataState().beginQuery(initialQuery)
        assertTrue(initialLoading.suppressOfficialOpenTime)

        val loaded = initialLoading.completeQuery()
        assertFalse(loaded.suppressOfficialOpenTime)
        assertFalse(
            loaded.beginQuery(initialQuery.copy(keyword = "next page filter"))
                .suppressOfficialOpenTime
        )

        val changedTerm = loaded.beginQuery(catalogQuery(termCode = "2", poolCode = "pool-a"))
        assertTrue(changedTerm.suppressOfficialOpenTime)
        assertTrue(
            changedTerm.beginQuery(catalogQuery(termCode = "2", poolCode = "pool-a", keyword = "later"))
                .suppressOfficialOpenTime
        )

        val loadedTerm = changedTerm.completeQuery()
        assertFalse(loadedTerm.suppressOfficialOpenTime)
        assertTrue(
            loadedTerm.beginQuery(catalogQuery(termCode = "2", poolCode = "pool-b"))
                .suppressOfficialOpenTime
        )
    }

    @Test
    fun `selection stops at twenty request ids`() {
        val draft = ShenzhenCourseSelectionDraftState()

        repeat(20) { index -> assertTrue(draft.toggle(availableCourse(requestId = "request-$index"))) }

        assertFalse(draft.toggle(availableCourse(requestId = "request-20")))
        assertEquals(20, draft.requestIds().size)
    }

    @Test
    fun `successful creation clears draft and failed creation keeps it`() {
        val card = availableCourse(requestId = "request-a")
        val draft = ShenzhenCourseSelectionDraftState()
        assertTrue(draft.toggle(card))

        val created = draft.create(listOf(card)) { courses ->
            assertEquals(listOf("request-a"), courses.map { it.selectionRequestId })
            "job-a"
        }

        assertEquals("job-a", created.getOrThrow())
        assertTrue(draft.requestIds().isEmpty())

        assertTrue(draft.toggle(card))
        val failed = draft.create(listOf(card)) { throw IllegalStateException("creation failed") }

        assertTrue(failed.isFailure)
        assertEquals(setOf("request-a"), draft.requestIds())
    }

    @Test
    fun `cancellation routes only a nonblank job id`() {
        val draft = ShenzhenCourseSelectionDraftState()
        var routedJobId: String? = null

        assertTrue(draft.routeCancellation("job-a") { jobId ->
            routedJobId = jobId
            true
        })
        assertEquals("job-a", routedJobId)

        assertFalse(draft.routeCancellation("") { error("blank job id must not route") })
    }

    @Test
    fun `read only confirmation routes terminal jobs without a create action`() {
        val draft = ShenzhenCourseSelectionDraftState()
        var confirmedJobId: String? = null

        assertTrue(draft.routeReadOnlyConfirmation("job-a", CourseSelectionJobStatus.COMPLETED) { jobId ->
            confirmedJobId = jobId
        })
        assertEquals("job-a", confirmedJobId)

        assertFalse(draft.routeReadOnlyConfirmation("job-b", CourseSelectionJobStatus.WAITING) {
            error("waiting job must not route confirmation")
        })
    }

    private fun availableCourse(
        requestId: String = "request-id",
        taskId: String = "task-id",
        id: String = "catalog-id",
        openAt: Long? = null
    ) = course(ShenzhenCourseCatalogSource.AVAILABLE, requestId, taskId, id, openAt)

    private fun schoolCourse() = course(ShenzhenCourseCatalogSource.SCHOOL)

    private fun catalogQuery(
        termCode: String,
        poolCode: String,
        keyword: String = ""
    ) = ShenzhenCourseCatalogQuery(
        source = ShenzhenCourseCatalogSource.AVAILABLE,
        term = TermItem("2026-2027", "2026-2027", termCode, "Term $termCode"),
        pool = ShenzhenSelectionPool(poolCode, poolCode),
        studentType = "1",
        keyword = keyword,
        page = 1
    )

    private fun course(
        source: ShenzhenCourseCatalogSource,
        requestId: String = "request-id",
        taskId: String = "task-id",
        id: String = "catalog-id",
        openAt: Long? = null
    ) = ShenzhenCourseCatalogItem(
        id = id,
        taskId = taskId,
        selectionRequestId = requestId,
        courseCode = "CS101",
        courseName = "Course",
        source = source,
        selectionOpenTime = openAt?.let {
            ShenzhenSelectionOpenTime(
                rawValue = it.toString(),
                epochMillis = it,
                source = ShenzhenSelectionOpenTimeSource.COURSE
            )
        }
    )
}
