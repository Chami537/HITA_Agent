package cn.limpu.hita.ui.eas.catalog

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import cn.limpu.hita.data.model.eas.ShenzhenCourseRecommendationResult
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationOptions
import cn.limpu.hita.data.model.eas.ShenzhenSelectionPools
import cn.limpu.hita.data.model.eas.TermItem
import cn.limpu.hita.data.repository.EASRepository
import cn.limpu.hita.ui.eas.EASViewModel
import com.limpu.component.data.DataState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private data class RecommendationRequest(
    val term: TermItem,
    val options: ShenzhenRecommendationOptions
)

@HiltViewModel
class ShenzhenCourseRecommendationViewModel @Inject constructor(
    easRepo: EASRepository
) : EASViewModel(easRepo) {
    private val request = MutableLiveData<RecommendationRequest>()

    val recommendations: LiveData<DataState<ShenzhenCourseRecommendationResult>> =
        request.switchMap { value ->
            easRepo.getShenzhenCourseRecommendations(
                value.term,
                ShenzhenSelectionPools.all,
                value.options
            )
        }

    fun generate(term: TermItem, options: ShenzhenRecommendationOptions) {
        request.value = RecommendationRequest(term, options)
    }

    fun retry(): Boolean {
        val current = request.value ?: return false
        request.value = current.copy()
        return true
    }
}
