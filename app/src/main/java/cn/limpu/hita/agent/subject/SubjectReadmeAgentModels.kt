package cn.limpu.hita.agent.subject

import androidx.lifecycle.LifecycleOwner
import cn.limpu.hita.data.model.resource.CourseResourceItem

data class SubjectReadmeAgentInput(
    val owner: LifecycleOwner,
    val subjectId: String,
    val courseCode: String?,
    val courseName: String?,
    val campus: String?,
)

data class SubjectReadmeAgentOutput(
    val candidates: List<CourseResourceItem>,
)
