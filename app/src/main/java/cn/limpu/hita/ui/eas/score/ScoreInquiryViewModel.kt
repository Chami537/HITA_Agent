package cn.limpu.hita.ui.eas.score

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.component.data.MTransformations
import com.limpu.component.data.Trigger
import cn.limpu.hita.data.model.eas.CourseScoreItem
import cn.limpu.hita.data.model.eas.ScoreSummary
import cn.limpu.hita.data.model.eas.TermItem
import cn.limpu.hita.data.repository.EASRepository
import cn.limpu.hita.data.source.web.service.EASService
import cn.limpu.hita.ui.eas.EASViewModel
import cn.limpu.hita.utils.WeightedScoreCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ScoreInquiryViewModel @Inject constructor(
    easRepo: EASRepository,
    private val savedStateHandle: SavedStateHandle
) : EASViewModel(easRepo) {

    companion object {
        private const val STATE_SELECTED_TERM_ID = "score_selected_term_id"
        private const val STATE_SELECTED_TEST_TYPE = "score_selected_test_type"
    }

    /**
     * LiveData区
     */
    private val pageController = MutableLiveData<Trigger>()

    val termsLiveData : LiveData<DataState<List<TermItem>>> = pageController.switchMap {
        return@switchMap easRepo.getAllTerms().map { state ->
            val data = state.data
            if (state.state != DataState.STATE.SUCCESS || data.isNullOrEmpty()) {
                return@map state
            }
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val minYear = currentYear - 4
            val filtered = data.filter { term ->
                val startYear = parseStartYear(term.yearCode)
                startYear == null || startYear >= minYear
            }
            val finalList = if (filtered.isNotEmpty()) filtered else data
            DataState(finalList, state.state)
        }
    }

    val selectedTermLiveData: MutableLiveData<TermItem> = MutableLiveData()
    val selectedTestTypeLiveData: MutableLiveData<EASService.TestType> = MutableLiveData()

    init {
        savedStateHandle.get<String>(STATE_SELECTED_TEST_TYPE)
            ?.let { saved -> runCatching { EASService.TestType.valueOf(saved) }.getOrNull() }
            ?.let { selectedTestTypeLiveData.value = it }
    }

    private val scoresWithSummaryLiveData =
        MTransformations.switchMap(selectedTermLiveData, selectedTestTypeLiveData) {
            return@switchMap easRepo.getPersonalScoresWithSummary(it.first, it.second)
        }

    val scoresLiveData: LiveData<DataState<List<CourseScoreItem>>> =
        scoresWithSummaryLiveData.map { state ->
            DataState(state.data?.items ?: emptyList(), state.state)
        }

    val scoreSummaryLiveData: LiveData<ScoreSummary?> =
        scoresWithSummaryLiveData.map { it.data?.summary }

    /** 当前筛选结果中，仅使用可解析数字成绩得到的本页估算。 */
    val localScoreLiveData: LiveData<WeightedScoreCalculator.ScoreResult> =
        scoresLiveData.map { state ->
            WeightedScoreCalculator.calculate(state.data ?: emptyList())
        }

    /**
     * 方法区
     */
    fun startRefresh() {
        pageController.value = Trigger.actioning
    }

    fun reconcileTerms(terms: List<TermItem>) {
        val selectedId = selectedTermLiveData.value?.id
            ?: savedStateHandle[STATE_SELECTED_TERM_ID]
        val selected = chooseScoreTerm(terms, selectedId) ?: return
        if (selectedTermLiveData.value?.id != selected.id) {
            selectedTermLiveData.value = selected
        }
        savedStateHandle[STATE_SELECTED_TERM_ID] = selected.id
    }

    fun selectTerm(term: TermItem) {
        savedStateHandle[STATE_SELECTED_TERM_ID] = term.id
        if (selectedTermLiveData.value?.id != term.id) {
            selectedTermLiveData.value = term
        }
    }

    fun ensureDefaultTestType() {
        if (selectedTestTypeLiveData.value == null) {
            selectTestType(EASService.TestType.NORMAL)
        }
    }

    fun selectTestType(testType: EASService.TestType) {
        savedStateHandle[STATE_SELECTED_TEST_TYPE] = testType.name
        if (selectedTestTypeLiveData.value != testType) {
            selectedTestTypeLiveData.value = testType
        }
    }

    fun retryCurrentQuery(): Boolean {
        val term = selectedTermLiveData.value ?: return false
        val testType = selectedTestTypeLiveData.value ?: return false
        selectedTermLiveData.value = term
        selectedTestTypeLiveData.value = testType
        return true
    }

    private fun parseStartYear(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("(\\d{4})").find(raw) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

}

internal fun chooseScoreTerm(terms: List<TermItem>, selectedId: String?): TermItem? {
    return terms.firstOrNull { it.id == selectedId }
        ?: terms.firstOrNull { it.isCurrent }
        ?: terms.firstOrNull()
}
