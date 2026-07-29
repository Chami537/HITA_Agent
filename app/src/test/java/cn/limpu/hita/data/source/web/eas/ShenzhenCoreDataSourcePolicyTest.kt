package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.EASToken
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShenzhenCoreDataSourcePolicyTest {
    @Test
    fun `API session remains primary when Web session is also present`() {
        val token = shenzhenToken(
            accessToken = "api-token",
            webCookies = mutableMapOf("SESSION" to "web-session")
        )

        assertFalse(ShenzhenCoreDataSourcePolicy.shouldUseWebFallback(token))
    }

    @Test
    fun `Web session is fallback when API session is unavailable`() {
        val token = shenzhenToken(
            accessToken = null,
            webCookies = mutableMapOf("SESSION" to "web-session")
        )

        assertTrue(ShenzhenCoreDataSourcePolicy.shouldUseWebFallback(token))
    }

    @Test
    fun `missing sessions do not select Web fallback`() {
        assertFalse(
            ShenzhenCoreDataSourcePolicy.shouldUseWebFallback(
                shenzhenToken(accessToken = null, webCookies = mutableMapOf())
            )
        )
    }

    private fun shenzhenToken(
        accessToken: String?,
        webCookies: MutableMap<String, String>
    ): EASToken = EASToken().apply {
        campus = EASToken.Campus.SHENZHEN
        this.accessToken = accessToken
        this.webCookies = HashMap(webCookies)
        webBaseUrl = "https://jw-hitsz-edu-cn.hitsz.edu.cn"
    }
}
