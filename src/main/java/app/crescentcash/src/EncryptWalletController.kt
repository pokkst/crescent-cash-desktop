package app.crescentcash.src

import app.crescentcash.src.ui.UIHelper
import app.crescentcash.src.utils.GuiUtils
import app.crescentcash.src.wallet.WalletHelper
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.PasswordField
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import org.apache.http.util.TextUtils
import org.bitcoinj.crypto.KeyCrypterScrypt

class EncryptWalletController {

    @FXML
    var innerWindow: VBox? = null
    @FXML
    var window: StackPane? = null
    @FXML
    var innerWindow2: HBox? = null
    @FXML
    internal var encryptionPassword: PasswordField? = null
    @FXML
    internal var encryptionPwdConfirm: PasswordField? = null
    @JvmField
    var overlayUI: UIHelper.OverlayUI<*>? = null

    fun initialize() {

    }

    fun encryptWallet(event: ActionEvent) {
        if (!TextUtils.isEmpty(encryptionPassword!!.text) && !TextUtils.isEmpty(encryptionPwdConfirm!!.text)) {
            if (encryptionPassword!!.text == encryptionPwdConfirm!!.text.toString()) {
                val password = encryptionPassword!!.text.toString()

                val scrypt = KeyCrypterScrypt(WalletHelper.SCRYPT_PARAMETERS)
                val key = scrypt.deriveKey(password)
                WalletHelper.wallet.encrypt(scrypt, key)
                GuiUtils.informationalAlert("Crescent Cash", "Encrypted wallet!")
                //WalletHelper.aesKey = key
                overlayUI!!.done()
            } else {
                GuiUtils.informationalAlert("Crescent Cash", "Passwords do not match.")
            }
        } else {
            GuiUtils.informationalAlert("Crescent Cash", "Please enter a password.")
        }
    }

    fun closeScreen(event: ActionEvent) {
        //WalletHelper.encrypted = false
       // Main.INSTANCE.uiHelper.controller.encryptionCheckbox!!.isSelected = WalletHelper.encrypted
        //Main.INSTANCE.settings.setBoolean("useEncryption", WalletHelper.encrypted)
        overlayUI!!.done()
    }
}
