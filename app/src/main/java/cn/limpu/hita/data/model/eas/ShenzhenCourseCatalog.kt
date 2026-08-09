package cn.limpu.hita.data.model.eas

enum class ShenzhenCourseCatalogSource {
    AVAILABLE,
    SCHOOL
}

enum class ShenzhenSelectionOpenTimeSource {
    COURSE,
    POOL_OR_PAGE,
    SELECTION_RULE
}

data class ShenzhenSelectionOpenTime(
    val rawValue: String,
    val epochMillis: Long,
    val source: ShenzhenSelectionOpenTimeSource
)

data class ShenzhenCourseCatalogItem(
    val id: String,
    val taskId: String = "",
    val selectionRequestId: String = "",
    val courseId: String = "",
    val taskNumber: String = "",
    val courseCode: String,
    val courseName: String,
    val teacher: String = "",
    val credits: String = "",
    val totalHours: String = "",
    val courseNature: String = "",
    val courseCategory: String = "",
    val offeringCollege: String = "",
    val campus: String = "",
    val schedule: String = "",
    val selectionRequirement: String = "",
    val teachingLanguage: String = "",
    val trainingLevel: String = "",
    val capacity: Int? = null,
    val selectedCount: Int? = null,
    val hasConflict: Boolean = false,
    val conflictDescription: String = "",
    val selectionPoolName: String = "",
    val classNumber: String = "",
    val meetings: List<ShenzhenCourseMeeting> = emptyList(),
    val source: ShenzhenCourseCatalogSource,
    val selectionOpenTime: ShenzhenSelectionOpenTime? = null
) {
    val remainingSeats: Int?
        get() = if (capacity != null && selectedCount != null) capacity - selectedCount else null

    val isFollowable: Boolean
        get() = source == ShenzhenCourseCatalogSource.SCHOOL &&
            meetings.isNotEmpty() && meetings.all(ShenzhenCourseMeeting::isStructurallyComplete)
}

data class ShenzhenCourseMeeting(
    val weeks: List<Int>,
    val weekday: Int,
    val beginPeriod: Int,
    val endPeriod: Int,
    val teacher: String = "",
    val location: String = ""
) {
    fun isStructurallyComplete(): Boolean =
        weeks.isNotEmpty() && weekday in 1..7 && beginPeriod > 0 && endPeriod >= beginPeriod
}

data class ShenzhenCourseCatalogPage(
    val items: List<ShenzhenCourseCatalogItem>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val selectionOpenTime: ShenzhenSelectionOpenTime? = null
) {
    val hasNextPage: Boolean
        get() = items.isNotEmpty() && page * pageSize < total
}

data class ShenzhenSelectionPool(
    val code: String,
    val name: String,
    val selectionOpenTime: ShenzhenSelectionOpenTime? = null
)

object ShenzhenSelectionPools {
    val all = listOf(
        ShenzhenSelectionPool("bx-b-b", "必修课程池"),
        ShenzhenSelectionPool("xx-b-b", "限选课程池"),
        ShenzhenSelectionPool("cxcytx-b-b", "创新创业通选课"),
        ShenzhenSelectionPool("shsj-b-b", "社会实践课"),
        ShenzhenSelectionPool("jsrw-b-b", "竞赛指导类课程"),
        ShenzhenSelectionPool("cxyx-b-b", "创新研修"),
        ShenzhenSelectionPool("cxsy-b-b", "创新实验"),
        ShenzhenSelectionPool("cx-b-b", "重修"),
        ShenzhenSelectionPool("buxiu-bcyxfj-b-b", "补修"),
        ShenzhenSelectionPool("sx-b-b", "跨专业课程体系"),
        ShenzhenSelectionPool("tsk-b-b", "文理通识"),
        ShenzhenSelectionPool("mooc-b-b", "MOOC")
    )
}

enum class ShenzhenCourseAttachmentKind {
    COURSE_DESCRIPTION,
    CHINESE_SYLLABUS,
    ENGLISH_SYLLABUS
}

data class ShenzhenCourseAttachment(
    val name: String,
    val serverPath: String = "",
    val kind: ShenzhenCourseAttachmentKind,
    val courseId: String,
    val sizeBytes: Long? = null
)
