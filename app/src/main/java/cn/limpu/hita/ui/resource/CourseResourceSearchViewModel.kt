package cn.limpu.hita.ui.resource

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import cn.limpu.hita.data.model.resource.CourseResourceItem
import cn.limpu.hita.data.repository.EASRepository
import cn.limpu.hita.data.repository.HoaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CourseResourceSearchViewModel @Inject constructor(
    private val repository: HoaRepository,
    private val easRepository: EASRepository
) : ViewModel() {
    private val hoaCampus = easRepository.getHoaCampus()
    private val queryLiveData = MutableLiveData<String>()

    val resultsLiveData: LiveData<DataState<List<CourseResourceItem>>> = queryLiveData.switchMap {
        repository.searchCourses(it, hoaCampus)
    }

    fun search(query: String) {
        queryLiveData.value = query.trim()
    }
}