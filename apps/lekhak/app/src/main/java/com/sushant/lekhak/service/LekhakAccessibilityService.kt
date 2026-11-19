package com.sushant.lekhak.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import com.sushant.lekhak.core.ChatParser
import com.sushant.lekhak.core.LekhakServiceManager as M
import com.sushant.lekhak.core.RawText

/** Detects open WhatsApp chats, tracks the message box and reads recent messages. */
class LekhakAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: LekhakAccessibilityService? = null
            private set
        val WHATSAPP = setOf("com.whatsapp", "com.whatsapp.w4b")
        const val SELF = "com.sushant.lekhak"
        const val HIDE_GRACE_MS = 1200L
        private const val SCRAPE_INTERVAL_MS = 400L
        private const val MAX_NODES = 2500
        private val IGNORED_KEYWORDS = listOf("inputmethod", "keyboard", "honeyboard", "swiftkey", "gboard")
        private val IGNORED_EXACT = setOf("com.android.systemui", "android")

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = "${context.packageName}/${LekhakAccessibilityService::class.java.name}"
            val short = "${context.packageName}/.service.LekhakAccessibilityService"
            return enabled.split(':').any { it.equals(me, true) || it.equals(short, true) }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var hidePending = false
    private var lastScrape = 0L
    private var keyboardPackages: Set<String> = emptySet()

    private val hideRunnable = Runnable {
        hidePending = false
        M.updateChatActiveState(false)
        M.clearInput()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        M.initialize(this)
        M.isAccessibilityActive = true
        keyboardPackages = try {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .enabledInputMethodList.map { it.packageName }.toSet()
        } catch (_: Exception) { emptySet() }
        // Bring the bubble back after a reboot or app update.
        if (M.serviceEnabled && Settings.canDrawOverlays(this)) FloatingBubbleService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == SELF) return // our own sheet / settings never change chat state
        if (pkg in WHATSAPP) {
            handleWhatsApp()
            return
        }
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (isIgnorable(pkg)) return // keyboard opening must not hide the bubble
            cancelHide()
            M.updateChatActiveState(false)
            M.clearInput()
        }
    }

    private fun isIgnorable(pkg: String): Boolean =
        pkg in IGNORED_EXACT || pkg in keyboardPackages || IGNORED_KEYWORDS.any { pkg.contains(it, true) }

    private fun cancelHide() {
        if (hidePending) handler.removeCallbacks(hideRunnable)
        hidePending = false
    }

    private fun handleWhatsApp() {
        val root = whatsAppRoot() ?: return
        val edit = findEditableNode(root)
        if (edit != null) {
            cancelHide()
            M.updateInputFocus(edit, draftText(edit))
            M.updateChatActiveState(true)
            val now = SystemClock.uptimeMillis()
            if (now - lastScrape > SCRAPE_INTERVAL_MS) {
                lastScrape = now
                M.updateChatContext(scrapeChatTexts(root))
            }
        } else if (M.isWhatsAppChatActive && !hidePending) {
            hidePending = true
            handler.postDelayed(hideRunnable, HIDE_GRACE_MS)
        }
    }

    /** Root of the WhatsApp window, even when our own overlay has focus. */
    private fun whatsAppRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { if (it.packageName?.toString() in WHATSAPP) return it }
        return try {
            windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNull { it.root }
                .firstOrNull { it.packageName?.toString() in WHATSAPP }
        } catch (_: Exception) { null }
    }

    /** Re-reads the WhatsApp message box right before inserting text. */
    fun refreshInputNode(): AccessibilityNodeInfo? {
        val root = whatsAppRoot() ?: return null
        val edit = findEditableNode(root) ?: return null
        M.updateInputFocus(edit, draftText(edit))
        return edit
    }

    /** Fresh draft + chat context, read at the moment the bubble is tapped. */
    fun snapshot() {
        val root = whatsAppRoot() ?: return
        findEditableNode(root)?.let { M.updateInputFocus(it, draftText(it)) }
        M.updateChatContext(scrapeChatTexts(root))
    }

    private fun draftText(node: AccessibilityNodeInfo): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && node.isShowingHintText) return ""
        return node.text?.toString()?.trim().orEmpty()
    }

    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var seen = 0
        while (stack.isNotEmpty() && seen < MAX_NODES) {
            val n = stack.removeFirst()
            seen++
            if (n.isEditable) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.add(it) }
        }
        return null
    }

    private fun scrapeChatTexts(root: AccessibilityNodeInfo): String {
        val items = mutableListOf<RawText>()
        val rect = Rect()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var seen = 0
        while (stack.isNotEmpty() && seen < MAX_NODES) {
            val n = stack.removeFirst()
            seen++
            if (n.isEditable) continue // never read the draft box as a message
            val id = n.viewIdResourceName.orEmpty()
            if (id.endsWith(":id/message_text") || id.endsWith(":id/caption")) {
                val t = n.text?.toString()
                if (!t.isNullOrBlank()) {
                    n.getBoundsInScreen(rect)
                    items += RawText(t, rect.left, rect.right, isMessageView = true, top = rect.top)
                }
                continue
            }
            if (n.childCount == 0) {
                val cls = n.className?.toString().orEmpty()
                if (cls.endsWith("TextView") || cls.endsWith("ViewGroup") || cls.endsWith(".View")) {
                    val t = (n.text ?: n.contentDescription)?.toString()
                    if (!t.isNullOrBlank()) {
                        n.getBoundsInScreen(rect)
                        items += RawText(t, rect.left, rect.right, top = rect.top)
                    }
                }
            } else {
                for (i in 0 until n.childCount) n.getChild(i)?.let { stack.add(it) }
            }
        }
        return ChatParser.build(items, resources.displayMetrics.widthPixels)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(hideRunnable)
        instance = null
        M.isAccessibilityActive = false
        M.updateChatActiveState(false)
        M.clearInput()
        super.onDestroy()
    }
}
