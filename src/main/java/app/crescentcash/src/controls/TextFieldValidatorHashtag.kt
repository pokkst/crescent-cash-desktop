package app.crescentcash.src.controls

import javafx.scene.control.TextField

class TextFieldValidatorHashtag : TextField() {

    override fun replaceText(start: Int, end: Int, text: String) {
        if (validate(text)) {
            super.replaceText(start, end, text)
        }
    }

    override fun replaceSelection(text: String) {
        if (validate(text)) {
            super.replaceSelection(text)
        }
    }

    private fun validate(text: String): Boolean {
        return text.matches("[a-zA-Z0-9_#.]*".toRegex())
    }
}