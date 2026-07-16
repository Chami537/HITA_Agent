package cn.limpu.hita.ui.eas.training

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import cn.limpu.hita.data.model.eas.ShenzhenTrainingPlan
import cn.limpu.hita.data.model.eas.ShenzhenTrainingPlanDetail
import cn.limpu.hita.data.repository.EASRepository
import cn.limpu.hita.ui.eas.EASViewModel
import com.limpu.component.data.DataState
import com.limpu.component.data.Trigger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ShenzhenTrainingPlanViewModel @Inject constructor(
    easRepo: EASRepository
) : EASViewModel(easRepo) {
    private val planRefresh = MutableLiveData<Trigger>()
    val plans: LiveData<DataState<List<ShenzhenTrainingPlan>>> = planRefresh.switchMap {
        easRepo.getShenzhenTrainingPlans()
    }

    val selectedPlan = MutableLiveData<ShenzhenTrainingPlan>()
    val detail: LiveData<DataState<ShenzhenTrainingPlanDetail>> = selectedPlan.switchMap {
        easRepo.getShenzhenTrainingPlanCourses(it)
    }

    fun refreshPlans() {
        planRefresh.value = Trigger.actioning
    }

    fun reconcilePlans(values: List<ShenzhenTrainingPlan>) {
        val selected = selectedPlan.value
        selectPlan(values.firstOrNull { it.id == selected?.id } ?: values.firstOrNull() ?: return)
    }

    fun selectPlan(plan: ShenzhenTrainingPlan) {
        selectedPlan.value = plan
    }

    fun retryPlans(): Boolean {
        planRefresh.value = Trigger.actioning
        return true
    }

    fun retryDetail(): Boolean {
        val plan = selectedPlan.value ?: return false
        selectedPlan.value = plan.copy()
        return true
    }
}
