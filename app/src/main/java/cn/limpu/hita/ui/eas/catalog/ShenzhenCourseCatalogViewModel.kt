package cn.limpu.hita.ui.eas.catalog

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.component.data.Trigger
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogPage
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogSource
import cn.limpu.hita.data.model.eas.ShenzhenCourseAttachment
import cn.limpu.hita.data.model.eas.ShenzhenHistoricalFailureReport
import cn.limpu.hita.data.model.eas.ShenzhenSelectionPool
import cn.limpu.hita.data.model.eas.ShenzhenSelectionPools
import cn.limpu.hita.data.model.eas.TermItem
import cn.limpu.hita.data.repository.EASRepository
import cn.limpu.hita.ui.eas.EASViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

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

@HiltViewModel
class ShenzhenCourseCatalogViewModel @Inject constructor(
    easRepo: EASRepository,
    private val savedStateHandle: SavedStateHandle
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
        selectedTermLiveData.value = term
        savedStateHandle[STATE_TERM_ID] = term.id
        submitQuery(page = if (resetPage) 1 else queryLiveData.value?.page ?: 1)
    }

    fun selectSource(source: ShenzhenCourseCatalogSource) {
        sourceLiveData.value = source
        savedStateHandle[STATE_SOURCE] = source.name
        submitQuery(page = 1)
    }

    fun selectPool(pool: ShenzhenSelectionPool) {
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
}
