package cn.limpu.hita.ui.timetable.manager

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import cn.limpu.hita.data.model.timetable.Timetable
import cn.limpu.hita.data.repository.IcsImportResult
import cn.limpu.hita.data.repository.TimetableRepository
import com.limpu.component.data.DataState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import javax.inject.Inject

@HiltViewModel
class TimetableManagerViewModel @Inject constructor(
    private val timetableRepository: TimetableRepository
) : ViewModel() {
    val timetablesLiveData:LiveData<List<Timetable>> = timetableRepository.getTimetables()

    private val exportController = MutableLiveData<Timetable>()
    val exportToICSResult = exportController.switchMap {
        return@switchMap timetableRepository.exportToICS(it.name ?: "课表", it.id)
    }


    init {
        timetableRepository.actionPrepareTimetableList()
    }

    fun startDeleteTimetables(timetables:List<Timetable>){
        timetableRepository.actionDeleteTimetables(timetables)
    }

    fun startNewTimetable(){
        timetableRepository.actionNewTimetable()
    }

    fun exportToIcs(timetable: Timetable) {
        exportController.value = timetable
    }
    
    /**
     * 从 ICS 文件导入课表
     */
    fun importFromICSAsNewTimetable(
        inputStream: InputStream,
        sourceName: String?
    ): LiveData<DataState<IcsImportResult>> {
        return timetableRepository.importFromICSAsNewTimetable(inputStream, sourceName)
    }
}
