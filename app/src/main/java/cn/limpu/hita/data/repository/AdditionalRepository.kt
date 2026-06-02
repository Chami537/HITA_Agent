package cn.limpu.hita.data.repository

import android.app.Application
import androidx.lifecycle.LiveData
import javax.inject.Inject
import com.limpu.component.data.DataState
import cn.limpu.hita.data.source.web.StaticWebSource
import cn.limpu.hita.data.source.web.additional.AdditionalWebSource
import cn.limpu.hita.data.source.web.service.AdditionalService

class AdditionalRepository @Inject constructor(application: Application) {
    private val additionalWebSource:AdditionalService = AdditionalWebSource()

    fun getLectures(pageSize:Int,pageOffset:Int): LiveData<DataState<List<Map<String,String>>>> {
        return additionalWebSource.getLectures(pageSize,pageOffset)
    }

    fun getNewsMeta(link:String): LiveData<DataState<Map<String,String>>> {
        return additionalWebSource.getNewsMeta(link)
    }


}
