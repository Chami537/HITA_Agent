package cn.limpu.hita.data.source.preference

internal object LegacyPreferenceMigration {
    fun preferredSource(plaintext: Map<String, *>, encrypted: Map<String, *>?): Map<String, *> =
        encrypted ?: plaintext

    fun copyThenDelete(
        values: Map<String, *>,
        persist: (Map<String, *>) -> Boolean,
        deleteLegacy: () -> Boolean
    ): Boolean {
        if (!persist(values)) return false
        return deleteLegacy()
    }
}
