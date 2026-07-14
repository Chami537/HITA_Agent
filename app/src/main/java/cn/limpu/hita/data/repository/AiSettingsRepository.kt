package cn.limpu.hita.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import cn.limpu.hita.utils.LogUtils

private const val SP_NAME_AI_SETTINGS = "ai_settings"
private const val KEY_CHAT_PROVIDER = "chat_provider"
private const val KEY_CUSTOM_DEEPSEEK_API_KEY = "custom_deepseek_api_key"
private const val KEY_CUSTOM_ZHIPU_API_KEY = "custom_zhipu_api_key"

enum class AiChatProvider(val value: String) {
    BUILTIN_DEEPSEEK("builtin_deepseek"),
    CUSTOM_DEEPSEEK("custom_deepseek");

    companion object {
        fun from(value: String?): AiChatProvider {
            return entries.firstOrNull { it.value == value } ?: BUILTIN_DEEPSEEK
        }
    }
}

data class AiSettings(
    val chatProvider: AiChatProvider,
    val customDeepSeekApiKey: String,
    val customZhipuApiKey: String,
)

class AiSettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preference: SharedPreferences = createPreferences(appContext)

    fun getSettings(): AiSettings {
        return AiSettings(
            chatProvider = AiChatProvider.from(preference.getString(KEY_CHAT_PROVIDER, null)),
            customDeepSeekApiKey = preference.getString(KEY_CUSTOM_DEEPSEEK_API_KEY, "").orEmpty(),
            customZhipuApiKey = preference.getString(KEY_CUSTOM_ZHIPU_API_KEY, "").orEmpty(),
        )
    }

    fun saveSettings(settings: AiSettings) {
        preference.edit()
            .putString(KEY_CHAT_PROVIDER, settings.chatProvider.value)
            .putString(KEY_CUSTOM_DEEPSEEK_API_KEY, settings.customDeepSeekApiKey.trim())
            .putString(KEY_CUSTOM_ZHIPU_API_KEY, settings.customZhipuApiKey.trim())
            .apply()
    }

    fun getCustomDeepSeekApiKey(): String {
        return preference.getString(KEY_CUSTOM_DEEPSEEK_API_KEY, "").orEmpty().trim()
    }

    fun getCustomZhipuApiKey(): String {
        return preference.getString(KEY_CUSTOM_ZHIPU_API_KEY, "").orEmpty().trim()
    }

    private fun createPreferences(context: Context): SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                SP_NAME_AI_SETTINGS,
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            LogUtils.e("Failed to create encrypted AI settings, falling back to plain preferences", e)
            context.getSharedPreferences(SP_NAME_AI_SETTINGS, Context.MODE_PRIVATE)
        }
    }
}
