package cn.limpu.hita.agent.tools

import cn.limpu.hita.agent.remote.AgentBackendClient

class WebSearchTool : ReActTool {
    override fun execute(input: ReActToolInput): String? {
        return try {
            val query = extractParam(input.actionInput)
            val result = AgentBackendClient.braveSearchSync(query)
            if (!result.ok) {
                return "搜索失败: ${result.error?.message ?: "未知错误"}"
            }
            formatResults(result.results, query)
        } catch (e: Exception) {
            "搜索失败：${e.message ?: "未知错误"}"
        }
    }

    private fun extractParam(actionInput: String): String {
        val regex = Regex(""""param"\s*:\s*"([^"]+)"""")
        return regex.find(actionInput)?.groupValues?.get(1) ?: actionInput.trim()
    }

    private fun formatResults(results: List<Map<String, Any>>, query: String): String {
        if (results.isEmpty()) return "未找到“$query”的网页搜索结果"
        return results.take(5).mapIndexed { index, r ->
            val title = r["title"]?.toString() ?: "搜索结果 ${index + 1}"
            val url = r["url"]?.toString() ?: ""
            val desc = r["content"]?.toString()?.ifBlank { r["description"]?.toString() } ?: ""
            "$title\n$url\n$desc"
        }.joinToString("\n\n")
    }
}
