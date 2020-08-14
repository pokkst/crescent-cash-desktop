package app.crescentcash.src.ui

import app.crescentcash.src.*
import app.crescentcash.src.utils.GuiUtils
import app.crescentcash.src.wallet.WalletHelper
import com.google.common.base.Splitter
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import org.apache.http.util.TextUtils
import org.bitcoinj.core.slp.*
import org.bitcoinj.crypto.BIP38PrivateKey
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.crypto.MnemonicException
import org.bitcoinj.utils.MonetaryFormat
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import java.net.InetSocketAddress
import java.net.Proxy


class UIController {
    @FXML
    var balance: Label? = null
    @FXML
    var balanceSlp: Label? = null
    @FXML
    var connectionStatus: Label? = null

    var model = UIModel()
    @FXML
    var window: AnchorPane? = null
    @FXML
    var balanceLabel: VBox? = null
    @FXML
    var slpTokens: ListView<SlpTokenBalance>? = null
    @FXML
    var txGrid: GridPane? = null
    @FXML
    var newReceiveAddress: TextField? = null
    @FXML
    var newReceiveAddressSlp: TextField? = null
    @FXML
    var receiveTab: Tab? = null
    @FXML
    var sendTab: Tab? = null
    @FXML
    var settingsTab: Tab? = null
    @FXML
    var historyTab: Tab? = null
    @FXML
    var toolsTab: Tab? = null
    @FXML
    var slpTokensTab: Tab? = null
    @FXML
    var historyBox: ScrollPane? = null
    @FXML
    var toAmount: TextField? = null
    @FXML
    var toAddress: TextField? = null
    @FXML
    var sendBtn: Button? = null
    @FXML
    var maxBtn: Button? = null
    @FXML
    var clearBtn: Button? = null
    @FXML
    var newSeed: TextField? = null
    @FXML
    var recoverBtn: Button? = null
    @FXML
    var firstStartPane: Pane? = null
    @FXML
    var restorePane: Pane? = null
    @FXML
    var tabPane: TabPane? = null
    @FXML
    var usernameCreate: TextField? = null
    @FXML
    var createWalletPane: Pane? = null
    @FXML
    var handleRestoreField: TextField? = null
    @FXML
    var cashAcctText: TextField? = null
    @FXML
    var xpubLabel: TextField? = null
    @FXML
    var qrCode: ImageView? = null
    @FXML
    var qrCodeSlp: ImageView? = null
    @FXML
    var sendType: ComboBox<String>? = null
    @FXML
    var displayUnits: ComboBox<String>? = null
    @FXML
    var fiatType: ComboBox<String>? = null
    /*@FXML
    var encryptionCheckbox: CheckBox? = null*/
    @FXML
    var torCheckbox: CheckBox? = null
    @FXML
    var networkNodeText: TextField? = null
    @FXML
    var privateKeyToSweep: TextField? = null
    @FXML
    var opReturnCheckbox: CheckBox? = null
    @FXML
    var sendTabVbox: VBox? = null
    @FXML
    var opReturnBox: HBox? = null
    @FXML
    var opReturnText: TextField? = null
    @FXML
    var allowLegacyP2SHCheckbox: CheckBox? = null
    @FXML
    var clearUtxosBtn: Button? = null
    @FXML
    var bip38PasswordText: PasswordField? = null
    @FXML
    var sweepWalletVbox: VBox? = null
    @FXML
    var toggleAddrBtn: Button? = null
    @FXML
    var togglePaymentCodeBtn: Button? = null

    private var newWalletSeed: DeterministicSeed? = null
    private var mnemonicCode: List<String>? = null

    fun initialize() {
        displayUnits!!.items.addAll(
                MonetaryFormat.CODE_BTC,
                MonetaryFormat.CODE_MBTC,
                MonetaryFormat.CODE_UBTC,
                "sats"
        )


        sendType!!.items.clear()
        sendType!!.items.addAll(
                displayUnits!!.value,
                UIHelper.fiat
        )

        fiatType!!.items.addAll(
                "USD",
                "EUR",
                "AUD"
        )

        displayUnits!!.value = Main.INSTANCE.settings.getString("displayUnit", MonetaryFormat.CODE_BTC)
        sendType!!.value = Main.INSTANCE.settings.getString("sendType", "BCH")
        fiatType!!.value = Main.INSTANCE.settings.getString("fiat", "USD")
        networkNodeText!!.text = Main.INSTANCE.settings.getString("networkNode", "")
        //encryptionCheckbox!!.isSelected = WalletHelper.encrypted
        torCheckbox!!.isSelected = WalletHelper.useTor
        opReturnCheckbox!!.isSelected = WalletHelper.addOpReturn
        allowLegacyP2SHCheckbox!!.isSelected = WalletHelper.allowLegacyP2SH

        if (WalletHelper.addOpReturn) {
            if (!sendTabVbox!!.children.contains(opReturnBox)) {
                sendTabVbox!!.children.add(1, opReturnBox)
            }
        } else {
            sendTabVbox!!.children.remove(opReturnBox)
        }

        networkNodeText!!.textProperty().addListener { observable, oldValue, newValue ->
            Main.INSTANCE.settings.setString("networkNode", newValue)
        }
    }

    fun send(actionEvent: ActionEvent) {
        Main.INSTANCE.walletHelper.send()
    }

    fun clearSendFields(actionEvent: ActionEvent) {
        toAddress!!.clear()
        toAmount!!.clear()
        WalletHelper.selectedUtxos = ArrayList()
        clearUtxosBtn!!.isVisible = false
        if (opReturnText != null) {
            opReturnText!!.clear()
        }
    }

    fun recoverWallet(actionEvent: ActionEvent) {
        if (!TextUtils.isEmpty(newSeed!!.text) && !TextUtils.isEmpty(handleRestoreField!!.text)) {
            Main.INSTANCE.netHelper.prepareWalletForVerification()
        }
    }

    fun displayRestorePane(actionEvent: ActionEvent) {
        Main.INSTANCE.uiHelper.setScreen("restore")
    }

    fun displayNewWallet(actionEvent: ActionEvent) {
        Main.INSTANCE.uiHelper.setScreen("createWallet")
    }

    fun registerWallet(actionEvent: ActionEvent) {
        if (!TextUtils.isEmpty(usernameCreate!!.text)) {

            if (usernameCreate!!.text.contains("#"))
                return

            //DEFAULT_SEED_ENTROPY_BITS * 2 generates a 24 word seed.
            val entropy = WalletHelper.getEntropy(DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS)
            var mnemonic: List<String>? = null
            try {
                mnemonic = MnemonicCode.INSTANCE.toMnemonic(entropy)
            } catch (e: MnemonicException.MnemonicLengthException) {
                e.printStackTrace()
            }

            Main.INSTANCE.uiHelper.setScreen("loadWallet")
            mnemonicCode = mnemonic
            val recoverySeed = StringBuilder()

            for (x in mnemonicCode!!.indices) {
                recoverySeed.append(mnemonicCode!![x]).append(if (x == mnemonicCode!!.size - 1) "" else " ")
            }

            newWalletSeed =
                    DeterministicSeed(Splitter.on(' ').splitToList(recoverySeed.toString()), null, "", System.currentTimeMillis() / 1000L)
            Main.INSTANCE.walletHelper.setupWalletKit(newWalletSeed, usernameCreate!!.text.trim(),
                verifyingRestore = false,
                upgradeToBip47 = false
            )
        }
    }

    fun displayRecoverySeed(actionEvent: ActionEvent) {
        Main.INSTANCE.uiHelper.overlayUI<WalletSettingsController>("wallet_settings.fxml")
    }

    fun setMaxCoins(actionEvent: ActionEvent) {
        sendType!!.value = displayUnits!!.value

        var balInBch = if (WalletHelper.selectedUtxos.size == 0) {
            WalletHelper.wallet.getBalance(Wallet.BalanceType.ESTIMATED).toPlainString()
        } else {
            Main.INSTANCE.walletHelper.getMaxValueOfSelectedUtxos().toPlainString()
        }

        var balBch: Double = java.lang.Double.parseDouble(balInBch)

        when (Main.INSTANCE.uiHelper.controller.displayUnits!!.value) {
            MonetaryFormat.CODE_BTC -> {
                balInBch = UIHelper.formatBalanceNoUnit(balBch, "#.########")
            }
            MonetaryFormat.CODE_MBTC -> {
                balBch *= 1000.0
                balInBch = UIHelper.formatBalanceNoUnit(balBch, "#.#####")
            }
            MonetaryFormat.CODE_UBTC -> {
                balBch *= 1000000.0
                balInBch = UIHelper.formatBalanceNoUnit(balBch, "#.##")
            }
            "sats" -> {
                balBch *= 100000000.0
                balInBch = UIHelper.formatBalanceNoUnit(balBch, "#")
            }
        }

        this.toAmount!!.text = balInBch
        Main.INSTANCE.settings.setString("sendType", sendType!!.value)
    }

    fun showKeys(action: ActionEvent) {
        Main.INSTANCE.uiHelper.overlayUI<IssuedKeysController>("issued_keys.fxml")
    }

    fun setSendType(action: ActionEvent) {
        println(sendType!!.value)
        Main.INSTANCE.settings.setString("sendType", sendType!!.value)
    }

    fun setDisplayUnits(action: ActionEvent) {
        val previous = Main.INSTANCE.settings.getString("displayUnit", displayUnits!!.value)
        val previousSlp = Main.INSTANCE.settings.getString("sendTypeSlp", displayUnits!!.value)

        if (sendType!!.value == previous) {
            sendType!!.value = displayUnits!!.value
        }

        if (previousSlp == previous) {
            Main.INSTANCE.settings.setString("sendTypeSlp", displayUnits!!.value)
        }

        Main.INSTANCE.settings.setString("displayUnit", displayUnits!!.value)
        Main.INSTANCE.settings.setString("sendType", sendType!!.value)
        Main.INSTANCE.uiHelper.refresh()
    }

    fun setFiatType(action: ActionEvent) {
        val previous = Main.INSTANCE.settings.getString("fiat", "USD")

        if (sendType!!.value == previous) {
            sendType!!.value = fiatType!!.value
        }

        UIHelper.fiat = fiatType!!.value
        Main.INSTANCE.settings.setString("fiat", UIHelper.fiat)
        Main.INSTANCE.settings.setString("sendType", sendType!!.value)
        Main.INSTANCE.uiHelper.refresh()
    }

    /*fun setEncryption(action: ActionEvent) {
        if (encryptionCheckbox!!.isSelected) {
            Main.INSTANCE.uiHelper.displayEncryptionSetup()
        } else {
            if (WalletHelper.aesKey != null) {
                WalletHelper.wallet.decrypt(WalletHelper.aesKey)
                WalletHelper.aesKey = null
            }
        }

        WalletHelper.encrypted = encryptionCheckbox!!.isSelected
        Main.INSTANCE.settings.setBoolean("useEncryption", WalletHelper.encrypted)
    }*/

    fun setTor(action: ActionEvent) {
        WalletHelper.useTor = torCheckbox!!.isSelected

        if (torCheckbox!!.isSelected) {
            WalletHelper.torProxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9050))
        }

        Main.INSTANCE.settings.setBoolean("useTor", WalletHelper.useTor)
    }

    fun setLegacyP2SH(action: ActionEvent) {
        WalletHelper.allowLegacyP2SH = allowLegacyP2SHCheckbox!!.isSelected
        Main.INSTANCE.settings.setBoolean("allowLegacyP2SH", WalletHelper.allowLegacyP2SH)
    }

    fun setOpReturn(action: ActionEvent) {
        WalletHelper.addOpReturn = opReturnCheckbox!!.isSelected
        Main.INSTANCE.settings.setBoolean("addOpReturn", WalletHelper.addOpReturn)
    }

    fun displayVerifySig(actionEvent: ActionEvent) {
        Main.INSTANCE.uiHelper.overlayUI<VerifySignatureController>("verify_sig.fxml")
    }

    fun sweepPaperWallet(actionEvent: ActionEvent) {
        val privKey = privateKeyToSweep!!.text.toString()
        if (!TextUtils.isEmpty(privKey)) {
            if (!WalletHelper.isEncryptedBIP38Key(privKey)) {
                Main.INSTANCE.walletHelper.sweepWallet(privKey)
                privateKeyToSweep!!.text = null
                if (sweepWalletVbox!!.children.contains(bip38PasswordText)) {
                    sweepWalletVbox!!.children.remove(bip38PasswordText)
                }
            } else {
                if (!sweepWalletVbox!!.children.contains(bip38PasswordText)) {
                    sweepWalletVbox!!.children.add(bip38PasswordText)
                } else {
                    val bip38Password = bip38PasswordText!!.text.toString()
                    if(!TextUtils.isEmpty(bip38Password)) {
                        val encryptedKey = BIP38PrivateKey.fromBase58(WalletHelper.parameters, privKey)
                        try {
                            val ecKey = encryptedKey.decrypt(bip38Password)
                            Main.INSTANCE.walletHelper.sweepWallet(ecKey.getPrivateKeyAsWiF(WalletHelper.parameters))
                            privateKeyToSweep!!.text = null
                            sweepWalletVbox!!.children.remove(bip38PasswordText)
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

    fun showUtxos(actionEvent: ActionEvent) {
        Main.INSTANCE.uiHelper.overlayUI<UtxoController>("utxos.fxml")
    }

    fun clearUtxos(actionEvent: ActionEvent) {
        WalletHelper.selectedUtxos = ArrayList()
        clearUtxosBtn!!.isVisible = false
    }

    fun sendToken(mouseEvent: MouseEvent) {
        if (slpTokens!!.selectionModel.selectedItem != null) {
            val position = slpTokens!!.selectionModel.selectedIndex
            WalletHelper.currentTokenPosition = position
            if (position != 0) {
                WalletHelper.currentTokenId = slpTokens!!.selectionModel.selectedItem.tokenId
            } else {
                WalletHelper.currentTokenId = ""
            }

            Main.INSTANCE.uiHelper.overlayUI<UtxoController>("send_slp.fxml")
        }
    }

    fun toggleAddress(actionEvent: ActionEvent) {
        WalletHelper.currentAddressView = !WalletHelper.currentAddressView

        if(WalletHelper.currentAddressView) {
            //SLP
            toggleAddrBtn!!.text = "Show BCH Address"
            Main.INSTANCE.uiHelper.controller.newReceiveAddressSlp!!.text = WalletHelper.slpWalletKit?.currentSlpReceiveAddress().toString()
        } else {
            //BCH
            toggleAddrBtn!!.text = "Show SLP Address"
            Main.INSTANCE.uiHelper.controller.newReceiveAddressSlp!!.text = WalletHelper.slpWalletKit?.wallet?.currentReceiveAddress().toString()
        }

        Main.INSTANCE.uiHelper.generateQRSlp(Main.INSTANCE.uiHelper.controller.newReceiveAddressSlp!!.text, 200, 200)
    }

    fun togglePaymentCode(actionEvent: ActionEvent) {
        WalletHelper.currentAddressViewBCH = !WalletHelper.currentAddressViewBCH

        if(WalletHelper.currentAddressViewBCH) {
            //Payment Code
            togglePaymentCodeBtn!!.text = "Show BCH Address"
            Main.INSTANCE.uiHelper.controller.newReceiveAddress!!.text = WalletHelper.walletKit?.paymentCode
        } else {
            //BCH
            togglePaymentCodeBtn!!.text = "Show Payment Code"
            Main.INSTANCE.uiHelper.controller.newReceiveAddress!!.text = WalletHelper.wallet.currentReceiveAddress().toString()
        }

        Main.INSTANCE.uiHelper.generateQR(Main.INSTANCE.uiHelper.controller.newReceiveAddress!!.text, 150, 150)
    }

    companion object {
        @JvmField
        var transactionSelected: Int = 0
    }
}
