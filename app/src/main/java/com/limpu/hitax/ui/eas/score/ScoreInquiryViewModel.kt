package com.limpu.hitax.ui.eas.score

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.limpu.component.data.DataState
import com.limpu.component.data.MTransformations
import com.limpu.component.data.Trigger
import com.limpu.hitax.data.model.eas.CourseScoreItem
import com.limpu.hitax.data.model.eas.ScoreSummary
import com.limpu.hitax.data.model.eas.TermItem
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.data.source.web.service.EASService
import com.limpu.hitax.ui.eas.EASViewModel
import com.limpu.hitax.utils.WeightedScoreCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@HiltViewModel
class ScoreInquiryViewModel @Inject constructor(easRepo: EASRepository) : EASViewModel(easRepo) {

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

    /** 当前学期的本地计算 GPA / CGPA / 学分绩 */
    val localScoreLiveData: LiveData<WeightedScoreCalculator.ScoreResult> =
        scoresLiveData.map { state ->
            WeightedScoreCalculator.calculate(state.data ?: emptyList())
        }

    /** 全部学期累计 CGPA / 学分绩 */
    val cumulativeScoreLiveData: MutableLiveData<WeightedScoreCalculator.ScoreResult> =
        MutableLiveData()

    private var cumulativeLoaded = false

    /**
     * 方法区
     */
    fun startRefresh() {
        pageController.value = Trigger.actioning
    }

    fun retryCurrentQuery(): Boolean {
        val term = selectedTermLiveData.value ?: return false
        val testType = selectedTestTypeLiveData.value ?: return false
        selectedTermLiveData.value = term
        selectedTestTypeLiveData.value = testType
        return true
    }

    fun loadCumulativeScores(terms: List<TermItem>) {
        if (cumulativeLoaded) return
        cumulativeLoaded = true
        viewModelScope.launch {
            val allSemesterItems = terms.map { term ->
                async { fetchTermScores(term) }
            }.awaitAll()
            val result = WeightedScoreCalculator.calculateCumulative(allSemesterItems)
            cumulativeScoreLiveData.value = result
        }
    }

    private suspend fun fetchTermScores(term: TermItem): List<CourseScoreItem> {
        return try {
            withTimeout(15000L) {
                val deferred = CompletableDeferred<List<CourseScoreItem>>()
                val liveData = easRepo.getPersonalScores(term, EASService.TestType.NORMAL)
                val observer = Observer<DataState<List<CourseScoreItem>>> { state ->
                    when (state.state) {
                        DataState.STATE.SUCCESS -> {
                            deferred.complete(state.data ?: emptyList())
                        }
                        DataState.STATE.NOTHING -> {} // 加载中
                        else -> {
                            deferred.complete(emptyList())
                        }
                    }
                }
                Handler(Looper.getMainLooper()).post {
                    liveData.observeForever(observer)
                }
                try {
                    deferred.await()
                } finally {
                    Handler(Looper.getMainLooper()).post {
                        liveData.removeObserver(observer)
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseStartYear(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("(\\d{4})").find(raw) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

}
