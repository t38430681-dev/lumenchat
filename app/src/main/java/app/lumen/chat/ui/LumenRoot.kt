package app.lumen.chat.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumen.chat.ui.theme.LumenTheme
import kotlinx.coroutines.launch

@Composable
fun LumenRoot(vm: ChatViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                UiEvent.NeedApiKey -> {
                    showSettings = true
                    launch { snackbar.showSnackbar("Adicione sua chave de API para conversar.") }
                }

                is UiEvent.Message -> launch { snackbar.showSnackbar(event.text) }
            }
        }
    }

    LumenTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
        if (showSettings) {
            SettingsScreen(
                vm = vm,
                settings = settings,
                snackbar = snackbar,
                onBack = { showSettings = false },
            )
        } else {
            ChatScreen(
                vm = vm,
                settings = settings,
                snackbar = snackbar,
                onOpenSettings = { showSettings = true },
            )
        }
    }
}
