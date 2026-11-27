package com.sushant.pageone.ui.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Read aloud with the phone's own offline voices. Speaks a page sentence by sentence. */
class Speech(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var sentences: List<String> = emptyList()
    var active by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var page by mutableIntStateOf(0)
    var index by mutableIntStateOf(0)
    var rate by mutableFloatStateOf(1f)
    var sleepAt by mutableStateOf<Long?>(null)
    var voiceName by mutableStateOf("Phone voice")
    var current by mutableStateOf("")
    var onPageDone: (() -> Unit)? = null
    var failed by mutableStateOf(false)

    fun start(page: Int, text: String, fromChar: Int = 0) {
        this.page = page
        sentences = split(text.substring(fromChar.coerceIn(0, text.length)))
        index = 0
        active = true
        ensure { speakCurrent() }
    }

    private fun ensure(then: () -> Unit) {
        if (tts != null && ready) { then(); return }
        tts = TextToSpeech(context) { st ->
            ready = st == TextToSpeech.SUCCESS
            if (!ready) { failed = true; active = false; return@TextToSpeech }
            val t = tts ?: return@TextToSpeech
            val loc = Locale("en", "IN")
            if (t.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) t.language = loc
            voiceName = t.voice?.locale?.displayName ?: "Phone voice"
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post { advance() }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { android.os.Handler(android.os.Looper.getMainLooper()).post { advance() } }
            })
            then()
        }
    }

    private fun advance() {
        if (!playing) return
        sleepAt?.let { if (System.currentTimeMillis() >= it) { pause(); sleepAt = null; return } }
        if (index < sentences.size - 1) { index++; speakCurrent() } else { playing = false; onPageDone?.invoke() }
    }

    private fun speakCurrent() {
        val t = tts ?: return
        if (sentences.isEmpty()) { playing = false; onPageDone?.invoke(); return }
        current = sentences[index]
        playing = true
        t.setSpeechRate(rate)
        t.speak(current, TextToSpeech.QUEUE_FLUSH, null, "s$index")
    }

    fun toggle() { if (playing) pause() else if (sentences.isNotEmpty()) { playing = true; speakCurrent() } }
    fun pause() { playing = false; tts?.stop() }
    fun stop() { pause(); active = false; current = "" }
    fun cycleRate() { rate = when (rate) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 0.8f; else -> 1f }; if (playing) speakCurrent() }
    fun cycleSleep(): String {
        val now = System.currentTimeMillis()
        val left = sleepAt?.let { ((it - now) / 60_000L).toInt() } ?: 0
        val next = when { sleepAt == null -> 15; left <= 15 -> 30; left <= 30 -> 60; else -> 0 }
        sleepAt = if (next == 0) null else now + next * 60_000L
        return if (next == 0) "Sleep timer off" else "Stops in $next min"
    }
    fun shutdown() { runCatching { tts?.stop(); tts?.shutdown() }; tts = null; ready = false }

    companion object {
        /** Sentences of reasonable length for the voice (unit-tested). */
        fun split(text: String): List<String> {
            val clean = text.replace(Regex("-\\n(?=\\p{L})"), "").replace(Regex("\\s+"), " ").trim()
            if (clean.isEmpty()) return emptyList()
            val parts = clean.split(Regex("(?<=[.!?।])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
            val out = ArrayList<String>()
            for (p in parts) {
                if (p.length <= 300) out += p
                else p.chunked(280).forEach { out += it }
            }
            return out
        }
    }
}
