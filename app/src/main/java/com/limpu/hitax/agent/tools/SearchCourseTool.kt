package com.limpu.hitax.agent.tools

import com.limpu.hitax.agent.remote.AgentBackendClient
import com.limpu.hitax.data.model.resource.AgentResourceCard

class SearchCourseTool : ReActTool {
    override fun execute(input: ReActToolInput): String {
        val keyword = extractKeyword(input.actionInput)
        if (keyword.isBlank()) return "搜索失败: 无法解析查询条件"

        return try {
            val response = AgentBackendClient.searchCoursesSync(keyword)
            if (!response.ok) {
                return "搜索失败: ${response.error?.message ?: "未知错误"}"
            }

            val results = extractResults(response.results)
            if (results.isEmpty()) return "未找到相关课程"
            input.onResourceCards(results.take(6).mapNotNull { it.toHoaCard(keyword) })

            val formatted = results.take(10).map { item ->
                val code = valueOf(item, "course_code", "code", "id")
                val name = valueOf(item, "course_name", "name", "title")
                val campus = valueOf(item, "campus")
                val teacher = extractTeacherSummary(item)
                buildString {
                    append("• ")
                    if (code.isNotBlank()) {
                        append(code)
                        append(" - ")
                    }
                    append(if (name.isNotBlank()) name else "未命名课程")
                    if (campus.isNotBlank()) {
                        append(" (")
                        append(campus)
                        append(")")
                    }
                    if (teacher.isNotBlank()) {
                        append(" | 教师: ")
                        append(teacher)
                    }
                }
            }

            "找到 ${results.size} 门相关课程:\n${formatted.joinToString("\n")}"
        } catch (e: Exception) {
            "搜索失败: ${e.message}"
        }
    }

    private fun extractKeyword(actionInput: String): String {
        val keys = listOf("query", "keyword", "param")
        keys.forEach { key ->
            val regex = Regex(""""$key"\s*:\s*"([^"]+)"""")
            val value = regex.find(actionInput)?.groupValues?.getOrNull(1)?.trim()
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return actionInput.trim().removeSurrounding("{", "}")
    }

    private fun extractResults(raw: Any?): List<Map<*, *>> {
        return when (raw) {
            is List<*> -> raw.filterIsInstance<Map<*, *>>()
            is Map<*, *> -> {
                val nested = listOf("results", "items", "courses")
                    .firstNotNullOfOrNull { key -> raw[key] as? List<*> }
                nested?.filterIsInstance<Map<*, *>>() ?: listOf(raw)
            }
            else -> emptyList()
        }
    }

    private fun valueOf(map: Map<*, *>, vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            map[key]?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }.orEmpty()
    }

    private fun extractTeacherSummary(map: Map<*, *>): String {
        val teachers = map["teachers"]
        return when (teachers) {
            is List<*> -> teachers.joinToString("/") { it.toString() }.trim('/')
            else -> valueOf(map, "teacher", "lecturer_name")
        }
    }

    private fun Map<*, *>.toHoaCard(keyword: String): AgentResourceCard? {
        val code = valueOf(this, "course_code", "code", "id")
        val name = valueOf(this, "course_name", "name", "title")
        val repoName = valueOf(this, "repo_name", "repoName", "repo")
        val repoType = valueOf(this, "repo_type", "repoType").ifBlank { "normal" }
        if (code.isBlank() && name.isBlank() && repoName.isBlank()) return null
        val resolvedRepoName = repoName.ifBlank { code }
        return AgentResourceCard(
            title = name.ifBlank { code.ifBlank { resolvedRepoName } },
            subtitle = listOf(
                code.takeIf { it.isNotBlank() },
                extractTeacherSummary(this).takeIf { it.isNotBlank() },
            ).filterNotNull().joinToString(" · "),
            sourceTag = "HOA",
            source = SearchExternalResourceTool.SOURCE_HOA,
            path = resolvedRepoName,
            query = keyword,
            repoName = resolvedRepoName,
            courseCode = code,
            repoType = repoType,
        )
    }
}
