package cn.limpu.hita.data.repository

import cn.limpu.hita.data.model.eas.EASToken
import cn.limpu.hita.data.source.preference.EasCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EasCredentialReloginPolicyTest {
    @Test
    fun `Shenzhen relogin uses saved password for matching account`() {
        assertEquals(
            "saved-password",
            EasCredentialReloginPolicy.passwordFor(
                EASToken.Campus.SHENZHEN,
                "student-001",
                EasCredential(EASToken.Campus.SHENZHEN, "student-001", "saved-password")
            )
        )
    }

    @Test
    fun `relogin rejects another account credential`() {
        assertNull(
            EasCredentialReloginPolicy.passwordFor(
                EASToken.Campus.SHENZHEN,
                "student-001",
                EasCredential(EASToken.Campus.SHENZHEN, "student-002", "other-password")
            )
        )
    }

    @Test
    fun `protocol relogin rejects non Shenzhen credential`() {
        assertNull(
            EasCredentialReloginPolicy.passwordFor(
                EASToken.Campus.SHENZHEN,
                "student-001",
                EasCredential(EASToken.Campus.WEIHAI, "student-001", "web-password")
            )
        )
    }
}
