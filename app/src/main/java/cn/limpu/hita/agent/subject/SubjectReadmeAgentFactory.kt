package cn.limpu.hita.agent.subject

import cn.limpu.hita.agent.core.AgentProvider
import cn.limpu.hita.agent.core.AgentOrchestrator
import cn.limpu.hita.agent.core.AgentToolRegistry
import cn.limpu.hita.agent.core.OrchestratorAgentSession
import cn.limpu.hita.agent.core.SimpleAgentProvider

object SubjectReadmeAgentFactory {
    fun create(): AgentOrchestrator<SubjectReadmeAgentInput, SubjectReadmeAgentOutput> {
        val registry = AgentToolRegistry().apply {
            register(ResolveCourseCandidatesTool())
        }
        return AgentOrchestrator(SubjectReadmeAgentEngine(registry))
    }

    fun createProvider(): AgentProvider<SubjectReadmeAgentInput, SubjectReadmeAgentOutput> {
        return SimpleAgentProvider {
            OrchestratorAgentSession(create())
        }
    }
}
