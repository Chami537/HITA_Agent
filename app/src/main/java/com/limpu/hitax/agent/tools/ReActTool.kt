package com.limpu.hitax.agent.tools

import android.app.Application
import com.limpu.hitax.agent.core.AgentProvider
import com.limpu.hitax.agent.core.AgentTraceEvent
import com.limpu.hitax.agent.timetable.TimetableAgentInput
import com.limpu.hitax.agent.timetable.TimetableAgentOutput
import com.limpu.hitax.data.model.resource.AgentResourceCard

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
