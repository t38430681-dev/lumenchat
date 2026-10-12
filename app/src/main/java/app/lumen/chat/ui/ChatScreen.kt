package app.lumen.chat.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumen.chat.data.AppSettings
import app.lumen.chat.data.ModelCatalog
import java.util.Locale
import kotlinx.coroutines.launch

private data class ConvRef(val id: String, val title: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    settings: AppSettings,
    snackbar: SnackbarHostState,
    onOpenSettings: () -> Unit,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val speaker = remember { Speaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    val speakingId by speaker.speakingId.collectAsStateWithLifecycle()

    var renameTarget by remember { mutableStateOf<ConvRef?>(null) }
    var deleteTarget by remember { mutableStateOf<ConvRef?>(null) }
    var modelMenu by remember { mutableStateOf(false) }
    var overflowMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) vm.draft = (vm.draft.trimEnd() + " " + spoken).trim()
        }
    }

    fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale agora")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("Reconhecimento de voz indisponível neste aparelho.") }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawer(
                conversations = conversations,
                selectedId = state.conversationId,
                search = vm.search,
                onSearch = { vm.search = it },
                onNewChat = {
                    vm.newChat()
                    scope.launch { drawerState.close() }
                },
                onOpen = {
                    vm.openConversation(it)
                    scope.launch { drawerState.close() }
                },
                onRename = { renameTarget = ConvRef(it.id, it.title) },
                onDelete = { deleteTarget = ConvRef(it.id, it.title) },
                onPin = { vm.togglePin(it) },
                onSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                },
            )
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = state.title.ifBlank { "Lumen" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Box {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { modelMenu = true },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = ModelCatalog.labelFor(state.model),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Icon(
                                        Icons.Filled.ArrowDropDown,
                                        contentDescription = "Trocar modelo",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                                    ModelCatalog.models.forEach { model ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(model.label)
                                                    Text(
                                                        model.hint,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            },
                                            trailingIcon = {
                                                if (model.id == state.model) Icon(Icons.Filled.Check, contentDescription = null)
                                            },
                                            onClick = {
                                                vm.setModel(model.id)
                                                modelMenu = false
                                            },
                                        )
                                    }
                                    if (ModelCatalog.models.none { it.id == state.model }) {
                                        DropdownMenuItem(
                                            text = { Text(state.model) },
                                            trailingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                                            onClick = { modelMenu = false },
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Conversas")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.newChat() }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Nova conversa")
                        }
                        val currentId = state.conversationId
                        if (currentId != null) {
                            Box {
                                IconButton(onClick = { overflowMenu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
                                }
                                DropdownMenu(expanded = overflowMenu, onDismissRequest = { overflowMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Compartilhar conversa") },
                                        leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                        onClick = {
                                            overflowMenu = false
                                            shareText(context, conversationAsText(state.title, state.messages))
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Renomear") },
                                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                        onClick = {
                                            overflowMenu = false
                                            renameTarget = ConvRef(currentId, state.title)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Apagar conversa") },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                        onClick = {
                                            overflowMenu = false
                                            deleteTarget = ConvRef(currentId, state.title)
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    if (state.messages.isEmpty()) {
                        EmptyState(
                            hasApiKey = settings.apiKey.isNotBlank(),
                            onPickSuggestion = { vm.draft = it },
                            onOpenSettings = onOpenSettings,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        MessageList(
                            messages = state.messages,
                            isStreaming = state.isStreaming,
                            speakingId = speakingId,
                            onCopy = { copyToClipboard(context, it) },
                            onSpeak = { message -> speaker.toggle(message.id, message.content) },
                            onShare = { shareText(context, it) },
                            onRegenerate = { vm.regenerate() },
                        )
                    }
                }
                InputBar(
                    draft = vm.draft,
                    onDraftChange = { vm.draft = it },
                    isStreaming = state.isStreaming,
                    onSend = { vm.send() },
                    onStop = { vm.stop() },
                    onMic = { startVoiceInput() },
                )
            }
        }
    }

    renameTarget?.let { target ->
        var text by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renomear conversa") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        vm.rename(target.id, text)
                        renameTarget = null
                    },
                ) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancelar") } },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Apagar conversa?") },
            text = { Text("“${target.title}” será removida deste aparelho. Isso não pode ser desfeito.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteConversation(target.id)
                        deleteTarget = null
                    },
                ) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun MessageList(
    messages: List<UiMessage>,
    isStreaming: Boolean,
    speakingId: String?,
    onCopy: (String) -> Unit,
    onSpeak: (UiMessage) -> Unit,
    onShare: (String) -> Unit,
    onRegenerate: () -> Unit,
) {
    val listState = rememberLazyListState()
    val reversed = remember(messages) { messages.asReversed() }
    val lastId = messages.lastOrNull()?.id

    // A lista é invertida: o item 0 é a mensagem mais recente, ancorada na base.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(reversed, key = { it.id }) { message ->
            if (message.isUser) {
                UserBubble(message.content)
            } else {
                AssistantMessage(
                    message = message,
                    canRegenerate = !isStreaming && message.id == lastId,
                    speaking = speakingId == message.id,
                    onCopy = { onCopy(message.content) },
                    onSpeak = { onSpeak(message) },
                    onShare = { onShare(message.content) },
                    onRegenerate = onRegenerate,
                )
            }
        }
    }
}
