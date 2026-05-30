package com.sks.vibetype

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.ExtractedTextRequest
import android.widget.TextView

class VibeTypeKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private var keyboardView: KeyboardView? = null

    // 🌟 한/영 전환을 완벽하게 처리하기 위해 두 개의 자판 객체를 생성합니다.
    private var englishKeyboard: Keyboard? = null
    private var koreanKeyboard: Keyboard? = null

    private lateinit var tvFriendly: TextView
    private lateinit var tvProfessional: TextView
    private lateinit var tvTrendy: TextView

    private var isKoreanMode = false
    private var isShifted = false

    override fun onCreateInputView(): View {
        val rootLayout = layoutInflater.inflate(R.layout.keyboard_base_layout, null)

        tvFriendly = rootLayout.findViewById(R.id.tvFriendly)
        tvProfessional = rootLayout.findViewById(R.id.tvProfessional)
        tvTrendy = rootLayout.findViewById(R.id.tvTrendy)

        keyboardView = rootLayout.findViewById(R.id.keyboardView) as? KeyboardView

        try {
            // 영어 자판과 한글 자판 구조를 각각 초기화 (qwerty.xml 하나로 둘 다 완벽 대응)
            englishKeyboard = Keyboard(this, R.xml.qwerty)
            koreanKeyboard = Keyboard(this, R.xml.qwerty)

            // 한글 자판 객체의 글자 레이블들을 미리 한글로 싹 세팅해 둡니다.
            initializeKoreanLabels()

            // 초기 상태는 영어 자판으로 시작
            keyboardView?.keyboard = englishKeyboard
            keyboardView?.setOnKeyboardActionListener(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return rootLayout
    }

    override fun onEvaluateInputViewShown(): Boolean = true

    // 현재 입력창의 전체 문장을 실시간으로 가져오는 함수 (AI 연동 엔진용)
    private fun getCurrentInputText(): String {
        val ic = currentInputConnection ?: return ""
        val extractedText = ic.getExtractedText(ExtractedTextRequest(), 0)
        return extractedText?.text?.toString() ?: ""
    }

    // 🌟 모든 입력 문제(스페이스, 엔터, 한영, 쉬프트)를 해결하는 마스터 컨트롤러
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return

        when (primaryCode) {
            -5 -> { // 1. 백스페이스 (글자 하나 지우기)
                ic.deleteSurroundingText(1, 0)
            }
            10 -> { // 2. 엔터 키 (줄바꿈 완벽 해결)
                ic.sendKeyValue(KeyEvent.KEYCODE_ENTER)
            }
            32 -> { // 3. 스페이스바 (공백 문자 직접 입력 방식으로 씹힘 전면 해결)
                ic.commitText(" ", 1)
            }
            -2 -> { // 4. 한/영 키 (자판 그래픽이 한글로 안 바뀌던 문제 완전 해결)
                isKoreanMode = !isKoreanMode
                if (isKoreanMode) {
                    keyboardView?.keyboard = koreanKeyboard
                } else {
                    keyboardView?.keyboard = englishKeyboard
                }
                // 대소문자 상태 유지 갱신
                keyboardView?.keyboard?.isShifted = isShifted
                keyboardView?.invalidateAllKeys() // 화면 리드로잉 강제 실행
            }
            -1 -> { // 5. Shift 키 (대문자/소문자 토글 완전 해결)
                isShifted = !isShifted
                keyboardView?.keyboard?.isShifted = isShifted
                keyboardView?.invalidateAllKeys() // 자판에 대소문자 실시간 반영
            }
            else -> { // 6. 일반 글자 입력 처리
                if (primaryCode > 0) {
                    var codeChar = primaryCode.toChar().toString()

                    if (isKoreanMode) {
                        // 한글 모드일 때 해당 쿼티 위치에 맞는 한글 자모음 추출
                        codeChar = convertEngToKor(codeChar)
                    } else {
                        // 영어 모드이면서 Shift가 켜져 있다면 대문자로 변환
                        if (isShifted) {
                            codeChar = codeChar.uppercase()
                        }
                    }
                    ic.commitText(codeChar, 1)

                    // Shift가 켜진 상태에서 글자를 하나 입력했다면, 일반적인 자판처럼 다시 소문자로 원위치
                    if (isShifted && primaryCode != -1) {
                        isShifted = false
                        englishKeyboard?.isShifted = false
                        koreanKeyboard?.isShifted = false
                        keyboardView?.invalidateAllKeys()
                    }
                }
            }
        }

        // 디버깅용 실시간 전체 문장 로그캣 출력
        val currentSentence = getCurrentInputText()
        android.util.Log.d("VibeTypeAI", "현재 입력된 전체 문장: $currentSentence")
    }

    // 편의를 위해 익스텐션 함수 형태로 KeyEvent 전달 메소드 구현
    private fun android.view.inputmethod.InputConnection.sendKeyValue(keyCode: Int) {
        this.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        this.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    // 초기 빌드 시 한글 자판 인스턴스의 텍스트 레이블을 강제로 매핑시키는 함수
    private fun initializeKoreanLabels() {
        val keys = koreanKeyboard?.keys ?: return
        for (key in keys) {
            if (key.codes != null && key.codes.isNotEmpty() && key.codes[0] > 0) {
                val origChar = key.codes[0].toChar().toString()
                key.label = convertEngToKor(origChar)
            }
        }
    }

    // 영문 쿼티 코드를 한글 자모음으로 일대일 치환해주는 매퍼
    private fun convertEngToKor(eng: String): String {
        return when (eng.lowercase()) {
            "q" -> "ㅂ" "w" -> "ㅈ" "e" -> "ㄷ" "r" -> "ㄱ" "t" -> "ㅅ"
            "y" -> "ㅛ" "u" -> "ㅕ" "i" -> "ㅑ" "o" -> "ㅐ" "p" -> "ㅔ"
            "a" -> "ㅁ" "s" -> "ㄴ" "d" -> "ㅇ" "f" -> "ㄹ" "g" -> "ㅎ"
            "h" -> "ㅗ" "j" -> "ㅓ" "k" -> "ㅏ" "l" -> "ㅣ"
            "z" -> "ㅋ" "x" -> "ㅌ" "c" -> "ㅊ" "v" -> "ㅍ" "b" -> "ㅠ"
            "n" -> "ㅜ" "m" -> "ㅡ"
            else -> eng
        }
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}