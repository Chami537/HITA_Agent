package cn.limpu.hita.data.model.resource

internal object CourseContributionOps {
    fun courseSection(
        courseName: String,
        sectionTitle: String,
        content: String,
        author: Map<String, String>,
        createCourse: Boolean,
    ): List<Map<String, Any>> = buildList {
        if (createCourse) {
            add(mapOf("op" to "create_course", "course_name" to courseName))
        }
        add(
            mapOf(
                "op" to "append_course_section_item",
                "course_name" to courseName,
                "section_title" to sectionTitle,
                "item" to mapOf("content" to content, "author" to author),
            )
        )
    }

    fun courseTeacherReview(
        courseName: String,
        teacherName: String,
        content: String,
        author: Map<String, String>,
        createCourse: Boolean,
    ): List<Map<String, Any>> = buildList {
        if (createCourse) {
            add(mapOf("op" to "create_course", "course_name" to courseName))
        }
        add(
            mapOf(
                "op" to "add_course_teacher_review",
                "course_name" to courseName,
                "teacher_name" to teacherName,
                "content" to content,
                "author" to author,
            )
        )
    }
}
