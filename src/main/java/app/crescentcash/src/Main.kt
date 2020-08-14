package app.crescentcash.src

import app.crescentcash.src.net.NetHelper
import app.crescentcash.src.ui.UIHelper
import app.crescentcash.src.utils.Constants
import app.crescentcash.src.utils.GuiUtils
import app.crescentcash.src.utils.SettingsHelper
import app.crescentcash.src.wallet.WalletHelper
import javafx.application.Application
import javafx.stage.Stage
import org.bitcoinj.kits.BIP47AppKit
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.system.exitProcess

class Main : Application() {

    lateinit var uiHelper: UIHelper
    lateinit var walletHelper: WalletHelper
    lateinit var netHelper: NetHelper
    lateinit var settings: SettingsHelper

    @Throws(Exception::class)
    override fun start(mainWindow: Stage) {
        settings = SettingsHelper(SettingsHelper.saveFile)
        settings.loadSettings()

        try {
            loadWindow(mainWindow)
        } catch (e: Throwable) {
            GuiUtils.crashAlert(e)
            throw e
        }
    }

    @Throws(IOException::class)
    private fun loadWindow(mainWindow: Stage) {
        INSTANCE = this

        uiHelper = UIHelper(mainWindow)
        walletHelper = WalletHelper()
        netHelper = NetHelper()

        mainWindow.show()

        if (!isNewUser) {
            if(BIP47AppKit.isWalletEncrypted(File(System.getProperty("user.home")), Constants.WALLET_NAME)) {
                /*
                If the saved setting we got is false, but our wallet is encrypted, then we set our saved setting to true.
                 */
                if (!WalletHelper.encrypted) {
                    WalletHelper.encrypted = true
                    settings.setBoolean("useEncryption", WalletHelper.encrypted)
                }

                uiHelper.displayUnlockScreen()
            } else {
                this.setupWalletAndChecks()
            }
        } else {
            uiHelper.displayNewUser()
        }
    }

    fun setupWalletAndChecks() {
        usingNewBlockStore = settings.getBoolean("usingNewBlockStore", false)
        usingNewBlockStoreSlp = settings.getBoolean("usingNewBlockStoreSlp", false)

        if(!usingNewBlockStore) {
            val chainFile = File(File(System.getProperty("user.home")), "${Constants.WALLET_NAME}.spvchain")
            if(chainFile.exists()) {
                chainFile.delete()
            }
            settings.setBoolean("usingNewBlockStore", true)
        }

        if(!usingNewBlockStoreSlp) {
            val chainFile = File(File(System.getProperty("user.home")), "users_slp_wallet.spvchain")
            if(chainFile.exists()) {
                chainFile.delete()
            }
            settings.setBoolean("usingNewBlockStoreSlp", true)
        }

        walletHelper.setupWalletKit(null, "", verifyingRestore = false, upgradeToBip47 = false)
        usingBip47CashAccount = settings.getBoolean("bip47CashAcct", false)
        uiHelper.displayWalletScreen()

        if(!usingBip47CashAccount) {
            upgradeToBip47CashAccount()
        } else {
            val cashAcct = settings.getString("cashAccount", "")
            cashAccountSaved = !cashAcct.contains("#???")

            if (!cashAccountSaved) {
                WalletHelper.registeredTxHash = settings.getString("cashAcctTx", "")
                val plainName = cashAcct.replace("#???", "")
                netHelper.checkForAccountIdentity(plainName, false)
            }
        }
    }

    private fun upgradeToBip47CashAccount() {
        val cashAcct = settings.getString("cashAccount", "")
        val plainName = cashAcct.split("#")[0]
        val address = WalletHelper.walletKit!!.getvWallet().currentReceiveAddress().toString()
        val paymentCode = WalletHelper.walletKit!!.paymentCode
        println("Registering...")
        settings.setBoolean("isNewUser", false)
        if (Constants.IS_PRODUCTION) netHelper.registerCashAccount(plainName, paymentCode, address)
    }

    @Throws(IOException::class, InterruptedException::class)
    override fun stop() {
        if (WalletHelper.walletKit != null) {
            WalletHelper.walletKit!!.stopAsync()
            WalletHelper.walletKit!!.awaitTerminated()
        }

        exitProcess(0)
    }

    companion object {
        lateinit var INSTANCE: Main
        @JvmField
        var cashAccountSaved = false
        @JvmField
        var usingNewBlockStore = false
        @JvmField
        var usingNewBlockStoreSlp = false
        @JvmField
        var usingBip47CashAccount = false
        @JvmField
        var useTestnet: Boolean = false
        @JvmField
        var isNewUser: Boolean = false

        @JvmStatic
        fun main(args: Array<String>) {
            launch(Main::class.java)
        }
    }
}