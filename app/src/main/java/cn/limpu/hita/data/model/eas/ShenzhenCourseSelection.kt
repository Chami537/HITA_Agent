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
    val message: String = "",
    val credentialScopeGeneration: Long = 0L
)

internal object CourseSelectionMessageSanitizer {
    fun sanitize(message: String): String =
        if (credentialEchoPattern.containsMatchIn(normalizeCredentialSeparators(message))) "" else message

    fun sanitize(job: CourseSelectionJob): CourseSelectionJob = job.copy(
        results = job.results.map { result -> result.copy(message = sanitize(result.message)) },
        message = sanitize(job.message)
    )

    private fun normalizeCredentialSeparators(message: String): String = buildString(message.length) {
        var index = 0
        while (index < message.length) {
            val codePoint = message.codePointAt(index)
            index += Character.charCount(codePoint)
            when (Character.getType(codePoint)) {
                Character.FORMAT.toInt(),
                Character.SPACE_SEPARATOR.toInt(),
                Character.LINE_SEPARATOR.toInt(),
                Character.PARAGRAPH_SEPARATOR.toInt() -> append(' ')
                else -> append(if (Character.isWhitespace(codePoint)) ' ' else String(Character.toChars(codePoint)))
            }
        }
    }

    private val credentialKeys = listOf(
        "cookie",
        "set-cookie",
        "jsessionid",
        "session",
        "sessionid",
        "token",
        "eastoken",
        "username",
        "user",
        "password",
        "passwd",
        "authorization"
    )

    private fun separatedKeyPattern(key: String): String =
        key.asIterable().joinToString("[ ]*") { Regex.escape(it.toString()) }

    private val credentialEchoPattern = Regex(
        "(?i)(?:\\b(?:${credentialKeys.joinToString("|") { separatedKeyPattern(it) }})\\b[ :=]*[=:]|" +
            "\\b${separatedKeyPattern("bearer")}\\b[ ]+)"
    )
}
