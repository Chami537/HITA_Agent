package cn.limpu.hita.data.model.eas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EASTokenWebSessionTest {
    @Test
    fun `direct Shenzhen web session requires route and JSESSIONID in dedicated jar`() {
        val token = EASToken().apply {
            campus = EASToken.Campus.SHENZHEN
            cookies["JSESSIONID"] = "app-api-session"
            cookies["route"] = "app-api-route"
        }

        assertFalse(token.hasShenzhenWebSession())

        token.webCookies["JSESSIONID"] = "web-session"
        token.webCookies["route"] = "web-route"
        assertTrue(token.hasShenzhenWebSession())
    }

    @Test
    fun `proxied Shenzhen web session accepts the new SESSION cookie`() {
        val token = EASToken().apply {
            campus = EASToken.Campus.SHENZHEN
            webBaseUrl = "https://jw-hitsz-edu-cn.hitsz.edu.cn"
            webCookies["SESSION"] = "proxy-session"
            webCookies["sdp_user_token"] = "proxy-user-token"
        }

        assertTrue(token.hasShenzhenWebSession())
    }

    @Test
    fun `empty proxy SESSION cookie is rejected`() {
        val token = EASToken().apply {
            campus = EASToken.Campus.SHENZHEN
            webCookies["SESSION"] = ""
            webCookies["sdp_user_token"] = "proxy-user-token"
        }

        assertFalse(token.hasShenzhenWebSession())
    }

    @Test
    fun `web cookies count as logged in without bearer token`() {
        val token = EASToken().apply {
            campus = EASToken.Campus.SHENZHEN
            webCookies["JSESSIONID"] = "web-session"
            webCookies["route"] = "web-route"
        }

        assertTrue(token.isLogin())
    }
}
