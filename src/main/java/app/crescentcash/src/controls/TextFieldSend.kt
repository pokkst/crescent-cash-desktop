package app.crescentcash.src.controls

import app.crescentcash.src.Main
import app.crescentcash.src.uri.URIHelper
import com.google.common.util.concurrent.ListenableFuture
import javafx.scene.control.TextField
import javafx.scene.input.Clipboard.getSystemClipboard
import org.bitcoinj.protocols.payments.PaymentProtocolException
import org.bitcoinj.protocols.payments.PaymentSession
import java.util.concurrent.ExecutionException


class TextFieldSend : TextField() {
    override fun paste() {
        super.paste()
        val clipboard = getSystemClipboard()
        if (clipboard.hasString()) {
            this.processScanOrPaste(clipboard.string)
        }
    }

    private fun processScanOrPaste(text: String) {
        val uri = URIHelper(text, true)
        val address = uri.address

        if (address.startsWith("http")) {
            Main.INSTANCE.uiHelper.controller.toAddress!!.text = address
            Main.INSTANCE.uiHelper.controller.sendType!!.value = Main.INSTANCE.uiHelper.controller.displayUnits!!.value
            Main.INSTANCE.settings.setString("sendType", Main.INSTANCE.uiHelper.controller.sendType!!.value)

            this.getBIP70Data(address)
        }
    }

    private fun getBIP70Data(url: String) {
        try {
            val future: ListenableFuture<PaymentSession> = PaymentSession.createFromUrl(url)

            val session = future.get()

            val amountWanted = session.value

            Main.INSTANCE.uiHelper.controller.toAmount!!.text =
                    URIHelper().processSendAmount(amountWanted.toPlainString())
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        } catch (e: PaymentProtocolException) {
            e.printStackTrace()
        }
    }
}