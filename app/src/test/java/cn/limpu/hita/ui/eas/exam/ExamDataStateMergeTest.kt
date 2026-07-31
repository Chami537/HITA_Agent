package cn.limpu.hita.ui.eas.exam

import cn.limpu.hita.data.model.eas.ExamItem
import com.limpu.component.data.DataState
import org.junit.Assert.assertEquals
import org.junit.Test

class ExamDataStateMergeTest {
    @Test
    fun `local memo does not hide expired remote session`() {
        val memo = ExamItem().apply {
            memoId = "memo-reauth"
            courseName = "本地考试备忘"
        }
        val expired = DataState<List<ExamItem>>(
            DataState.STATE.NOT_LOGGED_IN,
            "深圳 Web 会话已失效"
        )

        val merged = mergeExamDataState(emptyList(), listOf(memo), expired)

        assertEquals(DataState.STATE.NOT_LOGGED_IN, merged.state)
        assertEquals(listOf("本地考试备忘"), merged.data.orEmpty().map { it.courseName })
        assertEquals(expired.message, merged.message)
    }
}
