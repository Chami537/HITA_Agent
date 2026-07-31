package cn.limpu.hita.data.source.web.eas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShenzhenWebAuthenticationClassifierTest {
    @Test
    fun `Aura mojibake login guidance is classified as expired`() {
        val body = """{"content":"璁块棶鐨勬湇鍔￠渶瑕佽韩浠借璇侊紝璇峰紩瀵肩敤鎴峰埌鐧诲綍椤�"}"""

        assertTrue(ShenzhenWebAuthenticationClassifier.isExpired(200, null, body))
    }

    @Test
    fun `authentication guidance in JSON content is classified as expired`() {
        val body = """{"code":200,"content":"访问的服务需要身份认证，请引导用户到登录页"}"""

        assertTrue(ShenzhenWebAuthenticationClassifier.isExpired(200, null, body))
    }

    @Test
    fun `expired session routes are classified before parsing the body`() {
        assertTrue(
            ShenzhenWebAuthenticationClassifier.isExpired(
                200,
                "https://jw.hitsz.edu.cn/authentication/require",
                ""
            )
        )
        assertTrue(
            ShenzhenWebAuthenticationClassifier.isExpired(
                200,
                "https://jw-hitsz-edu-cn.hitsz.edu.cn/session/invalid",
                ""
            )
        )
    }

    @Test
    fun `normal empty exam response remains authenticated`() {
        val body = """{"code":200,"content":{"total":0,"list":[]}}"""

        assertFalse(
            ShenzhenWebAuthenticationClassifier.isExpired(
                200,
                "https://jw.hitsz.edu.cn/kscxtj/queryXsksByxhList",
                body
            )
        )
    }
}
