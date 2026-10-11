package cn.limpu.hita.ui.main.timetable.compare

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class ComparisonStateTest {
    private val config = ComparisonConfig("own", "friend")
    private fun ready(config: ComparisonConfig = this.config): ComparisonUiState.Ready {
        val schedule = cn.limpu.hita.feature.timetablecompare.ComparisonSchedule(
            cn.limpu.hita.feature.timetablecompare.TimeSpan(1, 2), emptyList(), emptyList())
        val snapshot = cn.limpu.hita.feature.timetableshare.protocol.SharedTimetable(
            "friend", "Friend", cn.limpu.hita.feature.timetableshare.protocol.ShareMode.BUSY,
            cn.limpu.hita.feature.timetableshare.protocol.SharedTerm("table", "term", 1, 2),
            emptyList(), emptyList(), emptyList())
        val row = cn.limpu.hita.data.model.timetable.share.FriendTimetableEntity(
            "friend", "Friend", null, "term", 1, 2, "BUSY", "", "", 0, 0)
        return ComparisonUiState.Ready(config, cn.limpu.hita.data.repository.ComparisonSource(
            cn.limpu.hita.data.model.timetable.Timetable(), emptyList(), row, snapshot, schedule, schedule),
            cn.limpu.hita.feature.timetablecompare.ComparisonResult(emptyList()))
    }
    @Test fun readyThenErrorClearsOverlayButAllowsRecoveryWithSameRequest() {
        val session = ComparisonSession()
        val request = session.reload(config, 10)
        assertTrue(session.accept(request, ready()))
        assertTrue(session.accept(request, ComparisonUiState.Error(42)))
        assertEquals(ComparisonUiState.Error(42), session.state.value)
        assertEquals(config, session.config.value)
        assertTrue(session.accept(request, ready()))
        assertTrue(session.state.value is ComparisonUiState.Ready)
    }
    @Test fun switchingFriendAndRangeRejectsPreviouslyReadyResult() {
        val session = ComparisonSession()
        val old = session.reload(config, 10)
        session.accept(old, ready())
        session.reload(config.copy(friendId = "other", startMinute = 600), 10)
        assertFalse(session.accept(old, ready()))
        assertEquals(ComparisonUiState.Loading, session.state.value)
    }
    @Test fun missingSourceDisablesConfigAndRejectsOldReady() {
        val session = ComparisonSession()
        val old = session.reload(config, 10)
        session.disable(ComparisonUiState.Error(42))
        assertNull(session.config.value)
        assertEquals(ComparisonUiState.Error(42), session.state.value)
        assertFalse(session.accept(old, ready()))
    }
    @Test fun disabledHandleIgnoresStaleConfiguration() {
        val saved = SavedStateHandle()
        saveComparisonConfig(saved, config)
        saved["comparison.enabled"] = false
        assertNull(restoreComparisonConfig(saved))
    }
    @Test fun oldWeekAndDisabledRequestsAreRejected() {
        val session = ComparisonSession()
        val old = session.reload(config, 10)
        val current = session.reload(config, 20)
        assertFalse(session.accept(old, ComparisonUiState.Error(1)))
        assertTrue(session.accept(current, ComparisonUiState.Error(2)))
        session.disable()
        assertFalse(session.accept(current, ComparisonUiState.Error(3)))
        assertEquals(ComparisonUiState.Off, session.state.value)
    }
    @Test fun reloadImmediatelyClearsPriorStateAndKeepsConfig() {
        val session = ComparisonSession()
        val first = session.reload(config, 10)
        session.accept(first, ComparisonUiState.Error(1))
        session.reload(config.copy(startMinute = 600), 10)
        assertEquals(ComparisonUiState.Loading, session.state.value)
        assertEquals(600, session.config.value!!.startMinute)
    }
    @Test fun viewportRangeFiltersAndGroupsPersistAndDisableDoesNotRestore() {
        val saved = SavedStateHandle()
        val changed = config.copy(startMinute = 600, endMinute = 1000, showFree = false,
            rangeCustomized = true, personalGroupIds = listOf("group"),
            excludedOwnCourses = mapOf("own" to setOf("math")),
            excludedFriendCourses = mapOf("friend" to setOf(1, 3)),
            excludedFriendCourseKeys = mapOf("friend" to setOf("0:math", "1:math")))
        saveComparisonConfig(saved, changed)
        assertEquals(changed, restoreComparisonConfig(saved))
        saveComparisonConfig(saved, null)
        assertNull(restoreComparisonConfig(saved))
    }
    @Test fun invalidRangeAndBlankIdsAreRejected() {
        assertFalse(config.copy(startMinute = 1000, endMinute = 900).isValid())
        assertFalse(config.copy(personalId = "").isValid())
        // 旧版本保存的窄范围键被忽略：恢复结果使用整天默认窗口而不是过期范围。
        val saved = SavedStateHandle(mapOf("comparison.enabled" to true,
            "comparison.personal" to "own", "comparison.friend" to "friend",
            "comparison.start" to 1400, "comparison.end" to 500))
        assertEquals(config, restoreComparisonConfig(saved))
    }
    @Test fun groupOnlyConfigRestoresWithoutFreezingMemberIds() {
        val saved = SavedStateHandle()
        val grouped = ComparisonConfig("", "friend", personalGroupIds = listOf("group"))
        assertTrue(grouped.isValid())
        assertTrue(grouped.selectedPersonalIds.isEmpty())
        saveComparisonConfig(saved, grouped)
        assertEquals(grouped, restoreComparisonConfig(saved))
    }
    @Test fun selectionDelimiterKeepsMultipleUuidIdsIntact() {
        val ids = listOf("d924c138-fc3e-475f-a7ee-43bd26a88ac6", "836c0051-129c-45ca-9f19-401a8ca0d5c4")
        val raw = ids.joinToString(COMPARISON_SELECTION_SEPARATOR)
        assertEquals(ids, raw.split(COMPARISON_SELECTION_SEPARATOR).filter { it.isNotBlank() })
        assertEquals(emptyList<String>(), "".split(COMPARISON_SELECTION_SEPARATOR).filter { it.isNotBlank() })
    }
    @Test fun draftFilterSaverRoundTripsMultipleParticipants() {
        val selections = mapOf("A" to setOf("course1", "course2"), "B" to emptySet())
        assertEquals(selections, decodeComparisonSets(encodeComparisonSets(selections)))
    }
    @Test fun filterSaverEscapesSeparatorsAndPercentWithoutChangingCourseIdentity() {
        val selections = mapOf("A" to setOf("0:math\u001dextra", "1:name\u001eextra", "0:%1D"))
        assertEquals(selections, decodeComparisonSets(encodeComparisonSets(selections)))
    }
}
