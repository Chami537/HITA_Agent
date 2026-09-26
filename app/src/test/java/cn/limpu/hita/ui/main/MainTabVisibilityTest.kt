package cn.limpu.hita.ui.main

import cn.limpu.hita.data.model.eas.EASToken
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainTabVisibilityTest {
    @Test
    fun `logged out default token does not show blog`() {
        assertFalse(MainTab.showsBlog(null))
        assertFalse(MainTab.showsBlog(EASToken()))
        assertFalse(MainTab.BLOG in MainTab.visibleFor(EASToken()))
    }

    @Test
    fun `shenzhen session shows blog`() {
        val appApi = EASToken().apply {
            campus = EASToken.Campus.SHENZHEN
            accessToken = "token"
        }
        val web = EASToken().apply {
            campus = EASToken.Campus.SHENZHEN
            webCookies["JSESSIONID"] = "session"
            webCookies["route"] = "route"
        }
        assertTrue(MainTab.showsBlog(appApi))
        assertTrue(MainTab.BLOG in MainTab.visibleFor(appApi))
        assertTrue(MainTab.showsBlog(web))
        assertTrue(MainTab.BLOG in MainTab.visibleFor(web))
    }

    @Test
    fun `saved password without a session does not show blog`() {
        val token = EASToken().apply {
            campus = EASToken.Campus.SHENZHEN
            username = "20240001"
            password = "secret"
        }
        assertFalse(MainTab.showsBlog(token))
    }

    @Test
    fun `other campuses stay hidden even when logged in`() {
        listOf(EASToken.Campus.BENBU, EASToken.Campus.WEIHAI).forEach { campus ->
            val token = EASToken().apply {
                this.campus = campus
                accessToken = "token"
            }
            assertFalse(MainTab.showsBlog(token))
            assertFalse(MainTab.BLOG in MainTab.visibleFor(token))
        }
    }
}
