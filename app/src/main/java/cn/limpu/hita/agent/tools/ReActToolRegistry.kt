package cn.limpu.hita.agent.tools

class ReActToolRegistry {
    private val tools = mutableMapOf<String, ReActTool>()

    fun register(name: String, tool: ReActTool) {
        tools[normalizeName(name)] = tool
    }

    fun get(name: String): ReActTool? = tools[normalizeName(name)]

    fun names(): List<String> = tools.keys.toList()

    companion object {
        fun normalizeName(name: String): String {
            return name
                .trim()
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
        }

        fun createDefault(): ReActToolRegistry = ReActToolRegistry().apply {
            register("get_timetable", GetTimetableTool())
            register("add_activity", AddActivityTool())
            register("search_empty_classroom", SearchEmptyClassroomTool())
            register("search_course", SearchCourseTool())
            register("get_course_detail", GetCourseDetailTool())
            register("search_external_resource", SearchExternalResourceTool())
            register("search_teacher", SearchTeacherTool())
            register("web_search", WebSearchTool())
            register("rag_search", RagSearchTool())
            register("search_timetable", SearchTimetableTool())
            register("crawl_page", CrawlPageTool())
            register("crawl_site", CrawlSiteTool())
            register("crawl_status", CrawlStatusTool())
            register("submit_review", SubmitReviewTool())
        }
    }
}
