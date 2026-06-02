package cn.limpu.hita.agent.timetable

import cn.limpu.hita.agent.core.AgentTool
import cn.limpu.hita.agent.core.AgentToolResult
import cn.limpu.hita.data.repository.TimetableRepository
import cn.limpu.hita.utils.LogUtils
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread

class SearchLocalTimetableTool : AgentTool<TimetableAgentInput, TimetableAgentOutput> {
    override val name: String = "search_local_timetable"

    override fun execute(
        input: TimetableAgentInput,
        onResult: (AgentToolResult<TimetableAgentOutput>) -> Unit,
    ) {
        if (input.action != TimetableAgentInput.Action.SEARCH_TIMETABLE) {
            onResult(AgentToolResult.failure("invalid action for $name"))
            return
        }

        val keyword = input.keyword?.trim().orEmpty()
        if (keyword.isEmpty()) {
            onResult(AgentToolResult.failure("搜索关键词不能为空"))
            return
        }

        thread(start = true) {
            try {
                val repository = TimetableRepository(input.application)
                LogUtils.d("Search timetable events by keyword: $keyword")

                val events = repository.searchEventsByKeywordSync(keyword)
                    .sortedBy { it.from.time }

                LogUtils.d("Found ${events.size} events matching '$keyword'")

                onResult(
                    AgentToolResult.success(
                        TimetableAgentOutput(
                            action = input.action,
                            timetableId = events.firstOrNull()?.timetableId ?: "",
                            timetableName = "全部课表",
                            events = events.map {
                                TimetableEventSnapshot(
                                    id = it.id,
                                    timetableId = it.timetableId,
                                    name = it.name,
                                    fromMs = it.from.time,
                                    toMs = it.to.time,
                                    place = it.place.orEmpty(),
                                    teacher = it.teacher.orEmpty(),
                                    type = it.type,
                                    source = it.source,
                                )
                            },
                        )
                    )
                )
            } catch (e: Exception) {
                LogUtils.e("SearchLocalTimetableTool error: ${e.message}", e)
                onResult(AgentToolResult.failure(e.message ?: "search timetable failed"))
            }
        }
    }
}
