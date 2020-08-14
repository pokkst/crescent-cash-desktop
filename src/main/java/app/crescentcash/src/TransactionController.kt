package app.crescentcash.src

import app.crescentcash.src.ui.UIController
import app.crescentcash.src.ui.UIHelper
import app.crescentcash.src.utils.GuiUtils
import app.crescentcash.src.wallet.WalletHelper
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Label
import org.bitcoinj.utils.MonetaryFormat
import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.text.DecimalFormat

class TransactionController {

    @JvmField
    var overlayUI: UIHelper.OverlayUI<*>? = null
    @FXML
    var transferredAmount: Label? = null
    @FXML
    var feeLabel: Label? = null
    @FXML
    var dateLabel: Label? = null
    @FXML
    var hashLabel: Label? = null
    @FXML
    var confLabel: Label? = null

    fun initialize() {
        val tx = Main.INSTANCE.uiHelper.controller.model.transactions[UIController.transactionSelected]

        val decimalFormatter = DecimalFormat("#,###.########")
        var receivedValueStr = MonetaryFormat.BTC.format(tx.getValue(WalletHelper.wallet)).toString()
        receivedValueStr = receivedValueStr.replace("BCH ", "")
        val amtTransferred = java.lang.Float.parseFloat(receivedValueStr)
        var amountDbl = decimalFormatter.format(Math.abs(amtTransferred)).toDouble()
        var amountStr = ""
        when (Main.INSTANCE.uiHelper.controller.displayUnits!!.value) {
            MonetaryFormat.CODE_BTC -> {
                amountStr = UIHelper.formatBalance(amountDbl, "#,###.########")
            }
            MonetaryFormat.CODE_MBTC -> {
                amountDbl *= 1000
                amountStr = UIHelper.formatBalance(amountDbl, "#,###.#####")
            }
            MonetaryFormat.CODE_UBTC -> {
                amountDbl *= 1000000
                amountStr = UIHelper.formatBalance(amountDbl, "#,###.##")
            }
            "sats" -> {
                amountDbl *= 100000000
                amountStr = UIHelper.formatBalance(amountDbl, "#,###")
            }
        }
        transferredAmount!!.text = "${Main.INSTANCE.uiHelper.controller.displayUnits!!.value} Transferred: $amountStr"

        if (tx.fee != null) {
            var feeValueStr = MonetaryFormat.BTC.format(tx.fee).toString()
            feeValueStr = feeValueStr.replace("BCH ", "")
            val fee = java.lang.Float.parseFloat(feeValueStr)
            feeLabel!!.text = "Fee: " + decimalFormatter.format(fee.toDouble())
        } else {
            feeLabel!!.text = "Fee: n/a"
        }

        dateLabel!!.text = "Tx Date: " + tx.updateTime
        hashLabel!!.text = "Tx Hash: " + tx.hash
        confLabel!!.text = "Confirmations: " + tx.confidence.depthInBlocks
    }

    fun close(event: ActionEvent) {
        overlayUI!!.done()
    }

    fun view(event: ActionEvent) {
        val tx = Main.INSTANCE.uiHelper.controller.model.transactions[UIController.transactionSelected]

        try {
            val coinType = if (Main.useTestnet) "tbch" else "bch"
            Desktop.getDesktop().browse(URI("https://explorer.bitcoin.com/$coinType/tx/" + tx.hash))
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        } catch (e: UnsupportedOperationException) {
            GuiUtils.informationalAlert("Unsupported operation", "This is not supported on this operating system.")
            e.printStackTrace()
        }
    }
}
