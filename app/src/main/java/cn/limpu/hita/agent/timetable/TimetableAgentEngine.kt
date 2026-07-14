package cn.limpu.hita.agent.timetable

import cn.limpu.hita.agent.core.AgentEngine
import cn.limpu.hita.agent.core.AgentToolExecutionPolicy
import cn.limpu.hita.agent.core.AgentToolExecutor
import cn.limpu.hita.agent.core.AgentToolResult
import cn.limpu.hita.agent.core.AgentToolRegistry
import cn.limpu.hita.agent.core.AgentTraceEvent
import cn.limpu.hita.agent.core.AgentTraceSanitizer

class TimetableAgentEngine(
    private val toolRegistry: AgentToolRegistry,
    private val toolExecutor: AgentToolExecutor = AgentToolExecutor(),
) : AgentEngine<TimetableAgentInput, TimetableAgentOutput> {

    override fun run(
        input: TimetableAgentInput,
        onTrace: (AgentTraceEvent) -> Unit,
        onResult: (AgentToolResult<TimetableAgentOutput>) -> Unit,
    ) {
        onTrace(
            AgentTraceEvent(
                stage = "start",
                message = "正在运行课表智能体",
                payload = AgentTraceSanitizer.sanitizePayload("action=${input.action},timetableId=${input.timetableId.orEmpty()}"),
            )
        )

        val toolName = when (input.action) {
            TimetableAgentInput.Action.GET_LOCAL_TIMETABLE -> "get_local_timetable"
            TimetableAgentInput.Action.ADD_TIMETABLE_ARRANGEMENT -> "add_timetable_arrangement"
            TimetableAgentInput.Action.SEARCH_TIMETABLE -> "search_local_timetable"
        }

        val tool = toolRegistry.get<TimetableAgentInput, TimetableAgentOutput>(toolName)
        if (tool == null) {
            onResult(AgentToolResult.failure("未找到所需的课表工具"))
            return
        }

        val policy = AgentToolExecutionPolicy(
            timeoutMs = 5000L,
            retryCount = 1,
            retryDelayMs = 180L,
        )

        toolExecutor.execute(
            tool = tool,
            input = input,
            policy = policy,
            onTrace = onTrace,
        ) { result ->
            if (!result.ok) {
                onResult(AgentToolResult.failure(result.error ?: "课表工具执行失败"))
                return@execute
            }

            val output = result.data
            if (output == null) {
                onResult(AgentToolResult.failure("课表工具返回为空"))
                return@execute
            }

            onTrace(
                AgentTraceEvent(
                    stage = "result",
                    message = "课表智能体执行完成",
                    payload = AgentTraceSanitizer.sanitizePayload(
                        "action=${output.action},eventCount=${output.events.size},addedCount=${output.addedEventIds.size}"
                    ),
                )
            )
            onResult(AgentToolResult.success(output))
        }
    }
}
