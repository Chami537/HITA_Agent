package cn.limpu.hita.data.repository

import cn.limpu.hita.feature.timetableshare.protocol.ShareMode
import cn.limpu.hita.feature.timetableshare.protocol.SharedOccurrence
import cn.limpu.hita.feature.timetableshare.protocol.SharedPeriod
import cn.limpu.hita.feature.timetableshare.protocol.SharedTerm
import cn.limpu.hita.feature.timetableshare.protocol.SharedTimetable
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.component.VEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FriendIcsMapperTest {

    private fun fixture(mode: ShareMode) = SharedTimetable(
        "id-1", "小林", mode,
        // 2026-08-31 周一 ～ 学期末
        SharedTerm("秋季课表", "2026年秋季", TERM_START, TERM_START + 20 * WEEK),
        listOf(SharedPeriod(510, 615)),
        listOf("高等数学", "大学物理"),
        listOf(
            SharedOccurrence(TERM_START + 8 * 3600_000L, TERM_START + 9 * 3600_000L, 0, "A101"),
            SharedOccurrence(TERM_START + WEEK + 8 * 3600_000L, TERM_START + WEEK + 9 * 3600_000L, 1, null),
            SharedOccurrence(TERM_START - DAY, TERM_START - DAY + 3600_000L, 0, null)
        )
    )

    @Test
    fun fullModeMapsCourseNamesPlacesAndWeeks() {
        val events = FriendIcsMapper.map(fixture(ShareMode.FULL))
        assertEquals(3, events.size)
        // 排序按开始时间：学期前那条在最前。
        assertEquals(-1, events[0].weekNumber)
        assertEquals("高等数学", events[1].title)
        assertEquals("A101", events[1].place)
        assertEquals(1, events[1].weekNumber)
        assertEquals("class", events[1].typeName)
        assertEquals("大学物理", events[2].title)
        assertNull(events[2].place)
        assertEquals(2, events[2].weekNumber)
    }

    @Test
    fun busyModeExportsBusyBlocksWithoutCourseNames() {
        val events = FriendIcsMapper.map(fixture(ShareMode.BUSY))
        assertEquals(3, events.size)
        assertTrue(events.all { it.title == "小林·忙碌" })
        assertTrue(events.all { it.typeName == "other" })
        assertTrue(events.all { it.place == null })
    }

    @Test
    fun writerRoundTripsThroughIcsParser() {
        val events = FriendIcsMapper.map(fixture(ShareMode.FULL))
        val dir = createTempDir("ics-test")
        try {
            val file = TimetableIcsWriter.write(dir, "friend.ics", events)
            val parsed = file.inputStream().use { CalendarBuilder().build(it) }
            val parsedEvents = parsed.components.filterIsInstance<VEvent>()
            assertEquals(events.size, parsedEvents.size)
            // 两条「高等数学」里只有一条带地点；按地点属性定位。
            val withPlace = parsedEvents.filter { it.getProperty("LOCATION") != null }
            assertEquals(1, withPlace.size)
            assertEquals("A101", withPlace[0].getProperty("LOCATION").value)
            assertEquals("class", withPlace[0].getProperty("X-HITA-TYPE")?.value)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun sameNamedExportsKeepBothCalendarsAndPreviouslySharedContent() {
        val dir = createTempDir("ics-same-name")
        try {
            val events = FriendIcsMapper.map(fixture(ShareMode.FULL))
            val first = TimetableIcsWriter.write(dir, "朋友.ics", events)
            val original = first.readBytes()
            val second = TimetableIcsWriter.write(dir, "朋友.ics",
                events.map { it.copy(title = "不同课表") })
            org.junit.Assert.assertNotEquals(first.canonicalFile, second.canonicalFile)
            org.junit.Assert.assertArrayEquals(original, first.readBytes())
            val parsed = second.inputStream().use { CalendarBuilder().build(it) }
            assertTrue(parsed.components.filterIsInstance<VEvent>().all { it.summary.value == "不同课表" })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun untrustedUnicodeNamesStayInsideExportDirectory() {
        val dir = createTempDir("ics-path")
        try {
            val name = "../..\\\\" + "课表😀".repeat(80) + "\n.ics"
            val file = TimetableIcsWriter.write(dir, name, FriendIcsMapper.map(fixture(ShareMode.FULL)))
            assertEquals(dir.canonicalFile, file.canonicalFile.parentFile)
            assertTrue(file.name.toByteArray(Charsets.UTF_8).size <= 255)
            val parsed = file.inputStream().use { CalendarBuilder().build(it) }
            assertEquals("大学物理", parsed.components.filterIsInstance<VEvent>().last().summary.value)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun bulkTokenTextFallsBackLosslesslyAtMessageBoundary() {
        val limit = cn.limpu.hita.feature.timetableshare.protocol.ShareLimits.MESSAGE_CHARS
        val codec = cn.limpu.hita.feature.timetableshare.protocol.TimetableShareCodec()
        val snapshot = fixture(ShareMode.FULL).let {
            it.copy(shareId = "5fb93366-071b-4bd6-a658-ff402a25311b",
                occurrences = it.occurrences.filter { occurrence -> occurrence.startMillis >= TERM_START })
        }
        val token = codec.encode(snapshot)
        val expected = codec.decodeMessage(token)
        val withinBudget = List(limit / (token.length + 2)) { "" to token }
        assertTrue(requireNotNull(TimetableTokenText.inlineText(withinBudget)).length <= limit)
        val oversized = withinBudget + ("" to token)
        assertNull(TimetableTokenText.inlineText(oversized))
        val dir = kotlin.io.path.createTempDirectory("token-export").toFile()
        try {
            val file = TimetableTokenText.write(dir, oversized)
            val decoded = file.readText(Charsets.UTF_8).split("\n\n").map(codec::decodeMessage)
            assertEquals(oversized.size, decoded.size)
            assertTrue(decoded.all { it == expected })
        } finally {
            dir.deleteRecursively()
        }
    }

    private companion object {
        const val DAY = 24 * 3600_000L
        const val WEEK = 7 * DAY
        const val TERM_START = 1788105600000L
    }
}
