package app.lumen.chat.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.lumen.chat.data.AppSettings
import app.lumen.chat.data.ModelCatalog
import app.lumen.chat.data.ThemeMode

private val tokenOptions = listOf(1024 to "1k", 2048 to "2k", 4096 to "4k", 8192 to "8k", 16000 to "16k")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: ChatViewModel,
    settings: AppSettings,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val uriHandler = LocalUriHandler.current

    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var systemPrompt by remember { mutableStateOf(settings.systemPrompt) }
    var customModel by remember {
        mutableStateOf(if (ModelCatalog.models.none { it.id == settings.defaultModel }) settings.defaultModel else "")
    }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // --- Chave de API ---
            SectionTitle("Chave de API")
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    vm.updateSettings { s -> s.copy(apiKey = it.trim()) }
                },
                label = { Text("Chave da Anthropic") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showKey) "Ocultar chave" else "Mostrar chave",
                        )
                    }
                },
                supportingText = {
                    val looksOdd = apiKey.isNotBlank() && !apiKey.trim().startsWith("sk-ant-")
                    Text(
                        if (looksOdd) "Chaves da Anthropic costumam começar com sk-ant-."
                        else "Fica salva criptografada neste aparelho e só é enviada à API da Anthropic.",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { uriHandler.openUri("https://console.anthropic.com/settings/keys") }) {
                Text("Criar uma chave no console da Anthropic")
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // --- Modelo padrão ---
            SectionTitle("Modelo padrão para novas conversas")
            ModelCatalog.models.forEach { model ->
                val selected = settings.defaultModel == model.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, role = Role.RadioButton) {
                            customModel = ""
                            vm.updateSettings { s -> s.copy(defaultModel = model.id) }
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(model.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            model.hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = customModel,
                onValueChange = {
                    customModel = it
                    val id = it.trim()
                    if (id.isNotEmpty()) vm.updateSettings { s -> s.copy(defaultModel = id) }
                },
                label = { Text("Outro modelo (ID personalizado)") },
                placeholder = { Text("ex.: claude-sonnet-5-5") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // --- Personalização ---
            SectionTitle("Instruções personalizadas")
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = {
                    systemPrompt = it
                    vm.updateSettings { s -> s.copy(systemPrompt = it) }
                },
                label = { Text("Como o assistente deve se comportar") },
                placeholder = { Text("ex.: Responda sempre em português, de forma direta e com exemplos.") },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Tamanho máximo da resposta")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tokenOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = settings.maxTokens == value,
                        onClick = { vm.updateSettings { s -> s.copy(maxTokens = value) } },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                "Em tokens. Respostas mais longas custam mais.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // --- Aparência ---
            SectionTitle("Aparência")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val labels = mapOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Escuro")
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { vm.updateSettings { s -> s.copy(themeMode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    ) { Text(labels.getValue(mode)) }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Cores do sistema", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Usa as cores do seu papel de parede (Material You).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.dynamicColor,
                        onCheckedChange = { checked -> vm.updateSettings { s -> s.copy(dynamicColor = checked) } },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // --- Dados ---
            SectionTitle("Dados")
            OutlinedButton(onClick = { confirmDeleteAll = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Apagar todas as conversas")
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // --- Sobre ---
            SectionTitle("Sobre")
            Text(
                "Lumen Chat 1.0.0 é um app independente e não oficial. Ele conversa com a API da " +
                    "Anthropic usando a sua chave: o uso é cobrado na sua conta da Anthropic. " +
                    "As conversas ficam salvas só neste aparelho.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Apagar todas as conversas?") },
            text = { Text("Todo o histórico será removido deste aparelho. Isso não pode ser desfeito.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteAll()
                        confirmDeleteAll = false
                    },
                ) { Text("Apagar tudo") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}
