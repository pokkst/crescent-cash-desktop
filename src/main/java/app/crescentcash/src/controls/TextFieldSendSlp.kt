package app.crescentcash.src.controls

import app.crescentcash.src.Main
import app.crescentcash.src.SendSlpTokenController
import app.crescentcash.src.uri.URIHelperSlp
import app.crescentcash.src.wallet.WalletHelper
import com.google.common.util.concurrent.ListenableFuture
import javafx.scene.control.TextField
import javafx.scene.input.Clipboard.getSystemClipboard
import org.bitcoinj.protocols.payments.PaymentProtocolException
import org.bitcoinj.protocols.payments.PaymentSession
import org.bitcoinj.protocols.payments.slp.SlpPaymentSession
import java.math.BigDecimal
import java.util.concurrent.ExecutionException


class TextFieldSendSlp : TextField() {
    override fun paste() {
        super.paste()
        val clipboard = getSystemClipboard()
        if (clipboard.hasString()) {
            this.processScanOrPaste(clipboard.string)
        }
    }

    private fun processScanOrPaste(text: String) {
        val uri = URIHelperSlp(text, true)
        val address = uri.address

        if (address.startsWith("http")) {
            SendSlpTokenController.INSTANCE.toAddress!!.text = address
            SendSlpTokenController.INSTANCE.sendType!!.value = Main.INSTANCE.uiHelper.controller.displayUnits!!.value
            Main.INSTANCE.settings.setString("sendTypeSlp", SendSlpTokenController.INSTANCE.sendType!!.value)

            if(text.startsWith("simpleledger")) {
                //if(WalletHelper.currentTokenId != "") {
                    this.getSlpBIP70Data(address)
                //}
            } else {
                this.getBIP70Data(address)
            }
        }
    }

    private fun getSlpBIP70Data(url: String) {
        try {
            val future: ListenableFuture<SlpPaymentSession> = SlpPaymentSession.createFromUrl(url)

            val session = future.get()

            val tokenId = session.tokenId
            val tokensWanted = session.totalTokenAmount
            val slpToken = WalletHelper.slpWalletKit?.getSlpToken(tokenId)

            if(slpToken != null) {
                val tokensWantedFormatted: Double =
                    BigDecimal.valueOf(tokensWanted).scaleByPowerOfTen(-slpToken.decimals).toDouble()
                SendSlpTokenController.INSTANCE.toAmount!!.text = if (slpToken.decimals == 0) {
                    tokensWantedFormatted.toInt().toString()
                } else {
                    tokensWantedFormatted.toString()
                }
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        } catch (e: PaymentProtocolException) {
            e.printStackTrace()
        }
    }

    private fun getBIP70Data(url: String) {
        try {
            val future: ListenableFuture<PaymentSession> = PaymentSession.createFromUrl(url)

            val session = future.get()

            val amountWanted = session.value

            SendSlpTokenController.INSTANCE.toAmount!!.text =
                    URIHelperSlp().processSendAmount(amountWanted.toPlainString())
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        } catch (e: PaymentProtocolException) {
            e.printStackTrace()
        }
    }
}