package app.crescentcash.src.thread

import app.crescentcash.src.Main

class ThreadReverseLookupCashAcct(val cashAddr: String, val legacyAddr: String) : Runnable {
    @Volatile
    var response: String = ""
        private set

    override fun run() {
        response = Main.INSTANCE.netHelper.reverseLookupCashAccount(cashAddr, legacyAddr)
    }
}