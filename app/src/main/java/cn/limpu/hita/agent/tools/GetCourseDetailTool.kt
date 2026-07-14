package cn.limpu.hita.agent.tools

import cn.limpu.hita.agent.remote.AgentBackendClient

class GetCourseDetailTool : ReActTool {
    override fun execute(input: ReActToolInput): String {
        val courseCode = extractCourseCode(input.actionInput)
        if (courseCode.isBlank()) return "获取详情失败: 无法解析课程代码"

        return try {
            val response = AgentBackendClient.readCourseSync(courseCode)
            if (!response.ok) {
                return "获取详情失败: ${response.error?.message ?: "未知错误"}"
            }

            val course = when (val raw = response.course ?: response.result) {
                is Map<*, *> -> raw
                else -> null
            } ?: return "课程详情（$courseCode）：\n暂无详细信息"

            val courseName = valueOf(course, "course_name", "name", "title")
            val teacher = extractTeacherSummary(course)
            val markdown = extractMarkdown(course)
            val summary = valueOf(course, "summary", "description", "intro")
            val focusQuery = extractFocusQuery(input.actionInput)
            val charRange = extractCharRange(input.actionInput)

            buildString {
                append("课程详情（")
                append(if (courseName.isNotBlank()) courseName else courseCode)
                append("）")
                if (teacher.isNotBlank()) {
                    append("\n教师：")
                    append(teacher)
                }
                append("\n\n")
                when {
                    markdown.isNotBlank() -> {
                        val range = charRange ?: TextRange(0, MAX_DETAIL_CHARS.coerceAtMost(markdown.length))
                        val start = range.start.coerceIn(0, markdown.length)
                        val end = range.end.coerceIn(start, markdown.length)
                        append(markdown.substring(start, end))
                        append("\n\n（当前显示字符 ${start}-${end} / ${markdown.length}。）")
                        if (end < markdown.length) {
                            append(" 如需继续读取，可再次调用 get_course_detail 并传入 {\"course_code\":\"")
                            append(courseCode)
                            append("\",\"start\":")
                            append(end)
                            append(",\"end\":")
                            append((end + MAX_DETAIL_CHARS).coerceAtMost(markdown.length))
                            append("}。")
                        }
                        val focused = focusedSnippets(markdown, focusQuery)
                        if (focused.isNotBlank()) {
                            append("\n\n与“")
                            append(focusQuery)
                            append("”相关的详情片段：\n")
                            append(focused)
                        } else if (focusQuery.isNotBlank()) {
                            append("\n\n未在课程详情全文中找到与“")
                            append(focusQuery)
                            append("”直接相关的片段。")
                        }
                    }
                    summary.isNotBlank() -> append(summary)
                    else -> append("暂无详细信息")
                }
            }
        } catch (e: Exception) {
            "获取详情失败: ${e.message}"
        }
    }

    private fun extractCourseCode(actionInput: String): String {
        val keys = listOf("course_code", "code", "param")
        keys.forEach { key ->
            val regex = Regex(""""$key"\s*:\s*"([^"]+)"""")
            val value = regex.find(actionInput)?.groupValues?.getOrNull(1)?.trim()
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return actionInput.trim().removeSurrounding("{", "}")
    }

    private fun extractFocusQuery(text: String): String {
        val keys = listOf("query", "keyword", "topic", "course_name", "project", "item")
        keys.forEach { key ->
            val regex = Regex(""""$key"\s*:\s*"([^"]+)"""")
            val value = regex.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return ""
    }

    private fun extractCharRange(actionInput: String): TextRange? {
        val start = extractInt(actionInput, "start", "char_start", "offset", "from")
        val end = extractInt(actionInput, "end", "char_end", "to")
        val limit = extractInt(actionInput, "limit", "length")
        return when {
            start != null && end != null -> TextRange(start, end)
            start != null && limit != null -> TextRange(start, start + limit)
            start != null -> TextRange(start, start + MAX_DETAIL_CHARS)
            else -> null
        }
    }

    private fun extractInt(text: String, vararg keys: String): Int? {
        keys.forEach { key ->
            val regex = Regex(""""$key"\s*:\s*(\d+)""")
            val value = regex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (value != null) return value
        }
        return null
    }

    private fun focusedSnippets(markdown: String, focusQuery: String): String {
        if (focusQuery.isBlank()) return ""
        val terms = focusQuery
            .split(Regex("\\s+|,|，|/|、"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
        if (terms.isEmpty()) return ""

        val snippets = mutableListOf<String>()
        terms.forEach { term ->
            var searchFrom = 0
            var found = 0
            while (found < MAX_SNIPPETS_PER_TERM) {
                val index = markdown.indexOf(term, startIndex = searchFrom, ignoreCase = true)
                if (index < 0) break
                val start = (index - SNIPPET_RADIUS).coerceAtLeast(0)
                val end = (index + term.length + SNIPPET_RADIUS).coerceAtMost(markdown.length)
                snippets.add(markdown.substring(start, end).replace(Regex("\\s+"), " ").trim())
                searchFrom = index + term.length
                found++
            }
        }
        return snippets.distinct().take(MAX_TOTAL_SNIPPETS).joinToString("\n") { "• ...$it..." }
    }

    private fun extractMarkdown(course: Map<*, *>): String {
        val rawContent = course["raw_content"] as? Map<*, *>
        return listOf(
            rawContent?.get("fit_markdown"),
            rawContent?.get("content"),
            course["readme_md"],
            course["markdown"],
        ).firstNotNullOfOrNull {
            it?.toString()?.trim()?.takeIf { value -> value.isNotBlank() }
        }.orEmpty()
    }

    private fun extractTeacherSummary(course: Map<*, *>): String {
        val teachers = course["teachers"]
        return when (teachers) {
            is List<*> -> teachers.joinToString("/") { it.toString() }.trim('/')
            else -> valueOf(course, "teacher", "lecturer_name")
        }
    }

    private fun valueOf(map: Map<*, *>, vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            map[key]?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }.orEmpty()
    }

    private companion object {
        const val MAX_DETAIL_CHARS = 4000
        const val SNIPPET_RADIUS = 350
        const val MAX_SNIPPETS_PER_TERM = 2
        const val MAX_TOTAL_SNIPPETS = 4
    }

    private data class TextRange(val start: Int, val end: Int)
}
