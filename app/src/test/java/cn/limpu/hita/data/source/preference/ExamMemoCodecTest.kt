package cn.limpu.hita.data.source.preference

import cn.limpu.hita.data.model.eas.ExamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamMemoCodecTest {
    @Test
    fun `memo survives json round trip`() {
        val original = ExamItem().apply {
            memoId = "memo-1"
            courseName = "离散数学"
            examDate = "2026-07-20"
            examTime = "09:00-11:00"
            examType = "期末"
            examLocation = "T3101"
            termId = "2025-2026-3"
            termName = "2026夏季"
            campusName = "深圳校区"
        }

        val decoded = ExamMemoCodec.decode(ExamMemoCodec.encode(listOf(original))).single()

        assertTrue(decoded.isMemo())
        assertEquals(original.memoId, decoded.memoId)
        assertEquals(original.courseName, decoded.courseName)
        assertEquals(original.examTime, decoded.examTime)
        assertEquals(original.termId, decoded.termId)
    }

    @Test
    fun `invalid and remote records are not restored as memos`() {
        val remote = ExamItem().apply { courseName = "大学物理" }
        val namelessMemo = ExamItem().apply { memoId = "memo-empty" }

        val decoded = ExamMemoCodec.decode(ExamMemoCodec.encode(listOf(remote, namelessMemo)))

        assertTrue(decoded.isEmpty())
        assertFalse(remote.isMemo())
    }

    @Test
    fun `merge keeps remote records separate and orders dated memos`() {
        val remote = ExamItem().apply {
            courseName = "机器学习"
            examDate = "2026-07-22"
        }
        val memo = ExamItem().apply {
            memoId = "memo-2"
            courseName = "软件构造"
            examDate = "2026-07-18"
        }

        val merged = ExamMemoCodec.merge(listOf(remote), listOf(memo))

        assertEquals(listOf("软件构造", "机器学习"), merged.map { it.courseName })
        assertTrue(merged.first().isMemo())
        assertFalse(merged.last().isMemo())
    }
}
