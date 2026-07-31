package cn.limpu.hita.data.source.web

import org.junit.Assert.assertEquals
import org.junit.Test

class HoaTargetResolverTest {
    @Test
    fun `repository identifier wins over different metadata course code`() {
        assertEquals(
            "SocialPractice",
            resolveHoaRepositoryIdentifier("HITSZ-OpenAuto/SocialPractice", "SOPR"),
        )
    }

    @Test
    fun `repository identifier preserves mixed case`() {
        assertEquals(
            "Innovation",
            resolveHoaRepositoryIdentifier("HITSZ-OpenAuto/Innovation", "CODE1234"),
        )
    }

    @Test
    fun `course code remains fallback when repository is unavailable`() {
        assertEquals("PE100X", resolveHoaRepositoryIdentifier("", "PE100X"))
    }
}
