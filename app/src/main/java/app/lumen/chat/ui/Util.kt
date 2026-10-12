package app.lumen.chat.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Lumen Chat", text))
    // A partir do Android 13 o sistema já mostra a confirmação.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
    }
}

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text.take(100_000))
    }
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (_: ActivityNotFoundException) {
        // Nenhum app para compartilhar.
    }
}

fun conversationAsText(title: String, messages: List<UiMessage>): String = buildString {
    if (title.isNotBlank()) append("# ").append(title).append("\n\n")
    messages.filter { !it.isError }.forEach { m ->
        append(if (m.isUser) "**Você**" else "**Assistente**").append("\n")
        append(m.content.trim()).append("\n\n")
    }
}.trim()
