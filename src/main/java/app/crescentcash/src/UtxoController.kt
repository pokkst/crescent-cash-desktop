package app.crescentcash.src

import app.crescentcash.src.ui.UIHelper
import app.crescentcash.src.ui.UIHelper.OverlayUI
import app.crescentcash.src.wallet.WalletHelper
import javafx.beans.binding.Bindings
import javafx.collections.FXCollections
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ListView
import javafx.scene.control.SelectionMode
import javafx.scene.control.TextField
import javafx.scene.control.cell.TextFieldListCell
import javafx.scene.input.MouseEvent
import javafx.util.StringConverter
import org.bitcoinj.core.TransactionOutput
import org.bitcoinj.utils.MonetaryFormat


class UtxoController {
    @FXML
    var utxos: ListView<TransactionOutput>? = null
    private val utxosList = FXCollections.observableArrayList<TransactionOutput>()
    @JvmField
    var overlayUI: OverlayUI<*>? = null
    @FXML
    var close: Button? = null
    @FXML
    var privKeyField: TextField? = null

    fun initialize() {
        refreshUtxos()
        utxos!!.selectionModel.selectionMode = SelectionMode.MULTIPLE
    }

    fun close(event: ActionEvent) {
        WalletHelper.selectedUtxos = ArrayList()
        overlayUI!!.done()
    }

    fun selectKey(arg0: MouseEvent) {
        if (utxos!!.selectionModel.selectedItem != null) {
            WalletHelper.selectedUtxos = ArrayList()
            for (x in 0 until utxos!!.selectionModel.selectedIndices.size) {
                val pos = utxos!!.selectionModel.selectedIndices[x]
                WalletHelper.selectedUtxos.add(utxosList[pos])
            }
        }
    }

    private fun refreshUtxos() {
        val listOfUtxos = ArrayList<TransactionOutput>()
        for (utxo in WalletHelper.wallet.calculateAllSpendCandidates(false, true, false)) {
            listOfUtxos.add(utxo)
        }

        utxosList.setAll(listOfUtxos)

        Bindings.bindContent(utxos!!.items, utxosList)

        utxos!!.setCellFactory { param ->
            TextFieldListCell(object : StringConverter<TransactionOutput>() {
                override fun toString(output: TransactionOutput): String {
                    val txHash = output.parentTransactionHash.toString()
                    val index = output.index
                    var amount = output.value.toPlainString()
                    var balBch = java.lang.Double.parseDouble(amount)
                    val model = Main.INSTANCE.uiHelper.controller.model
                    when (Main.INSTANCE.uiHelper.controller.displayUnits!!.value) {
                        MonetaryFormat.CODE_BTC -> {
                            amount = UIHelper.formatBalanceNoUnit(balBch, "#,###.########")
                        }
                        MonetaryFormat.CODE_MBTC -> {
                            balBch *= 1000
                            amount = UIHelper.formatBalanceNoUnit(balBch, "#,###.#####")
                        }
                        MonetaryFormat.CODE_UBTC -> {
                            balBch *= 1000000
                            amount = UIHelper.formatBalanceNoUnit(balBch, "#,###.##")
                        }
                        "sats" -> {
                            balBch *= 100000000
                            amount = UIHelper.formatBalanceNoUnit(balBch, "#,###")
                        }
                    }
                    return "$txHash:$index\n$amount"
                }

                override fun fromString(string: String): TransactionOutput? {
                    return null
                }
            })
        }
    }

    fun openSend(actionEvent: ActionEvent) {
        val controller = Main.INSTANCE.uiHelper.controller
        controller.tabPane!!.selectionModel.select(controller.sendTab)
        overlayUI!!.done()
    }
}
