package com.example.vibetype_customkeyboard

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class TestInputActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        val editText = EditText(this).apply {
            id = R.id.testInputEditText
            hint = getString(R.string.test_input_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 5
            setSingleLine(false)
            requestFocus()
        }
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                Log.d(TAG, "Test input text changed: ${text.toString().replace("\n", "\\n")}")
            }
            override fun afterTextChanged(text: Editable?) = Unit
        })

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
            addView(
                TextView(this@TestInputActivity).apply {
                    text = getString(R.string.test_input_title)
                    textSize = 20f
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                editText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        setContentView(layout)

        editText.post {
            val inputMethodManager = getSystemService(InputMethodManager::class.java)
            inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }
        editText.postDelayed({
            val inputMethodManager = getSystemService(InputMethodManager::class.java)
            inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_FORCED)
        }, 500)
    }

    companion object {
        private const val TAG = "VibeTypeTestInput"
    }
}
