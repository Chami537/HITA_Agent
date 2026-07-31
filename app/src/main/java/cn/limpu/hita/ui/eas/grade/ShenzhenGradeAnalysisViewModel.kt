package cn.limpu.hita.ui.eas.grade

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import cn.limpu.hita.data.model.eas.ShenzhenGradeAnalysis
import cn.limpu.hita.data.model.eas.ShenzhenGradeCourse
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogSource
import cn.limpu.hita.data.model.eas.ShenzhenHistoricalFailureReport
import cn.limpu.hita.data.model.eas.TermItem
import cn.limpu.hita.data.repository.EASRepository
import cn.limpu.hita.ui.eas.EASViewModel
import com.limpu.component.data.DataState
import com.limpu.component.data.Trigger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ShenzhenGradeAnalysisViewModel @Inject constructor(
    easRepo: EASRepository
) : EASViewModel(easRepo) {
    private val termRefresh = MutableLiveData<Trigger>()
    val terms: LiveData<DataState<List<TermItem>>> = termRefresh.switchMap {
        easRepo.getAllTerms()
    }

    val selectedTerm = MutableLiveData<TermItem>()
    private val courseRefresh = MutableLiveData<Trigger>()
    val courses: LiveData<DataState<List<ShenzhenGradeCourse>>> = courseRefresh.switchMap {
        selectedTerm.value?.let(easRepo::getShenzhenGradeCourses)
            ?: MutableLiveData(DataState(DataState.STATE.NOTHING))
    }

    val selectedCourse = MutableLiveData<ShenzhenGradeCourse?>()
    val analysis: LiveData<DataState<ShenzhenGradeAnalysis>> = selectedCourse.switchMap { course ->
        course?.let(easRepo::getShenzhenGradeAnalysis)
            ?: MutableLiveData(DataState(DataState.STATE.NOTHING))
    }
    private val peerTeacherRequest = MutableLiveData<Pair<TermItem, ShenzhenGradeCourse>>()
    val peerTeacherComparison: LiveData<DataState<ShenzhenHistoricalFailureReport>> =
        peerTeacherRequest.switchMap { (term, course) ->
            easRepo.getShenzhenHistoricalTeacherFailureRates(
                referenceTerm = term,
                studentType = easRepo.getEasToken().getStudentType(),
                referenceCourse = ShenzhenCourseCatalogItem(
                    id = course.taskId,
                    taskId = course.taskId,
                    taskNumber = course.taskNumber,
                    courseCode = course.courseCode,
                    courseName = course.courseName,
                    teacher = course.teacher,
                    source = ShenzhenCourseCatalogSource.SCHOOL
                ),
                yearsBack = 0
            )
        }

    fun refreshTerms() {
        termRefresh.value = Trigger.actioning
    }

    fun selectTerm(term: TermItem) {
        selectedTerm.value = term
        courseRefresh.value = Trigger.actioning
    }

    fun reconcileTerms(values: List<TermItem>) {
        val current = selectedTerm.value
        selectTerm(
            values.firstOrNull { it.id == current?.id }
                ?: values.firstOrNull { it.isCurrent }
                ?: values.firstOrNull()
                ?: return
        )
    }

    fun openCourse(course: ShenzhenGradeCourse) {
        selectedCourse.value = course
    }

    fun closeCourse() {
        selectedCourse.value = null
    }

    fun retryCourses(): Boolean {
        if (selectedTerm.value == null) return false
        courseRefresh.value = Trigger.actioning
        return true
    }

    fun retryAnalysis(): Boolean {
        val course = selectedCourse.value ?: return false
        selectedCourse.value = course.copy()
        return true
    }

    fun loadPeerTeacherComparison(): Boolean {
        val term = selectedTerm.value ?: return false
        val course = selectedCourse.value ?: return false
        peerTeacherRequest.value = term to course
        return true
    }

    fun retryPeerTeacherComparison(): Boolean {
        val request = peerTeacherRequest.value ?: return false
        peerTeacherRequest.value = request.copy()
        return true
    }
}
