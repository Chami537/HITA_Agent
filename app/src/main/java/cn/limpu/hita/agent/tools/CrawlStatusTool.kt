package cn.limpu.hita.agent.tools

import cn.limpu.hita.agent.remote.AgentBackendClient

class CrawlStatusTool : ReActTool {
    override fun execute(input: ReActToolInput): String? {
        return try {
            val taskId = extractTaskId(input.actionInput)
            AgentBackendClient.crawlStatusSync(taskId) ?: "无法获取爬取状态"
        } catch (e: Exception) {
            "获取爬取状态失败：${e.message ?: "未知错误"}"
        }
    }

    private fun extractTaskId(actionInput: String): String {
        val regex = Regex(""""task_id"\s*:\s*"([^"]+)"""")
        return regex.find(actionInput)?.groupValues?.get(1) ?: actionInput.trim()
    }
}
