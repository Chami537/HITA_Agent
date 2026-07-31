package cn.limpu.hita.data.repository

import cn.limpu.hita.data.model.eas.ExamItem
import cn.limpu.hita.data.model.timetable.EventItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExamEventMapperTest {
    @Test
    fun identityKey_matchesLegacyAndPrefixedNames() {
        val exam = ExamItem().apply {
            courseName = "高等数学"
            examDate = "2026-01-10"
            examTime = "08:30-10:30"
            examLocation = "正心楼 101"
        }
        val mapped = ExamEventMapper.toEvent(exam, "eas")!!
        val legacy = EventItem().apply {
            type = EventItem.TYPE.EXAM
            name = "高等数学"
            place = "正心楼 101"
            from = mapped.from
            to = mapped.to
        }

        assertEquals(ExamEventMapper.identityKey(legacy), ExamEventMapper.identityKey(mapped))
    }

    @Test
    fun toEvent_rejectsEmptyOrInvalidExamTime() {
        val emptyDate = ExamItem().apply {
            courseName = "高等数学"
            examDate = ""
            examTime = "08:30-10:30"
        }
        val invalidTime = ExamItem().apply {
            courseName = "高等数学"
            examDate = "2026-01-10"
            examTime = "08:30"
        }

        assertNull(ExamEventMapper.toEvent(emptyDate, "eas"))
        assertNull(ExamEventMapper.toEvent(invalidTime, "eas"))
    }

    @Test
    fun toEvent_trimsFieldsAndFormatsExamName() {
        val exam = ExamItem().apply {
            courseName = "  [考试]  高等数学  "
            examDate = "2026-01-10"
            examTime = " 08:30 - 10:30 "
            examLocation = " 正心楼   101 "
        }
        val mapped = ExamEventMapper.toEvent(exam, "eas")

        assertNotNull(mapped)
        assertEquals("[考试] 高等数学", mapped!!.name)
        assertEquals(" 正心楼   101 ", mapped.place)
    }
}
