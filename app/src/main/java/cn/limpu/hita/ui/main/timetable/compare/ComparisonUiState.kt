package cn.limpu.hita.ui.main.timetable.compare

import androidx.lifecycle.SavedStateHandle
import cn.limpu.hita.data.repository.ComparisonSource
import cn.limpu.hita.feature.timetablecompare.ComparisonResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ComparisonConfig(
    val personalId: String,
    val friendId: String,
    val startMinute: Int = 510,
    val endMinute: Int = 1350,
    val showFree: Boolean = true,
    val showConflicts: Boolean = true,
    val personalIds: List<String> = emptyList(),
    val friendIds: List<String> = emptyList(),
    val excludedOwnCourses: Map<String, Set<String>> = emptyMap(),
    val excludedFriendCourses: Map<String, Set<Int>> = emptyMap(),
    /**
     * 用户是否在配置面板确认过范围。false（恢复/默认）时网格忽略 start/end，
     * 直接覆盖课表作息的首节至末节——三校区作息不同（本部/威海 08:00 起），
     * 固定默认值会裁掉非深圳校区的首节。
     */
    val rangeCustomized: Boolean = false,
    val personalGroupIds: List<String> = emptyList(),
    val excludedFriendCourseKeys: Map<String, Set<String>> = emptyMap(),
) {
    val selectedPersonalIds: List<String>
        get() = personalIds.ifEmpty { listOf(personalId).filter { it.isNotBlank() } }.distinct()
    val selectedFriendIds: List<String>
        get() = friendIds.ifEmpty { listOf(friendId).filter { it.isNotBlank() } }.distinct()

    fun isValid() = (selectedPersonalIds.isNotEmpty() || personalGroupIds.isNotEmpty()) &&
        selectedFriendIds.isNotEmpty() && startMinute in 0..1440 && endMinute in 0..1440 && startMinute < endMinute
}
sealed interface ComparisonUiState {
    data object Off : ComparisonUiState
    data object Loading : ComparisonUiState
    data class Ready(val config: ComparisonConfig, val source: ComparisonSource,
        val result: ComparisonResult) : ComparisonUiState
    data class Error(val messageResource: Int) : ComparisonUiState
}

/** Shared request guard used by the ViewModel; every reload clears the prior result immediately. */
internal class ComparisonSession {
    data class Request(val revision: Long, val config: ComparisonConfig, val monday: Long)
    private var revision = 0L
    private var monday: Long? = null
    private val mutableConfig = MutableStateFlow<ComparisonConfig?>(null)
    private val mutableState = MutableStateFlow<ComparisonUiState>(ComparisonUiState.Off)
    val config = mutableConfig.asStateFlow()
    val state = mutableState.asStateFlow()
    fun reload(config: ComparisonConfig, monday: Long): Request {
        require(config.isValid())
        revision++
        this.monday = monday
        mutableConfig.value = config
        mutableState.value = ComparisonUiState.Loading
        return Request(revision, config, monday)
    }
    fun accept(request: Request, state: ComparisonUiState): Boolean {
        if (request.revision != revision || request.config != config.value || request.monday != monday) return false
        mutableState.value = state
        return true
    }
    fun disable(state: ComparisonUiState = ComparisonUiState.Off) {
        revision++
        mutableConfig.value = null
        mutableState.value = state
    }
}

internal fun saveComparisonConfig(handle: SavedStateHandle, config: ComparisonConfig?) {
    handle["comparison.enabled"] = config != null
    if (config == null) return
    handle["comparison.personal"] = config.personalId
    handle["comparison.friend"] = config.friendId
    handle["comparison.free"] = config.showFree
    handle["comparison.conflicts"] = config.showConflicts
    handle["comparison.start"] = config.startMinute
    handle["comparison.end"] = config.endMinute
    handle["comparison.rangeCustomized"] = config.rangeCustomized
    handle["comparison.groups"] = config.personalGroupIds.joinToString(UNIT)
    handle["comparison.excludeFriendKeys"] = encodeComparisonSets(config.excludedFriendCourseKeys)
    handle["comparison.personals"] = config.personalIds.joinToString(UNIT)
    handle["comparison.friends"] = config.friendIds.joinToString(UNIT)
    handle["comparison.excludeOwn"] = encodeComparisonSets(config.excludedOwnCourses)
    handle["comparison.excludeFriend"] = encodeComparisonSets(config.excludedFriendCourses.mapValues { (_, indexes) ->
        indexes.map { it.toString() }.toSet()
    })
}
internal fun restoreComparisonConfig(handle: SavedStateHandle): ComparisonConfig? {
    if (handle.get<Boolean>("comparison.enabled") != true) return null
    val personal = handle.get<String>("comparison.personal") ?: return null
    val friend = handle.get<String>("comparison.friend") ?: return null
    val excludedFriends = decodeComparisonSets(handle.get<String>("comparison.excludeFriend")).mapValues { (_, values) ->
        values.mapNotNull { it.toIntOrNull() }.toSet()
    }
    return ComparisonConfig(
        personal,
        friend,
        if (handle.get<Boolean>("comparison.rangeCustomized") == true) handle.get<Int>("comparison.start") ?: 510 else 510,
        if (handle.get<Boolean>("comparison.rangeCustomized") == true) handle.get<Int>("comparison.end") ?: 1350 else 1350,
        handle.get<Boolean>("comparison.free") ?: true,
        handle.get<Boolean>("comparison.conflicts") ?: true,
        personalIds = decodeList(handle.get<String>("comparison.personals")),
        friendIds = decodeList(handle.get<String>("comparison.friends")),
        excludedOwnCourses = decodeComparisonSets(handle.get<String>("comparison.excludeOwn")),
        excludedFriendCourses = excludedFriends,
        rangeCustomized = handle.get<Boolean>("comparison.rangeCustomized") ?: false,
        personalGroupIds = decodeList(handle.get<String>("comparison.groups")),
        excludedFriendCourseKeys = decodeComparisonSets(handle.get<String>("comparison.excludeFriendKeys")),
    ).takeIf { it.isValid() }
}

private const val UNIT = "\u001f"
private const val RECORD = "\u001e"
private const val FIELD = "\u001d"

internal fun encodeComparisonSets(map: Map<String, Set<String>>): String =
    map.entries.joinToString(RECORD) { (id, values) ->
        (listOf(id) + values.sorted()).joinToString(FIELD) { value ->
            value.replace("%", "%25").replace(FIELD, "%1D").replace(RECORD, "%1E")
        }
    }

private fun decodeList(raw: String?): List<String> =
    raw?.split(UNIT)?.filter { it.isNotBlank() }.orEmpty()

internal fun decodeComparisonSets(raw: String?): Map<String, Set<String>> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw.split(RECORD).mapNotNull { record ->
        val parts = record.split(FIELD).map {
            it.replace("%1D", FIELD).replace("%1E", RECORD).replace("%25", "%")
        }
        val id = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        id to parts.drop(1).filter { it.isNotBlank() }.toSet()
    }.toMap()
}
