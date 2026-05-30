package com.example.vibetype_customkeyboard

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

class VibeTypeKeyboardService : InputMethodService() {

    private lateinit var relationshipRow: LinearLayout
    private lateinit var dimOverlay: View
    private lateinit var suggestionPanel: LinearLayout
    private lateinit var suggestionButtons: List<Button>

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

        // Basic keyboard controls operate on the currently focused text input.
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
