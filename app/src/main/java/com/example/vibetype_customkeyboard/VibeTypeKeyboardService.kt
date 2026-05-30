package com.example.vibetype_customkeyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout

class VibeTypeKeyboardService : InputMethodService() {

    private lateinit var relationshipRow: LinearLayout
    private lateinit var dimOverlay: View
    private lateinit var suggestionPanel: LinearLayout
    private lateinit var suggestionButtons: List<Button>
    private var composingState = HangulState()

    override fun onCreateInputView(): View {
        Log.d(TAG, "VibeType keyboard created")

        // Inflate the custom keyboard UI that Android shows inside text fields.
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        relationshipRow = view.findViewById(R.id.relationshipRow)
        dimOverlay = view.findViewById(R.id.dimOverlay)
        suggestionPanel = view.findViewById(R.id.suggestionPanel)
        suggestionButtons = listOf(
            view.findViewById(R.id.suggestionAButton),
            view.findViewById(R.id.suggestionBButton),
            view.findViewById(R.id.suggestionCButton)
        )

        // VibeTyping is the entry point for the relationship-based rewrite flow.
        view.findViewById<Button>(R.id.vibeTypingButton).setOnClickListener {
            Log.d(TAG, "VibeTyping button clicked")
            relationshipRow.visibility = View.VISIBLE
        }

        wireRelationshipButton(view, R.id.friendButton, "friend")
        wireRelationshipButton(view, R.id.teammateButton, "teammate")
        wireRelationshipButton(view, R.id.businessButton, "business")
        wireRelationshipButton(view, R.id.professorButton, "professor")
        wireHangulKeys(view)

        // Basic keyboard controls operate on the currently focused text input.
        view.findViewById<Button>(R.id.spaceButton).setOnClickListener {
            Log.d(TAG, "Space button clicked")
            finishHangulComposition()
            commitText(" ")
        }

        view.findViewById<Button>(R.id.deleteButton).setOnClickListener {
            Log.d(TAG, "Delete button clicked")
            if (!deleteFromHangulComposition()) {
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
        }

        view.findViewById<Button>(R.id.enterButton).setOnClickListener {
            Log.d(TAG, "Enter button clicked")
            handleEnter()
        }

        return view
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composingState = HangulState()
    }

    override fun onFinishInput() {
        finishHangulComposition()
        super.onFinishInput()
    }

    private fun wireRelationshipButton(view: View, buttonId: Int, relationship: String) {
        view.findViewById<Button>(buttonId).setOnClickListener {
            Log.d(TAG, "Relationship selected: $relationship")
            showSuggestionPanel(relationship)
        }
    }

    private fun wireHangulKeys(view: View) {
        findButtons(view).forEach { button ->
            val jamo = button.tag?.toString()
            if (jamo != null && jamo.length == 1 && jamo[0] in HANGUL_JAMO_RANGE) {
                button.setOnClickListener {
                    Log.d(TAG, "Hangul key clicked: $jamo")
                    handleHangulJamo(jamo[0])
                }
            }
        }
    }

    private fun findButtons(view: View): List<Button> {
        if (view is Button) return listOf(view)
        if (view !is ViewGroup) return emptyList()

        return (0 until view.childCount).flatMap { index ->
            findButtons(view.getChildAt(index))
        }
    }

    private fun showSuggestionPanel(relationship: String) {
        val currentText = currentInputConnection
            ?.getTextBeforeCursor(MAX_REPLACE_CHARS, 0)
            ?.toString()
            .orEmpty()

        Log.d(TAG, "Input before cursor: $currentText")

        getMockSuggestions(currentText, relationship).forEachIndexed { index, suggestion ->
            suggestionButtons[index].text = "${SUGGESTION_LABELS[index]}. $suggestion"
            suggestionButtons[index].setOnClickListener {
                Log.d(TAG, "Suggestion ${SUGGESTION_LABELS[index]} clicked: $suggestion")
                replaceCurrentText(suggestion)
                hideSuggestionPanel()
            }
        }

        dimOverlay.visibility = View.VISIBLE
        suggestionPanel.visibility = View.VISIBLE
        dimOverlay.animate().alpha(1f).setDuration(120).start()
        suggestionPanel.animate().alpha(1f).translationY(0f).setDuration(160).start()
    }

    // Mock relationship-aware suggestions for hackathon testing before real API integration.
    private fun getMockSuggestions(input: String, relationship: String): List<String> {
        val fallbackTopic = if (input.isBlank()) "that" else input.trim()

        return when (relationship) {
            "friend" -> listOf(
                "Sounds good, let's do it!",
                "Yeah, I like that idea.",
                "Totally. ${fallbackTopic.replaceFirstChar { it.uppercase() }} works for me."
            )
            "teammate" -> listOf(
                "Looks good. I'll follow up on this.",
                "I agree with this direction.",
                "Let's move forward and sync on the next step."
            )
            "business" -> listOf(
                "Thank you for the update. This sounds good to me.",
                "I appreciate the context and will review it shortly.",
                "That approach works well from my side."
            )
            "professor" -> listOf(
                "Thank you, Professor. I appreciate your guidance.",
                "I understand. I will review this carefully and follow up.",
                "Thank you for your feedback. I will revise it accordingly."
            )
            else -> listOf(
                "That sounds good to me.",
                "I agree with this.",
                "Thank you for the update."
            )
        }
    }

    private fun hideSuggestionPanel() {
        dimOverlay.animate().alpha(0f).setDuration(120).withEndAction {
            dimOverlay.visibility = View.GONE
        }.start()

        suggestionPanel.animate()
            .alpha(0f)
            .translationY(-24f)
            .setDuration(160)
            .withEndAction {
                suggestionPanel.visibility = View.GONE
                suggestionPanel.translationY = 24f
                relationshipRow.visibility = View.GONE
            }
            .start()
    }

    private fun replaceCurrentText(text: String) {
        Log.d(TAG, "Replacing current text with: $text")
        composingState = HangulState()

        val inputConnection = currentInputConnection ?: return
        val beforeLength = inputConnection.getTextBeforeCursor(MAX_REPLACE_CHARS, 0)?.length ?: 0
        val afterLength = inputConnection.getTextAfterCursor(MAX_REPLACE_CHARS, 0)?.length ?: 0

        inputConnection.beginBatchEdit()
        inputConnection.deleteSurroundingText(beforeLength, afterLength)
        inputConnection.commitText(text, 1)
        inputConnection.endBatchEdit()
    }

    private fun commitText(text: String) {
        Log.d(TAG, "Committing text: $text")
        currentInputConnection?.commitText(text, 1)
    }

    private fun handleHangulJamo(jamo: Char) {
        if (jamo in CONSONANT_TO_CHO) {
            handleConsonant(jamo)
        } else if (jamo in VOWEL_TO_JUNG) {
            handleVowel(jamo)
        } else {
            finishHangulComposition()
            commitText(jamo.toString())
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
                finishHangulComposition()
                composingState.cho = choIndex
                composingState.raw = jamo
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
                    val previousSyllable = composeHangul(
                        composingState.cho,
                        composingState.jung,
                        splitJong.first
                    )
                    currentInputConnection?.commitText(previousSyllable.toString(), 1)
                    composingState = HangulState(
                        cho = JONG_TO_CHO.getValue(splitJong.second),
                        jung = jungIndex
                    )
                } else {
                    val movingCho = JONG_TO_CHO.getValue(composingState.jong!!)
                    val previousSyllable = composeHangul(composingState.cho, composingState.jung, null)
                    currentInputConnection?.commitText(previousSyllable.toString(), 1)
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
        if (text.isEmpty()) return

        Log.d(TAG, "Composing Hangul: $text")
        currentInputConnection?.setComposingText(text, 1)
    }

    private fun finishHangulComposition() {
        if (composingState.isEmpty()) return

        Log.d(TAG, "Finishing Hangul composition: ${composingState.toText()}")
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
            composingState.cho != null -> composingState.cho = null
            else -> composingState.raw = null
        }

        if (composingState.isEmpty()) {
            currentInputConnection?.commitText("", 1)
            currentInputConnection?.finishComposingText()
        } else {
            updateHangulComposition()
        }

        return true
    }

    private fun handleEnter() {
        finishHangulComposition()

        val editorInfo = currentInputEditorInfo
        val inputType = editorInfo.inputType
        val isMultiLine = inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION

        when {
            isMultiLine -> commitText("\n")
            action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED -> {
                Log.d(TAG, "Performing editor action: $action")
                currentInputConnection?.performEditorAction(action)
            }
            else -> {
                val inputConnection = currentInputConnection ?: return
                inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
        }
    }

    private fun composeHangul(cho: Int?, jung: Int?, jong: Int?): Char {
        return if (cho != null && jung != null) {
            (HANGUL_BASE + (cho * JUNG_COUNT + jung) * JONG_COUNT + (jong ?: 0)).toChar()
        } else {
            jung?.let { JUNG_COMPAT[it] } ?: cho?.let { CHO_COMPAT[it] } ?: '\u0000'
        }
    }

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

    companion object {
        private const val TAG = "VibeTypeKeyboard"
        private const val MAX_REPLACE_CHARS = 500
        private const val HANGUL_BASE = 0xAC00
        private const val JUNG_COUNT = 21
        private const val JONG_COUNT = 28
        private val HANGUL_JAMO_RANGE = 'ㄱ'..'ㅣ'
        private val SUGGESTION_LABELS = listOf("A", "B", "C")
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
