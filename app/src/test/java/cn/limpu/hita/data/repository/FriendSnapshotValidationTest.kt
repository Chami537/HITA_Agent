package cn.limpu.hita.data.repository

import cn.limpu.hita.data.model.timetable.share.FriendTimetableEntity
import cn.limpu.hita.feature.timetableshare.protocol.*
import org.junit.Assert.*
import org.junit.Test

class FriendSnapshotValidationTest {
    private val codec = TimetableShareCodec()
    private val snapshot = fullFixture()
    private fun row() = FriendTimetableEntity(
        snapshot.shareId, snapshot.nickname, "本地备注", snapshot.term.termName,
        snapshot.term.startMillis, snapshot.term.endMillis, snapshot.mode.name,
        codec.canonicalJson(snapshot), codec.digest(snapshot), 1L, 2L,
    )

    @Test fun validSnapshotMatchesWithoutDependingOnLocalRemarkOrTimestamps() {
        assertTrue(row().matchesSnapshot(snapshot, codec))
        assertTrue(row().copy(remark = "另一个备注", importedAt = 7L, updatedAt = 9L).matchesSnapshot(snapshot, codec))
    }

    @Test fun rejectsMismatchedIdentityPrivacyModeTermMetadataAndDigest() {
        val valid = row()
        val corrupt = listOf(
            valid.copy(shareId = "22222222-2222-4222-8222-222222222222"),
            valid.copy(nickname = "另一个昵称"),
            valid.copy(mode = ShareMode.BUSY.name),
            valid.copy(termName = "另一个学期"),
            valid.copy(startMillis = valid.startMillis + 1),
            valid.copy(endMillis = valid.endMillis - 1),
            valid.copy(contentDigest = "0".repeat(64)),
        )
        corrupt.forEach { assertFalse(it.matchesSnapshot(snapshot, codec)) }
    }

    @Test fun changedCourseContentCannotReusePreviousStoredDigest() {
        val changed = snapshot.copy(courses = listOf("另一个课程"))
        assertFalse(row().matchesSnapshot(changed, codec))
    }
}
