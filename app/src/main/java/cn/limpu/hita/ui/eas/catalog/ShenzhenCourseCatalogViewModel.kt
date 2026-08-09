package cn.limpu.hita.ui.eas.catalog

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.limpu.component.data.DataState
import com.limpu.component.data.Trigger
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogPage
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogSource
import cn.limpu.hita.data.model.eas.ShenzhenCourseAttachment
import cn.limpu.hita.data.model.eas.ShenzhenHistoricalFailureReport
import cn.limpu.hita.data.model.eas.ShenzhenSelectionPool
import cn.limpu.hita.data.model.eas.ShenzhenSelectionPools
import cn.limpu.hita.data.model.eas.ShenzhenSelectionOpenTime
import cn.limpu.hita.data.model.eas.TermItem
import cn.limpu.hita.data.model.eas.CourseSelectionJob
import cn.limpu.hita.data.model.eas.CourseSelectionJobStatus
import cn.limpu.hita.data.repository.CourseSelectionJobCoordinator
import cn.limpu.hita.data.repository.CourseSelectionJobPolicy
import cn.limpu.hita.data.repository.EASRepository
import cn.limpu.hita.data.repository.TimetableRepository
import cn.limpu.hita.data.repository.CourseSelectionDraft
import cn.limpu.hita.data.repository.CoursePlanConflictEngine
import cn.limpu.hita.data.model.timetable.TimePeriodInDay
import cn.limpu.hita.ui.eas.EASViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class ShenzhenCourseCatalogQuery(
    val source: ShenzhenCourseCatalogSource,
    val term: TermItem,
    val pool: ShenzhenSelectionPool,
    val studentType: String,
    val keyword: String,
    val page: Int
)

data class ShenzhenHistoricalFailureRequest(
    val course: ShenzhenCourseCatalogItem,
    val term: TermItem,
    val studentType: String
)

enum class CourseSelectionScheduleValidation {
    VALID,
    TOO_SOON,
    TOO_FAR
}

enum class CourseSelectionCommandFailure {
    NO_TERM,
    NO_POOL,
    NO_COURSES,
    TOO_MANY_COURSES,
    SCHEDULE_TOO_SOON,
    SCHEDULE_TOO_FAR,
    CREATION_FAILED,
    CANNOT_CANCEL,
    CANNOT_CONFIRM
}

sealed interface CourseSelectionCommandResult {
    data class Created(val jobId: String) : CourseSelectionCommandResult
    data class Rejected(val failure: CourseSelectionCommandFailure) : CourseSelectionCommandResult
}

sealed interface CourseSelectionSchedulePrefill {
    data object Manual : CourseSelectionSchedulePrefill
    data class Official(val scheduledAtMillis: Long) : CourseSelectionSchedulePrefill
    data class TooFar(val officialAtMillis: Long) : CourseSelectionSchedulePrefill
}

object ShenzhenCourseSelectionUiPolicy {
    fun canSelect(course: ShenzhenCourseCatalogItem): Boolean =
        course.source == ShenzhenCourseCatalogSource.AVAILABLE

    fun shouldShowFilterShortcut(
        firstVisibleItemIndex: Int,
        canScrollBackward: Boolean
    ): Boolean = firstVisibleItemIndex > 0 && canScrollBackward

    fun validateSchedule(now: Long, scheduled: Long): CourseSelectionScheduleValidation {
        val delay = scheduled - now
        return when {
            delay < CourseSelectionJobPolicy.MIN_SCHEDULE_DELAY_MS ->
                CourseSelectionScheduleValidation.TOO_SOON
            delay > CourseSelectionJobPolicy.MAX_SCHEDULE_AHEAD_MS ->
                CourseSelectionScheduleValidation.TOO_FAR
            else -> CourseSelectionScheduleValidation.VALID
        }
    }

    fun earliestOfficialOpenTime(
        courses: List<ShenzhenCourseCatalogItem>,
        fallback: ShenzhenSelectionOpenTime? = null
    ): ShenzhenSelectionOpenTime? =
        (courses.mapNotNull { it.selectionOpenTime } + listOfNotNull(fallback))
            .minByOrNull { it.epochMillis }

    fun schedulePrefill(
        now: Long,
        courses: List<ShenzhenCourseCatalogItem>
    ): CourseSelectionSchedulePrefill {
        val earliest = earliestOfficialOpenTime(courses) ?: return CourseSelectionSchedulePrefill.Manual
        return when (validateSchedule(now, earliest.epochMillis)) {
            CourseSelectionScheduleValidation.VALID ->
                CourseSelectionSchedulePrefill.Official(earliest.epochMillis)
            CourseSelectionScheduleValidation.TOO_FAR ->
                CourseSelectionSchedulePrefill.TooFar(earliest.epochMillis)
            CourseSelectionScheduleValidation.TOO_SOON ->
                CourseSelectionSchedulePrefill.Manual
        }
    }
}

internal class ShenzhenCourseSelectionDraftState {
    private val selectedCoursesByRequestId = linkedMapOf<String, ShenzhenCourseCatalogItem>()

    fun toggle(course: ShenzhenCourseCatalogItem): Boolean {
        if (!ShenzhenCourseSelectionUiPolicy.canSelect(course)) return false
        val requestId = course.selectionRequestId
        if (requestId.isBlank()) return false
        if (selectedCoursesByRequestId.containsKey(requestId)) {
            selectedCoursesByRequestId.remove(requestId)
            return true
        }
        if (selectedCoursesByRequestId.size >= CourseSelectionJobPolicy.MAX_COURSES) return false
        selectedCoursesByRequestId[requestId] = course
        return true
    }

    fun clear() {
        selectedCoursesByRequestId.clear()
    }

    fun requestIds(): Set<String> = LinkedHashSet(selectedCoursesByRequestId.keys)

    fun selectedCourses(cards: List<ShenzhenCourseCatalogItem>): List<ShenzhenCourseCatalogItem> {
        val cardRequestIds = cards.mapNotNull { course ->
            course.selectionRequestId.takeIf(selectedCoursesByRequestId::containsKey)
        }
        val cardRequestIdSet = cardRequestIds.toSet()
        return (cardRequestIds + selectedCoursesByRequestId.keys.filterNot(cardRequestIdSet::contains))
            .mapNotNull(selectedCoursesByRequestId::get)
    }

    fun <T> create(
        cards: List<ShenzhenCourseCatalogItem>,
        create: (List<ShenzhenCourseCatalogItem>) -> T
    ): Result<T> = runCatching { create(selectedCourses(cards)) }
        .onSuccess { clear() }

    fun routeCancellation(jobId: String, cancel: (String) -> Boolean): Boolean =
        jobId.isNotBlank() && cancel(jobId)

    fun routeReadOnlyConfirmation(
        jobId: String,
        status: CourseSelectionJobStatus,
        confirm: (String) -> Unit
    ): Boolean {
        if (jobId.isBlank() || status in setOf(
                CourseSelectionJobStatus.WAITING,
                CourseSelectionJobStatus.RUNNING,
                CourseSelectionJobStatus.CANCELLED
            )
        ) {
            return false
        }
        confirm(jobId)
        return true
    }
}

@HiltViewModel
class ShenzhenCourseCatalogViewModel @Inject constructor(
    easRepo: EASRepository,
    private val timetableRepository: TimetableRepository,
    private val savedStateHandle: SavedStateHandle,
    private val courseSelectionJobCoordinator: CourseSelectionJobCoordinator
) : EASViewModel(easRepo) {
    companion object {
        private const val STATE_TERM_ID = "shenzhen_catalog_term_id"
        private const val STATE_SOURCE = "shenzhen_catalog_source"
        private const val STATE_POOL = "shenzhen_catalog_pool"
        private const val STATE_STUDENT_TYPE = "shenzhen_catalog_student_type"
    }

    val pools = ShenzhenSelectionPools.all

    private val refreshController = MutableLiveData<Trigger>()
    val termsLiveData: LiveData<DataState<List<TermItem>>> = refreshController.switchMap {
        easRepo.getShenzhenWebTerms()
    }

    val selectedTermLiveData = MutableLiveData<TermItem>()
    val sourceLiveData = MutableLiveData(
        savedStateHandle.get<String>(STATE_SOURCE)
            ?.let { runCatching { ShenzhenCourseCatalogSource.valueOf(it) }.getOrNull() }
            ?: ShenzhenCourseCatalogSource.AVAILABLE
    )
    val selectedPoolLiveData = MutableLiveData(
        pools.firstOrNull { it.code == savedStateHandle.get<String>(STATE_POOL) }
            ?: pools.first { it.code == "xx-b-b" }
    )
    val studentTypeLiveData = MutableLiveData(
        savedStateHandle.get<String>(STATE_STUDENT_TYPE)
            ?: easRepo.getEasToken().getStudentType()
    )
    val queryLiveData = MutableLiveData<ShenzhenCourseCatalogQuery>()
    val followedSectionIdsLiveData = MutableLiveData<Set<String>>(emptySet())
    val startDateLiveData = selectedTermLiveData.switchMap { term ->
        easRepo.getShenzhenCoursePlanningStartDate(term)
    }
    val scheduleStructureLiveData = selectedTermLiveData.switchMap { term ->
        easRepo.getShenzhenCoursePlanningScheduleStructure(term)
    }
    val selectedCoursesLiveData = selectedTermLiveData.switchMap { term ->
        easRepo.getShenzhenSelectedCourses(term)
    }
    val courseSelectionDraftLiveData = MutableLiveData<CourseSelectionDraft>()
    val coursePlanActionLiveData = MediatorLiveData<DataState<CourseSelectionDraft>>()
    private var coursePlanActionSource: LiveData<DataState<CourseSelectionDraft>>? = null
    val coursePlanProjectionActionLiveData =
        MediatorLiveData<DataState<TimetableRepository.CoursePlanProjectionResult>>()
    private var coursePlanProjectionActionSource:
        LiveData<DataState<TimetableRepository.CoursePlanProjectionResult>>? = null
    private var pendingCoursePlanProjection = false
    val followActionLiveData = MediatorLiveData<DataState<Boolean>>()
    private var followActionSource: LiveData<DataState<Boolean>>? = null

    init {
        coursePlanProjectionActionLiveData.addSource(selectedCoursesLiveData) {
            continuePendingCoursePlanProjection()
        }
        coursePlanProjectionActionLiveData.addSource(startDateLiveData) {
            continuePendingCoursePlanProjection()
        }
        coursePlanProjectionActionLiveData.addSource(scheduleStructureLiveData) {
            continuePendingCoursePlanProjection()
        }
    }

    val coursesLiveData: LiveData<DataState<ShenzhenCourseCatalogPage>> = queryLiveData.switchMap { query ->
        when (query.source) {
            ShenzhenCourseCatalogSource.AVAILABLE -> easRepo.queryShenzhenAvailableCourses(
                query.term,
                query.pool,
                query.keyword,
                query.page
            )
            ShenzhenCourseCatalogSource.SCHOOL -> easRepo.queryShenzhenSchoolCourses(
                query.term,
                query.studentType,
                query.keyword,
                query.page
            )
        }
    }
    private val submissionDraftState = ShenzhenCourseSelectionDraftState()
    val selectedForSubmissionLiveData = MutableLiveData<Set<String>>(emptySet())
    val selectedSubmissionCoursesLiveData = MediatorLiveData<List<ShenzhenCourseCatalogItem>>().apply {
        addSource(selectedForSubmissionLiveData) { value = selectedSubmissionCoursesInCardOrder() }
        addSource(coursesLiveData) { value = selectedSubmissionCoursesInCardOrder() }
    }
    val selectionJobsLiveData: LiveData<List<CourseSelectionJob>> =
        courseSelectionJobCoordinator.jobs.asLiveData()
    private val _selectionCommandEventLiveData = MutableLiveData<CourseSelectionCommandResult?>()
    val selectionCommandEventLiveData: LiveData<CourseSelectionCommandResult?> =
        _selectionCommandEventLiveData
    private val attachmentCourseLiveData = MutableLiveData<ShenzhenCourseCatalogItem>()
    val attachmentsLiveData: LiveData<DataState<List<ShenzhenCourseAttachment>>> =
        attachmentCourseLiveData.switchMap { course ->
            easRepo.getShenzhenCourseAttachments(course)
        }
    private val historicalFailureRequestLiveData =
        MutableLiveData<ShenzhenHistoricalFailureRequest>()
    val historicalFailureLiveData: LiveData<DataState<ShenzhenHistoricalFailureReport>> =
        historicalFailureRequestLiveData.switchMap { request ->
            easRepo.getShenzhenHistoricalTeacherFailureRates(
                request.term,
                request.studentType,
                request.course
            )
        }

    fun startRefresh() {
        refreshController.value = Trigger.actioning
    }

    fun reconcileTerms(terms: List<TermItem>) {
        val selectedId = selectedTermLiveData.value?.id ?: savedStateHandle[STATE_TERM_ID]
        val selected = terms.firstOrNull { it.id == selectedId }
            ?: terms.firstOrNull { it.isCurrent }
            ?: terms.firstOrNull()
            ?: return
        selectTerm(selected, resetPage = true)
    }

    fun selectTerm(term: TermItem, resetPage: Boolean = true) {
        if (selectedTermLiveData.value?.id != term.id) clearSubmissionDraft()
        selectedTermLiveData.value = term
        savedStateHandle[STATE_TERM_ID] = term.id
        followedSectionIdsLiveData.value = timetableRepository.followedSchoolSectionIds(term)
        courseSelectionDraftLiveData.value = timetableRepository.courseSelectionDraft(term)
        submitQuery(page = if (resetPage) 1 else queryLiveData.value?.page ?: 1)
    }

    fun selectSource(source: ShenzhenCourseCatalogSource) {
        if (sourceLiveData.value != source) clearSubmissionDraft()
        sourceLiveData.value = source
        savedStateHandle[STATE_SOURCE] = source.name
        submitQuery(page = 1)
    }

    fun selectPool(pool: ShenzhenSelectionPool) {
        if (selectedPoolLiveData.value?.code != pool.code) clearSubmissionDraft()
        selectedPoolLiveData.value = pool
        savedStateHandle[STATE_POOL] = pool.code
        submitQuery(page = 1)
    }

    fun selectStudentType(studentType: String) {
        studentTypeLiveData.value = studentType
        savedStateHandle[STATE_STUDENT_TYPE] = studentType
        submitQuery(page = 1)
    }

    fun search(keyword: String) {
        submitQuery(keyword = keyword.trim(), page = 1)
    }

    fun previousPage() {
        val current = queryLiveData.value ?: return
        if (current.page > 1) submitQuery(page = current.page - 1)
    }

    fun nextPage() {
        val current = queryLiveData.value ?: return
        val page = coursesLiveData.value?.data ?: return
        if (page.hasNextPage) submitQuery(page = current.page + 1)
    }

    fun retry(): Boolean {
        val query = queryLiveData.value ?: return false
        queryLiveData.value = query.copy()
        return true
    }

    fun toggleCourseForSubmission(course: ShenzhenCourseCatalogItem): Boolean {
        if (!submissionDraftState.toggle(course)) {
            if (
                ShenzhenCourseSelectionUiPolicy.canSelect(course) &&
                course.selectionRequestId.isNotBlank() &&
                course.selectionRequestId !in submissionDraftState.requestIds() &&
                submissionDraftState.requestIds().size >= CourseSelectionJobPolicy.MAX_COURSES
            ) {
                publishSelectionCommand(CourseSelectionCommandResult.Rejected(
                    CourseSelectionCommandFailure.TOO_MANY_COURSES
                ))
            }
            return false
        }
        publishSubmissionSelection()
        return true
    }

    fun clearSubmissionDraft() {
        submissionDraftState.clear()
        publishSubmissionSelection()
    }

    fun createImmediateSelectionJob(): CourseSelectionCommandResult = createSelectionJob { term, pool, courses ->
        courseSelectionJobCoordinator.createImmediate(term, pool, courses)
    }

    fun createScheduledSelectionJob(scheduledAtMillis: Long): CourseSelectionCommandResult {
        val validation = ShenzhenCourseSelectionUiPolicy.validateSchedule(
            now = System.currentTimeMillis(),
            scheduled = scheduledAtMillis
        )
        if (validation != CourseSelectionScheduleValidation.VALID) {
            return publishSelectionCommand(CourseSelectionCommandResult.Rejected(
                validation.toCommandFailure()
            ))
        }
        return createSelectionJob { term, pool, courses ->
            courseSelectionJobCoordinator.createScheduled(term, pool, courses, scheduledAtMillis)
        }
    }

    fun cancelSelectionJob(jobId: String): Boolean {
        val cancelled = submissionDraftState.routeCancellation(
            jobId,
            courseSelectionJobCoordinator::cancel
        )
        if (!cancelled) {
            publishSelectionCommand(CourseSelectionCommandResult.Rejected(
                CourseSelectionCommandFailure.CANNOT_CANCEL
            ))
        }
        return cancelled
    }

    fun confirmSelectionJob(jobId: String): Boolean {
        val job = courseSelectionJobCoordinator.jobs.value.firstOrNull { it.id == jobId }
            ?: return false
        var confirmationJobId: String? = null
        if (!submissionDraftState.routeReadOnlyConfirmation(jobId, job.status) { confirmationJobId = it }) return false
        viewModelScope.launch {
            try {
                courseSelectionJobCoordinator.confirm(checkNotNull(confirmationJobId))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                publishSelectionCommand(CourseSelectionCommandResult.Rejected(
                    CourseSelectionCommandFailure.CANNOT_CONFIRM
                ))
            }
        }
        return true
    }

    fun consumeSelectionCommandEvent() {
        _selectionCommandEventLiveData.value = null
    }

    fun retryCoursePlanPreviewDependencies(): Boolean {
        val term = selectedTermLiveData.value ?: return false
        selectedTermLiveData.value = term
        return true
    }

    fun loadAttachments(course: ShenzhenCourseCatalogItem) {
        attachmentCourseLiveData.value = course
    }

    fun retryAttachments(): Boolean {
        val course = attachmentCourseLiveData.value ?: return false
        attachmentCourseLiveData.value = course.copy()
        return true
    }

    fun loadHistoricalFailureRates(course: ShenzhenCourseCatalogItem): Boolean {
        val term = selectedTermLiveData.value ?: return false
        historicalFailureRequestLiveData.value = ShenzhenHistoricalFailureRequest(
            course = course,
            term = term,
            studentType = studentTypeLiveData.value ?: "1"
        )
        return true
    }

    fun retryHistoricalFailureRates(): Boolean {
        val request = historicalFailureRequestLiveData.value ?: return false
        historicalFailureRequestLiveData.value = request.copy()
        return true
    }

    fun toggleFollow(course: ShenzhenCourseCatalogItem): Boolean {
        val term = selectedTermLiveData.value ?: return false
        val startDate = startDateLiveData.value?.data ?: return false
        val schedule = scheduleStructureLiveData.value?.data ?: return false
        val id = course.taskId.ifBlank { course.id }
        val followed = id !in followedSectionIdsLiveData.value.orEmpty()
        followActionSource?.let(followActionLiveData::removeSource)
        val source = timetableRepository.setSchoolSectionFollowed(
            course = course,
            term = term,
            termStartMillis = startDate.timeInMillis,
            schedule = schedule,
            followed = followed
        )
        followActionSource = source
        followActionLiveData.addSource(source) { state ->
            followActionLiveData.value = state
            if (state.state == DataState.STATE.SUCCESS) {
                followedSectionIdsLiveData.value = timetableRepository.followedSchoolSectionIds(term)
                followActionLiveData.removeSource(source)
                followActionSource = null
            }
        }
        return true
    }

    fun toggleCoursePlanCourse(course: ShenzhenCourseCatalogItem): Boolean {
        val term = selectedTermLiveData.value ?: return false
        val selected = selectedCoursesLiveData.value?.data.orEmpty()
        val draft = courseSelectionDraftLiveData.value ?: timetableRepository.courseSelectionDraft(term)
        val id = course.taskId.ifBlank { course.id }
        runCoursePlanAction(
            timetableRepository.setCoursePlanCourse(
                course,
                term,
                startDateLiveData.value?.data?.timeInMillis,
                scheduleStructureLiveData.value?.data,
                selected,
                id !in draft.courseIds
            )
        )
        return true
    }

    fun setCoursePlanProjectionEnabled(enabled: Boolean): Boolean {
        val term = selectedTermLiveData.value ?: return false
        coursePlanProjectionActionSource?.let(coursePlanProjectionActionLiveData::removeSource)
        coursePlanProjectionActionSource = null
        if (enabled) {
            planningDependencyFailure()?.let { message ->
                pendingCoursePlanProjection = false
                coursePlanProjectionActionLiveData.value = DataState(
                    DataState.STATE.FETCH_FAILED,
                    message
                )
                return true
            }
            if (!planningDependenciesReady()) {
                pendingCoursePlanProjection = true
                coursePlanProjectionActionLiveData.value = DataState(
                    DataState.STATE.LOADING,
                    "正在读取已选课程、第一教学周日期与作息"
                )
                return true
            }
        }
        pendingCoursePlanProjection = false
        startCoursePlanProjectionAction(term, enabled)
        return true
    }

    private fun continuePendingCoursePlanProjection() {
        if (!pendingCoursePlanProjection) return
        planningDependencyFailure()?.let { message ->
            pendingCoursePlanProjection = false
            coursePlanProjectionActionSource = null
            coursePlanProjectionActionLiveData.value = DataState(
                DataState.STATE.FETCH_FAILED,
                message
            )
            return
        }
        if (!planningDependenciesReady()) return
        val term = selectedTermLiveData.value ?: return
        pendingCoursePlanProjection = false
        startCoursePlanProjectionAction(term, enabled = true)
    }

    private fun planningDependenciesReady(): Boolean =
        selectedCoursesLiveData.value?.state == DataState.STATE.SUCCESS &&
            startDateLiveData.value?.state == DataState.STATE.SUCCESS &&
            scheduleStructureLiveData.value?.state == DataState.STATE.SUCCESS

    private fun planningDependencyFailure(): String? {
        val dependencies = listOf(
            selectedCoursesLiveData.value to "本学期已选课程读取失败",
            startDateLiveData.value to "本学期第一教学周日期读取失败",
            scheduleStructureLiveData.value to "本学期作息读取失败"
        )
        dependencies.forEach { (state, fallback) ->
            if (
                state != null &&
                state.state !in setOf(
                    DataState.STATE.NOTHING,
                    DataState.STATE.LOADING,
                    DataState.STATE.SUCCESS
                )
            ) {
                return state.message ?: fallback
            }
        }
        return null
    }

    private fun startCoursePlanProjectionAction(term: TermItem, enabled: Boolean) {
        val source = timetableRepository.setCoursePlanProjectionEnabled(
            term,
            startDateLiveData.value?.data?.timeInMillis,
            scheduleStructureLiveData.value?.data,
            selectedCoursesLiveData.value?.data.orEmpty(),
            enabled
        )
        coursePlanProjectionActionSource = source
        coursePlanProjectionActionLiveData.addSource(source) { state ->
            coursePlanProjectionActionLiveData.value = state
            if (state.state != DataState.STATE.NOTHING) {
                coursePlanProjectionActionLiveData.removeSource(source)
                coursePlanProjectionActionSource = null
                if (state.state == DataState.STATE.SUCCESS && state.data != null) {
                    courseSelectionDraftLiveData.value = state.data?.draft
                } else if (state.state == DataState.STATE.FETCH_FAILED) {
                    courseSelectionDraftLiveData.value = timetableRepository.courseSelectionDraft(term)
                }
            }
        }
    }

    fun consumeCoursePlanProjectionAction() {
        coursePlanProjectionActionLiveData.value = DataState(DataState.STATE.NOTHING)
    }

    fun clearCoursePlan(): Boolean {
        val term = selectedTermLiveData.value ?: return false
        runCoursePlanAction(
            timetableRepository.clearCourseSelectionDraft(
                term,
                startDateLiveData.value?.data?.timeInMillis,
                scheduleStructureLiveData.value?.data,
                selectedCoursesLiveData.value?.data.orEmpty()
            )
        )
        return true
    }

    fun coursePlanConflict(course: ShenzhenCourseCatalogItem): String? {
        val effective = selectedCoursesLiveData.value?.data.orEmpty() +
            courseSelectionDraftLiveData.value?.courses.orEmpty()
        return CoursePlanConflictEngine.conflictDescription(course, effective)
    }

    private fun runCoursePlanAction(source: LiveData<DataState<CourseSelectionDraft>>) {
        coursePlanActionSource?.let(coursePlanActionLiveData::removeSource)
        coursePlanActionSource = source
        coursePlanActionLiveData.addSource(source) { state ->
            coursePlanActionLiveData.value = state
            if (state.state != DataState.STATE.NOTHING) {
                coursePlanActionLiveData.removeSource(source)
                coursePlanActionSource = null
                if (state.state == DataState.STATE.SUCCESS && state.data != null) {
                    courseSelectionDraftLiveData.value = state.data
                }
            }
        }
    }

    private fun submitQuery(
        keyword: String = queryLiveData.value?.keyword.orEmpty(),
        page: Int
    ) {
        val term = selectedTermLiveData.value ?: return
        queryLiveData.value = ShenzhenCourseCatalogQuery(
            source = sourceLiveData.value ?: ShenzhenCourseCatalogSource.AVAILABLE,
            term = term,
            pool = selectedPoolLiveData.value ?: pools.first(),
            studentType = studentTypeLiveData.value ?: "1",
            keyword = keyword,
            page = page
        )
    }

    private fun createSelectionJob(
        create: (TermItem, ShenzhenSelectionPool, List<ShenzhenCourseCatalogItem>) -> CourseSelectionJob
    ): CourseSelectionCommandResult {
        val term = selectedTermLiveData.value ?: return publishSelectionCommand(
            CourseSelectionCommandResult.Rejected(CourseSelectionCommandFailure.NO_TERM)
        )
        val cards = coursesLiveData.value?.data?.items.orEmpty()
        val courses = submissionDraftState.selectedCourses(cards)
        if (courses.isEmpty()) {
            return publishSelectionCommand(CourseSelectionCommandResult.Rejected(
                CourseSelectionCommandFailure.NO_COURSES
            ))
        }
        val pool = selectedPoolLiveData.value ?: return publishSelectionCommand(
            CourseSelectionCommandResult.Rejected(CourseSelectionCommandFailure.NO_POOL)
        )
        return submissionDraftState.create(cards) { create(term, pool, it) }
            .fold(
                onSuccess = { job ->
                    publishSubmissionSelection()
                    publishSelectionCommand(CourseSelectionCommandResult.Created(job.id))
                },
                onFailure = {
                    publishSelectionCommand(CourseSelectionCommandResult.Rejected(
                        CourseSelectionCommandFailure.CREATION_FAILED
                    ))
                }
            )
    }

    private fun publishSubmissionSelection() {
        selectedForSubmissionLiveData.value = submissionDraftState.requestIds()
        selectedSubmissionCoursesLiveData.value = selectedSubmissionCoursesInCardOrder()
    }

    private fun publishSelectionCommand(
        result: CourseSelectionCommandResult
    ): CourseSelectionCommandResult {
        _selectionCommandEventLiveData.value = result
        return result
    }

    private fun selectedSubmissionCoursesInCardOrder(): List<ShenzhenCourseCatalogItem> {
        return submissionDraftState.selectedCourses(coursesLiveData.value?.data?.items.orEmpty())
    }

    private fun CourseSelectionScheduleValidation.toCommandFailure(): CourseSelectionCommandFailure = when (this) {
        CourseSelectionScheduleValidation.TOO_SOON -> CourseSelectionCommandFailure.SCHEDULE_TOO_SOON
        CourseSelectionScheduleValidation.TOO_FAR -> CourseSelectionCommandFailure.SCHEDULE_TOO_FAR
        CourseSelectionScheduleValidation.VALID -> error("A valid schedule has no command failure")
    }
}
