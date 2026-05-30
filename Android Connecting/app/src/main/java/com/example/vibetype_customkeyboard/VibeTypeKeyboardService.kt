package com.example.vibetype_customkeyboard

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

class VibeTypeKeyboardService : InputMethodService() {

    private lateinit var rootView: FrameLayout
    private lateinit var keyboardLayout: LinearLayout
    private lateinit var popupOverlay: LinearLayout
    private lateinit var popupTitle: TextView
    private lateinit var popupResultButtons: List<Button>
    private lateinit var toneButtons: Map<Tone, Button>
    private lateinit var modeButton: Button
    private lateinit var shiftButton: Button
    private lateinit var keyButtons: MutableList<Button>

    private val mainHandler = Handler(Looper.getMainLooper())
    private var backspaceRepeatRunnable: Runnable? = null
    private var suggestionRequestId = 0

    private var selectedTone: Tone? = null
    private var isKoreanMode = false
    private var isShifted = false
    private var composingState = HangulState()

    override fun onCreateInputView(): View {
        rootView = FrameLayout(this).apply {
            setBackgroundColor(COLOR_KEYBOARD_BG)
        }

        keyboardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(6.dp(), 6.dp(), 6.dp(), 26.dp())
            setBackgroundColor(COLOR_KEYBOARD_BG)
        }
        rootView.addView(keyboardLayout, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))

        buildToneStrip()
        buildKeyboardRows()
        buildPopupOverlay()
        return rootView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composingState = HangulState()
        hideSuggestionPopup()
    }

    override fun onFinishInput() {
        finishHangulComposition()
        stopBackspaceRepeat()
        suggestionRequestId++
        hideSuggestionPopup()
        super.onFinishInput()
    }

    private fun buildToneStrip() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 5.dp())
        }

        toneButtons = Tone.entries.associateWith { tone ->
            makeButton(
                label = tone.label,
                weight = 1f,
                heightDp = 34,
                role = KeyRole.TONE
            ).also { button ->
                button.setOnClickListener {
                    tapFeedback()
                    selectedTone = tone
                    updateToneSelection()
                    requestSuggestionsForTone(tone)
                }
                row.addView(button)
            }
        }

        keyboardLayout.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        updateToneSelection()
    }

    private fun buildPopupOverlay() {
        popupOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xD9151722.toInt(), 0xE2111320.toInt())
            )
        }

        popupTitle = TextView(this).apply {
            text = "Choose a VibeType result"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, 8.dp())
        }
        popupOverlay.addView(popupTitle, LinearLayout.LayoutParams(MATCH, WRAP))

        popupResultButtons = (0..2).map { index ->
            Button(this).apply {
                text = "Option ${index + 1}"
                setAllCaps(false)
                gravity = Gravity.CENTER_VERTICAL
                minHeight = 0
                minWidth = 0
                includeFontPadding = false
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                background = gradientPopupBackground()
                setPadding(14.dp(), 0, 14.dp(), 0)
                layoutParams = LinearLayout.LayoutParams(MATCH, 58.dp()).apply {
                    setMargins(0, 4.dp(), 0, 4.dp())
                }
            }.also(popupOverlay::addView)
        }

        popupOverlay.addView(
            Button(this).apply {
                text = "Close"
                setAllCaps(false)
                textSize = 12f
                setTextColor(COLOR_TEXT)
                background = keyBackground(KeyRole.SUGGESTION, false)
                setOnClickListener {
                    tapFeedback()
                    hideSuggestionPopup()
                }
                layoutParams = LinearLayout.LayoutParams(MATCH, 40.dp()).apply {
                    setMargins(0, 8.dp(), 0, 0)
                }
            }
        )

        rootView.addView(popupOverlay, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun buildKeyboardRows() {
        keyButtons = mutableListOf()
        addCharacterRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"))
        addCharacterRow(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"))
        addCharacterRow(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"), startSpacerWeight = 0.45f, endSpacerWeight = 0.45f)

        val thirdRow = newRow()
        shiftButton = makeButton("⇧", 1.35f, role = KeyRole.SPECIAL).also {
            it.setOnClickListener { handleShift() }
            thirdRow.addView(it)
        }
        listOf("z", "x", "c", "v", "b", "n", "m").forEach { key ->
            thirdRow.addView(characterButton(key))
        }
        thirdRow.addView(makeButton("⌫", 1.35f, role = KeyRole.SPECIAL).also {
            it.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        handleBackspace()
                        startBackspaceRepeat()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        stopBackspaceRepeat()
                        true
                    }
                    else -> true
                }
            }
        })
        keyboardLayout.addView(thirdRow, LinearLayout.LayoutParams(MATCH, WRAP))

        val bottomRow = newRow()
        modeButton = makeButton("한", 1.3f, role = KeyRole.LANGUAGE).also {
            it.setOnClickListener { toggleLanguageMode() }
            bottomRow.addView(it)
        }
        bottomRow.addView(makeButton(",", 0.8f).also { it.setOnClickListener { commitLiteral(",") } })
        bottomRow.addView(makeButton("space", 4.2f, role = KeyRole.SPACE).also { it.setOnClickListener { handleSpace() } })
        bottomRow.addView(makeButton(".", 0.8f).also { it.setOnClickListener { commitLiteral(".") } })
        bottomRow.addView(makeButton("↵", 1.3f, role = KeyRole.ENTER).also {
            it.setOnClickListener { handleEnter() }
        })
        keyboardLayout.addView(bottomRow, LinearLayout.LayoutParams(MATCH, WRAP))

        updateKeyLabels()
    }

    private fun addCharacterRow(keys: List<String>, startSpacerWeight: Float = 0f, endSpacerWeight: Float = 0f) {
        val row = newRow()
        if (startSpacerWeight > 0f) row.addView(spacer(startSpacerWeight))
        keys.forEach { row.addView(characterButton(it)) }
        if (endSpacerWeight > 0f) row.addView(spacer(endSpacerWeight))
        keyboardLayout.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun newRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 2.dp(), 0, 2.dp())
        }
    }

    private fun spacer(weight: Float): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, KEY_HEIGHT_DP.dp(), weight)
        }
    }

    private fun characterButton(key: String): Button {
        return makeButton(key, 1f).also { button ->
            button.tag = key
            button.setOnClickListener { handleCharacterKey(key) }
            keyButtons.add(button)
        }
    }

    private fun makeButton(
        label: String,
        weight: Float,
        heightDp: Int = KEY_HEIGHT_DP,
        role: KeyRole = KeyRole.NORMAL
    ): Button {
        return Button(this).apply {
            text = label
            setAllCaps(false)
            gravity = Gravity.CENTER
            minHeight = 0
            minWidth = 0
            includeFontPadding = false
            textSize = when (role) {
                KeyRole.SUGGESTION -> 12f
                KeyRole.TONE -> 11f
                KeyRole.SPACE -> 13f
                KeyRole.ENTER -> 27f
                KeyRole.LANGUAGE -> 19f
                else -> 20f
            }
            typeface = Typeface.create(Typeface.DEFAULT, if (role == KeyRole.NORMAL) Typeface.NORMAL else Typeface.BOLD)
            setTextColor(if (role == KeyRole.TONE || role == KeyRole.SUGGESTION) COLOR_TEXT_SOFT else COLOR_TEXT)
            background = keyBackground(role, selected = false)
            if (role == KeyRole.ENTER) {
                setPadding(2.dp(), 0, 2.dp(), 5.dp())
            } else {
                setPadding(2.dp(), 0, 2.dp(), 0)
            }
            isSoundEffectsEnabled = true
            layoutParams = LinearLayout.LayoutParams(0, heightDp.dp(), weight).apply {
                setMargins(3.dp(), 2.dp(), 3.dp(), 2.dp())
            }
        }
    }

    private fun keyBackground(role: KeyRole, selected: Boolean): GradientDrawable {
        val color = when {
            selected -> COLOR_ACCENT
            role == KeyRole.SPECIAL || role == KeyRole.LANGUAGE -> COLOR_SPECIAL_KEY
            role == KeyRole.SUGGESTION -> COLOR_SUGGESTION_KEY
            role == KeyRole.TONE -> COLOR_TOOL_KEY
            else -> COLOR_KEY
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8.dp().toFloat()
            setColor(color)
        }
    }

    private fun handleCharacterKey(rawKey: String) {
        tapFeedback()
        val ic = currentInputConnection ?: return

        if (rawKey.length == 1 && rawKey[0].isDigit()) {
            finishHangulComposition()
            ic.commitText(rawKey, 1)
            return
        }

        if (isKoreanMode) {
            val jamo = englishKeyToHangul(rawKey, isShifted)
            if (jamo != null) {
                handleHangulJamo(jamo)
            } else {
                finishHangulComposition()
                ic.commitText(rawKey, 1)
            }
        } else {
            finishHangulComposition()
            val text = if (isShifted) rawKey.uppercase(Locale.US) else rawKey
            ic.commitText(text, 1)
        }

        if (isShifted) {
            isShifted = false
            updateKeyLabels()
        }
    }

    private fun handleShift() {
        tapFeedback()
        isShifted = !isShifted
        updateKeyLabels()
    }

    private fun toggleLanguageMode() {
        tapFeedback()
        finishHangulComposition()
        isKoreanMode = !isKoreanMode
        isShifted = false
        updateKeyLabels()
    }

    private fun handleBackspace() {
        tapFeedback()
        if (!deleteFromHangulComposition()) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    private fun startBackspaceRepeat() {
        stopBackspaceRepeat()
        backspaceRepeatRunnable = object : Runnable {
            override fun run() {
                handleBackspace()
                mainHandler.postDelayed(this, BACKSPACE_REPEAT_INTERVAL_MS)
            }
        }.also {
            mainHandler.postDelayed(it, BACKSPACE_INITIAL_DELAY_MS)
        }
    }

    private fun stopBackspaceRepeat() {
        backspaceRepeatRunnable?.let(mainHandler::removeCallbacks)
        backspaceRepeatRunnable = null
    }

    private fun handleSpace() {
        tapFeedback()
        finishHangulComposition()
        currentInputConnection?.commitText(" ", 1)
    }

    private fun commitLiteral(text: String) {
        tapFeedback()
        finishHangulComposition()
        currentInputConnection?.commitText(text, 1)
    }

    private fun handleEnter() {
        tapFeedback()
        finishHangulComposition()

        val editorInfo = currentInputEditorInfo ?: return
        val inputType = editorInfo.inputType
        val isMultiLine = inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION

        when {
            isMultiLine -> currentInputConnection?.commitText("\n", 1)
            action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED ->
                currentInputConnection?.performEditorAction(action)
            else -> {
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
        }
    }

    private fun updateKeyLabels() {
        keyButtons.forEach { button ->
            val raw = button.tag?.toString().orEmpty()
            button.text = if (raw.length == 1 && raw[0].isLetter()) {
                if (isKoreanMode) englishKeyToHangul(raw, isShifted)?.toString() ?: raw
                else if (isShifted) raw.uppercase(Locale.US) else raw
            } else {
                raw
            }
        }
        modeButton.text = if (isKoreanMode) "A" else "한"
        modeButton.textSize = 19f
        shiftButton.background = keyBackground(KeyRole.SPECIAL, selected = isShifted)
        shiftButton.setTextColor(if (isShifted) Color.WHITE else COLOR_TEXT)
    }

    private fun updateToneSelection() {
        toneButtons.forEach { (tone, button) ->
            val selected = tone == selectedTone
            button.background = keyBackground(KeyRole.TONE, selected)
            button.setTextColor(if (selected) Color.WHITE else COLOR_TEXT_SOFT)
        }
    }

    private fun requestSuggestionsForTone(tone: Tone) {
        finishHangulComposition()
        val input = getCurrentSentence().trim()

        if (input.isBlank()) {
            showPopupMessage("문장을 먼저 입력해 주세요.")
            return
        }

        val requestId = ++suggestionRequestId
        showLoadingPopup(tone)

        Thread {
            val result = runCatching {
                requestGeminiSuggestions(input, tone)
            }

            mainHandler.post {
                if (requestId == suggestionRequestId) {
                    result
                        .onSuccess { showSuggestions(it) }
                        .onFailure { error ->
                            Log.w(TAG, "Gemini request failed", error)
                            showGeminiError(error)
                        }
                }
            }
        }.start()
    }

    private fun requestGeminiSuggestions(input: String, tone: Tone): List<String> {
        val connection = (URL(buildGeminiUrl()).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6000
            readTimeout = 9000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        val prompt = """
            You are VibeType, an AI assistant that rewrites the user's exact message into natural English.

            User input: "$input"
            Target tone: ${tone.prompt}

            Generate exactly 3 natural English versions of the user's input.
            Rules:
            - Preserve the user's original meaning, topic, request, names, dates, and details.
            - If the input is Korean, translate the same meaning into natural English.
            - If the input is awkward English, rewrite it naturally.
            - Do not answer the message, add new facts, or switch to a generic example.
            - Keep each option ready to send in a chat or email.

            Respond ONLY with a JSON object:
            {"results":["expression 1","expression 2","expression 3"]}
        """.trimIndent()

        val payload = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            .put("generationConfig", JSONObject().put("temperature", 0.45).put("maxOutputTokens", 1024))
            .toString()

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload) }

        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = BufferedReader(stream.reader(Charsets.UTF_8)).use { it.readText() }

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Gemini HTTP ${connection.responseCode}: $body")
        }

        val rawText = JSONObject(body)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .replace("```json", "")
            .replace("```", "")
            .trim()

        val jsonText = rawText.substringAfter("{", rawText).substringBeforeLast("}", rawText)
        val results = JSONObject("{$jsonText}").getJSONArray("results")
        val parsedResults = (0 until minOf(3, results.length()))
            .map { results.getString(it).trim() }
            .filter { it.isNotBlank() }

        if (parsedResults.isEmpty()) {
            throw IllegalStateException("Gemini returned no usable suggestions.")
        }

        return parsedResults
    }

    private fun showSuggestions(results: List<String>) {
        popupTitle.text = "${selectedTone?.label ?: "VibeType"} results"
        popupOverlay.visibility = View.VISIBLE
        popupResultButtons.forEachIndexed { index, button ->
            val suggestion = results.getOrNull(index)
            if (suggestion == null) {
                button.visibility = View.GONE
            } else {
                button.visibility = View.VISIBLE
                button.text = suggestion
                button.isEnabled = true
                button.alpha = 1f
                button.setTextColor(Color.WHITE)
                button.background = gradientPopupBackground()
                button.setOnClickListener {
                    tapFeedback()
                    replaceCurrentText(suggestion)
                    hideSuggestionPopup()
                }
            }
        }
    }

    private fun showGeminiError(error: Throwable) {
        popupTitle.text = "Gemini 결과를 가져오지 못했어요"
        popupOverlay.visibility = View.VISIBLE
        popupResultButtons.forEachIndexed { index, button ->
            button.visibility = if (index == 0) View.VISIBLE else View.GONE
            button.text = if (index == 0) {
                "API 연결 또는 응답 형식 문제입니다. Logcat에서 $TAG 를 확인해 주세요."
            } else {
                ""
            }
            button.isEnabled = false
            button.alpha = 0.9f
            button.setTextColor(Color.WHITE)
            button.background = gradientPopupBackground()
            button.setOnClickListener(null)
        }
        Log.w(TAG, "Suggestion popup error: ${error.message}", error)
    }

    private fun showLoadingPopup(tone: Tone) {
        popupTitle.text = "Generating ${tone.label} tone..."
        popupOverlay.visibility = View.VISIBLE
        val labels = listOf("Asking Gemini", "Matching tone", "Preparing options")
        popupResultButtons.forEachIndexed { index, button ->
            button.visibility = View.VISIBLE
            button.text = labels[index]
            button.isEnabled = false
            button.alpha = 0.78f
            button.background = gradientPopupBackground()
            button.setTextColor(Color.WHITE)
            button.setOnClickListener(null)
        }
    }

    private fun showPopupMessage(message: String) {
        popupTitle.text = message
        popupOverlay.visibility = View.VISIBLE
        popupResultButtons.forEachIndexed { index, button ->
            button.text = if (index == 0) "입력창에 문장을 쓴 뒤 톤을 선택하세요." else ""
            button.visibility = if (index == 0) View.VISIBLE else View.GONE
            button.isEnabled = false
            button.alpha = 0.9f
            button.background = gradientPopupBackground()
            button.setOnClickListener(null)
        }
    }

    private fun hideSuggestionPopup() {
        if (::popupOverlay.isInitialized) {
            popupOverlay.visibility = View.GONE
            popupResultButtons.forEach { button ->
                button.visibility = View.VISIBLE
                button.isEnabled = true
                button.alpha = 1f
            }
        }
    }

    private fun gradientPopupBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(COLOR_POPUP_PURPLE, COLOR_POPUP_BLUE)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14.dp().toFloat()
        }
    }

    private fun replaceCurrentText(text: String) {
        composingState = HangulState()
        val ic = currentInputConnection ?: return
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        val before = extracted?.selectionStart?.coerceAtLeast(0)
            ?: ic.getTextBeforeCursor(MAX_REPLACE_CHARS, 0)?.length
            ?: 0
        val after = extracted?.let { (it.text.length - it.selectionEnd).coerceAtLeast(0) }
            ?: ic.getTextAfterCursor(MAX_REPLACE_CHARS, 0)?.length
            ?: 0

        ic.beginBatchEdit()
        ic.deleteSurroundingText(before, after)
        ic.commitText(text, 1)
        ic.endBatchEdit()
    }

    private fun getCurrentSentence(): String {
        val ic = currentInputConnection ?: return ""
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString()
        if (!extracted.isNullOrBlank()) return extracted

        val before = ic.getTextBeforeCursor(MAX_REPLACE_CHARS, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(MAX_REPLACE_CHARS, 0)?.toString().orEmpty()
        return before + after
    }

    private fun handleHangulJamo(jamo: Char) {
        if (jamo in CONSONANT_TO_CHO) {
            handleConsonant(jamo)
        } else if (jamo in VOWEL_TO_JUNG) {
            handleVowel(jamo)
        } else {
            finishHangulComposition()
            currentInputConnection?.commitText(jamo.toString(), 1)
        }
        updateHangulComposition()
    }

    private fun handleConsonant(jamo: Char) {
        val choIndex = CONSONANT_TO_CHO.getValue(jamo)
        val jongIndex = CONSONANT_TO_JONG[jamo]

        when {
            composingState.cho == null && composingState.jung == null -> {
                composingState.cho = choIndex
                composingState.raw = jamo
            }
            composingState.cho != null && composingState.jung == null -> {
                val doubleCho = DOUBLE_CHO[composingState.cho to choIndex]
                if (doubleCho != null) {
                    composingState.cho = doubleCho
                    composingState.raw = CHO_COMPAT[doubleCho]
                } else {
                    finishHangulComposition()
                    composingState.cho = choIndex
                    composingState.raw = jamo
                }
            }
            composingState.jung != null && composingState.jong == null && jongIndex != null -> {
                composingState.jong = jongIndex
                composingState.raw = null
            }
            composingState.jung != null && composingState.jong != null && jongIndex != null -> {
                val combinedJong = DOUBLE_JONG[composingState.jong to jongIndex]
                if (combinedJong != null) {
                    composingState.jong = combinedJong
                } else {
                    finishHangulComposition()
                    composingState.cho = choIndex
                    composingState.raw = jamo
                }
            }
            else -> {
                finishHangulComposition()
                composingState.cho = choIndex
                composingState.raw = jamo
            }
        }
    }

    private fun handleVowel(jamo: Char) {
        val jungIndex = VOWEL_TO_JUNG.getValue(jamo)

        when {
            composingState.cho == null && composingState.jung == null -> {
                composingState.jung = jungIndex
                composingState.raw = jamo
            }
            composingState.cho != null && composingState.jung == null -> {
                composingState.jung = jungIndex
                composingState.raw = null
            }
            composingState.jung != null && composingState.jong == null -> {
                val combinedJung = DOUBLE_JUNG[composingState.jung to jungIndex]
                if (combinedJung != null) {
                    composingState.jung = combinedJung
                    composingState.raw = null
                } else {
                    finishHangulComposition()
                    composingState.jung = jungIndex
                    composingState.raw = jamo
                }
            }
            composingState.cho != null && composingState.jung != null && composingState.jong != null -> {
                val splitJong = SPLIT_DOUBLE_JONG[composingState.jong]
                if (splitJong != null) {
                    currentInputConnection?.commitText(
                        composeHangul(composingState.cho, composingState.jung, splitJong.first).toString(),
                        1
                    )
                    composingState = HangulState(cho = JONG_TO_CHO.getValue(splitJong.second), jung = jungIndex)
                } else {
                    val movingCho = JONG_TO_CHO.getValue(composingState.jong!!)
                    currentInputConnection?.commitText(
                        composeHangul(composingState.cho, composingState.jung, null).toString(),
                        1
                    )
                    composingState = HangulState(cho = movingCho, jung = jungIndex)
                }
            }
            else -> {
                finishHangulComposition()
                composingState.jung = jungIndex
                composingState.raw = jamo
            }
        }
    }

    private fun updateHangulComposition() {
        val text = composingState.toText()
        if (text.isNotEmpty()) {
            currentInputConnection?.setComposingText(text, 1)
        }
    }

    private fun finishHangulComposition() {
        if (composingState.isEmpty()) return
        currentInputConnection?.finishComposingText()
        composingState = HangulState()
    }

    private fun deleteFromHangulComposition(): Boolean {
        if (composingState.isEmpty()) return false

        when {
            composingState.jong != null -> composingState.jong = null
            composingState.jung != null -> {
                val splitJung = SPLIT_DOUBLE_JUNG[composingState.jung]
                if (splitJung != null) {
                    composingState.jung = splitJung.first
                } else {
                    composingState.jung = null
                    composingState.raw = composingState.cho?.let { CHO_COMPAT[it] }
                }
            }
            composingState.cho != null -> {
                composingState.cho = null
                composingState.raw = null
            }
            else -> composingState.raw = null
        }

        if (composingState.isEmpty()) {
            currentInputConnection?.setComposingText("", 1)
            currentInputConnection?.finishComposingText()
        } else {
            updateHangulComposition()
        }
        return true
    }

    private fun composeHangul(cho: Int?, jung: Int?, jong: Int?): Char {
        return if (cho != null && jung != null) {
            (HANGUL_BASE + (cho * JUNG_COUNT + jung) * JONG_COUNT + (jong ?: 0)).toChar()
        } else {
            jung?.let { JUNG_COMPAT[it] } ?: cho?.let { CHO_COMPAT[it] } ?: '\u0000'
        }
    }

    private fun englishKeyToHangul(key: String, shifted: Boolean): Char? {
        return when (key.lowercase(Locale.US)) {
            "q" -> if (shifted) 'ㅃ' else 'ㅂ'
            "w" -> if (shifted) 'ㅉ' else 'ㅈ'
            "e" -> if (shifted) 'ㄸ' else 'ㄷ'
            "r" -> if (shifted) 'ㄲ' else 'ㄱ'
            "t" -> if (shifted) 'ㅆ' else 'ㅅ'
            "y" -> 'ㅛ'
            "u" -> 'ㅕ'
            "i" -> 'ㅑ'
            "o" -> if (shifted) 'ㅒ' else 'ㅐ'
            "p" -> if (shifted) 'ㅖ' else 'ㅔ'
            "a" -> 'ㅁ'
            "s" -> 'ㄴ'
            "d" -> 'ㅇ'
            "f" -> 'ㄹ'
            "g" -> 'ㅎ'
            "h" -> 'ㅗ'
            "j" -> 'ㅓ'
            "k" -> 'ㅏ'
            "l" -> 'ㅣ'
            "z" -> 'ㅋ'
            "x" -> 'ㅌ'
            "c" -> 'ㅊ'
            "v" -> 'ㅍ'
            "b" -> 'ㅠ'
            "n" -> 'ㅜ'
            "m" -> 'ㅡ'
            else -> null
        }
    }

    private fun buildGeminiUrl(): String {
        return "$GEMINI_API_BASE_URL/models/$GEMINI_MODEL:generateContent?key=$GEMINI_API_KEY"
    }

    private fun tapFeedback() {
        rootView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

    private data class HangulState(
        var cho: Int? = null,
        var jung: Int? = null,
        var jong: Int? = null,
        var raw: Char? = null
    ) {
        fun isEmpty(): Boolean = cho == null && jung == null && jong == null && raw == null

        fun toText(): String {
            raw?.let { return it.toString() }
            if (cho != null && jung != null) {
                return (HANGUL_BASE + (cho!! * JUNG_COUNT + jung!!) * JONG_COUNT + (jong ?: 0))
                    .toChar()
                    .toString()
            }
            return jung?.let { JUNG_COMPAT[it].toString() }
                ?: cho?.let { CHO_COMPAT[it].toString() }
                ?: ""
        }
    }

    private enum class KeyRole {
        NORMAL,
        SPECIAL,
        SPACE,
        ENTER,
        LANGUAGE,
        SUGGESTION,
        TONE
    }

    private enum class Tone(val label: String, val prompt: String) {
        FRIEND("Friend", "very casual and close-friend style, like texting a best friend. Use contractions and, when the context fits, use more Gen Z/MZ-style wording, abbreviations, or close-friend expressions such as tbh, ngl, lol, fr, low-key, kinda, wanna, gotta, no worries, you're good, or bro/bestie-style phrasing. Make it feel natural between close friends, but still preserve the user's exact meaning and do not force slang where it does not fit."),
        PROFESSOR("Professor", "polite and respectful, like emailing a professor or academic. Formal but not stiff."),
        TEAMMATE("Team", "collaborative and approachable, like messaging a project teammate. Friendly but professional."),
        BUSINESS("Business", "professional and formal, suitable for business communication with clients or seniors.")
    }

    companion object {
        private const val TAG = "VibeTypeKeyboard"
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        private const val KEY_HEIGHT_DP = 46
        private const val MAX_REPLACE_CHARS = 1000
        private const val BACKSPACE_INITIAL_DELAY_MS = 380L
        private const val BACKSPACE_REPEAT_INTERVAL_MS = 58L

        private const val GEMINI_API_KEY = "AQ.Ab8RN6KSoNwyrGyyedipTAh3Ch1ANsCytQKVLHkeKs4RQvj0eA"
        private const val GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val GEMINI_MODEL = "gemini-2.5-flash"

        private const val COLOR_KEYBOARD_BG = 0xFFE1E4EA.toInt()
        private const val COLOR_KEY = 0xFFF9FAFC.toInt()
        private const val COLOR_SPECIAL_KEY = 0xFFC9CED7.toInt()
        private const val COLOR_TOOL_KEY = 0xFFECEF3F6.toInt()
        private const val COLOR_SUGGESTION_KEY = 0xFFFFFFFF.toInt()
        private const val COLOR_ACCENT = 0xFF6F5DF6.toInt()
        private const val COLOR_POPUP_PURPLE = 0xFF7B5CF0.toInt()
        private const val COLOR_POPUP_BLUE = 0xFF247CFF.toInt()
        private const val COLOR_TEXT = 0xFF20232A.toInt()
        private const val COLOR_TEXT_SOFT = 0xFF646B78.toInt()

        private const val HANGUL_BASE = 0xAC00
        private const val JUNG_COUNT = 21
        private const val JONG_COUNT = 28

        private val CHO_COMPAT = listOf(
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
        )

        private val JUNG_COMPAT = listOf(
            'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ',
            'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ'
        )

        private val CONSONANT_TO_CHO = mapOf(
            'ㄱ' to 0, 'ㄲ' to 1, 'ㄴ' to 2, 'ㄷ' to 3, 'ㄸ' to 4,
            'ㄹ' to 5, 'ㅁ' to 6, 'ㅂ' to 7, 'ㅃ' to 8, 'ㅅ' to 9,
            'ㅆ' to 10, 'ㅇ' to 11, 'ㅈ' to 12, 'ㅉ' to 13, 'ㅊ' to 14,
            'ㅋ' to 15, 'ㅌ' to 16, 'ㅍ' to 17, 'ㅎ' to 18
        )

        private val CONSONANT_TO_JONG = mapOf(
            'ㄱ' to 1, 'ㄲ' to 2, 'ㄴ' to 4, 'ㄷ' to 7, 'ㄹ' to 8,
            'ㅁ' to 16, 'ㅂ' to 17, 'ㅅ' to 19, 'ㅆ' to 20, 'ㅇ' to 21,
            'ㅈ' to 22, 'ㅊ' to 23, 'ㅋ' to 24, 'ㅌ' to 25, 'ㅍ' to 26, 'ㅎ' to 27
        )

        private val JONG_TO_CHO = mapOf(
            1 to 0, 2 to 1, 4 to 2, 7 to 3, 8 to 5, 16 to 6, 17 to 7,
            19 to 9, 20 to 10, 21 to 11, 22 to 12, 23 to 14, 24 to 15,
            25 to 16, 26 to 17, 27 to 18
        )

        private val VOWEL_TO_JUNG = mapOf(
            'ㅏ' to 0, 'ㅐ' to 1, 'ㅑ' to 2, 'ㅒ' to 3, 'ㅓ' to 4,
            'ㅔ' to 5, 'ㅕ' to 6, 'ㅖ' to 7, 'ㅗ' to 8, 'ㅘ' to 9,
            'ㅙ' to 10, 'ㅚ' to 11, 'ㅛ' to 12, 'ㅜ' to 13, 'ㅝ' to 14,
            'ㅞ' to 15, 'ㅟ' to 16, 'ㅠ' to 17, 'ㅡ' to 18, 'ㅢ' to 19, 'ㅣ' to 20
        )

        private val DOUBLE_CHO = mapOf(
            (0 to 0) to 1,
            (3 to 3) to 4,
            (7 to 7) to 8,
            (9 to 9) to 10,
            (12 to 12) to 13
        )

        private val DOUBLE_JUNG = mapOf(
            (8 to 0) to 9, (8 to 1) to 10, (8 to 20) to 11,
            (13 to 4) to 14, (13 to 5) to 15, (13 to 20) to 16,
            (18 to 20) to 19
        )

        private val SPLIT_DOUBLE_JUNG = mapOf(
            9 to (8 to 0), 10 to (8 to 1), 11 to (8 to 20),
            14 to (13 to 4), 15 to (13 to 5), 16 to (13 to 20),
            19 to (18 to 20)
        )

        private val DOUBLE_JONG = mapOf(
            (1 to 19) to 3, (4 to 22) to 5, (4 to 27) to 6,
            (8 to 1) to 9, (8 to 16) to 10, (8 to 17) to 11,
            (8 to 19) to 12, (8 to 25) to 13, (8 to 26) to 14,
            (8 to 27) to 15, (17 to 19) to 18
        )

        private val SPLIT_DOUBLE_JONG = mapOf(
            3 to (1 to 19), 5 to (4 to 22), 6 to (4 to 27),
            9 to (8 to 1), 10 to (8 to 16), 11 to (8 to 17),
            12 to (8 to 19), 13 to (8 to 25), 14 to (8 to 26),
            15 to (8 to 27), 18 to (17 to 19)
        )
    }
}
