package app.crescentcash.src

import app.crescentcash.src.ui.UIHelper.OverlayUI
import app.crescentcash.src.wallet.WalletHelper
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.TextField
import org.apache.http.util.TextUtils
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection


class ManageKeyController {
    @JvmField
    var overlayUI: OverlayUI<*>? = null
    @FXML
    var close: Button? = null
    @FXML
    var messageToSign: TextField? = null
    @FXML
    var signSignature: TextField? = null
    @FXML
    var newCashAcctName: TextField? = null

    fun initialize() {
        println(WalletHelper.currentEcKey.toAddress(WalletHelper.parameters))
    }

    fun close(event: ActionEvent) {
        overlayUI!!.done()
    }

    fun signMessage(actionEvent: ActionEvent) {
        if (!TextUtils.isEmpty(messageToSign!!.text)) {
            val signature = Main.INSTANCE.walletHelper.signMessageWithKey(WalletHelper.currentEcKey, messageToSign!!.text.toString())
            signSignature!!.text = signature
        }
    }

    fun copyPrivateKey(actionEvent: ActionEvent) {
        val stringSelection = StringSelection(WalletHelper.currentEcKey/*.maybeDecrypt(WalletHelper.aesKey)*/.getPrivateKeyAsWiF(WalletHelper.parameters))
        val clpbrd = Toolkit.getDefaultToolkit().systemClipboard
        clpbrd.setContents(stringSelection, null)
    }

    fun registerCashAcct(actionEvent: ActionEvent) {
        val name = newCashAcctName!!.text.toString()
        if (!TextUtils.isEmpty(name)) {
            val address = WalletHelper.currentEcKey.toAddress(WalletHelper.parameters)
            val txHash = Main.INSTANCE.walletHelper.registerCashAccount(WalletHelper.currentEcKey, name)
            Main.INSTANCE.settings.setString("cashacct_$address", "$name#???")
            Main.INSTANCE.settings.setString("cashacct_tx_$address", txHash)
        }
    }
}