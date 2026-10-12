package app.lumen.chat.data

import app.lumen.chat.data.api.AnthropicClient
import app.lumen.chat.data.api.ApiException
import app.lumen.chat.data.api.ApiMessage
import app.lumen.chat.data.api.MessagesRequest
import app.lumen.chat.data.api.StreamEvent
import app.lumen.chat.data.db.ChatDao
import app.lumen.chat.data.db.ConversationEntity
import app.lumen.chat.data.db.MessageEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class ChatRepository(
    private val dao: ChatDao,
    private val api: AnthropicClient,
) {
    fun conversations(query: String): Flow<List<ConversationEntity>> {
        val q = query.trim()
        return if (q.isEmpty()) dao.allConversations() else dao.searchConversations(escapeLike(q))
    }

    fun messages(conversationId: String): Flow<List<MessageEntity>> = dao.messages(conversationId)

    fun conversation(conversationId: String): Flow<ConversationEntity?> = dao.conversation(conversationId)

    suspend fun cleanup() = dao.deleteEmptyAssistantMessages()

    suspend fun createConversation(model: String, title: String): ConversationEntity {
        val now = System.currentTimeMillis()
        val conversation = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            model = model,
            createdAt = now,
            updatedAt = now,
        )
        dao.insertConversation(conversation)
        return conversation
    }

    suspend fun addUserMessage(conversationId: String, text: String): MessageEntity =
        addMessage(conversationId, role = "user", content = text)

    suspend fun addAssistantPlaceholder(conversationId: String): MessageEntity =
        addMessage(conversationId, role = "assistant", content = "")

    private suspend fun addMessage(conversationId: String, role: String, content: String): MessageEntity {
        val time = maxOf(System.currentTimeMillis(), (dao.lastMessageTime(conversationId) ?: 0L) + 1)
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = role,
            content = content,
            createdAt = time,
        )
        dao.insertMessage(message)
        dao.touch(conversationId, time)
        return message
    }

    suspend fun updateContent(messageId: String, content: String) =
        dao.updateMessage(messageId, content, isError = false)

    /** Fecha a mensagem do assistente: salva o texto final, converte em erro ou descarta se vazia. */
    suspend fun finishAssistant(messageId: String, conversationId: String, text: String, error: String?) {
        when {
            text.isBlank() && error == null -> dao.deleteMessage(messageId)
            text.isBlank() -> dao.updateMessage(messageId, error.orEmpty(), isError = true)
            else -> dao.updateMessage(messageId, text, isError = false)
        }
        dao.touch(conversationId, System.currentTimeMillis())
    }

    /** Remove respostas do assistente no fim da conversa (para regenerar). */
    suspend fun dropTrailingAssistant(conversationId: String) {
        dao.messagesOnce(conversationId)
            .takeLastWhile { it.role == "assistant" }
            .forEach { dao.deleteMessage(it.id) }
    }

    fun stream(conversationId: String, settings: AppSettings): Flow<StreamEvent> = flow {
        val model = dao.conversationOnce(conversationId)?.model ?: settings.defaultModel
        val history = dao.messagesOnce(conversationId)
            .filter { !it.isError && it.content.isNotBlank() }
            .map { ApiMessage(it.role, it.content) }
            .takeLast(MAX_HISTORY)
            .dropWhile { it.role != "user" }

        if (history.isEmpty()) throw ApiException("Não há nada para enviar.")

        val request = MessagesRequest(
            model = model,
            maxTokens = settings.maxTokens,
            messages = history,
            system = settings.systemPrompt.trim().ifEmpty { null },
        )
        emitAll(api.stream(settings.apiKey, request))
    }

    suspend fun rename(id: String, title: String) = dao.rename(id, title.trim())
    suspend fun setPinned(id: String, pinned: Boolean) = dao.setPinned(id, pinned)
    suspend fun setModel(id: String, model: String) = dao.setModel(id, model)
    suspend fun delete(id: String) = dao.deleteConversation(id)
    suspend fun deleteAll() = dao.deleteAllConversations()

    private fun escapeLike(q: String): String =
        q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private companion object {
        const val MAX_HISTORY = 80
    }
}
