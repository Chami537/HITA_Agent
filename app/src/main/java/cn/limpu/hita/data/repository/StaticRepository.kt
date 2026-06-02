package cn.limpu.hita.data.repository

import android.app.Application
import androidx.lifecycle.LiveData
import javax.inject.Inject
import com.limpu.component.data.DataState
import cn.limpu.hita.data.source.web.StaticWebSource

class StaticRepository @Inject constructor(
    application: Application,
    private val staticWebSource: StaticWebSource
) {

    fun getAboutPage(): LiveData<DataState<String?>> {
        return staticWebSource.getAboutPage()
    }

    fun getUAPage(): LiveData<DataState<String?>> {
        return staticWebSource.getUserAgreementPage()
    }

    fun getPPPage(): LiveData<DataState<String?>> {
        return staticWebSource.getPrivacyPolicyPage()
    }

}