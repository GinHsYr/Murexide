package com.juhao.murexide.ui.chat.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ReplacementSpan
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.text.InputType
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.graphics.scale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import com.juhao.murexide.data.DefaultEmoji
import com.juhao.murexide.data.DefaultEmojiParser
import com.juhao.murexide.data.MentionToken
import com.juhao.murexide.utils.MentionUtils
import kotlin.math.roundToInt

private object DefaultEmojiBitmapCache : LruCache<String, Bitmap>(4 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int {
        return (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun load(context: Context, emoji: DefaultEmoji, targetHeight: Int): Bitmap? {
        val key = "${emoji.assetPath}@$targetHeight"
        get(key)?.let { return it }

        val source = runCatching {
            context.assets.open(emoji.assetPath).use(BitmapFactory::decodeStream)
        }.getOrNull() ?: return null
        val width = (
            source.width.toFloat() / source.height.coerceAtLeast(1) * targetHeight
            ).roundToInt().coerceAtLeast(1)
        val scaled = if (source.width == width && source.height == targetHeight) {
            source
        } else {
            source.scale(width, targetHeight, true).also {
                source.recycle()
            }
        }
        put(key, scaled)
        return scaled
    }
}

private class DefaultEmojiSpan(
    val emojiName: String,
    private val bitmap: Bitmap
) : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int = bitmap.width

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val drawTop = top + (bottom - top - bitmap.height) / 2f
        canvas.drawBitmap(bitmap, x, drawTop, paint)
    }
}

private class DefaultEmojiEditText(context: Context) : AppCompatEditText(context) {
    private var ready = false
    private var internalChange = false
    private var textChangeInProgress = false
    private var previousValue = TextFieldValue("")
    private var currentMentions: List<MentionToken> = emptyList()
    private var currentEmojis: List<DefaultEmoji> = emptyList()
    private var focused: () -> Unit = {}
    private var valueChanged: (
        value: TextFieldValue,
        mentions: List<MentionToken>,
        insertedText: String,
        insertPosition: Int
    ) -> Unit = { _, _, _, _ -> }

    init {
        background = null
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
        setMinLines(1)
        maxLines = 5
        isSingleLine = false
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        showSoftInputOnFocus = true
        isVerticalScrollBarEnabled = false
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_ACTION_NONE

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                text: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
                if (internalChange) return
                textChangeInProgress = true
                previousValue = TextFieldValue(
                    text = text?.toString().orEmpty(),
                    selection = currentSelection(text?.length ?: 0)
                )
            }

            override fun onTextChanged(
                text: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) = Unit

            override fun afterTextChanged(editable: Editable?) {
                if (internalChange || editable == null) return

                val rawValue = TextFieldValue(
                    text = editable.toString(),
                    selection = currentSelection(editable.length)
                )
                val protectedRanges = DefaultEmojiParser.findMatches(
                    text = previousValue.text,
                    emojis = currentEmojis
                ).map { TextRange(it.start, it.endExclusive) }
                val result = MentionUtils.processEdit(
                    old = previousValue,
                    new = rawValue,
                    mentions = currentMentions,
                    protectedRanges = protectedRanges
                )

                internalChange = true
                if (editable.toString() != result.value.text) {
                    editable.replace(0, editable.length, result.value.text)
                }
                applyEmojiSpans(editable)
                setSelectionSafely(result.value.selection, editable.length)
                internalChange = false
                textChangeInProgress = false

                currentMentions = result.mentions
                previousValue = result.value
                valueChanged(
                    result.value,
                    result.mentions,
                    result.insertedText,
                    result.insertPos
                )
            }
        })

        ready = true
    }

    override fun onFocusChanged(
        focused: Boolean,
        direction: Int,
        previouslyFocusedRect: android.graphics.Rect?
    ) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (ready && focused) this.focused()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) requestEditorFocus()
        return handled
    }

    fun requestEditorFocus() {
        post {
            if (!hasFocus()) requestFocus()
            if (hasFocus()) {
                val inputMethodManager = context.getSystemService(InputMethodManager::class.java)
                inputMethodManager?.showSoftInput(this, 0)
            }
        }
    }

    fun bind(
        value: TextFieldValue,
        mentions: List<MentionToken>,
        emojis: List<DefaultEmoji>,
        enabled: Boolean,
        textColor: Color,
        hintColor: Color,
        textSizeSp: Float,
        onValueChanged: (
            value: TextFieldValue,
            mentions: List<MentionToken>,
            insertedText: String,
            insertPosition: Int
        ) -> Unit,
        onFocused: () -> Unit
    ) {
        currentMentions = mentions
        currentEmojis = emojis
        valueChanged = onValueChanged
        focused = onFocused
        isEnabled = enabled
        setTextColor(textColor.toArgb())
        setHintTextColor(hintColor.toArgb())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)

        val currentText = text?.toString().orEmpty()
        internalChange = true
        if (currentText != value.text) {
            setText(value.text)
        }
        applyEmojiSpans(editableText)
        setSelectionSafely(value.selection, value.text.length)
        internalChange = false
        textChangeInProgress = false
        previousValue = value
    }

    override fun onSelectionChanged(selectionStart: Int, selectionEnd: Int) {
        super.onSelectionChanged(selectionStart, selectionEnd)
        if (
            !ready || internalChange || textChangeInProgress ||
            selectionStart < 0 || selectionEnd < 0
        ) return

        val currentText = text?.toString().orEmpty()
        val protectedRanges = DefaultEmojiParser.findMatches(
            text = currentText,
            emojis = currentEmojis
        ).map { TextRange(it.start, it.endExclusive) }
        val result = MentionUtils.processEdit(
            old = previousValue.copy(text = currentText),
            new = TextFieldValue(currentText, TextRange(selectionStart, selectionEnd)),
            mentions = currentMentions,
            protectedRanges = protectedRanges
        )

        if (result.value.selection.start != selectionStart || result.value.selection.end != selectionEnd) {
            internalChange = true
            setSelectionSafely(result.value.selection, currentText.length)
            internalChange = false
        }
        previousValue = result.value
        valueChanged(result.value, result.mentions, "", -1)
    }

    private fun applyEmojiSpans(editable: Editable?) {
        if (editable == null) return
        editable.getSpans(0, editable.length, DefaultEmojiSpan::class.java)
            .forEach(editable::removeSpan)

        val emojiHeight = (paint.textSize * 1.2f).roundToInt().coerceAtLeast(1)
        DefaultEmojiParser.findMatches(editable.toString(), currentEmojis).forEach { match ->
            val bitmap = DefaultEmojiBitmapCache.load(context, match.emoji, emojiHeight)
                ?: return@forEach
            editable.setSpan(
                DefaultEmojiSpan(match.emoji.name, bitmap),
                match.start,
                match.endExclusive,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun currentSelection(textLength: Int): TextRange {
        val safeStart = selectionStart.takeIf { it >= 0 }?.coerceIn(0, textLength) ?: textLength
        val safeEnd = selectionEnd.takeIf { it >= 0 }?.coerceIn(0, textLength) ?: safeStart
        return TextRange(safeStart, safeEnd)
    }

    private fun setSelectionSafely(selection: TextRange, textLength: Int) {
        val start = selection.start.coerceIn(0, textLength)
        val end = selection.end.coerceIn(0, textLength)
        if (this.selectionStart != start || this.selectionEnd != end) {
            setSelection(start, end)
        }
    }
}

@Composable
internal fun DefaultEmojiTextField(
    value: TextFieldValue,
    mentions: List<MentionToken>,
    emojis: List<DefaultEmoji>,
    enabled: Boolean,
    textColor: Color,
    hintColor: Color,
    textSizeSp: Float,
    focusRequester: FocusRequester,
    onValueChange: (
        value: TextFieldValue,
        mentions: List<MentionToken>,
        insertedText: String,
        insertPosition: Int
    ) -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editorHolder = remember { arrayOfNulls<DefaultEmojiEditText>(1) }
    AndroidView(
        factory = { context ->
            DefaultEmojiEditText(context).apply {
                hint = "输入消息..."
                editorHolder[0] = this
            }
        },
        update = { editor ->
            editor.bind(
                value = value,
                mentions = mentions,
                emojis = emojis,
                enabled = enabled,
                textColor = textColor,
                hintColor = hintColor,
                textSizeSp = textSizeSp,
                onValueChanged = onValueChange,
                onFocused = onFocused
            )
        },
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.hasFocus) editorHolder[0]?.requestEditorFocus()
            }
    )
}
