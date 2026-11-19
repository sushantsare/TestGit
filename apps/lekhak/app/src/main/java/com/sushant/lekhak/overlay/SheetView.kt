package com.sushant.lekhak.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import com.sushant.lekhak.ai.Suggestion
import com.sushant.lekhak.core.LekhakServiceManager
import com.sushant.lekhak.core.Palette
import kotlin.math.max
import kotlin.math.min

/**
 * The expanded bottom sheet shown over WhatsApp. Plain Views so it can live in a
 * WindowManager overlay. All actions are reported through the callbacks.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class SheetView(
    context: Context,
    private val p: Palette,
    private val screenHeight: Int,
) : FrameLayout(context) {

    var onDismiss: () -> Unit = {}
    var onToneSelected: (String) -> Unit = {}
    var onLanguageSelected: (String) -> Unit = {}
    var onRewrite: (String) -> Unit = {}
    var onSuggestionTapped: (Suggestion) -> Unit = {}

    private val backdrop = View(context).apply { setBackgroundColor(0xB0000000.toInt()) }
    val card = LinearLayout(context)
    private val modeLabel = TextView(context)
    private val toneRow = LinearLayout(context)
    private val langRow = LinearLayout(context)
    val promptInput = EditText(context)
    private val loading = LinearLayout(context)
    private val list = LinearLayout(context)
    private val scroll = ScrollView(context)
    private var animating = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        addView(backdrop, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        backdrop.setOnClickListener { onDismiss() }

        val pad = context.dp(16)
        val radius = context.dp(16).toFloat()
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(pad, context.dp(8), pad, pad)
        card.background = GradientDrawable().apply {
            setColor(p.background)
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            setStroke(context.dp(1), p.divider)
        }
        card.isClickable = true // touches on the card must not reach the backdrop
        val h = min((screenHeight * 0.75f).toInt(), max((screenHeight * 0.40f).toInt(), context.dp(440)))
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, h, Gravity.BOTTOM))
        // Keep the last card above the navigation bar.
        setOnApplyWindowInsetsListener { _, insets ->
            val bottom = WindowInsetsCompat.toWindowInsetsCompat(insets)
                .getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            card.setPadding(pad, context.dp(8), pad, pad + bottom)
            (card.layoutParams as LayoutParams).height = h + bottom
            card.requestLayout()
            insets
        }

        buildHandleAndHeader()
        card.addView(chipScroller(toneRow), LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(10) })
        card.addView(chipScroller(langRow), LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(6) })
        buildPromptRow()
        buildContent()
        renderChips()
    }

    // ---------------- building ----------------
    private fun buildHandleAndHeader() {
        val handle = View(context).apply { background = rounded(p.handle, context.dp(3).toFloat()) }
        val handleBox = FrameLayout(context).apply {
            setPadding(0, context.dp(4), 0, context.dp(8))
            addView(handle, LayoutParams(context.dp(40), context.dp(5), Gravity.CENTER_HORIZONTAL))
            tag = "drag_handle"
        }
        card.addView(handleBox, LinearLayout.LayoutParams(-1, -2))
        attachDrag(handleBox)

        val header = FrameLayout(context)
        header.addView(TextView(context).apply {
            text = "Lekhak"
            setTextColor(p.brand)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }, LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
        modeLabel.apply {
            setTextColor(p.text)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            text = "Reply Suggestions"
        }
        header.addView(modeLabel, LayoutParams(-2, -2, Gravity.CENTER))
        header.addView(TextView(context).apply {
            text = "✕"
            setTextColor(p.textSecondary)
            textSize = 16f
            gravity = Gravity.CENTER
            contentDescription = "Close"
            setOnClickListener { onDismiss() }
        }, LayoutParams(context.dp(40), context.dp(36), Gravity.END or Gravity.CENTER_VERTICAL))
        card.addView(header, LinearLayout.LayoutParams(-1, context.dp(36)))
        attachDrag(header)
        card.addView(View(context).apply { setBackgroundColor(p.divider) },
            LinearLayout.LayoutParams(-1, context.dp(1)).apply { topMargin = context.dp(6) })
    }

    private fun chipScroller(row: LinearLayout) = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        row.orientation = LinearLayout.HORIZONTAL
        addView(row)
    }

    private fun buildPromptRow() {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        promptInput.apply {
            hint = "E.g. agree warmly, decline politely, translate to French"
            setHintTextColor(if (p.isDark) 0xFF7C8590.toInt() else 0xFF8A84A0.toInt())
            setTextColor(p.text)
            textSize = 13f
            maxLines = 2
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_GO
            background = rounded(p.input, context.dp(8).toFloat(), context.dp(1), p.border)
            setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
            tag = "prompt_input"
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO) { submitPrompt(); true } else false
            }
        }
        row.addView(promptInput, LinearLayout.LayoutParams(0, context.dp(44), 1f))
        val btn = Button(context).apply {
            text = "Rewrite"
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(p.chipSelected, context.dp(8).toFloat())
            stateListAnimator = null
            minWidth = 0
            minimumWidth = 0
            setPadding(context.dp(16), 0, context.dp(16), 0)
            tag = "rewrite_button"
            setOnClickListener { submitPrompt() }
        }
        row.addView(btn, LinearLayout.LayoutParams(-2, context.dp(44)).apply { marginStart = context.dp(8) })
        card.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(10) })
    }

    private fun submitPrompt() {
        hideKeyboard()
        onRewrite(promptInput.text.toString().trim())
    }

    private fun buildContent() {
        val content = FrameLayout(context)
        loading.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(ProgressBar(context).apply {
                indeterminateTintList = ColorStateList.valueOf(p.brand)
            }, LinearLayout.LayoutParams(context.dp(36), context.dp(36)))
            addView(TextView(context).apply {
                text = "Generating..."
                gravity = Gravity.CENTER
                setTextColor(p.textSecondary)
                textSize = 13f
                setPadding(0, context.dp(8), 0, 0)
            })
            visibility = View.GONE
        }
        list.orientation = LinearLayout.VERTICAL
        scroll.addView(list)
        scroll.isVerticalScrollBarEnabled = false
        content.addView(scroll, LayoutParams(-1, -1))
        content.addView(loading, LayoutParams(-1, -1))
        card.addView(content, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = context.dp(12) })
    }

    // ---------------- chips ----------------
    fun renderChips() {
        fillChips(toneRow, LekhakServiceManager.TONES, LekhakServiceManager.selectedTone) { onToneSelected(it) }
        fillChips(langRow, LekhakServiceManager.LANGUAGES, LekhakServiceManager.targetLanguage) { onLanguageSelected(it) }
    }

    private fun fillChips(row: LinearLayout, items: List<String>, selected: String, click: (String) -> Unit) {
        row.removeAllViews()
        items.forEach { item ->
            val on = item == selected
            row.addView(TextView(context).apply {
                text = item
                textSize = 12.5f
                setTextColor(if (on) 0xFFFFFFFF.toInt() else p.textSecondary)
                typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER
                setPadding(context.dp(14), 0, context.dp(14), 0)
                background = if (on) rounded(p.chipSelected, context.dp(16).toFloat(), context.dp(1), p.chipStroke)
                else rounded(p.input, context.dp(16).toFloat(), context.dp(1), p.border)
                isSelected = on
                tag = "chip_$item"
                setOnClickListener { if (!on) click(item) }
            }, LinearLayout.LayoutParams(-2, context.dp(34)).apply { marginEnd = context.dp(8) })
        }
    }

    // ---------------- state ----------------
    fun setMode(rewrite: Boolean) {
        modeLabel.text = if (rewrite) "Rewrite Draft" else "Reply Suggestions"
    }

    fun showLoading() {
        list.removeAllViews()
        loading.visibility = View.VISIBLE
    }

    fun showSuggestions(items: List<Suggestion>) {
        loading.visibility = View.GONE
        list.removeAllViews()
        scroll.scrollTo(0, 0)
        items.forEachIndexed { i, s -> list.addView(suggestionCard(s, i)) }
    }

    private fun suggestionCard(s: Suggestion, index: Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(p.card, context.dp(12).toFloat(), context.dp(1), if (p.isDark) p.card else p.border)
            clipToOutline = true
            tag = "suggestion_$index"
            alpha = 0f
        }
        row.addView(View(context).apply {
            background = rounded(if (s.isError) p.error else p.brand, context.dp(2).toFloat())
        }, LinearLayout.LayoutParams(context.dp(4), LinearLayout.LayoutParams.MATCH_PARENT))
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
        }
        body.addView(TextView(context).apply {
            text = if (s.isError) "Error" else s.label
            setTextColor(if (s.isError) p.error else p.brand)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        body.addView(TextView(context).apply {
            text = s.text
            setTextColor(p.text)
            textSize = 14.5f
            setLineSpacing(0f, 1.15f)
            setPadding(0, context.dp(4), 0, 0)
            setTextIsSelectable(false)
        })
        row.addView(body, LinearLayout.LayoutParams(0, -2, 1f))
        if (!s.isError) {
            row.isClickable = true
            row.contentDescription = "${s.label}: ${s.text}. Tap to insert."
            row.setOnClickListener { onSuggestionTapped(s) }
        }
        row.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(10) }
        row.translationY = context.dp(8).toFloat()
        row.animate().alpha(1f).translationY(0f).setDuration(300).setStartDelay(50L * index).start()
        return row
    }

    // ---------------- animation & gestures ----------------
    fun animateIn() {
        card.post {
            card.translationY = card.height.toFloat().coerceAtLeast(1f)
            backdrop.alpha = 0f
            card.animate().translationY(0f).setDuration(200).start()
            backdrop.animate().alpha(1f).setDuration(200).start()
        }
    }

    fun animateOut(end: () -> Unit) {
        if (animating) return
        animating = true
        hideKeyboard()
        card.animate().translationY(card.height.toFloat()).setDuration(200).withEndAction(end).start()
        backdrop.animate().alpha(0f).setDuration(200).start()
    }

    private fun attachDrag(v: View) {
        var startY = 0f
        var dragging = false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startY = e.rawY; dragging = false; false }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - startY
                    if (dy > context.dp(4)) dragging = true
                    if (dragging) card.translationY = max(0f, dy)
                    dragging
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        if (card.translationY > context.dp(60)) onDismiss()
                        else card.animate().translationY(0f).setDuration(150).start()
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(promptInput.windowToken, 0)
        promptInput.clearFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onDismiss()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
