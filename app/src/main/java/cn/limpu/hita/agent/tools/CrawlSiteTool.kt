package cn.limpu.hita.agent.tools

import cn.limpu.hita.agent.remote.AgentBackendClient

class CrawlSiteTool : ReActTool {
    override fun execute(input: ReActToolInput): String? {
        return try {
            val (url, maxPages) = extractParams(input.actionInput)
            AgentBackendClient.crawlSiteSync(url, maxPages) ?: "无法启动站点爬取"
        } catch (e: Exception) {
            "启动站点爬取失败：${e.message ?: "未知错误"}"
        }
    }

    private fun extractParams(actionInput: String): Pair<String, Int> {
        val urlRegex = Regex(""""url"\s*:\s*"([^"]+)"""")
        val maxPagesRegex = Regex(""""max_pages"\s*:\s*(\d+)""")

        val url = urlRegex.find(actionInput)?.groupValues?.get(1)
            ?: actionInput.trim()
        val maxPages = maxPagesRegex.find(actionInput)?.groupValues?.get(1)?.toIntOrNull()
            ?: 10

        return Pair(url, maxPages)
    }
}
