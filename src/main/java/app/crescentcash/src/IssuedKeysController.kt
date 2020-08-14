package app.crescentcash.src

import app.crescentcash.src.ui.UIHelper.OverlayUI
import app.crescentcash.src.utils.GuiUtils
import app.crescentcash.src.wallet.WalletHelper
import javafx.beans.binding.Bindings
import javafx.collections.FXCollections
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ListView
import javafx.scene.control.PasswordField
import javafx.scene.control.TextField
import javafx.scene.control.cell.TextFieldListCell
import javafx.scene.input.MouseEvent
import javafx.scene.layout.VBox
import javafx.util.StringConverter
import org.apache.http.util.TextUtils
import org.bitcoinj.core.CashAddress
import org.bitcoinj.core.CashAddressFactory
import org.bitcoinj.core.ECKey
import org.bitcoinj.crypto.BIP38PrivateKey


class IssuedKeysController {
    @FXML
    var keys: ListView<ECKey>? = null
    private val keysList = FXCollections.observableArrayList<ECKey>()
    @JvmField
    var overlayUI: OverlayUI<*>? = null
    @FXML
    var close: Button? = null
    @FXML
    var keyTextsVbox: VBox? = null
    @FXML
    var privKeyField: TextField? = null
    @FXML
    var bip38ImportPasswordText: PasswordField? = null

    fun initialize() {
        refreshKeys()

        keyTextsVbox!!.children.remove(bip38ImportPasswordText)
    }

    fun close(event: ActionEvent) {
        overlayUI!!.done()
    }

    fun selectKey(arg0: MouseEvent) {
        if (keys!!.selectionModel.selectedItem != null) {
            WalletHelper.currentEcKey = keys!!.selectionModel.selectedItem
            Main.INSTANCE.uiHelper.overlayUI<ManageKeyController>("manage_key.fxml")
        }
    }

    fun importPrivateKey(actionEvent: ActionEvent) {
        val privKey = privKeyField!!.text.toString()
        if (!TextUtils.isEmpty(privKey)) {
            if (!WalletHelper.isEncryptedBIP38Key(privKey)) {
                Main.INSTANCE.walletHelper.importPrivateKey(privKey)
                privKeyField!!.text = null
                if (keyTextsVbox!!.children.contains(bip38ImportPasswordText)) {
                    keyTextsVbox!!.children.remove(bip38ImportPasswordText)
                }
                this.refreshKeys()
            } else {
                if (!keyTextsVbox!!.children.contains(bip38ImportPasswordText)) {
                    keyTextsVbox!!.children.add(bip38ImportPasswordText)
                } else {
                    val bip38Password = bip38ImportPasswordText!!.text.toString()
                    if(!TextUtils.isEmpty(bip38Password)) {
                        val encryptedKey = BIP38PrivateKey.fromBase58(WalletHelper.parameters, privKey)
                        try {
                            val ecKey = encryptedKey.decrypt(bip38Password)
                            Main.INSTANCE.walletHelper.importPrivateKey(ecKey.getPrivateKeyAsWiF(WalletHelper.parameters))
                            privKeyField!!.text = null
                            keyTextsVbox!!.children.remove(bip38ImportPasswordText)
                            this.refreshKeys()
                        } catch (e: BIP38PrivateKey.BadPassphraseException) {
                            GuiUtils.informationalAlert("Crescent Cash", "Incorrect password!")
                        }
                    } else {
                        GuiUtils.informationalAlert("Crescent Cash", "Please enter a password!")
                    }
                }
            }
        }
    }

    private fun refreshKeys() {
        val listOfKeys = ArrayList<ECKey>()
        for (key in WalletHelper.wallet.issuedReceiveKeys) {
            listOfKeys.add(key)
        }
        for (key in WalletHelper.wallet.importedKeys) {
            listOfKeys.add(key)
        }

        keysList.setAll(listOfKeys)

        Bindings.bindContent(keys!!.items, keysList)

        keys!!.setCellFactory { param ->
            TextFieldListCell(object : StringConverter<ECKey>() {
                override fun toString(key: ECKey): String {
                    val params = WalletHelper.parameters
                    val legacyAddr = key.toAddress(params).toString()
                    val cashAddr = CashAddressFactory.create().getFromBase58(params, legacyAddr).toString().replace(params.cashAddrPrefix + ":", "")
                    return "$cashAddr\n$legacyAddr"
                }

                override fun fromString(string: String): ECKey? {
                    return null
                }
            })
        }
    }
}
