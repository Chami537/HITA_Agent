package com.limpu.hitax.agent.timetable

import com.limpu.hitax.agent.core.AgentTool
import com.limpu.hitax.agent.core.AgentToolResult
import com.limpu.hitax.data.repository.ScheduleEventCreator
import com.limpu.hitax.data.repository.TimetableRepository
import com.limpu.hitax.utils.LogUtils
import kotlin.concurrent.thread

class AddTimetableArrangementTool : AgentTool<TimetableAgentInput, TimetableAgentOutput> {
    override val name: String = "add_timetable_arrangement"

    override fun execute(
        input: TimetableAgentInput,
        onResult: (AgentToolResult<TimetableAgentOutput>) -> Unit,
    ) {
        if (input.action != TimetableAgentInput.Action.ADD_TIMETABLE_ARRANGEMENT) {
            onResult(AgentToolResult.failure("invalid action for $name"))
            return
        }

        val arrangement = input.arrangement
        if (arrangement == null) {
            onResult(AgentToolResult.failure("arrangement is required"))
            return
        }
        if (arrangement.name.isBlank()) {
            onResult(AgentToolResult.failure("arrangement name is required"))
            return
        }
        if (arrangement.toMs <= arrangement.fromMs) {
            onResult(AgentToolResult.failure("arrangement end time must be after start time"))
            return
        }

        thread(start = true) {
            try {
                LogUtils.d("[DEBUG] Starting tool execution")
                val repository = TimetableRepository(input.application)
                LogUtils.d("[DEBUG] Got repository instance")

                val timetable = input.timetableId
                    ?.let { repository.getTimetableByIdSync(it) }
                    ?: repository.getRecentTimetableSync()
                    ?: repository.ensureDefaultCustomTimetableSync()
                    LogUtils.d("[DEBUG] Got timetable: id=${timetable.id}, name=${timetable.name}")

                val result = ScheduleEventCreator.buildEvents(
                    timetable = timetable,
                    content = ScheduleEventCreator.Content(
                        name = arrangement.name,
                        place = arrangement.place.orEmpty(),
                        teacher = arrangement.teacher.orEmpty(),
                        subject = null,
                        type = arrangement.type,
                    ),
                    ranges = listOf(
                        ScheduleEventCreator.FixedRange(
                            fromMs = arrangement.fromMs,
                            toMs = arrangement.toMs,
                        )
                    ),
                    source = com.limpu.hitax.data.model.timetable.EventItem.SOURCE_AGENT,
                )
                LogUtils.d("[DEBUG] Created events: count=${result.events.size}, timetableId=${timetable.id}")

                ScheduleEventCreator.persist(result, repository)
                LogUtils.d("[DEBUG] addEventsSync called successfully")

                LogUtils.d("[DEBUG] Tool execution successful, returning result")
                onResult(
                    AgentToolResult.success(
                        TimetableAgentOutput(
                            action = input.action,
                            timetableId = timetable.id,
                            timetableName = timetable.name,
                            addedEventIds = result.events.map { it.id },
                        )
                    )
                )
            } catch (e: Exception) {
                LogUtils.e("[DEBUG] Tool execution failed", e)
                onResult(AgentToolResult.failure(e.message ?: "add timetable arrangement failed"))
            }
        }
    }
}
