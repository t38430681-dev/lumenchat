package app.lumen.chat.data.api

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

@Serializable
data class ApiMessage(val role: String, val content: String)

@Serializable
data class MessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<ApiMessage>,
    val stream: Boolean = true,
    val system: String? = null,
)

sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
}

class ApiException(message: String, val httpCode: Int? = null) : Exception(message)

@OptIn(ExperimentalSerializationApi::class)
class AnthropicClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * Faz uma chamada em streaming à Messages API e emite os pedaços de texto
     * conforme chegam. Cancelar a coleta cancela a requisição de rede.
     */
    fun stream(apiKey: String, request: MessagesRequest): Flow<StreamEvent> = flow {
        val body = json.encodeToString(MessagesRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url(ENDPOINT)
            .header("x-api-key", apiKey)
            .header("anthropic-version", API_VERSION)
            .header("accept", "text/event-stream")
            .post(body)
            .build()

        val call = http.newCall(httpRequest)
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw ApiException(describeError(response), response.code)
                }
                val source = response.body?.source() ?: throw ApiException("A resposta veio vazia.")
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    parseEvent(data)?.let { emit(it) }
                }
            }
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw ApiException("Falha de conexão. Verifique sua internet e tente de novo.")
        } finally {
            handle?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    private fun parseEvent(data: String): StreamEvent? {
        val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
        return when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "content_block_delta" -> {
                val delta = obj["delta"] as? JsonObject ?: return null
                if (delta["type"]?.jsonPrimitive?.contentOrNull == "text_delta") {
                    delta["text"]?.jsonPrimitive?.contentOrNull?.let { StreamEvent.Delta(it) }
                } else {
                    null
                }
            }

            "error" -> {
                val message = (obj["error"] as? JsonObject)
                    ?.get("message")?.jsonPrimitive?.contentOrNull
                throw ApiException(message ?: "A API retornou um erro durante a resposta.")
            }

            else -> null
        }
    }

    private fun describeError(response: Response): String {
        val raw = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
        val detail = runCatching {
            json.parseToJsonElement(raw).jsonObject["error"]
                ?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()

        val friendly = when (response.code) {
            400 -> "Requisição inválida."
            401 -> "Chave de API inválida ou ausente. Confira nos Ajustes."
            403 -> "Esta chave não tem permissão para essa operação."
            404 -> "Modelo não encontrado. Confira o ID do modelo nos Ajustes."
            413 -> "A conversa ficou grande demais. Comece uma nova."
            429 -> "Limite de uso atingido. Aguarde um pouco e tente de novo."
            in 500..599 -> "O serviço está instável ou sobrecarregado. Tente de novo em instantes."
            else -> "Erro ${response.code}."
        }
        return if (detail.isNullOrBlank()) friendly else "$friendly\n($detail)"
    }

    private companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val API_VERSION = "2023-06-01"
    }
}
