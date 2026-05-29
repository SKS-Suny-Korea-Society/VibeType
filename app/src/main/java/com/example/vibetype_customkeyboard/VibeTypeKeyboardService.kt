package com.example.vibetype_customkeyboard

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

class VibeTypeKeyboardService : InputMethodService() {

    private lateinit var recommendationContainer: LinearLayout

    override fun onCreateInputView(): View {
        Log.d(TAG, "VibeType keyboard created")

        // Inflate the custom keyboard UI that Android shows inside text fields.
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        recommendationContainer = view.findViewById(R.id.recommendationContainer)

        // These buttons read the current message and generate mock AI suggestions.
        view.findViewById<Button>(R.id.casualButton).setOnClickListener {
            Log.d(TAG, "Casual button clicked")
            generateSuggestions("casual")
        }

        view.findViewById<Button>(R.id.politeButton).setOnClickListener {
            Log.d(TAG, "Polite button clicked")
            generateSuggestions("polite")
        }

        view.findViewById<Button>(R.id.translateButton).setOnClickListener {
            Log.d(TAG, "Translate button clicked")
            generateSuggestions("translate")
        }

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

        showSuggestions(getMockSuggestions("", "casual"))
        return view
    }

    private fun generateSuggestions(mode: String) {
        val input = currentInputConnection
            ?.getTextBeforeCursor(MAX_INPUT_LENGTH, 0)
            ?.toString()
            .orEmpty()
            .trim()

        Log.d(TAG, "Input text read: $input")
        Log.d(TAG, "Selected mode: $mode")

        // Keep this boundary small so the real backend API can replace it later.
        val suggestions = try {
            getMockSuggestions(input, mode)
        } catch (error: Exception) {
            Log.e(TAG, "Suggestion generation failed, using fallback", error)
            getMockSuggestions("", mode)
        }

        Log.d(TAG, "Suggestions generated: $suggestions")
        showSuggestions(suggestions)
    }

    // Mock AI recommendations for hackathon testing before real API integration.
    private fun getMockSuggestions(input: String, mode: String): List<String> {
        if (input.isBlank()) {
            return when (mode) {
                "casual" -> listOf(
                    "Sounds good to me.",
                    "Yep, let's do it.",
                    "I like that idea."
                )
                "polite" -> listOf(
                    "That sounds great. Thank you.",
                    "I appreciate your help.",
                    "Please let me know what works best."
                )
                "translate" -> listOf(
                    "This is a translated sentence.",
                    "Thank you for your message.",
                    "I will check and reply soon."
                )
                else -> listOf("That sounds good!")
            }
        }

        return when (mode) {
            "casual" -> listOf(
                "Sounds good: $input",
                "Yeah, $input",
                "That works for me. $input"
            )
            "polite" -> listOf(
                "That sounds great. Thank you. $input",
                "I appreciate it. $input",
                "Please let me know what works best. $input"
            )
            "translate" -> listOf(
                "This is a translated sentence: $input",
                "Translated: $input",
                "English version: $input"
            )
            else -> listOf(input)
        }
    }

    private fun showSuggestions(suggestions: List<String>) {
        recommendationContainer.removeAllViews()

        suggestions.forEach { suggestion ->
            val suggestionButton = Button(this).apply {
                text = suggestion
                isAllCaps = false
                setOnClickListener {
                    Log.d(TAG, "Suggestion inserted: $suggestion")
                    commitText(suggestion)
                }
            }

            recommendationContainer.addView(
                suggestionButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun commitText(text: String) {
        Log.d(TAG, "Committing text: $text")
        currentInputConnection?.commitText(text, 1)
    }

    companion object {
        private const val TAG = "VibeTypeKeyboard"
        private const val MAX_INPUT_LENGTH = 500
    }
}
