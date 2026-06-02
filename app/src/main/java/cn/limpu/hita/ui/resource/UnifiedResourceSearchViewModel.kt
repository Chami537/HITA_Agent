package cn.limpu.hita.ui.resource

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import cn.limpu.hita.data.model.resource.ExternalResourceEntry
import cn.limpu.hita.data.model.resource.ResourceSource
import cn.limpu.hita.data.model.resource.UnifiedResourceItem
import cn.limpu.hita.data.repository.EASRepository
import cn.limpu.hita.data.repository.ExternalResourceRepository
import cn.limpu.hita.data.repository.HoaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UnifiedResourceSearchViewModel @Inject constructor(
    private val hoaRepository: HoaRepository,
    private val externalRepository: ExternalResourceRepository,
    private val easRepository: EASRepository,
) : ViewModel() {

    private val queryLiveData = MutableLiveData<String>()
    private val browseLiveData = MutableLiveData<Pair<String, ResourceSource>>()

    val searchResults: LiveData<DataState<List<UnifiedResourceItem>>> = queryLiveData.switchMap {
        searchAll(it)
    }

    val browseResults: LiveData<DataState<List<ExternalResourceEntry>>> = browseLiveData.switchMap { (path, source) ->
        externalRepository.listDirectory(path, source)
    }

    fun search(query: String) {
        queryLiveData.value = query.trim()
    }

    fun browse(path: String, source: ResourceSource) {
        browseLiveData.value = Pair(path, source)
    }

    private fun searchAll(query: String): LiveData<DataState<List<UnifiedResourceItem>>> {
        val mediator = MediatorLiveData<DataState<List<UnifiedResourceItem>>>()
        val hoaCampus = easRepository.getHoaCampus()

        var hoaResult: List<UnifiedResourceItem>? = null
        var externalResult: List<UnifiedResourceItem>? = null
        var hoaFailed = false
        var externalFailed = false

        val hoaLive = hoaRepository.searchCourses(query, hoaCampus)
        val externalLive = externalRepository.searchCourses(query)

        fun mergeAndPost() {
            val hoaDone = hoaResult != null || hoaFailed
            val extDone = externalResult != null || externalFailed
            if (!hoaDone || !extDone) return

            val merged = mutableListOf<UnifiedResourceItem>()
            hoaResult?.let { merged.addAll(it) }
            externalResult?.let { merged.addAll(it) }
            merged.sortBy { it.displayName }

            if (merged.isNotEmpty()) {
                mediator.value = DataState(merged, DataState.STATE.SUCCESS)
            } else if (hoaFailed && externalFailed) {
                mediator.value = DataState(DataState.STATE.FETCH_FAILED, "所有数据源均不可用")
            } else {
                mediator.value = DataState(merged, DataState.STATE.SUCCESS)
            }
        }

        mediator.addSource(hoaLive) { state ->
            if (state.state == DataState.STATE.SUCCESS) {
                val items = state.data?.map { course ->
                    UnifiedResourceItem.HoaCourse(
                        repoName = course.repoName,
                        courseName = course.courseName,
                        courseCode = course.courseCode,
                        repoType = course.repoType,
                        teachers = course.teachers,
                    )
                } ?: emptyList()
                hoaResult = items
            } else {
                hoaFailed = true
            }
            mergeAndPost()
        }

        mediator.addSource(externalLive) { state ->
            if (state.state == DataState.STATE.SUCCESS) {
                val items = state.data?.map { course ->
                    UnifiedResourceItem.ExternalCourse(
                        courseName = course.courseName,
                        category = course.category,
                        source = course.source,
                        path = course.path,
                    )
                } ?: emptyList()
                externalResult = items
            } else {
                externalFailed = true
            }
            mergeAndPost()
        }

        return mediator
    }
}
