package app.lumen.chat.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Leitura em voz alta de respostas usando o TextToSpeech do Android. */
class Speaker(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingStart: (() -> Unit)? = null
    private var lastUtterance: String? = null

    private val _speakingId = MutableStateFlow<String?>(null)
    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            if (utteranceId == lastUtterance) _speakingId.value = null
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            if (utteranceId == lastUtterance) _speakingId.value = null
        }
    }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                engine.setLanguage(Locale.getDefault())
                engine.setOnUtteranceProgressListener(listener)
                ready = true
                pendingStart?.invoke()
                pendingStart = null
            } else {
                _speakingId.value = null
            }
        }
    }

    fun toggle(id: String, markdown: String) {
        if (_speakingId.value == id) {
            stop()
            return
        }
        stop()
        val plain = stripMarkdown(markdown)
        if (plain.isBlank()) return

        _speakingId.value = id
        val start = {
            val engine = tts
            if (engine != null) {
                val chunks = chunk(plain, MAX_CHUNK)
                chunks.forEachIndexed { index, part ->
                    val utteranceId = "$id#$index"
                    if (index == chunks.lastIndex) lastUtterance = utteranceId
                    engine.speak(
                        part,
                        if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                        null,
                        utteranceId,
                    )
                }
            }
        }
        if (ready) start() else pendingStart = start
    }

    fun stop() {
        tts?.stop()
        pendingStart = null
        lastUtterance = null
        _speakingId.value = null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _speakingId.value = null
    }

    private fun chunk(text: String, max: Int): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (paragraph in text.split("\n\n")) {
            var part = paragraph
            while (part.length > max) {
                val cut = part.lastIndexOf(' ', max).let { if (it <= 0) max else it }
                if (current.isNotEmpty()) {
                    out += current.toString()
                    current.clear()
                }
                out += part.substring(0, cut)
                part = part.substring(cut).trimStart()
            }
            if (current.isNotEmpty() && current.length + part.length + 2 > max) {
                out += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(part)
        }
        if (current.isNotEmpty()) out += current.toString()
        return out.filter { it.isNotBlank() }
    }

    private companion object {
        const val MAX_CHUNK = 3500
    }
}

/** Remove a marcação Markdown para que a voz não leia asteriscos, crases etc. */
fun stripMarkdown(src: String): String {
    var s = src
    s = Regex("```[\\s\\S]*?(```|\$)").replace(s, " ")
    s = Regex("`([^`]*)`").replace(s, "\$1")
    s = Regex("!?\\[([^\\]]*)]\\([^)]*\\)").replace(s, "\$1")
    s = Regex("(?m)^\\s{0,3}#{1,6}\\s*").replace(s, "")
    s = Regex("(?m)^\\s*([-*+]|\\d+[.)])\\s+").replace(s, "")
    s = Regex("(?m)^>\\s?").replace(s, "")
    s = s.replace("**", "").replace("__", "").replace("~~", "").replace("*", "")
    return s.trim()
}
