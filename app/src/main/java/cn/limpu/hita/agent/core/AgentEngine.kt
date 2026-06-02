package cn.limpu.hita.agent.core

interface AgentEngine<I, O> {
    fun run(
        input: I,
        onTrace: (AgentTraceEvent) -> Unit,
        onResult: (AgentToolResult<O>) -> Unit,
    )
}
