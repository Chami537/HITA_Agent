package com.limpu.hitax.agent.tools

import com.limpu.hitax.data.model.resource.AgentResourceCard
import com.limpu.hitax.data.model.resource.CourseResourceItem
import com.limpu.hitax.data.model.resource.ExternalCourseItem
import com.limpu.hitax.data.model.resource.ExternalResourceEntry
import com.limpu.hitax.data.model.resource.ResourceSource
import com.limpu.hitax.data.repository.ExternalResourceRepository
import com.limpu.hitax.data.repository.HoaRepository
import com.limpu.hitax.data.source.web.HITCSWebSource

class SearchExternalResourceTool : ReActTool {
    override fun execute(input: ReActToolInput): String {
        val keyword = extractKeyword(input.actionInput)
        if (keyword.isBlank()) return "资料搜索失败: 无法解析查询条件"

        val hoaResults = runCatching { HoaRepository().searchCoursesSync(keyword) }.getOrElse { emptyList() }
        val externalResults = runCatching { ExternalResourceRepository().searchCoursesSync(keyword) }.getOrElse { emptyList() }
        if (hoaResults.isEmpty() && externalResults.isEmpty()) {
            return "未在 HOA、HITCS 或薪火中找到“$keyword”相关资料"
        }

        val cards = buildList {
            addAll(hoaResults.take(MAX_CARD_COUNT_PER_SOURCE).map { it.toCard(keyword) })
            addAll(externalResults.take(MAX_CARD_COUNT_PER_SOURCE * 2).map { it.toCard(keyword) })
        }.take(MAX_TOTAL_CARD_COUNT)
        input.onResourceCards(cards)

        val formatted = buildList {
            hoaResults.take(5).forEach { item ->
                add(formatHoaResult(item))
            }
            externalResults.take(10 - size).forEach { item ->
                add(formatExternalResult(item))
            }
        }
        val hitcsDetails = buildHitcsDetails(externalResults)

        return buildString {
            append("在 HOA/深圳资源找到 ${hoaResults.size} 个课程资源，在 HITCS/薪火找到 ${externalResults.size} 个资料目录。")
            if (formatted.isNotEmpty()) {
                append("\n")
                append(formatted.mapIndexed { index, value -> "${index + 1}. $value" }.joinToString("\n"))
            }
            if (hitcsDetails.isNotBlank()) {
                append("\n\nHITCS 命中目录内容预览：\n")
                append(hitcsDetails)
            }
            append("\n\n已在回答下方生成可点击资料卡片。")
        }
    }

    private fun extractKeyword(actionInput: String): String {
        val keys = listOf("query", "keyword", "course_name", "param")
        keys.forEach { key ->
            val regex = Regex(""""$key"\s*:\s*"([^"]+)"""")
            val value = regex.find(actionInput)?.groupValues?.getOrNull(1)?.trim()
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return actionInput.trim().removeSurrounding("{", "}").trim('"')
    }

    private fun sourceLabel(source: ResourceSource): String {
        return when (source) {
            ResourceSource.HITCS -> "HITCS"
            ResourceSource.FIREWORKS -> "薪火"
        }
    }

    private fun CourseResourceItem.toCard(keyword: String): AgentResourceCard {
        return AgentResourceCard(
            title = courseName.ifBlank { courseCode.ifBlank { repoName } },
            subtitle = listOf(
                courseCode.takeIf { it.isNotBlank() },
                teachers.take(2).joinToString(" / ").takeIf { it.isNotBlank() },
            ).filterNotNull().joinToString(" · "),
            sourceTag = "HOA",
            source = SOURCE_HOA,
            path = repoName,
            query = keyword,
            repoName = repoName,
            courseCode = courseCode,
            repoType = repoType.ifBlank { "normal" },
        )
    }

    private fun ExternalCourseItem.toCard(keyword: String): AgentResourceCard {
        return AgentResourceCard(
            title = courseName,
            subtitle = category,
            sourceTag = sourceLabel(source),
            source = source.name,
            path = path,
            query = keyword,
        )
    }

    private fun formatHoaResult(item: CourseResourceItem): String {
        return buildString {
            append("[HOA] ")
            append(item.courseName.ifBlank { item.courseCode.ifBlank { item.repoName } })
            if (item.courseCode.isNotBlank()) {
                append(" · ")
                append(item.courseCode)
            }
            val teachers = item.teachers.take(2).joinToString(" / ")
            if (teachers.isNotBlank()) {
                append(" · 教师: ")
                append(teachers)
            }
        }
    }

    private fun formatExternalResult(item: ExternalCourseItem): String {
        return buildString {
            append("[")
            append(sourceLabel(item.source))
            append("] ")
            append(item.courseName)
            if (item.category.isNotBlank()) {
                append(" · ")
                append(item.category)
            }
            if (item.path.isNotBlank()) {
                append("\n   路径: ")
                append(item.path)
            }
        }
    }

    private fun buildHitcsDetails(results: List<ExternalCourseItem>): String {
        val hitcsResults = results.filter { it.source == ResourceSource.HITCS }.take(MAX_HITCS_DETAIL_COURSES)
        if (hitcsResults.isEmpty()) return ""

        return hitcsResults.mapNotNull { course ->
            runCatching {
                val entries = HITCSWebSource.listDirectorySync(course.path)
                formatHitcsDirectoryPreview(course, entries)
            }.getOrNull()
        }.joinToString("\n\n")
    }

    private fun formatHitcsDirectoryPreview(
        course: ExternalCourseItem,
        entries: List<ExternalResourceEntry>,
    ): String {
        val directories = entries.filter { it.isDir }.take(MAX_HITCS_ENTRY_COUNT)
        val files = entries.filterNot { it.isDir }.take(MAX_HITCS_ENTRY_COUNT)
        val markdownFiles = entries.filter {
            !it.isDir && it.name.endsWith(".md", ignoreCase = true)
        }.take(MAX_HITCS_MARKDOWN_COUNT)

        return buildString {
            append("• ")
            append(course.courseName)
            append("（")
            append(course.path)
            append("）")
            if (directories.isNotEmpty()) {
                append("\n  文件夹: ")
                append(directories.joinToString("、") { it.name })
            }
            if (files.isNotEmpty()) {
                append("\n  文件: ")
                append(files.joinToString("、") { it.name })
            }
            markdownFiles.forEach { file ->
                val preview = runCatching {
                    HITCSWebSource.readMarkdownPreviewSync(file.path, MAX_MARKDOWN_PREVIEW_CHARS)
                }.getOrNull()?.cleanMarkdownPreview().orEmpty()
                if (preview.isNotBlank()) {
                    append("\n  Markdown 摘要 ${file.name}: ")
                    append(preview)
                }
            }
        }
    }

    private fun String.cleanMarkdownPreview(): String {
        return lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .take(MAX_MARKDOWN_PREVIEW_CHARS)
    }

    companion object {
        const val SOURCE_HOA = "HOA"
        private const val MAX_CARD_COUNT_PER_SOURCE = 4
        private const val MAX_TOTAL_CARD_COUNT = 10
        private const val MAX_HITCS_DETAIL_COURSES = 3
        private const val MAX_HITCS_ENTRY_COUNT = 8
        private const val MAX_HITCS_MARKDOWN_COUNT = 2
        private const val MAX_MARKDOWN_PREVIEW_CHARS = 5000
    }
}
