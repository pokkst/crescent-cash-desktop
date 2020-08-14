package app.crescentcash.src

import app.crescentcash.src.controls.TextFieldSend
import app.crescentcash.src.controls.TextFieldSendSlp
import app.crescentcash.src.ui.UIHelper
import app.crescentcash.src.ui.UIHelper.OverlayUI
import app.crescentcash.src.uri.URIHelperSlp
import app.crescentcash.src.utils.Constants
import app.crescentcash.src.utils.GuiUtils
import app.crescentcash.src.utils.OpPushParser
import app.crescentcash.src.wallet.WalletHelper
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.vdurmont.emoji.EmojiParser
import javafx.application.Platform
import javafx.beans.value.ObservableValue
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.layout.HBox
import org.apache.http.util.TextUtils
import org.bitcoinj.core.*
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.protocols.payments.PaymentProtocol
import org.bitcoinj.protocols.payments.PaymentProtocolException
import org.bitcoinj.protocols.payments.PaymentSession
import org.bitcoinj.protocols.payments.slp.SlpPaymentProtocol
import org.bitcoinj.protocols.payments.slp.SlpPaymentSession
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.script.ScriptOpCodes
import org.bitcoinj.utils.MonetaryFormat
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import org.bouncycastle.util.encoders.Hex
import java.io.IOException
import java.lang.Exception
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import java.util.concurrent.ExecutionException
import javax.swing.event.ChangeListener


class SendSlpTokenController {
    lateinit var bchToSendSlp: String
    @JvmField
    var overlayUI: OverlayUI<*>? = null
    @FXML
    var close: Button? = null
    @FXML
    var toAddress: TextFieldSendSlp? = null
    @FXML
    var toAmount: TextField? = null
    @FXML
    var title: Label? = null
    @FXML
    var sendType: ComboBox<String>? = null
    @FXML
    var sendHBox: HBox? = null

    fun initialize() {
        INSTANCE = this

        sendType!!.items.clear()
        sendType!!.items.addAll(
            Main.INSTANCE.uiHelper.controller.displayUnits!!.value,
            UIHelper.fiat
        )

        sendType!!.value = Main.INSTANCE.settings.getString("sendTypeSlp", Main.INSTANCE.uiHelper.controller.displayUnits!!.value)

        val tokenId = WalletHelper.currentTokenId
        if(tokenId != "") {
            sendHBox!!.children.remove(sendType)
            title?.text = "Send ${WalletHelper.slpWalletKit!!.getSlpToken(tokenId).ticker}"
        } else {
            title?.text = "Send"
            toAddress?.promptText = "Cash Account or a BCH address"
        }

        if(tokenId != "") {
            val decimalPlaces = WalletHelper.slpWalletKit?.getSlpToken(tokenId)?.decimals
            toAmount?.textProperty()
                ?.addListener { observable: ObservableValue<out String>?, oldStr: String?, newStr: String? ->
                    if (newStr != null) {
                        if (!newStr.matches("-?\\d*(\\.\\d{0,$decimalPlaces})?".toRegex())) {
                            toAmount?.text = oldStr
                        }
                    }
                }
        }
    }

    fun close(event: ActionEvent) {
        this.close()
    }

    fun setMaxCoins(actionEvent: ActionEvent) {
        val tokenId = WalletHelper.currentTokenId
        if(tokenId != "") {
            val slpToken = WalletHelper.slpWalletKit?.getSlpToken(tokenId)
            if(slpToken?.decimals == 0) {
                toAmount?.text = WalletHelper.slpWalletKit!!.slpBalances[WalletHelper.currentTokenPosition - 1].balance.toInt().toString()
            } else {
                toAmount?.text = WalletHelper.slpWalletKit!!.slpBalances[WalletHelper.currentTokenPosition - 1].balance.toString()
            }
        } else {
            var balInBch = WalletHelper.slpWalletKit!!.wallet.getBalance(Wallet.BalanceType.ESTIMATED).toPlainString()
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

            toAmount!!.text = balInBch
        }
    }

    fun clearSendFields(actionEvent: ActionEvent) {
        this.clear()
    }

    fun send(actionEvent: ActionEvent) {
        val address = toAddress!!.text
        if(!TextUtils.isEmpty(toAddress?.text) && !TextUtils.isEmpty(toAmount?.text)) {
            val tokenId = WalletHelper.currentTokenId

            if(tokenId != "") {
                if(toAddress?.text.toString().startsWith("http")) {
                    this.processSlpBIP70(toAddress?.text.toString())
                } else {
                    try {
                        val tx = WalletHelper.slpWalletKit?.createSlpTransaction(address, tokenId, java.lang.Double.parseDouble(toAmount?.text), null)
                        WalletHelper.slpWalletKit?.broadcastSlpTransaction(tx)
                        Main.INSTANCE.uiHelper.refresh()
                        this.clear()
                        this.close()
                    } catch (e: InsufficientMoneyException) {
                        e.message?.let { this.throwSendError(it) }
                    } catch (e: Wallet.CouldNotAdjustDownwards) {
                        this.throwSendError("Not enough BCH for fee!")
                    } catch (e: Wallet.ExceededMaxTransactionSize) {
                        this.throwSendError("Transaction is too large!")
                    } catch (e: NullPointerException) {
                        this.throwSendError("Cash Account not found.")
                    } catch (e: IllegalArgumentException) {
                        e.message?.let { this.throwSendError(it) }
                    } catch (e: AddressFormatException) {
                        this.throwSendError("Invalid address!")
                    }
                }
            } else {
                this.send()
            }
        } else {
            if(TextUtils.isEmpty(address)) {
                this.throwSendError("Enter an address")
            } else if(TextUtils.isEmpty(toAmount?.text)) {
                this.throwSendError("Enter an amount")
            }
        }
    }

    fun send() {
        val amount = toAmount!!.text
        val amtDblToFrmt: Double

        amtDblToFrmt = if (!TextUtils.isEmpty(amount)) {
            java.lang.Double.parseDouble(amount)
        } else {
            0.0
        }

        val formatter = DecimalFormat("#.########", DecimalFormatSymbols(Locale.US))
        val amtDblFrmt = formatter.format(amtDblToFrmt)
        var amtToSend = java.lang.Double.parseDouble(amtDblFrmt)

        if (sendType!!.value == "USD" || sendType!!.value == "EUR" || sendType!!.value == "AUD") {
            object : Thread() {
                override fun run() {
                    when (sendType!!.value) {
                        "USD" -> {
                            val usdToBch = amtToSend / Main.INSTANCE.netHelper.price
                            bchToSendSlp = formatter.format(usdToBch)
                            processPayment()
                        }
                        "EUR" -> {
                            val usdToBch = amtToSend / Main.INSTANCE.netHelper.priceEur
                            bchToSendSlp = formatter.format(usdToBch)
                            processPayment()
                        }
                        "AUD" -> {
                            val usdToBch = amtToSend / Main.INSTANCE.netHelper.priceAud
                            bchToSendSlp = formatter.format(usdToBch)
                            processPayment()
                        }
                    }
                }
            }.start()
        } else {
            when (sendType!!.value) {
                MonetaryFormat.CODE_BTC -> {
                    println("No formatting needed")
                    bchToSendSlp = formatter.format(amtToSend)
                }
                MonetaryFormat.CODE_MBTC -> {
                    val mBTCToSend = amtToSend
                    amtToSend = mBTCToSend / 1000.0
                    bchToSendSlp = formatter.format(amtToSend)
                }
                MonetaryFormat.CODE_UBTC -> {
                    val uBTCToSend = amtToSend
                    amtToSend = uBTCToSend / 1000000.0
                    bchToSendSlp = formatter.format(amtToSend)
                }
                "sats" -> {
                    val satsToSend = amtToSend.toLong()
                    amtToSend = satsToSend / 100000000.0
                    bchToSendSlp = formatter.format(amtToSend)
                }
            }

            processPayment()
        }
    }

    private fun processPayment() {
        println(bchToSendSlp)
        if (!TextUtils.isEmpty(toAmount!!.text) && !TextUtils.isEmpty(bchToSendSlp) && toAmount!!.text != null && bchToSendSlp != "") {
            val amtCheckVal = java.lang.Double.parseDouble(Coin.parseCoin(bchToSendSlp).toPlainString())

            if (TextUtils.isEmpty(toAddress!!.text)) {
                throwSendError("Please enter a recipient.")
            } else if (amtCheckVal < 0.00001) {
                throwSendError("Enter a valid amount. Minimum is 0.00001 BCH")
            } else if (Main.INSTANCE.walletHelper.getBalance(WalletHelper.slpWalletKit!!.wallet).isLessThan(
                    Coin.parseCoin(
                        amtCheckVal.toString()
                    )
                )
            ) {
                throwSendError("Insufficient balance!")
            } else {
                val recipientText = toAddress!!.text
                val uri = URIHelperSlp(recipientText, false)
                val address = uri.address

                val amount = if (uri.amount != "null")
                    uri.amount
                else
                    "null"

                if (amount != "null")
                    bchToSendSlp = amount

                if (address.startsWith("http")) {
                    toAddress!!.text = address
                    sendType!!.value = Main.INSTANCE.uiHelper.controller.displayUnits!!.value
                    Main.INSTANCE.settings.setString("sendTypeSlp", sendType!!.value)

                    this.processBIP70(address)
                } else if (address.startsWith("+")) {
                    val rawNumber = URIHelperSlp().getRawPhoneNumber(address)
                    val numberString = rawNumber.replace("+", "")
                    var amtToSats = java.lang.Double.parseDouble(bchToSendSlp)
                    val satFormatter = DecimalFormat("#", DecimalFormatSymbols(Locale.US))
                    amtToSats *= 100000000
                    val sats = satFormatter.format(amtToSats).toInt()
                    val url = "https://pay.cointext.io/p/$numberString/$sats"
                    println(url)
                    this.processBIP70(url)
                } else {
                    if (address.contains("#")) {
                        val toAddressFixed = EmojiParser.removeAllEmojis(address)
                        val toAddressStripped = toAddressFixed.replace("; ", "")
                        sendCoins(bchToSendSlp, toAddressStripped)
                    } else {
                        try {
                            sendCoins(bchToSendSlp, address)
                        } catch (e: AddressFormatException) {
                            e.printStackTrace()
                            throwSendError("Invalid address!")
                        }
                    }
                }
            }
        } else {
            throwSendError("Please enter an amount.")
        }
    }

    private fun sendCoins(amount: String, toAddress: String) {
        if (toAddress.contains("#") || Address.isValidCashAddr(WalletHelper.parameters, toAddress) || Address.isValidLegacyAddress(
                WalletHelper.parameters, toAddress) && (!LegacyAddress.fromBase58(MainNetParams.get(), toAddress).p2sh || WalletHelper.allowLegacyP2SH)) {
            object : Thread() {
                override fun run() {
                    val recipientAddress = toAddress
                    val coinAmt = Coin.parseCoin(amount)

                    if (coinAmt.getValue() > 0.0) {
                        try {
                            val req: SendRequest

                            if (WalletHelper.useTor) {
                                req = if (coinAmt == Main.INSTANCE.walletHelper.getBalance(WalletHelper.slpWalletKit!!.wallet)) {
                                    SendRequest.emptyWallet(
                                        WalletHelper.parameters, recipientAddress,
                                        WalletHelper.torProxy
                                    )
                                } else {
                                    SendRequest.to(
                                        WalletHelper.parameters, recipientAddress, coinAmt,
                                        WalletHelper.torProxy
                                    )
                                }

                            } else {
                                req = if (coinAmt == Main.INSTANCE.walletHelper.getBalance(WalletHelper.slpWalletKit!!.wallet)) {
                                    SendRequest.emptyWallet(WalletHelper.parameters, recipientAddress)
                                } else {
                                    SendRequest.to(WalletHelper.parameters, recipientAddress, coinAmt)
                                }
                            }

                            req.allowUnconfirmed()
                            req.ensureMinRequiredFee = false
                            req.feePerKb = Coin.valueOf(java.lang.Long.parseLong(1.toString() + "") * 1000L)
                            val tx = WalletHelper.slpWalletKit!!.wallet.sendCoinsOffline(req)
                            val txHexBytes = Hex.encode(tx.bitcoinSerialize())
                            val txHex = String(txHexBytes, StandardCharsets.UTF_8)
                            Platform.runLater { WalletHelper.slpWalletKit!!.broadcastSlpTransaction(tx) }

                            if (!WalletHelper.useTor) {
                                Main.INSTANCE.netHelper.broadcastTransaction(
                                    txHex,
                                    "https://rest.bitcoin.com/v2/rawtransactions/sendRawTransaction"
                                )
                            }

                            Main.INSTANCE.netHelper.broadcastTransaction(
                                txHex,
                                "https://rest.imaginary.cash/v2/rawtransactions/sendRawTransaction"
                            )

                            Platform.runLater {
                                clear()
                                close()
                            }
                        } catch (e: InsufficientMoneyException) {
                            e.printStackTrace()
                            e.message?.let { throwSendError(it) }
                        } catch (e: Wallet.CouldNotAdjustDownwards) {
                            e.printStackTrace()
                            throwSendError("Not enough BCH for fee!")
                        } catch (e: Wallet.ExceededMaxTransactionSize) {
                            e.printStackTrace()
                            throwSendError("Transaction is too large!")
                        } catch (e: NullPointerException) {
                            e.printStackTrace()
                            throwSendError("Cash Account not found.")
                        }

                    }
                }
            }.start()
        } else if (!Address.isValidCashAddr(WalletHelper.parameters, toAddress) || !Address.isValidLegacyAddress(
                WalletHelper.parameters,
                toAddress
            )
        ) {
            throwSendError("Invalid address!")
        }
    }

    private fun processBIP70(url: String) {
        try {
            //for some reason CoinText payments can't be verified on Desktop, so we disable verifyPki here.
            val future: ListenableFuture<PaymentSession> = PaymentSession.createFromUrl(url, false)

            val session = future.get()
            if (session.isExpired) {
                throwSendError("Session expired!")
            }

            val req = session.sendRequest
            req.allowUnconfirmed()
            WalletHelper.slpWalletKit!!.wallet.completeTx(req)

            val ack = session.sendPayment(ImmutableList.of(req.tx), WalletHelper.slpWalletKit!!.wallet.freshReceiveAddress(), null)
            if (ack != null) {
                Futures.addCallback<PaymentProtocol.Ack>(ack, object : FutureCallback<PaymentProtocol.Ack> {
                    override fun onSuccess(ack: PaymentProtocol.Ack?) {
                        WalletHelper.slpWalletKit!!.wallet.commitTx(req.tx)
                        clear()
                    }

                    override fun onFailure(throwable: Throwable) {
                        throwSendError("An error occurred.")
                    }
                }, MoreExecutors.directExecutor())
            }
        } catch (e: PaymentProtocolException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        } catch (e: InsufficientMoneyException) {
            throwSendError("You do not have enough BCH!")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun processSlpBIP70(url: String) {
        try {
            val future: ListenableFuture<SlpPaymentSession> = SlpPaymentSession.createFromUrl(url)

            val session = future.get()
            if (session.isExpired) {
                throwSendError("Invoice expired!")
                return
            }

            val tokenId = session.tokenId
            val slpToken = WalletHelper.slpWalletKit?.getSlpToken(tokenId)
            if(slpToken != null) {
                val rawTokens = session.rawTokenAmounts
                val addresses = session.getSlpAddresses(WalletHelper.parameters)
                val tx = WalletHelper.slpWalletKit?.createSlpTransactionBip70(tokenId, null, rawTokens, addresses, session)
                val ack = session.sendPayment(ImmutableList.of(tx!!), WalletHelper.slpWalletKit?.wallet?.freshReceiveAddress(), null)
                if (ack != null) {
                    Futures.addCallback<SlpPaymentProtocol.Ack>(ack, object : FutureCallback<SlpPaymentProtocol.Ack> {
                        override fun onSuccess(ack: SlpPaymentProtocol.Ack?) {
                            clear()
                        }

                        override fun onFailure(throwable: Throwable) {
                            throwSendError("An error occurred.")
                        }
                    }, MoreExecutors.directExecutor())
                }
            } else {
                this.throwSendError("Unknown token!")
            }
        } catch (e: InsufficientMoneyException) {
            e.message?.let { this.throwSendError(it) }
        } catch (e: Wallet.CouldNotAdjustDownwards) {
            this.throwSendError("Not enough BCH for fee!")
        } catch (e: Wallet.ExceededMaxTransactionSize) {
            this.throwSendError("Transaction is too large!")
        } catch (e: IllegalArgumentException) {
            e.message?.let { this.throwSendError(it) }
        } catch (e: AddressFormatException) {
            this.throwSendError("Invalid address!")
        } catch (e: Exception) {
            e.message?.let { this.throwSendError(it) }
        }
    }

    fun setSendType(action: ActionEvent) {
        println(sendType!!.value)
        Main.INSTANCE.settings.setString("sendTypeSlp", sendType!!.value)
    }

    fun throwSendError(text: String) {
        GuiUtils.informationalAlert("Crescent Cash", text)
        this.clear()
    }

    private fun clear() {
        toAddress?.text = null
        toAmount?.text = null
    }

    private fun close() {
        overlayUI!!.done()
    }

    companion object {
        lateinit var INSTANCE: SendSlpTokenController
    }
}