package app.crescentcash.src.thread

import app.crescentcash.src.Main
import org.bitcoinj.core.ECKey

class ThreadCheckForCashAcct(val key: ECKey, val cashAcctTx: String, val name: String) : Runnable {
    @Volatile
    var response: String = ""
        private set

    override fun run() {
        response = Main.INSTANCE.netHelper.checkForCashAccount(key, cashAcctTx, name)
    }
}