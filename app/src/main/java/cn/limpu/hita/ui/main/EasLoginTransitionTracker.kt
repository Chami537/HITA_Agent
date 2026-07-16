package cn.limpu.hita.ui.main

import cn.limpu.hita.data.model.eas.EASToken

/** Distinguishes a real login/account change from routine cookie/token refreshes. */
internal class EasLoginTransitionTracker {
    private var lastSessionKey: String? = null

    fun shouldTriggerLoginWork(token: EASToken): Boolean {
        val currentKey = token.sessionKey()
        val shouldTrigger = currentKey != null && currentKey != lastSessionKey
        lastSessionKey = currentKey
        return shouldTrigger
    }

    private fun EASToken.sessionKey(): String? {
        if (!isLogin()) return null
        val account = username?.takeIf { it.isNotBlank() }
            ?: stuId?.takeIf { it.isNotBlank() }
            ?: id?.takeIf { it.isNotBlank() }
            ?: "authenticated"
        return "${campus.name}:$account"
    }
}
