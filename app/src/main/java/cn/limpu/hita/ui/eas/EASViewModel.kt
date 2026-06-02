package cn.limpu.hita.ui.eas

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.component.data.Trigger
import cn.limpu.hita.data.repository.EASRepository
import cn.limpu.hita.utils.LiveDataUtils

abstract class EASViewModel(val easRepo: EASRepository) : ViewModel() {


    private val loginCheckController = MutableLiveData<Trigger>()
    val loginCheckResult:LiveData<DataState<Boolean>> = loginCheckController.switchMap{
        if(it.isActioning){
            return@switchMap easRepo.loginCheck()
        }
        return@switchMap LiveDataUtils.getMutableLiveData(DataState(DataState.STATE.NOTHING))
    }

    /**
     * 方法区
     */
    fun startLoginCheck(){
        loginCheckController.value = Trigger.actioning
    }

}