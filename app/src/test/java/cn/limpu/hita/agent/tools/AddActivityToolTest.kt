package cn.limpu.hita.agent.tools

import org.junit.Assert.assertNull
import org.junit.Test

class AddActivityToolTest {
    @Test
    fun incompleteToolInputDoesNotInventAnEvent() {
        val parsed = AddActivityTool().parseAddActivityInput("{")

        assertNull(parsed.name)
        assertNull(parsed.fromMs)
        assertNull(parsed.toMs)
    }
}
