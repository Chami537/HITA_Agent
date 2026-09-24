package cn.limpu.hita.data.source.preference

import cn.limpu.hita.data.model.eas.EASToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EasCredentialMigrationPolicyTest {
    @Test
    fun `complete Shenzhen credential migrates and trims account identity`() {
        assertEquals(
            EasCredential(EASToken.Campus.SHENZHEN, "student-001", "secret with spaces "),
            LegacyEasCredentialPolicy.migratableCredential(
                EASToken.Campus.SHENZHEN,
                username = " student-001 ",
                password = "secret with spaces "
            )
        )
    }

    @Test
    fun `non Shenzhen token password is not treated as a login credential`() {
        assertNull(
            LegacyEasCredentialPolicy.migratableCredential(
                EASToken.Campus.BENBU,
                username = "student-001",
                password = "student-001"
            )
        )
    }

    @Test
    fun `blank or missing legacy fields are not migrated`() {
        assertNull(
            LegacyEasCredentialPolicy.migratableCredential(
                EASToken.Campus.SHENZHEN,
                username = " ",
                password = "secret"
            )
        )
        assertNull(
            LegacyEasCredentialPolicy.migratableCredential(
                EASToken.Campus.SHENZHEN,
                username = "student-001",
                password = null
            )
        )
    }

    @Test
    fun `credential keys isolate campus and account but normalize surrounding spaces`() {
        val shenzhenKey = EasCredentialStoreKeyPolicy.key(EASToken.Campus.SHENZHEN, " student-001 ")
        assertEquals("SHENZHEN:student-001", shenzhenKey)
        assertEquals("BENBU:student-001", EasCredentialStoreKeyPolicy.key(EASToken.Campus.BENBU, "student-001"))
        assertEquals("SHENZHEN:student-002", EasCredentialStoreKeyPolicy.key(EASToken.Campus.SHENZHEN, "student-002"))
    }
}
