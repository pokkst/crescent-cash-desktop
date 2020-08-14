package app.crescentcash.src.async

import app.crescentcash.src.Main
import app.crescentcash.src.utils.GuiUtils
import app.crescentcash.src.wallet.WalletHelper
import com.victorlaerte.asynctask.AsyncTask
import org.bitcoinj.core.Address
import org.bitcoinj.core.CashAddress
import org.bitcoinj.core.CashAddressFactory
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet

class AsyncTaskVerifyWallet(private val tempWallet: Wallet, private val cashAcctName: String, private val seed: DeterministicSeed) : AsyncTask<Void, Void, String>() {
    override fun progressCallback(vararg params: Void?) {
    }

    lateinit var cashAcctEmoji: String

    override fun doInBackground(vararg params: Void?): String? {
        val netHelper = Main.INSTANCE.netHelper

        return try {
            val cashAcctAddress = org.bitcoinj.net.NetHelper().getCashAccountAddress(WalletHelper.parameters, cashAcctName)
            cashAcctEmoji = netHelper.getCashAccountEmoji(cashAcctName)
            cashAcctAddress
        } catch (e: NullPointerException) {
            println("ERROR")
            e.printStackTrace()
            null
        }
    }

    override fun onPreExecute() {

    }

    override fun onPostExecute(result: String?) {
        val walletHelper = Main.INSTANCE.walletHelper
        println(result)

        if(result != null) {
            if (Address.isValidPaymentCode(result)) {
                Main.usingBip47CashAccount = true
                walletHelper.setupWalletKit(seed, cashAcctName, verifyingRestore = true, upgradeToBip47 = false)

                Main.INSTANCE.settings.setString("cashAccount", cashAcctName)
                Main.INSTANCE.settings.setString("cashEmoji", cashAcctEmoji)
                Main.INSTANCE.settings.setBoolean("isNewUser", false)
                Main.INSTANCE.settings.setBoolean("bip47CashAcct", Main.usingBip47CashAccount)

                Main.INSTANCE.uiHelper.setScreen("startRestore")
            } else {
                val accountAddress: Address? = when {
                    Address.isValidCashAddr(WalletHelper.parameters, result) -> CashAddressFactory.create().getFromFormattedAddress(WalletHelper.parameters, result)
                    Address.isValidLegacyAddress(WalletHelper.parameters, result) -> LegacyAddress.fromBase58(WalletHelper.parameters, result)
                    else -> {
                        GuiUtils.informationalAlert("Crescent Cash", "No address found.")
                        null
                    }
                }

                val tempWallet = Wallet.fromSeed(WalletHelper.parameters, seed)

                val isAddressMine = if (accountAddress != null) {
                    tempWallet.isPubKeyHashMine(accountAddress.hash)
                } else {
                    false
                }

                if (isAddressMine) {
                    walletHelper.setupWalletKit(seed, cashAcctName, verifyingRestore = true, upgradeToBip47 = true)

                    Main.INSTANCE.settings.setString("cashAccount", cashAcctName)
                    Main.INSTANCE.settings.setString("cashEmoji", cashAcctEmoji)
                    Main.INSTANCE.settings.setBoolean("isNewUser", false)
                    Main.INSTANCE.settings.setBoolean("bip47CashAcct", false)
                    Main.INSTANCE.uiHelper.setScreen("startRestore")

                } else {
                    GuiUtils.informationalAlert("Crescent Cash", "Verification failed.")
                }
            }
        } else {
            GuiUtils.informationalAlert("Crescent Cash", "Verification failed, try again.")
        }
    }
}