package cn.limpu.hita.agent.timetable

import cn.limpu.hita.agent.core.AgentOrchestrator
import cn.limpu.hita.agent.core.AgentProvider
import cn.limpu.hita.agent.core.AgentToolRegistry
import cn.limpu.hita.agent.core.OrchestratorAgentSession
import cn.limpu.hita.agent.core.SimpleAgentProvider

object TimetableAgentFactory {
    fun create(): AgentOrchestrator<TimetableAgentInput, TimetableAgentOutput> {
        val registry = AgentToolRegistry().apply {
            register(GetLocalTimetableTool())
            register(AddTimetableArrangementTool())
            register(SearchLocalTimetableTool())
        }
        return AgentOrchestrator(TimetableAgentEngine(registry))
    }

    fun createProvider(): AgentProvider<TimetableAgentInput, TimetableAgentOutput> {
        return SimpleAgentProvider {
            OrchestratorAgentSession(create())
        }
    }
}
