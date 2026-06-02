package cn.limpu.hita.agent.tools

import android.app.Application
import cn.limpu.hita.agent.core.AgentProvider
import cn.limpu.hita.agent.core.AgentTraceEvent
import cn.limpu.hita.agent.timetable.TimetableAgentInput
import cn.limpu.hita.agent.timetable.TimetableAgentOutput
import cn.limpu.hita.data.model.resource.AgentResourceCard

data class ReActToolInput(
    val actionInput: String,
    val userMessage: String,
    val application: Application,
    val timetableId: String?,
    val agentProvider: AgentProvider<TimetableAgentInput, TimetableAgentOutput>,
    val onTrace: (AgentTraceEvent) -> Unit,
    val onResourceCards: (List<AgentResourceCard>) -> Unit = {},
)

fun interface ReActTool {
    fun execute(input: ReActToolInput): String?
}
