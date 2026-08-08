package cn.limpu.hita.data.notice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNoticeCenterTest {

    private val now = 1_800_000_000_000L

    private fun notice(
        id: String,
        kind: String = "service",
        severity: String = "info",
        minAppVersion: Long? = null,
        affectedMinVersion: Long? = null,
        startsAt: Long? = null,
        endsAt: Long? = null,
    ) = AppNotice(
        id = id,
        kind = kind,
        severity = severity,
        title = "t",
        body = "b",
        minAppVersion = minAppVersion,
        affectedMinVersion = affectedMinVersion,
        startsAt = startsAt,
        endsAt = endsAt,
    )

    @Test
    fun `notice inside time window is active`() {
        val notices = listOf(
            notice("a", startsAt = now - 1000, endsAt = now + 1000)
        )
        assertEquals(listOf("a"), AppNoticeCenter.activeNotices(notices, now).map { it.id })
    }

    @Test
    fun `notice outside time window is filtered out`() {
        val notices = listOf(
            notice("expired", startsAt = now - 5000, endsAt = now - 1000),
            notice("future", startsAt = now + 1000, endsAt = now + 5000),
        )
        assertTrue(AppNoticeCenter.activeNotices(notices, now).isEmpty())
    }

    @Test
    fun `critical notice sorted before info`() {
        val notices = listOf(
            notice("info"),
            notice("critical", severity = "critical"),
        )
        assertEquals(
            listOf("critical", "info"),
            AppNoticeCenter.activeNotices(notices, now).map { it.id }
        )
    }

    @Test
    fun `version notice with higher min version stays active for current app`() {
        // 当前版本由 BuildConfig.VERSION_CODE 提供；min_app_version 高于当前才应触发版本提醒，
        // 过滤逻辑本身不在这里剔除（剔除发生在 UI 层比较 VERSION_CODE），此处验证不被时间窗误伤。
        val notices = listOf(
            notice("v", kind = "version", startsAt = now - 1000)
        )
        assertEquals(listOf("v"), AppNoticeCenter.activeNotices(notices, now).map { it.id })
    }

    @Test
    fun `affected min version below current keeps notice`() {
        val notices = listOf(
            notice("a", affectedMinVersion = 1L)
        )
        assertEquals(listOf("a"), AppNoticeCenter.activeNotices(notices, now).map { it.id })
    }
}
