package cn.limpu.hita.data.repository

import androidx.lifecycle.LiveData
import javax.inject.Inject
import com.limpu.component.data.DataState
import cn.limpu.hita.data.model.resource.CourseReadmeData
import cn.limpu.hita.data.model.resource.CourseResourceItem
import cn.limpu.hita.data.model.resource.CourseStructureSummary
import cn.limpu.hita.data.model.resource.ValidateReadmeResult
import cn.limpu.hita.data.source.web.HoaResourceSource
import org.json.JSONArray

class HoaRepository @Inject constructor() {
    fun searchCourses(query: String, campus: String? = null): LiveData<DataState<List<CourseResourceItem>>> {
        return HoaResourceSource.searchCourses(query, campus)
    }

    fun searchCoursesSync(query: String, campus: String? = null): List<CourseResourceItem> {
        return HoaResourceSource.searchCoursesSync(query, campus)
    }

    fun getCourseReadme(repoName: String, campus: String? = null): LiveData<DataState<CourseReadmeData>> {
        return HoaResourceSource.getCourseReadme(repoName, campus)
    }

    fun getCourseStructure(repoName: String, campus: String? = null): LiveData<DataState<CourseStructureSummary>> {
        return HoaResourceSource.getCourseStructure(repoName, campus)
    }

    fun validateReadme(
        repoName: String,
        courseCode: String,
        courseName: String,
        repoType: String,
        readmeMd: String
    ): LiveData<DataState<ValidateReadmeResult>> {
        return HoaResourceSource.validateReadme(repoName, courseCode, courseName, repoType, readmeMd)
    }
    fun submitOps(
        repoName: String,
        courseCode: String,
        courseName: String,
        repoType: String,
        ops: JSONArray,
        campus: String? = null,
    ): LiveData<DataState<String>> {
        return HoaResourceSource.submitOps(repoName, courseCode, courseName, repoType, ops, campus)
    }

}
