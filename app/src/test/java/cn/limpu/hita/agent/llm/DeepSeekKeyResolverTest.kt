package cn.limpu.hita.agent.llm

import cn.limpu.hita.data.repository.AiChatProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekKeyResolverTest {

    @Test
    fun `saved custom key is used when built in key is unavailable`() {
        assertEquals(
            "custom-key",
            resolveDeepSeekApiKey(
                chatProvider = AiChatProvider.BUILTIN_DEEPSEEK,
                builtInKey = "",
                customKey = " custom-key ",
            ),
        )
    }

    @Test
    fun `built in key remains preferred when available`() {
        assertEquals(
            "built-in-key",
            resolveDeepSeekApiKey(
                chatProvider = AiChatProvider.BUILTIN_DEEPSEEK,
                builtInKey = " built-in-key ",
                customKey = "custom-key",
            ),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `custom provider requires a saved key`() {
        resolveDeepSeekApiKey(
            chatProvider = AiChatProvider.CUSTOM_DEEPSEEK,
            builtInKey = "built-in-key",
            customKey = "",
        )
    }
}
