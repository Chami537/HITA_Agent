package cn.limpu.hita.data.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 事件目录协议约束：
 * - 事件名必须 snake_case（与 iOS 端协议一致，服务端按名聚合）
 * - 事件名不允许重复
 */
class UsageAnalyticsEventTest {

    @Test
    fun `all event names are unique`() {
        val names = UsageAnalyticsEvent.entries.map { it.eventName }
        assertEquals("事件名重复", names.size, names.toSet().size)
    }

    @Test
    fun `all event names are snake_case`() {
        UsageAnalyticsEvent.entries.forEach { event ->
            assertTrue(
                "事件名不符合 snake_case: ${event.eventName}",
                event.eventName.matches(Regex("^[a-z][a-z0-9]*(_[a-z][a-z0-9]*)*$"))
            )
        }
    }

    @Test
    fun `every event name ends with verb or noun pattern`() {
        // 协议要求 `模块_动作_结果` 语义：至少两段
        UsageAnalyticsEvent.entries.forEach { event ->
            assertTrue(
                "事件名缺少模块段: ${event.eventName}",
                event.eventName.contains('_')
            )
        }
    }
}
