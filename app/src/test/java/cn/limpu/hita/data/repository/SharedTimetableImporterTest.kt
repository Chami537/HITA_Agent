package cn.limpu.hita.data.repository

import cn.limpu.hita.data.model.timetable.EventItem
import cn.limpu.hita.feature.timetableshare.protocol.SharedOccurrence
import cn.limpu.hita.feature.timetableshare.protocol.SharedPeriod
import cn.limpu.hita.feature.timetableshare.protocol.SharedTerm
import cn.limpu.hita.feature.timetableshare.protocol.SharedTimetable
import cn.limpu.hita.feature.timetableshare.protocol.ShareMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedTimetableImporterTest {

    private val term = SharedTerm(
        timetableName = "秋季课表",
        termName = "2026 秋季",
        startMillis = 1_700_000_000_000L,
        endMillis = 1_708_000_000_000L,
    )
    private val periods = listOf(SharedPeriod(510, 615), SharedPeriod(630, 735))

    private fun fullSnapshot() = SharedTimetable(
        shareId = "5fb93366-071b-4bd6-a658-ff402a25311b",
        nickname = "小王",
        mode = ShareMode.FULL,
        term = term,
        periods = periods,
        courses = listOf("数据结构", "操作系统"),
        occurrences = listOf(
            SharedOccurrence(1_700_005_000_000L, 1_700_006_000_000L, courseIndex = 0, place = "A101"),
            SharedOccurrence(1_700_105_000_000L, 1_700_106_000_000L, courseIndex = 1, place = null),
        ),
    )

    @Test
    fun `full snapshot becomes timetable subjects and class events`() {
        val bundle = SharedTimetableImporter.buildBundleFromShare(fullSnapshot())

        assertEquals("秋季课表", bundle.timetable.name)
        assertEquals(term.startMillis, bundle.timetable.startTime.time)
        assertEquals(term.endMillis, bundle.timetable.endTime.time)
        assertEquals(2, bundle.timetable.scheduleStructure.size)
        assertEquals(510, bundle.timetable.scheduleStructure[0].from.hour * 60 + bundle.timetable.scheduleStructure[0].from.minute)

        assertEquals(2, bundle.subjects.size)
        assertTrue(bundle.subjects.all { it.timetableId == bundle.timetable.id })

        assertEquals(2, bundle.events.size)
        assertTrue(bundle.events.all { it.type == EventItem.TYPE.CLASS })
        assertTrue(bundle.events.all { it.source == EventItem.SOURCE_SHARE_IMPORT })
        assertTrue(bundle.events.all { it.timetableId == bundle.timetable.id })
        val first = bundle.events.first { it.name == "数据结构" }
        assertEquals("A101", first.place)
        assertEquals(1_700_005_000_000L, first.from.time)
        // subject 关联与颜色非零
        assertTrue(bundle.subjects.first { it.name == "数据结构" }.id == first.subjectId)
        assertTrue(bundle.subjects.all { it.color != 0 })
    }

    @Test
    fun `busy snapshot is rejected for my timetable`() {
        val busy = fullSnapshot().copy(mode = ShareMode.BUSY, courses = emptyList(),
            occurrences = listOf(SharedOccurrence(1_700_005_000_000L, 1_700_006_000_000L)))
        var thrown = false
        try {
            SharedTimetableImporter.buildBundleFromShare(busy)
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test(expected = cn.limpu.hita.feature.timetableshare.protocol.ShareFormatException::class)
    fun `invalid course index rejects the entire import`() {
        val snapshot = fullSnapshot().copy(
            occurrences = fullSnapshot().occurrences + SharedOccurrence(
                1_700_205_000_000L, 1_700_206_000_000L, courseIndex = 99, place = "X"
            )
        )
        SharedTimetableImporter.buildBundleFromShare(snapshot)
    }

    @Test
    fun `ics bundle becomes friend snapshot with stable share id`() {
        val bundle = SharedTimetableImporter.buildBundleFromShare(fullSnapshot())
        val snapshot = SharedTimetableImporter.buildSnapshotFromIcs(bundle, "friend.ics")

        assertEquals(ShareMode.FULL, snapshot.mode)
        assertEquals("friend.ics", snapshot.nickname)
        assertEquals(listOf("数据结构", "操作系统"), snapshot.courses)
        assertEquals(2, snapshot.occurrences.size)
        assertEquals(term.startMillis, snapshot.term.startMillis)
        assertEquals(2, snapshot.periods.size)
        assertEquals(510, snapshot.periods[0].startMinute)
        // 同名文件 → 同一 shareId(重复导入视为更新);不同名 → 不同条目。
        val again = SharedTimetableImporter.buildSnapshotFromIcs(bundle, "friend.ics")
        val other = SharedTimetableImporter.buildSnapshotFromIcs(bundle, "other.ics")
        assertEquals(snapshot.shareId, again.shareId)
        assertNotEquals(snapshot.shareId, other.shareId)
    }

    @Test
    fun `share snapshot round trip preserves courses and occurrences`() {
        val original = fullSnapshot()
        val bundle = SharedTimetableImporter.buildBundleFromShare(original)
        val back = SharedTimetableImporter.buildSnapshotFromIcs(bundle, "x")
        assertEquals(original.courses, back.courses)
        assertEquals(original.occurrences.size, back.occurrences.size)
        assertEquals(original.occurrences.map { it.startMillis }.sorted(),
            back.occurrences.map { it.startMillis }.sorted())
    }

    @Test(expected = cn.limpu.hita.feature.timetableshare.protocol.ShareFormatException::class)
    fun `invalid period rejects the entire import instead of substituting defaults`() {
        SharedTimetableImporter.buildBundleFromShare(fullSnapshot().copy(periods = emptyList()))
    }

    @Test(expected = cn.limpu.hita.feature.timetableshare.protocol.ShareFormatException::class)
    fun `reversed occurrence rejects the entire import`() {
        val snapshot = fullSnapshot()
        SharedTimetableImporter.buildBundleFromShare(snapshot.copy(
            occurrences = listOf(snapshot.occurrences.first().let { it.copy(endMillis = it.startMillis) })
        ))
    }

    @Test
    fun `same named subjects retain their own course indices on reexport`() {
        val snapshot = fullSnapshot().copy(courses = listOf("数学", "数学"))
        val bundle = SharedTimetableImporter.buildBundleFromShare(snapshot)
        val exported = SharedTimetableImporter.buildSnapshotFromIcs(bundle, "朋友")
        assertEquals(listOf(0, 1), exported.occurrences.map { it.courseIndex })
        assertEquals(snapshot.occurrences.map { it.startMillis }, exported.occurrences.map { it.startMillis })
    }

    private val icsHeader = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//HITA//EN\r\n" +
        "BEGIN:VEVENT\r\nUID:course\r\nDTSTAMP:20261101T000000Z\r\n" +
        "DTSTART:20261102T003000Z\r\nDTEND:20261102T021500Z\r\nSUMMARY:数学\r\n"

    @Test
    fun `friend ICS reader closes its stream and preserves absolute course time`() {
        var closed = false
        val bytes = (icsHeader + "END:VEVENT\r\nEND:VCALENDAR\r\n").toByteArray(Charsets.UTF_8)
        val input = object : java.io.ByteArrayInputStream(bytes) {
            override fun close() { closed = true; super.close() }
        }
        val snapshot = SharedTimetableImporter.readFriendIcs(input, "朋友.ics", "朋友")
        assertTrue(closed)
        assertEquals(listOf("数学"), snapshot.courses)
        assertEquals(java.time.Instant.parse("2026-11-02T00:30:00Z").toEpochMilli(),
            snapshot.occurrences.single().startMillis)
    }

    @Test
    fun `oversized friend ICS is rejected before unbounded parsing and closes stream`() {
        val limit = SharedTimetableImporter.FRIEND_ICS_BYTES
        val bytes = ByteArray(limit + 1024) { 'a'.code.toByte() }
        (icsHeader + "DESCRIPTION:").toByteArray(Charsets.UTF_8).copyInto(bytes)
        var readCount = 0
        var closed = false
        val input = object : java.io.ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, length).also { if (it > 0) readCount += it }
            override fun close() { closed = true; super.close() }
        }
        try {
            SharedTimetableImporter.readFriendIcs(input, "朋友.ics", "朋友")
            org.junit.Assert.fail("oversized input must be rejected")
        } catch (error: cn.limpu.hita.feature.timetableshare.protocol.ShareFormatException) {
            assertEquals(cn.limpu.hita.feature.timetableshare.protocol.ShareError.TOO_LONG, error.error)
        }
        assertTrue(closed)
        assertEquals(limit + 1, readCount)
    }
}
