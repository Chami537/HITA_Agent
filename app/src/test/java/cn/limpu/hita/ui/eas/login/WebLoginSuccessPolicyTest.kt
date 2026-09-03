package cn.limpu.hita.ui.eas.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLoginSuccessPolicyTest {
    @Test
    fun `Weihai loginCAS without authenticated cookies is not success`() {
        assertFalse(
            WebLoginSuccessPolicy.isWeihaiAuthenticatedPage(
                "https://webvpn.hitwh.edu.cn/http/eas/loginCAS",
                emptyMap()
            )
        )
    }

    @Test
    fun `Weihai function page with VPN ticket and JSESSIONID is success`() {
        assertTrue(
            WebLoginSuccessPolicy.isWeihaiAuthenticatedPage(
                "https://webvpn.hitwh.edu.cn/http/eas/kbcx/queryGrkb",
                mapOf(
                    "wengine_vpn_ticket" to "ticket",
                    "JSESSIONID" to "session"
                )
            )
        )
    }

    @Test
    fun `Shenzhen probes both proxy and direct hosts`() {
        val urls = WebLoginSuccessPolicy.shenzhenCookieProbeUrls(
            proxyBaseUrl = "https://jw-hitsz-edu-cn.hitsz.edu.cn",
            directBaseUrl = "https://jw.hitsz.edu.cn"
        )

        assertTrue(urls.any { it.startsWith("https://jw-hitsz-edu-cn.hitsz.edu.cn/") })
        assertTrue(urls.any { it.startsWith("https://jw.hitsz.edu.cn/") })
    }

    @Test
    fun `Shenzhen direct host keeps direct web base`() {
        assertEquals(
            "https://jw.hitsz.edu.cn",
            WebLoginSuccessPolicy.shenzhenWebBaseUrl(
                host = "jw.hitsz.edu.cn",
                proxyBaseUrl = "https://jw-hitsz-edu-cn.hitsz.edu.cn",
                directBaseUrl = "https://jw.hitsz.edu.cn"
            )
        )
    }
}
