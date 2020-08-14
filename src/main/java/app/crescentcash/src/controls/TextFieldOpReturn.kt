package app.crescentcash.src.controls

import javafx.scene.control.TextField


class TextFieldOpReturn : TextField() {
    private val maxLength: Int = 150

    override fun replaceText(start: Int, end: Int, insertedText: String) {
        // Get the text in the textfield, before the user enters something
        val currentText = if (this.text == null) "" else this.text

        // Compute the text that should normally be in the textfield now
        val finalText = currentText.substring(0, start) + insertedText + currentText.substring(end)

        // If the max length is not excedeed
        val numberOfexceedingCharacters = finalText.length - maxLength
        if (numberOfexceedingCharacters <= 0) {
            // Normal behavior
            super.replaceText(start, end, insertedText)
        } else {
            // Otherwise, cut the the text that was going to be inserted
            val cutInsertedText = insertedText.substring(
                    0,
                    insertedText.length - numberOfexceedingCharacters
            )

            // And replace this text
            super.replaceText(start, end, cutInsertedText)
        }
    }
}