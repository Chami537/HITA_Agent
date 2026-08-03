package cn.limpu.hita.data.model.eas

enum class CourseSelectionJobStatus {
    WAITING,
    RUNNING,
    COMPLETED,
    PARTIAL,
    FAILED,
    CANCELLED
}

enum class CourseSelectionCourseStatus {
    CONFIRMED,
    UNCONFIRMED,
    BUSINESS_FAILURE,
    AUTH_REQUIRED,
    UNKNOWN
}

data class CourseSelectionJobCourse(
    val requestId: String,
    val taskId: String,
    val courseId: String,
    val courseCode: String,
    val courseName: String,
    val teacher: String,
    val poolCode: String
)

data class CourseSelectionCourseResult(
    val courseId: String,
    val status: CourseSelectionCourseStatus,
    val message: String,
    val submittedAtMillis: Long,
    val confirmedAtMillis: Long? = null
)

data class CourseSelectionJob(
    val id: String,
    val termId: String,
    val termYearCode: String,
    val termCode: String,
    val scheduledAtMillis: Long,
    val createdAtMillis: Long,
    val status: CourseSelectionJobStatus,
    val courses: List<CourseSelectionJobCourse>,
    val results: List<CourseSelectionCourseResult> = emptyList(),
    val message: String = ""
)
