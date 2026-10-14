package cn.limpu.hita.ui.timetable.share

import cn.limpu.hita.feature.timetableshare.protocol.ShareMode
import org.junit.Assert.*
import org.junit.Test

class ShareFormStateTest {
    @Test fun staleGenerationDoesNotReplaceCurrentForm() {
        val old = ShareFormState("a", "好友")
        val edited = old.edit(id = "b")
        assertNull(edited.accept(old.revision, "HITA1:old").token)
        assertEquals(ShareMode.FULL, old.mode)
    }
    @Test fun editingInvalidatesTokenAndStopsGenerating() {
        val old = ShareFormState("a", "好友", token = "old", generating = true)
        val edited = old.edit(name = "新昵称")
        assertNull(edited.token)
        assertFalse(edited.generating)
        assertEquals(old.revision + 1, edited.revision)
    }
    @Test fun everyShareChoiceInvalidatesDisplayedQrToken() {
        val original = ShareFormState("a", "好友", token = "HITA2:old")
        assertNull(original.edit(id = "b").token)
        assertNull(original.edit(name = "新昵称").token)
        assertNull(original.edit(selectedMode = ShareMode.BUSY).token)
    }
    @Test fun currentGenerationIsAcceptedAndNicknameUsesCodePoints() {
        val state = ShareFormState("a", "😀".repeat(40), generating = true)
        assertTrue(state.nicknameValid)
        assertFalse(state.edit(name = "😀".repeat(41)).nicknameValid)
        assertFalse(state.edit(name = " ").nicknameValid)
        assertEquals("token", state.accept(state.revision, "token").token)
        assertFalse(state.accept(state.revision, "token").generating)
    }
}
