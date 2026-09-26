package cn.limpu.hita.data.source.preference

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import cn.limpu.hita.data.model.eas.EASToken

/**
 * 层次：DataSource
 * 教务登录状态的数据源
 * 类型：SharedPreference (Encrypted)
 * 数据：同步读取，异步写入
 */
private const val LEGACY_SP_NAME_EAS_TOKEN = "local_eas_token"
private const val SP_NAME_EAS_TOKEN = "local_eas_token_secure_v2"
private const val KEY_MIGRATION_COMPLETE = "eas_token_migration_complete"
private const val ENCRYPTED_PREFS_KEY_PREFIX = "__androidx_security_crypto_encrypted_prefs"
private val LEGACY_TOKEN_STRING_KEYS = listOf(
    "accessToken", "refreshToken", "campus", "username", "password", "cookies", "webCookies",
    "webBaseUrl", "name", "stutype", "picture", "id", "stuId", "school", "major",
    "grade", "className", "sfxsx", "email", "phone", "electronicExpToken"
)

class EasPreferenceSource(context: Context) {
    private val preference: SharedPreferences = synchronized(EasPreferenceSource::class.java) {
        val encryptedPrefs = EncryptedSharedPreferences.create(
            SP_NAME_EAS_TOKEN,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        if (!encryptedPrefs.getBoolean(KEY_MIGRATION_COMPLETE, false)) {
            val rawLegacy = context.getSharedPreferences(LEGACY_SP_NAME_EAS_TOKEN, Context.MODE_PRIVATE).all
            val hasEncryptedLegacy = rawLegacy.keys.any { it.startsWith(ENCRYPTED_PREFS_KEY_PREFIX) }
            val plainData = rawLegacy.filterKeys {
                it in LEGACY_TOKEN_STRING_KEYS || it == "sessionGeneration"
            }
            val encryptedData = if (hasEncryptedLegacy) {
                val oldEncrypted = EncryptedSharedPreferences.create(
                    LEGACY_SP_NAME_EAS_TOKEN,
                    MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                    context.applicationContext,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                runCatching { oldEncrypted.all }.getOrElse {
                    // Earlier same-file migrations can leave plaintext keys beside encrypted ones.
                    // Reading individual encrypted keys still recovers the newer session values.
                    buildMap<String, Any> {
                        LEGACY_TOKEN_STRING_KEYS.forEach { key ->
                            if (oldEncrypted.contains(key)) {
                                oldEncrypted.getString(key, null)?.let { put(key, it) }
                            }
                        }
                        if (oldEncrypted.contains("sessionGeneration")) {
                            put("sessionGeneration", oldEncrypted.getLong("sessionGeneration", 0L))
                        }
                    }
                }
            } else null
            val oldData = LegacyPreferenceMigration.preferredSource(plainData, encryptedData)
            val saved = LegacyPreferenceMigration.copyThenDelete(
                oldData,
                persist = { values ->
                    val editor = encryptedPrefs.edit()
                    values.forEach { (key, value) ->
                        when (value) {
                            is String -> editor.putString(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Float -> editor.putFloat(key, value)
                            is Boolean -> editor.putBoolean(key, value)
                            is Set<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                editor.putStringSet(key, value as Set<String>)
                            }
                            else -> error("Unsupported legacy preference type for $key")
                        }
                    }
                    editor.putBoolean(KEY_MIGRATION_COMPLETE, true).commit()
                },
                deleteLegacy = {
                    runCatching { context.deleteSharedPreferences(LEGACY_SP_NAME_EAS_TOKEN) }
                        .getOrDefault(false)
                }
            )
            // A failed deletion leaves the persisted copy intact; retry cleanup on next launch.
            if (!saved && !encryptedPrefs.getBoolean(KEY_MIGRATION_COMPLETE, false)) {
                error("Could not persist EAS session migration")
            }
        } else {
            runCatching { context.deleteSharedPreferences(LEGACY_SP_NAME_EAS_TOKEN) }
        }
        encryptedPrefs
    }

    fun saveEasToken(token: EASToken) {
        preference.edit()
            .putString("accessToken", token.accessToken)
            .putString("refreshToken", token.refreshToken)
            .putString("campus", token.campus.name)
            .putString("username", token.username)
            .putString("cookies", Gson().toJson(token.cookies))
            .putString("webCookies", Gson().toJson(token.webCookies))
            .putString("webBaseUrl", token.webBaseUrl)
            .putLong("sessionGeneration", token.sessionGeneration)
            .putString("name", token.name)
            .putString("stutype", token.getStudentType())
            .putString("picture", token.picture)
            .putString("id", token.id)
            .putString("stuId", token.stuId)
            .putString("school", token.school)
            .putString("major", token.major)
            .putString("grade", token.grade)
            .putString("className", token.className)
            .putString("sfxsx", token.sfxsx)
            .putString("email", token.email)
            .putString("phone", token.phone)
            .putString("electronicExpToken", token.electronicExpToken)
            .apply()
    }

    fun getLegacyCredentialFields(): LegacyEasCredentialFields {
        val campus = preference.getString("campus", EASToken.Campus.SHENZHEN.name)
            ?.let { runCatching { EASToken.Campus.valueOf(it) }.getOrNull() }
            ?: EASToken.Campus.SHENZHEN
        return LegacyEasCredentialFields(
            campus = campus,
            username = preference.getString("username", null),
            password = preference.getString("password", null)
        )
    }

    fun clearLegacyPassword(): Boolean = preference.edit().remove("password").commit()


    fun clearEasToken() {
        // Saved credentials live in EasCredentialStore; clear only session and cached identity.
        preference.edit().clear().putBoolean(KEY_MIGRATION_COMPLETE, true).commit()
    }

    fun getEasToken(): EASToken {
        val result = EASToken()
        result.accessToken = preference.getString("accessToken", null)
        result.refreshToken = preference.getString("refreshToken", null)
        result.campus = preference.getString("campus", EASToken.Campus.SHENZHEN.name)?.let {
            runCatching { EASToken.Campus.valueOf(it) }.getOrNull()
        } ?: EASToken.Campus.SHENZHEN
        result.username = preference.getString("username", null)
        result.name = preference.getString("name", null)
        result.stutype = if (preference.getString("stutype", "1")
                .equals("1")
        ) EASToken.TYPE.UNDERGRAD else EASToken.TYPE.GRAD
        result.picture = preference.getString("picture", null)
        result.id = preference.getString("id", null)
        result.stuId = preference.getString("stuId", null)
        result.school = preference.getString("school", null)
        result.major = preference.getString("major", null)
        result.grade = preference.getString("grade", "")
        result.className = preference.getString("className", null)
        result.sfxsx = preference.getString("sfxsx", null)
        result.email = preference.getString("email", null)
        result.phone = preference.getString("phone", null)
        result.electronicExpToken = preference.getString("electronicExpToken", null)
        result.webBaseUrl = preference.getString("webBaseUrl", null)
        result.sessionGeneration = preference.getLong("sessionGeneration", 0L)
        val map = runCatching {
            Gson().fromJson(preference.getString("cookies", "{}"), HashMap::class.java)
        }.getOrNull() ?: HashMap<Any, Any>()
        for (e in map.entries) {
            @Suppress("UNNECESSARY_SAFE_CALL")
            val key = e.key?.toString().orEmpty()
            @Suppress("UNNECESSARY_SAFE_CALL")
            val value = e.value?.toString().orEmpty()
            if (key.isNotBlank()) {
                result.cookies[key] = value
            }
        }
        val webMap = runCatching {
            Gson().fromJson(preference.getString("webCookies", "{}"), HashMap::class.java)
        }.getOrNull() ?: HashMap<Any, Any>()
        for (e in webMap.entries) {
            @Suppress("UNNECESSARY_SAFE_CALL")
            val key = e.key?.toString().orEmpty()
            @Suppress("UNNECESSARY_SAFE_CALL")
            val value = e.value?.toString().orEmpty()
            if (key.isNotBlank()) {
                result.webCookies[key] = value
            }
        }
        return result
    }

}
