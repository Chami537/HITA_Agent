package cn.limpu.hita.data.analytics

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AnalyticsCorrelationTest {
    @Test fun correlationIsScopedAndDoesNotSurviveExceptionOrCrossThreads() {
        val outer = AnalyticsOperationContext.Value(UUID.randomUUID().toString(), 1)
        val inner = outer.copy(id = UUID.randomUUID().toString())
        AnalyticsOperationContext.withOperation(outer) {
            assertEquals(outer, AnalyticsOperationContext.get())
            runCatching { AnalyticsOperationContext.withOperation(inner) { error("synthetic failure") } }
            assertEquals(outer, AnalyticsOperationContext.get())
            var other: AnalyticsOperationContext.Value? = outer
            Thread { other = AnalyticsOperationContext.get() }.apply { start(); join() }
            assertNull(other)
        }
        assertNull(AnalyticsOperationContext.get())
    }
    @Test fun onlyFirstPartyRequestsCarryAnEnabledOperationAndRedirectsRemoveIt() {
        val id = UUID.randomUUID().toString()
        var enabled: String? = id
        val interceptor = AnalyticsCorrelationInterceptor("https://api.hita.limpu.cn/".toHttpUrl()) { enabled }
        val request = Request.Builder().url("https://api.hita.limpu.cn/api/courses/search").build()
        assertEquals(id, interceptor.correlate(request).header("X-HITA-Operation-ID"))
        val redirect = interceptor.correlate(request).newBuilder().url("https://example.org/").build()
        assertNull(interceptor.correlate(redirect).header("X-HITA-Operation-ID"))
        assertNull(interceptor.correlate(request.newBuilder().url("http://api.hita.limpu.cn/").build()).header("X-HITA-Operation-ID"))
        enabled = null
        assertNull(interceptor.correlate(request).header("X-HITA-Operation-ID"))
    }
}
