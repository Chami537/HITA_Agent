package cn.limpu.hita.data.source.preference

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import cn.limpu.hita.data.model.eas.EASToken
import java.io.File

/**
 * 层次：DataSource
 * 教务登录状态的数据源
 * 类型：SharedPreference (Encrypted)
 * 数据：同步读取，异步写入
 */
private const val SP_NAME_EAS_TOKEN = "local_eas_token"

class EasPreferenceSource(context: Context) {
    private val preference: SharedPreferences = run {
        // Migration: if old plaintext SP exists, move data to encrypted SP then delete plaintext file
        val oldData = try {
            val plainPrefs = context.getSharedPreferences(SP_NAME_EAS_TOKEN, Context.MODE_PRIVATE)
            if (plainPrefs.contains("username")) plainPrefs.all.toMap() else null
        } catch (_: Exception) { null }

        if (oldData != null) {
            try {
                val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                File(prefsDir, "${SP_NAME_EAS_TOKEN}.xml").delete()
            } catch (_: Exception) { }
        }

        val encryptedPrefs = EncryptedSharedPreferences.create(
            SP_NAME_EAS_TOKEN,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        if (oldData != null) {
            val editor = encryptedPrefs.edit()
            oldData.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        editor.putStringSet(key, value as? Set<String>)
                    }
                }
            }
            editor.apply()
        }

        encryptedPrefs
    }

    fun saveEasToken(token: EASToken) {
        preference.edit()
            .putString("accessToken", token.accessToken)
            .putString("refreshToken", token.refreshToken)
            .putString("campus", token.campus.name)
            .putString("username", token.username)
            .putString("password", token.password)
            .putString("cookies", Gson().toJson(token.cookies))
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


    fun clearEasToken() {
        preference.edit()
            .putString("accessToken", null)
            .putString("refreshToken", null)
            .putString("password", null)
            .putString("electronicExpToken", null)
            .putString("cookies", null)
            .apply()
    }

    fun getEasToken(): EASToken {
        val result = EASToken()
        result.accessToken = preference.getString("accessToken", null)
        result.refreshToken = preference.getString("refreshToken", null)
        result.campus = preference.getString("campus", EASToken.Campus.SHENZHEN.name)?.let {
            runCatching { EASToken.Campus.valueOf(it) }.getOrNull()
        } ?: EASToken.Campus.SHENZHEN
        result.username = preference.getString("username", null)
        result.password = preference.getString("password", null)
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
        return result
    }

}
