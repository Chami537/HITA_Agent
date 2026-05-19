package com.limpu.hitax.agent.tools

import com.limpu.hitax.data.model.resource.AgentResourceCard
import com.limpu.hitax.data.model.resource.ResourceSource
import com.limpu.hitax.data.repository.ExternalResourceRepository

class SearchExternalResourceTool : ReActTool {
    override fun execute(input: ReActToolInput): String {
        val keyword = extractKeyword(input.actionInput)
        if (keyword.isBlank()) return "资料搜索失败: 无法解析查询条件"

        return try {
            val results = ExternalResourceRepository().searchCoursesSync(keyword)
            if (results.isEmpty()) return "未在 HITCS 或薪火中找到“$keyword”相关资料"

            val cards = results.take(MAX_CARD_COUNT).map { item ->
                AgentResourceCard(
                    title = item.courseName,
                    subtitle = item.category,
                    sourceTag = sourceLabel(item.source),
                    source = item.source.name,
                    path = item.path,
                    query = keyword,
                )
            }
            input.onResourceCards(cards)

            val formatted = results.take(10).mapIndexed { index, item ->
                buildString {
                    append("${index + 1}. [")
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

            "在 HITCS/薪火找到 ${results.size} 个相关资料目录:\n${formatted.joinToString("\n")}\n\n已在回答下方生成可点击资料卡片。"
        } catch (e: Exception) {
            "资料搜索失败: ${e.message}"
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

    companion object {
        private const val MAX_CARD_COUNT = 6
    }
}
