package cn.limpu.hita.ui.eas.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShenzhenWebAutoLoginTest {
    @Test
    fun `student type defaults safely to undergraduate`() {
        assertEquals(ShenzhenWebAutoLogin.UNDERGRAD, ShenzhenWebAutoLogin.normalizeStudentType(null))
        assertEquals(ShenzhenWebAutoLogin.UNDERGRAD, ShenzhenWebAutoLogin.normalizeStudentType("1"))
        assertEquals(ShenzhenWebAutoLogin.POSTGRAD, ShenzhenWebAutoLogin.normalizeStudentType("2"))
        assertEquals(ShenzhenWebAutoLogin.UNDERGRAD, ShenzhenWebAutoLogin.normalizeStudentType("unknown"))
    }

    @Test
    fun `only Shenzhen proxy root is treated as automatic portal entry`() {
        val base = "https://jw-hitsz-edu-cn.hitsz.edu.cn"

        assertTrue(ShenzhenWebAutoLogin.isProxyRoot("$base/", base))
        assertTrue(ShenzhenWebAutoLogin.isProxyRoot("$base/?locale=zh", base))
        assertFalse(ShenzhenWebAutoLogin.isProxyRoot("$base/authentication/main", base))
        assertFalse(ShenzhenWebAutoLogin.isProxyRoot("https://ids.hit.edu.cn/", base))
    }

    @Test
    fun `generated script respects enabled stages and preferred role`() {
        val undergrad = ShenzhenWebAutoLogin.buildClickScript("1", true, false)
        val postgrad = ShenzhenWebAutoLogin.buildClickScript("2", false, true)

        assertTrue(undergrad.contains("if (true)"))
        assertTrue(undergrad.contains("统一身份认证登录"))
        assertTrue(undergrad.contains("var preferred = '1'"))
        assertTrue(postgrad.contains("var preferred = '2'"))
        assertTrue(postgrad.contains("postgrad-role"))
        assertTrue(postgrad.contains("if (false)"))
        assertTrue(postgrad.contains("if (true)"))
    }
}
