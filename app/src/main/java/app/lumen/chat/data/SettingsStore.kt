package app.lumen.chat.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Guarda os ajustes. A chave de API fica em EncryptedSharedPreferences (AES-256,
 * chave mestra no Android Keystore).
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(K_API_KEY, next.apiKey)
            .putString(K_MODEL, next.defaultModel)
            .putString(K_SYSTEM, next.systemPrompt)
            .putInt(K_MAX_TOKENS, next.maxTokens)
            .putString(K_THEME, next.themeMode.name)
            .putBoolean(K_DYNAMIC, next.dynamicColor)
            .apply()
        _settings.value = next
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            apiKey = prefs.getString(K_API_KEY, "").orEmpty(),
            defaultModel = prefs.getString(K_MODEL, null)?.takeIf { it.isNotBlank() } ?: defaults.defaultModel,
            systemPrompt = prefs.getString(K_SYSTEM, "").orEmpty(),
            maxTokens = prefs.getInt(K_MAX_TOKENS, defaults.maxTokens),
            themeMode = runCatching {
                ThemeMode.valueOf(prefs.getString(K_THEME, null) ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs.getBoolean(K_DYNAMIC, defaults.dynamicColor),
        )
    }

    private companion object {
        const val FILE = "lumen_secure_prefs"
        const val FILE_FALLBACK = "lumen_prefs_fallback"
        const val K_API_KEY = "api_key"
        const val K_MODEL = "default_model"
        const val K_SYSTEM = "system_prompt"
        const val K_MAX_TOKENS = "max_tokens"
        const val K_THEME = "theme_mode"
        const val K_DYNAMIC = "dynamic_color"

        fun createPrefs(context: Context): SharedPreferences {
            fun encrypted(): SharedPreferences {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return EncryptedSharedPreferences.create(
                    context,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }

            return try {
                encrypted()
            } catch (e: Exception) {
                // Keystore corrompido (ex.: restauração de backup). Recomeça do zero.
                context.deleteSharedPreferences(FILE)
                try {
                    encrypted()
                } catch (e2: Exception) {
                    // Último recurso: armazenamento privado do app (sandbox), sem criptografia extra.
                    context.getSharedPreferences(FILE_FALLBACK, Context.MODE_PRIVATE)
                }
            }
        }
    }
}
