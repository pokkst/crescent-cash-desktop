package app.crescentcash.src

import app.crescentcash.src.ui.UIHelper
import app.crescentcash.src.utils.GuiUtils
import app.crescentcash.src.wallet.WalletHelper
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.TextArea
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import org.bitcoinj.wallet.DeterministicSeed

class WalletSettingsController {

    @FXML
    var innerWindow: VBox? = null
    @FXML
    var window: StackPane? = null
    @FXML
    var innerWindow2: HBox? = null
    @FXML
    internal var walletRecoverySeed: TextArea? = null
    @JvmField
    var overlayUI: UIHelper.OverlayUI<*>? = null


    fun initialize() {
        val seed: DeterministicSeed = WalletHelper.wallet.keyChainSeed

        if (seed.isEncrypted) {
            GuiUtils.informationalAlert("Crescent Cash", "Wallet is encrypted!")
        }
        val mnemonicCode = seed.mnemonicCode
        kotlin.checkNotNull(mnemonicCode)    // Already checked for encryption.
        val origWords = StringBuilder()

        for (s in mnemonicCode) {
            origWords.append(s).append(" ")
        }
        walletRecoverySeed!!.text = origWords.toString()
    }

    fun closeClicked(event: ActionEvent) {
        overlayUI!!.done()
    }
}
