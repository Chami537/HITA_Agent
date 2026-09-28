package cn.limpu.hita.agent.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmChatServiceParseTest {
    @Test
    fun answerProseThatMentionsAnActionIsNotExecuted() {
        val parsed = LlmChatService.parseLocalReActStep(
            """
            思考：说明怎么用
            答案：不要调用动作：add_activity。
            """.trimIndent()
        )

        assertEquals("答案", parsed.action)
        assertTrue(parsed.thought.contains("不要调用"))
        assertEquals("", parsed.actionInput)
    }

    @Test
    fun lineAnchoredActionStillSelectsTheTool() {
        val parsed = LlmChatService.parseLocalReActStep(
            """
            思考：需要添加
            动作：add_activity
            动作输入：{"name":"开会"}
            """.trimIndent()
        )

        assertEquals("add_activity", parsed.action)
        assertTrue(parsed.actionInput.contains("开会"))
    }
}
