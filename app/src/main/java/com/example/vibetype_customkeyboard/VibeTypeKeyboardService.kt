package com.example.vibetype_customkeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

class VibeTypeKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard
    private var isKoreanMode = false
    private lateinit var relationshipRow: LinearLayout
    private lateinit var dimOverlay: View
    private lateinit var suggestionPanel: LinearLayout
    private lateinit var suggestionButtons: List<Button>

    override fun onCreateInputView(): View {
        Log.d(TAG, "VibeType keyboard created")

        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        
        // Initialize KeyboardView
        keyboardView = view.findViewById(R.id.keyboardView)
        keyboard = Keyboard(this, R.xml.keyboard)
        keyboardView.keyboard = keyboard
        keyboardView.setOnKeyboardActionListener(this)

        relationshipRow = view.findViewById(R.id.relationshipRow)
        dimOverlay = view.findViewById(R.id.dimOverlay)
        suggestionPanel = view.findViewById(R.id.suggestionPanel)
        suggestionButtons = listOf(
            view.findViewById(R.id.suggestionAButton),
            view.findViewById(R.id.suggestionBButton),
            view.findViewById(R.id.suggestionCButton)
        )

        view.findViewById<Button>(R.id.vibeTypingButton).setOnClickListener {
            Log.d(TAG, "VibeTyping button clicked")
            relationshipRow.visibility = View.VISIBLE
        }

        wireRelationshipButton(view, R.id.friendButton, "friend")
        wireRelationshipButton(view, R.id.teammateButton, "teammate")
        wireRelationshipButton(view, R.id.businessButton, "business")
        wireRelationshipButton(view, R.id.professorButton, "professor")

        view.findViewById<Button>(R.id.spaceButton).setOnClickListener {
            Log.d(TAG, "Space button clicked")
            commitText(" ")
        }

        view.findViewById<Button>(R.id.deleteButton).setOnClickListener {
            Log.d(TAG, "Delete button clicked")
            currentInputConnection?.deleteSurroundingText(1, 0)
        }

        view.findViewById<Button>(R.id.enterButton).setOnClickListener {
            Log.d(TAG, "Enter button clicked")
            commitText("\n")
        }

        return view
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val inputConnection = currentInputConnection
        
        when (primaryCode) {
            -5 -> {
                // Backspace
                inputConnection?.deleteSurroundingText(1, 0)
                Log.d(TAG, "Backspace pressed")
            }
            -100 -> {
                // 한/영 전환
                isKoreanMode = !isKoreanMode
                Log.d(TAG, "Language mode toggled: Korean=$isKoreanMode")
            }
            -101 -> {
                // 입력 (Submit)
                inputConnection?.commitText("\n", 1)
                Log.d(TAG, "Enter pressed")
            }
            32 -> {
                // Space
                inputConnection?.commitText(" ", 1)
                Log.d(TAG, "Space pressed")
            }
            10 -> {
                // Enter key
                inputConnection?.commitText("\n", 1)
                Log.d(TAG, "Enter pressed")
            }
            else -> {
                // 일반 문자 입력
                val c = primaryCode.toChar().toString()
                val text = if (isKoreanMode) convertToKorean(c) else c.uppercase()
                inputConnection?.commitText(text, 1)
                Log.d(TAG, "Key pressed: $c -> $text (Korean: $isKoreanMode)")
            }
        }
    }

    override fun onPress(primaryCode: Int) {
        Log.d(TAG, "Key pressed: $primaryCode")
    }

    override fun onRelease(primaryCode: Int) {
        Log.d(TAG, "Key released: $primaryCode")
    }

    override fun onText(text: CharSequence?) {
        Log.d(TAG, "Text input: $text")
        currentInputConnection?.commitText(text.toString(), 1)
    }

    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}

    private fun convertToKorean(english: String): String {
        // 영문을 한글로 변환하는 기본 매핑
        return when (english.lowercase()) {
            "q" -> "ㅂ"
            "w" -> "ㅈ"
            "e" -> "ㄷ"
            "r" -> "ㄱ"
            "t" -> "ㅅ"
            "y" -> "ㅛ"
            "u" -> "ㅕ"
            "i" -> "ㅑ"
            "o" -> "ㅐ"
            "p" -> "ㅔ"
            "a" -> "ㅁ"
            "s" -> "ㄴ"
            "d" -> "ㅇ"
            "f" -> "ㄹ"
            "g" -> "ㅎ"
            "h" -> "ㅗ"
            "j" -> "ㅓ"
            "k" -> "ㅏ"
            "l" -> "ㅣ"
            "z" -> "ㅆ"
            "x" -> "ㅈ"
            "c" -> "ㅊ"
            "v" -> "ㅋ"
            "b" -> "ㅌ"
            "n" -> "ㅍ"
            "m" -> "ㅎ"
            else -> english
        }
    }

    private fun wireRelationshipButton(view: View, buttonId: Int, relationship: String) {
        view.findViewById<Button>(buttonId).setOnClickListener {
            Log.d(TAG, "Relationship selected: $relationship")
            showSuggestionPanel(relationship)
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

    companion object {
        private const val TAG = "VibeTypeKeyboard"
        private const val MAX_REPLACE_CHARS = 500
        private val SUGGESTION_LABELS = listOf("A", "B", "C")
    }
}
