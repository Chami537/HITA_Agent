package cn.limpu.hita.ui.about

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.component.data.Trigger
import cn.limpu.hita.BuildConfig
import cn.limpu.hita.data.repository.EASRepository
import cn.limpu.hita.data.repository.StaticRepository
import cn.limpu.hita.data.repository.UpdateRepository
import cn.limpu.hita.utils.LiveDataUtils
import com.limpu.hitauser.data.model.CheckUpdateResult
import com.limpu.hitauser.data.repository.LocalUserRepository
import com.limpu.hitauser.data.repository.ManagerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val staticRepo: StaticRepository,
    private val localUserRepository: LocalUserRepository,
    private val managerRepository: ManagerRepository,
    private val updateRepository: UpdateRepository,
    private val easRepository: EASRepository
) : ViewModel() {

    private val refreshController = MutableLiveData<Trigger>()

    val aboutPageLiveData = refreshController.switchMap{
        return@switchMap staticRepo.getAboutPage()
    }

    val releaseHistoryLiveData = refreshController.switchMap {
        return@switchMap updateRepository.getReleaseHistory(
            updateUrl = BuildConfig.UPDATE_URL,
            allowPrerelease = BuildConfig.UPDATE_ALLOW_PRERELEASE
        )
    }

    private val checkUpdateTrigger = MutableLiveData<Long>()
    val checkUpdateResult = checkUpdateTrigger.switchMap { currentCode ->
        updateRepository.checkUpdateWithFallback(
            currentVersionName = BuildConfig.VERSION_NAME,
            currentVersionCode = currentCode,
            builtInVersionName = BuildConfig.UPDATE_VERSION_NAME,
            builtInVersionCode = BuildConfig.UPDATE_VERSION_CODE,
            updateUrl = BuildConfig.UPDATE_URL,
            updateLog = BuildConfig.UPDATE_LOG,
            allowPrerelease = BuildConfig.UPDATE_ALLOW_PRERELEASE
        ).let { github ->
            return@switchMap github
        }
    }


    fun refresh() {
        refreshController.value = Trigger.actioning
    }

    fun checkForUpdate(versionCode: Long) {
        checkUpdateTrigger.value = versionCode
    }
}
