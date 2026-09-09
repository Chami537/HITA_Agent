package cn.limpu.hita.agent.llm

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class ChatCompletionUsageTest {
    @Test fun missingUsageFieldsRemainUnknownInsteadOfBecomingZero() {
        val response = Gson().fromJson("""{"usage":{"prompt_tokens":12}}""", ChatCompletionResponse::class.java)
        assertEquals(12, response.usage?.promptTokens)
        assertNull(response.usage?.completionTokens)
        val empty = Gson().fromJson("""{"usage":{}}""", ChatCompletionResponse::class.java)
        assertNull(empty.usage?.promptTokens)
        assertNull(empty.usage?.completionTokens)
        val explicitZero = Gson().fromJson("""{"usage":{"prompt_tokens":0,"completion_tokens":0}}""", ChatCompletionResponse::class.java)
        assertEquals(0, explicitZero.usage?.promptTokens)
        assertEquals(0, explicitZero.usage?.completionTokens)
    }
}
