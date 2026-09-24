package cn.limpu.hita.data.repository

import cn.limpu.hita.data.model.eas.EASToken
import cn.limpu.hita.data.source.preference.EasCredential

object EasCredentialReloginPolicy {
    fun passwordFor(
        tokenCampus: EASToken.Campus,
        tokenUsername: String?,
        credential: EasCredential?
    ): String? {
        if (tokenCampus != EASToken.Campus.SHENZHEN) return null
        val username = tokenUsername?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return credential
            ?.takeIf {
                it.campus == EASToken.Campus.SHENZHEN &&
                    it.username.trim() == username &&
                    it.password.isNotEmpty()
            }
            ?.password
    }
}
