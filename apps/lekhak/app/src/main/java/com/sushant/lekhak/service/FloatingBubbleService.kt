package com.sushant.lekhak.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sushant.lekhak.R
import com.sushant.lekhak.SettingsActivity
import com.sushant.lekhak.ai.AiException
import com.sushant.lekhak.ai.AiRepository
import com.sushant.lekhak.ai.AiRequest
import com.sushant.lekhak.ai.Mode
import com.sushant.lekhak.ai.Suggestion
import com.sushant.lekhak.core.LekhakServiceManager as M
import com.sushant.lekhak.core.Palette
import com.sushant.lekhak.core.ServiceStateListener
import com.sushant.lekhak.core.toast
import com.sushant.lekhak.overlay.BubbleView
import com.sushant.lekhak.overlay.SheetView
import com.sushant.lekhak.overlay.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Foreground service that owns the floating bubble and the bottom-sheet overlay. */
class FloatingBubbleService : Service(), ServiceStateListener {

    companion object {
        const val NOTIFICATION_ID = 8871
        const val CHANNEL_ID = "LekhakBubbleChannel"
        private const val LONG_PRESS_MS = 550L

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, FloatingBubbleService::class.java))
            } catch (_: Exception) {
                // Background start not allowed right now; Settings or the accessibility service will retry.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private var bubble: BubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleShown = false
    private var sheet: SheetView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val repo = AiRepository()
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        M.initialize(this)
        startInForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        M.isBubbleServiceActive = true
        M.addListener(this)
        if (M.isWhatsAppChatActive) showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Lekhak bubble", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Keeps the Lekhak bubble ready inside WhatsApp"
                    setShowBadge(false)
                }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pen)
            .setContentTitle("Lekhak is active")
            .setContentText("Tap the violet pen icon inside WhatsApp to view smart replies.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun overlayType() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private fun screenW() = resources.displayMetrics.widthPixels
    private fun screenH() = resources.displayMetrics.heightPixels

    // ---------------- bubble ----------------
    private fun ensureBubble(): BubbleView {
        bubble?.let { return it }
        val size = dp(56)
        val prefs = M.prefs(this)
        val params = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("bubble_x", screenW() - size - dp(16))
            y = prefs.getInt("bubble_y", (screenH() * 0.8f).toInt() - size)
        }
        clampToScreen(params, size)
        val view = BubbleView(this)
        attachBubbleTouch(view, params)
        view.scaleX = 0f
        view.scaleY = 0f
        view.visibility = View.GONE
        wm.addView(view, params)
        bubble = view
        bubbleParams = params
        return view
    }

    private fun clampToScreen(p: WindowManager.LayoutParams, size: Int) {
        p.x = p.x.coerceIn(0, (screenW() - size).coerceAtLeast(0))
        p.y = p.y.coerceIn(0, (screenH() - size).coerceAtLeast(0))
    }

    private fun showBubble() {
        if (sheet != null) return
        val v = try { ensureBubble() } catch (_: Exception) { return }
        if (bubbleShown) return
        bubbleShown = true
        v.animate().cancel()
        v.visibility = View.VISIBLE
        v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
    }

    private fun hideBubble() {
        val v = bubble ?: return
        if (!bubbleShown) return
        bubbleShown = false
        v.animate().cancel()
        v.animate().scaleX(0f).scaleY(0f).setDuration(150).withEndAction {
            if (!bubbleShown) v.visibility = View.GONE
        }.start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachBubbleTouch(view: BubbleView, params: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var longPressed = false
        val longPress = Runnable {
            longPressed = true
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            toast("Opening Lekhak Settings...")
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    dragging = false; longPressed = false
                    view.animate().alpha(1f).setDuration(100).start()
                    handler.postDelayed(longPress, LONG_PRESS_MS)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > 10 || abs(dy) > 10)) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging && !longPressed) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        clampToScreen(params, view.width)
                        safeUpdate(view, params)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    view.animate().alpha(BubbleView.IDLE_ALPHA).setDuration(200).start()
                    when {
                        longPressed -> Unit
                        dragging -> snapToEdge(view, params)
                        e.actionMasked == MotionEvent.ACTION_UP -> openSheet()
                    }
                }
            }
            true
        }
    }

    private fun safeUpdate(v: View, p: WindowManager.LayoutParams) {
        try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
    }

    private fun snapToEdge(view: View, params: WindowManager.LayoutParams) {
        val size = view.width
        val left = dp(16)
        val right = screenW() - size - dp(16)
        val target = if (params.x + size / 2 < screenW() / 2) left else right
        ValueAnimator.ofInt(params.x, target).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                params.x = it.animatedValue as Int
                safeUpdate(view, params)
            }
            start()
        }
        M.prefs(this).edit().putInt("bubble_x", target).putInt("bubble_y", params.y).apply()
    }

    // ---------------- sheet ----------------
    private fun openSheet() {
        if (sheet != null) return
        LekhakAccessibilityService.instance?.snapshot()
        val palette = Palette.resolve(this, M.themeMode)
        val view = SheetView(this, palette, screenH())
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        view.onDismiss = { closeSheet() }
        view.onToneSelected = { M.setTone(it); view.renderChips(); generate(view) }
        view.onLanguageSelected = { M.setLanguage(it); view.renderChips(); generate(view) }
        view.onRewrite = { M.customPromptInstruction = it; generate(view) }
        view.onSuggestionTapped = { insert(it) }
        try {
            wm.addView(view, params)
        } catch (_: Exception) {
            toast("Lekhak can't draw over apps. Open Lekhak to allow it.")
            return
        }
        sheet = view
        // hide the bubble instantly while the sheet is open
        bubble?.let { it.animate().cancel(); it.scaleX = 0f; it.scaleY = 0f; it.visibility = View.GONE }
        bubbleShown = false
        M.customPromptInstruction = ""
        view.animateIn()
        generate(view)
    }

    private fun closeSheet(after: (() -> Unit)? = null) {
        val view = sheet ?: return
        job?.cancel()
        view.animateOut {
            try { wm.removeView(view) } catch (_: Exception) {}
            sheet = null
            after?.invoke()
            if (M.isWhatsAppChatActive) showBubble()
        }
    }

    private fun generate(view: SheetView) {
        val draft = M.lastCapturedInputText
        val rewrite = draft.isNotBlank()
        view.setMode(rewrite)
        view.showLoading()
        job?.cancel()
        if (!M.isAiReady()) {
            view.showSuggestions(listOf(Suggestion("Error",
                if (M.useGemini) "Add your Gemini API key in Lekhak Settings (long-press the bubble)."
                else "Please add your OpenAI API key in Settings (long-press the bubble).", isError = true)))
            return
        }
        val req = AiRequest(
            mode = if (rewrite) Mode.REWRITE else Mode.SMART_REPLY,
            history = M.chatContextSnippet,
            draft = draft,
            tone = M.selectedTone,
            language = M.targetLanguage,
            customInstruction = M.customPromptInstruction,
        )
        job = scope.launch {
            val result = try {
                repo.generate(req)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                listOf(Suggestion("Error", e.message ?: "AI generation failed. Try again.", isError = true))
            } catch (e: Exception) {
                listOf(Suggestion("Error", "AI generation failed. Try again.", isError = true))
            }
            if (sheet === view) view.showSuggestions(result)
        }
    }

    private fun insert(s: Suggestion) {
        if (M.insertText(this, s.text)) {
            toast("Inserted into chat!")
            closeSheet()
            return
        }
        // WhatsApp may refuse while our sheet has focus: close it, then try once more.
        closeSheet {
            handler.postDelayed({
                if (M.insertText(this, s.text)) toast("Inserted into chat!")
                else toast("Couldn't insert. Tap WhatsApp's message box once, then try again.", long = true)
            }, 250)
        }
    }

    // ---------------- listener ----------------
    override fun onChatActiveStateChanged(isActive: Boolean) {
        if (isActive) showBubble() else {
            hideBubble()
            if (sheet != null && !isActive) closeSheet()
        }
    }

    override fun onDestroy() {
        M.removeListener(this)
        M.isBubbleServiceActive = false
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        sheet?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        sheet = null
        bubble = null
        super.onDestroy()
    }
}
