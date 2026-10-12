package app.lumen.chat.data

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ModelOption(val id: String, val label: String, val hint: String)

object ModelCatalog {
    val models = listOf(
        ModelOption("claude-sonnet-5-5", "Sonnet 5.5", "Rápido e equilibrado para o dia a dia"),
        ModelOption("claude-opus-5-5", "Opus 5.5", "Mais capaz, para tarefas difíceis"),
        ModelOption("claude-haiku-5-5", "Haiku 5.5", "O mais veloz e econômico"),
    )

    val default: ModelOption = models.first()

    fun labelFor(id: String): String = models.firstOrNull { it.id == id }?.label ?: id
}

data class AppSettings(
    val apiKey: String = "",
    val defaultModel: String = ModelCatalog.default.id,
    val systemPrompt: String = "",
    val maxTokens: Int = 4096,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
)
