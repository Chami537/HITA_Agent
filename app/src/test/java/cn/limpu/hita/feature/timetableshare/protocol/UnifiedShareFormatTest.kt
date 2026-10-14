package cn.limpu.hita.feature.timetableshare.protocol

import cn.limpu.hita.data.model.timetable.EventItem
import cn.limpu.hita.data.model.timetable.Timetable
import cn.limpu.hita.data.repository.IcsImportBundleBuilder
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Location
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class UnifiedShareFormatTest {
    private val codec = TimetableShareCodec()
    private fun millis(value: String) = LocalDateTime.parse(value).atZone(ShareLimits.ZONE).toInstant().toEpochMilli()

    @Test fun easAndIcsEventsUseTheSameCompactShareFormat() {
        val timetable = Timetable().apply {
            name = "秋季课表"
            startTime = Timestamp(millis("2026-08-31T00:00:00"))
            endTime = Timestamp(millis("2026-12-31T00:00:00"))
        }
        fun events(source: String) = (0..11).map { week ->
            EventItem().apply {
                timetableId = timetable.id
                this.source = source
                name = "数学"
                place = "A101"
                subjectId = "course-1"
                from = Timestamp(millis("2026-08-31T08:30:00") + week * 7L * 86_400_000L)
                to = Timestamp(millis("2026-08-31T10:15:00") + week * 7L * 86_400_000L)
            }
        }
        val shareId = "11111111-1111-4111-8111-111111111111"
        val eas = ShareSnapshotBuilder().build(timetable, events(EventItem.SOURCE_EAS_IMPORT), shareId, "小林", ShareMode.FULL)
        val ics = ShareSnapshotBuilder().build(timetable, events(EventItem.SOURCE_ICS_IMPORT), shareId, "小林", ShareMode.FULL)

        assertEquals(eas, ics)
        val token = codec.encode(eas)
        assertTrue(token.startsWith("HITA2:"))
        assertEquals(token, codec.encode(ics))
        assertEquals(eas, codec.decodeMessage(token))
        val compactJson = GZIPInputStream(Base64.getUrlDecoder().decode(token.removePrefix("HITA2:")).inputStream())
            .reader().use { it.readText() }
        val root = JsonParser().parse(compactJson).asJsonObject
        assertEquals(1, root.getAsJsonArray("g").size())
        assertEquals(0, root.getAsJsonArray("x").size())
        assertEquals(0, root.getAsJsonArray("z").size())
        assertFalse(compactJson.contains("1788136200000"))
        val oldBytes = java.io.ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(codec.canonicalJson(eas).toByteArray()) }
        }.size()
        assertTrue("weekly share should be smaller than HITA1", Base64.getUrlDecoder()
            .decode(token.removePrefix("HITA2:")).size < oldBytes)
    }

    @Test fun irregularAndSubMinuteIcsTimesRoundTripExactly() {
        val base = fullFixture()
        val regular = base.occurrences.single()
        val irregular = SharedOccurrence(millis("2026-09-02T09:12:00"), millis("2026-09-02T10:47:00"), 0, "B202")
        val subMinute = SharedOccurrence(millis("2026-09-03T09:12:07") + 123, millis("2026-09-03T10:47:11") + 456, 0, "B202")
        val snapshot = base.copy(occurrences = listOf(regular, irregular, subMinute))
        val token = codec.encode(snapshot)
        assertEquals(codec.canonicalJson(snapshot), codec.canonicalJson(codec.decodeMessage(token)))
        val root = JsonParser().parse(GZIPInputStream(Base64.getUrlDecoder().decode(token.removePrefix("HITA2:")).inputStream())
            .reader().use { it.readText() }).asJsonObject
        assertEquals(1, root.getAsJsonArray("g").size())
        assertEquals(1, root.getAsJsonArray("x").size())
        assertEquals(1, root.getAsJsonArray("z").size())
    }

    @Test fun oldV1TokenStillDecodesAndDuplicateLessonsRemainDistinct() {
        assertEquals(fullFixture(), codec.decodeMessage(FIXED_TOKEN))
        val duplicate = fullFixture().copy(occurrences = List(2) { fullFixture().occurrences.single() })
        assertEquals(2, codec.decodeMessage(codec.encode(duplicate)).occurrences.size)
    }

    @Test fun actualIcsImportBundleSharesThroughTheSameSnapshotBuilder() {
        val start = millis("2026-09-01T08:30:00")
        val end = millis("2026-09-01T10:15:00")
        val event = VEvent(DateTime(start), DateTime(end), "数学").apply {
            properties.add(Location("A101"))
        }
        val bundle = IcsImportBundleBuilder.build(listOf(event), "我的课表.ics")
        assertEquals(EventItem.SOURCE_ICS_IMPORT, bundle.events.single().source)
        val snapshot = ShareSnapshotBuilder().build(bundle.timetable, bundle.events,
            "11111111-1111-4111-8111-111111111111", "小林", ShareMode.FULL)
        assertEquals(listOf(SharedOccurrence(start, end, 0, "A101")), snapshot.occurrences)
        assertEquals(snapshot, codec.decodeMessage(codec.encode(snapshot)))
    }

    @Test fun compactDecoderRejectsDuplicateFieldsAndOutOfRangeWeekMasks() {
        val token = codec.encode(fullFixture())
        val json = GZIPInputStream(Base64.getUrlDecoder().decode(token.removePrefix("HITA2:")).inputStream())
            .reader().use { it.readText() }
        fun compactToken(raw: String): String {
            val bytes = java.io.ByteArrayOutputStream().also { out ->
                GZIPOutputStream(out).use { it.write(raw.toByteArray(Charsets.UTF_8)) }
            }.toByteArray()
            return "HITA2:" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
        val duplicateKey = json.replaceFirst("\"v\":2", "\"v\":2,\"v\":2")
        val root = JsonParser().parse(json).asJsonObject
        val group = root.getAsJsonArray("g")[0].asJsonArray
        group.set(group.size() - 1, com.google.gson.JsonPrimitive(1L shl 54))
        listOf(duplicateKey, root.toString()).forEach { invalid ->
            assertEquals(ShareError.INVALID_FIELDS,
                assertThrows(ShareFormatException::class.java) { codec.decodeMessage(compactToken(invalid)) }.error)
        }
    }
}
