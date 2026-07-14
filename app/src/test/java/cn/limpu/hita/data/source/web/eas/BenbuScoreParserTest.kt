package cn.limpu.hita.data.source.web.eas

import org.junit.Assert.assertEquals
import org.junit.Test

class BenbuScoreParserTest {
    @Test
    fun `fractional course credit is preserved`() {
        assertEquals(1.5f, parseScoreCredit("1.5"), 0f)
    }
}
