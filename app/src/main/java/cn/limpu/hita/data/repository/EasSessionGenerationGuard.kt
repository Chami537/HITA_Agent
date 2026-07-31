package cn.limpu.hita.data.repository

internal object EasSessionGenerationGuard {
    fun acceptsServiceRefresh(
        refreshEnabled: Boolean,
        tokenGeneration: Long,
        currentGeneration: Long,
        storedSessionLoggedIn: Boolean
    ): Boolean = refreshEnabled &&
        tokenGeneration == currentGeneration &&
        storedSessionLoggedIn
}
