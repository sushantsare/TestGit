package com.sushant.lekhak.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.sushant.lekhak.BuildConfig
import com.sushant.lekhak.service.LekhakAccessibilityService
import java.util.concurrent.CopyOnWriteArraySet

interface ServiceStateListener {
    fun onInputFocusChanged(hasFocus: Boolean, text: String) {}
    fun onChatContextUpdated(context: String) {}
    fun onChatActiveStateChanged(isActive: Boolean) {}
}

/** Central state shared by the accessibility service, the bubble service and Settings. */
object LekhakServiceManager {
    const val PREFS = "lekhak_prefs"
    const val SECRETS = "lekhak_secrets"
    const val DEFAULT_OPENAI_URL = "https://api.openai.com/v1/"
    const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
    const val DEFAULT_GEMINI_MODEL = "gemini-flash-latest"
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    val TONES = listOf("Casual", "Professional", "Witty", "Direct", "Grammar Polishing")
    val LANGUAGES = listOf("English", "Hindi", "French", "Spanish", "Hinglish")

    @Volatile var isAccessibilityActive = false
    @Volatile var isBubbleServiceActive = false
    @Volatile var isWhatsAppChatActive = false
        private set

    var useGemini = true
    var selectedTone = "Casual"
    var targetLanguage = "English"
    var customPromptInstruction = ""
    var customOpenAiKey = ""
    var customOpenAiUrl = DEFAULT_OPENAI_URL
    var customOpenAiModel = DEFAULT_OPENAI_MODEL
    var geminiKey = ""
    var geminiModel = DEFAULT_GEMINI_MODEL
    var themeMode = THEME_SYSTEM
    var serviceEnabled = true
        private set

    @Volatile var activeInputNode: AccessibilityNodeInfo? = null
    @Volatile var lastCapturedInputText = ""
    @Volatile var chatContextSnippet = ""

    private var prefs: SharedPreferences? = null
    private var secrets: SharedPreferences? = null
    private val listeners = CopyOnWriteArraySet<ServiceStateListener>()
    private val main = Handler(Looper.getMainLooper())
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val s = app.getSharedPreferences(SECRETS, Context.MODE_PRIVATE)
        prefs = p
        secrets = s
        useGemini = p.getBoolean("use_gemini", true)
        selectedTone = p.getString("tone", "Casual")!!.takeIf { it in TONES } ?: "Casual"
        targetLanguage = p.getString("language", "English")!!.takeIf { it in LANGUAGES } ?: "English"
        customOpenAiUrl = p.getString("openai_api_url", DEFAULT_OPENAI_URL)!!.ifBlank { DEFAULT_OPENAI_URL }
        customOpenAiModel = p.getString("openai_api_model", DEFAULT_OPENAI_MODEL)!!.ifBlank { DEFAULT_OPENAI_MODEL }
        geminiModel = p.getString("gemini_model", DEFAULT_GEMINI_MODEL)!!.ifBlank { DEFAULT_GEMINI_MODEL }
        themeMode = p.getString("theme", THEME_SYSTEM)!!
        serviceEnabled = p.getBoolean("service_enabled", true)
        customOpenAiKey = s.getString("openai_api_key", "")!!
        geminiKey = s.getString("gemini_api_key", "")!!
        initialized = true
    }

    fun prefs(context: Context): SharedPreferences {
        initialize(context)
        return prefs!!
    }

    // ---------- keys & provider ----------
    fun hasBuiltInGeminiKey() = BuildConfig.GEMINI_API_KEY.isNotBlank()
    fun effectiveGeminiKey() = geminiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
    fun isAiReady() = if (useGemini) effectiveGeminiKey().isNotBlank() else customOpenAiKey.isNotBlank()

    fun setProvider(gemini: Boolean) {
        useGemini = gemini
        prefs?.edit()?.putBoolean("use_gemini", gemini)?.apply()
    }

    fun saveOpenAi(key: String, url: String, model: String) {
        customOpenAiKey = key.trim()
        customOpenAiUrl = url.trim().ifBlank { DEFAULT_OPENAI_URL }.let { if (it.endsWith("/")) it else "$it/" }
        customOpenAiModel = model.trim().ifBlank { DEFAULT_OPENAI_MODEL }
        secrets?.edit()?.putString("openai_api_key", customOpenAiKey)?.apply()
        prefs?.edit()?.putString("openai_api_url", customOpenAiUrl)?.putString("openai_api_model", customOpenAiModel)?.apply()
    }

    fun saveGemini(key: String, model: String) {
        geminiKey = key.trim()
        geminiModel = model.trim().ifBlank { DEFAULT_GEMINI_MODEL }
        secrets?.edit()?.putString("gemini_api_key", geminiKey)?.apply()
        prefs?.edit()?.putString("gemini_model", geminiModel)?.apply()
    }

    fun setTone(tone: String) {
        selectedTone = tone
        prefs?.edit()?.putString("tone", tone)?.apply()
    }

    fun setLanguage(lang: String) {
        targetLanguage = lang
        prefs?.edit()?.putString("language", lang)?.apply()
    }

    fun setTheme(mode: String) {
        themeMode = mode
        prefs?.edit()?.putString("theme", mode)?.apply()
    }

    fun updateServiceEnabled(enabled: Boolean) {
        serviceEnabled = enabled
        prefs?.edit()?.putBoolean("service_enabled", enabled)?.apply()
    }

    // ---------- listeners ----------
    fun addListener(l: ServiceStateListener) { listeners.add(l) }
    fun removeListener(l: ServiceStateListener) { listeners.remove(l) }

    private fun post(block: (ServiceStateListener) -> Unit) {
        main.post { listeners.forEach(block) }
    }

    fun updateInputFocus(node: AccessibilityNodeInfo?, text: String) {
        activeInputNode = node
        val changed = text != lastCapturedInputText
        lastCapturedInputText = text
        if (changed) post { it.onInputFocusChanged(node != null, text) }
    }

    fun clearInput() {
        activeInputNode = null
        lastCapturedInputText = ""
    }

    fun updateChatContext(context: String) {
        if (context == chatContextSnippet) return
        chatContextSnippet = context
        post { it.onChatContextUpdated(context) }
    }

    fun updateChatActiveState(active: Boolean) {
        if (active == isWhatsAppChatActive) return
        isWhatsAppChatActive = active
        post { it.onChatActiveStateChanged(active) }
    }

    // ---------- insertion (no clipboard) ----------
    /**
     * Puts [text] into WhatsApp's message box. Uses ACTION_SET_TEXT only, never the clipboard.
     * Refreshes the input node first (it can go stale), then retries once after focusing it.
     */
    fun insertText(context: Context, text: String): Boolean {
        val fresh = LekhakAccessibilityService.instance?.refreshInputNode()
        val node = fresh ?: activeInputNode ?: return false
        if (!node.isEditable) return false
        if (setText(node, text)) return true
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (setText(node, text)) return true
        val again = LekhakAccessibilityService.instance?.refreshInputNode() ?: return false
        return setText(again, text)
    }

    private fun setText(node: AccessibilityNodeInfo, text: String): Boolean = try {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    } catch (_: Exception) {
        false
    }
}
