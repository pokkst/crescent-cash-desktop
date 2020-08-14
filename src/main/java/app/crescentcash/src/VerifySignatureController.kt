package app.crescentcash.src

import app.crescentcash.src.ui.UIHelper.OverlayUI
import app.crescentcash.src.utils.GuiUtils
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.TextField


class VerifySignatureController {
    @JvmField
    var overlayUI: OverlayUI<*>? = null
    @FXML
    var close: Button? = null
    @FXML
    var messageToVerify: TextField? = null
    @FXML
    var addressToVerify: TextField? = null
    @FXML
    var signatureToVerify: TextField? = null

    fun close(event: ActionEvent) {
        overlayUI!!.done()
    }

    fun verifyMessage(actionEvent: ActionEvent) {
        val message = messageToVerify!!.text.toString()
        val address = addressToVerify!!.text.toString()
        val signature = signatureToVerify!!.text.toString()

        if (address.contains("#")) {
            object : Thread() {
                override fun run() {
                    verify(message, address, signature)
                }
            }.start()
        } else {
            verify(message, address, signature)
        }
    }

    private fun verify(message: String, address: String, signature: String) {
        val isVerified = Main.INSTANCE.walletHelper.isSignatureValid(address, signature, message)

        if (isVerified) {
            GuiUtils.informationalAlert("Crescent Cash", "Signature is valid!")
        } else {
            GuiUtils.informationalAlert("Crescent Cash", "Signature is NOT valid!")
        }
    }
}