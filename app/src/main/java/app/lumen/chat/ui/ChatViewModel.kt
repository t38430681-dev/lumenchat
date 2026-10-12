package app.lumen.chat.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.lumen.chat.LumenApplication
import app.lumen.chat.data.AppSettings
import app.lumen.chat.data.ChatRepository
import app.lumen.chat.data.ModelCatalog
import app.lumen.chat.data.SettingsStore
import app.lumen.chat.data.api.ApiException
import app.lumen.chat.data.api.StreamEvent
import app.lumen.chat.data.db.ConversationEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiMessage(
    val id: String,
    val isUser: Boolean,
    val content: String,
    val isError: Boolean,
    val isStreaming: Boolean,
)

data class ChatUiState(
    val conversationId: String? = null,
    val title: String = "",
    val model: String = ModelCatalog.default.id,
    val messages: List<UiMessage> = emptyList(),
    val isStreaming: Boolean = false,
)

sealed interface UiEvent {
    data object NeedApiKey : UiEvent
    data class Message(val text: String) : UiEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val saved: SavedStateHandle,
    private val repo: ChatRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsStore.settings

    /** Texto digitado no campo de mensagem. */
    var draft by mutableStateOf("")

    /** Texto da busca no menu lateral. */
    var search by mutableStateOf("")

    private val currentId: StateFlow<String?> = saved.getStateFlow<String?>(KEY_CONVERSATION, null)
    private val pendingModel = MutableStateFlow(settingsStore.settings.value.defaultModel)
    private val streamingId = MutableStateFlow<String?>(null)

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var generation: Job? = null
    private val busy: Boolean get() = generation?.isActive == true

    val conversations: StateFlow<List<ConversationEntity>> = snapshotFlow { search }
        .debounce(150)
        .distinctUntilChanged()
        .flatMapLatest { repo.conversations(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val currentConversation = currentId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repo.conversation(id)
    }

    private val storedMessages = currentId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repo.messages(id)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        currentConversation,
        storedMessages,
        streamingId,
        pendingModel,
    ) { conversation, messages, streaming, pending ->
        ChatUiState(
            conversationId = conversation?.id,
            title = conversation?.title.orEmpty(),
            model = conversation?.model ?: pending,
            messages = messages.map { m ->
                UiMessage(
                    id = m.id,
                    isUser = m.role == "user",
                    content = m.content,
                    isError = m.isError,
                    isStreaming = streaming != null && streaming == m.id,
                )
            },
            isStreaming = streaming != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch { repo.cleanup() }
    }

    // ---- Envio e geração ----

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || busy || !hasApiKey()) return
        draft = ""
        launchReply(userText = text)
    }

    fun regenerate() {
        if (busy || currentId.value == null || !hasApiKey()) return
        launchReply(userText = null, regenerate = true)
    }

    fun stop() {
        generation?.cancel()
    }

    private fun hasApiKey(): Boolean {
        if (settingsStore.settings.value.apiKey.isBlank()) {
            _events.tryEmit(UiEvent.NeedApiKey)
            return false
        }
        return true
    }

    private fun launchReply(userText: String?, regenerate: Boolean = false) {
        generation = viewModelScope.launch {
            val config = settingsStore.settings.value

            val conversationId = currentId.value ?: repo.createConversation(
                model = pendingModel.value,
                title = titleFrom(userText.orEmpty()),
            ).id.also { saved[KEY_CONVERSATION] = it }

            if (regenerate) repo.dropTrailingAssistant(conversationId)
            if (userText != null) repo.addUserMessage(conversationId, userText)

            val reply = repo.addAssistantPlaceholder(conversationId)
            streamingId.value = reply.id

            val buffer = StringBuilder()
            var error: String? = null
            var lastWrite = 0L
            try {
                repo.stream(conversationId, config).collect { event ->
                    if (event is StreamEvent.Delta) {
                        buffer.append(event.text)
                        val now = System.currentTimeMillis()
                        if (now - lastWrite >= WRITE_INTERVAL_MS) {
                            repo.updateContent(reply.id, buffer.toString())
                            lastWrite = now
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Exception) {
                error = "Erro inesperado: ${e.message ?: e::class.simpleName}"
            } finally {
                val failure = error
                withContext(NonCancellable) {
                    repo.finishAssistant(reply.id, conversationId, buffer.toString(), failure)
                    streamingId.value = null
                    // Com texto parcial, o erro vira aviso; sem texto, vira a própria mensagem.
                    if (failure != null && buffer.isNotBlank()) {
                        _events.tryEmit(UiEvent.Message(failure))
                    }
                }
            }
        }
    }

    // ---- Conversas ----

    fun newChat() {
        viewModelScope.launch {
            generation?.cancelAndJoin()
            resetToBlank()
        }
    }

    fun openConversation(id: String) {
        viewModelScope.launch {
            generation?.cancelAndJoin()
            saved[KEY_CONVERSATION] = id
        }
    }

    fun startWithSharedText(text: String) {
        viewModelScope.launch {
            generation?.cancelAndJoin()
            resetToBlank()
            draft = text.take(MAX_SHARED_CHARS)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            if (currentId.value == id) {
                generation?.cancelAndJoin()
                resetToBlank()
            }
            repo.delete(id)
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            generation?.cancelAndJoin()
            resetToBlank()
            repo.deleteAll()
        }
    }

    fun rename(id: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repo.rename(id, title) }
    }

    fun togglePin(conversation: ConversationEntity) {
        viewModelScope.launch { repo.setPinned(conversation.id, !conversation.pinned) }
    }

    fun setModel(model: String) {
        val id = currentId.value
        if (id == null) {
            pendingModel.value = model
        } else {
            viewModelScope.launch { repo.setModel(id, model) }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsStore.update(transform)
        if (currentId.value == null) {
            pendingModel.value = settingsStore.settings.value.defaultModel
        }
    }

    private fun resetToBlank() {
        saved[KEY_CONVERSATION] = null
        pendingModel.value = settingsStore.settings.value.defaultModel
        draft = ""
    }

    private fun titleFrom(text: String): String =
        text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?.take(TITLE_MAX)
            ?: "Nova conversa"

    companion object {
        private const val KEY_CONVERSATION = "conversation_id"
        private const val WRITE_INTERVAL_MS = 120L
        private const val TITLE_MAX = 48
        private const val MAX_SHARED_CHARS = 20_000

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LumenApplication
                ChatViewModel(
                    saved = createSavedStateHandle(),
                    repo = app.container.repository,
                    settingsStore = app.container.settings,
                )
            }
        }
    }
}
