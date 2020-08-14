package app.crescentcash.src

import app.crescentcash.src.ui.UIHelper
import app.crescentcash.src.utils.Constants
import app.crescentcash.src.utils.GuiUtils
import app.crescentcash.src.wallet.WalletHelper
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.PasswordField
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import org.apache.http.util.TextUtils
import org.bitcoinj.kits.BIP47AppKit
import java.io.File

class UnlockWalletController {

    @FXML
    var innerWindow: VBox? = null
    @FXML
    var window: StackPane? = null
    @FXML
    var innerWindow2: HBox? = null
    @FXML
    internal var unlockPassword: PasswordField? = null
    @JvmField
    var overlayUI: UIHelper.OverlayUI<*>? = null


    fun initialize() {

    }

    fun unlockWallet(event: ActionEvent) {
        if (!TextUtils.isEmpty(unlockPassword!!.text)) {
            val password = unlockPassword!!.text
            val encryptedWallet = BIP47AppKit.getEncryptedWallet(File(System.getProperty("user.home")), Constants.WALLET_NAME)
            val scrypt = encryptedWallet.keyCrypter
            val key = scrypt!!.deriveKey(password)
            if (encryptedWallet.checkAESKey(key)) {
                encryptedWallet.decrypt(key)
                encryptedWallet.saveToFile(File(File(System.getProperty("user.home")), Constants.WALLET_NAME + ".wallet"))
                Main.INSTANCE.setupWalletAndChecks()

                overlayUI!!.done()
            } else {
                GuiUtils.informationalAlert("Crescent Cash", "Invalid password.")
            }
        } else {
            GuiUtils.informationalAlert("Crescent Cash", "Please enter your password.")
        }
    }
}
