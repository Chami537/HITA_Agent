package cn.limpu.hita.data.notice

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNoticePopupTest {
    @Test
    fun unseenInfoAndGroupPopupOnceWhileCriticalAndUpdateWaitTheirOwnDialog() {
        val notices = listOf(
            notice("group", "group", "info"),
            notice("svc", "service", "info"),
            notice("seen", "service", "info"),
            notice("down", "incident", "critical"),
            notice("update", "version", "info", minAppVersion = 50),
            notice("old-update", "version", "info", minAppVersion = 10),
        )

        val popup = AppNoticeCenter.noticesToPopup(notices, seenIds = setOf("seen"), currentVersionCode = 20)

        assertEquals(listOf("group", "svc", "old-update"), popup.map { it.id })
    }

    @Test
    fun versionWithoutThresholdStillPopsWithTheRest() {
        val popup = AppNoticeCenter.noticesToPopup(
            listOf(notice("note", "version", "info", minAppVersion = null)),
            seenIds = emptySet(),
            currentVersionCode = 20,
        )

        assertEquals(listOf("note"), popup.map { it.id })
    }

    private fun notice(
        id: String,
        kind: String,
        severity: String,
        minAppVersion: Long? = null,
    ) = AppNotice(
        id = id,
        kind = kind,
        severity = severity,
        title = id,
        body = id,
        minAppVersion = minAppVersion,
        affectedMinVersion = null,
        startsAt = null,
        endsAt = null,
    )
}
