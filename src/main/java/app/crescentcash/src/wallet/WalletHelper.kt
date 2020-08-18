package app.crescentcash.src.wallet

import app.crescentcash.src.Main
import app.crescentcash.src.net.NetHelper
import app.crescentcash.src.ui.UIHelper
import app.crescentcash.src.uri.URIHelper
import app.crescentcash.src.utils.Constants
import app.crescentcash.src.utils.GuiUtils
import app.crescentcash.src.utils.OpPushParser
import app.crescentcash.src.utils.UtxoUtil
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.protobuf.ByteString
import com.vdurmont.emoji.EmojiParser
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import org.apache.http.util.TextUtils
import org.bitcoinj.core.*
import org.bitcoinj.core.bip47.BIP47Channel
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.crypto.BIP38PrivateKey
import org.bitcoinj.crypto.KeyCrypterScrypt
import org.bitcoinj.kits.BIP47AppKit
import org.bitcoinj.kits.SlpAppKit
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.net.discovery.PeerDiscovery
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.protocols.payments.PaymentProtocol
import org.bitcoinj.protocols.payments.PaymentProtocolException
import org.bitcoinj.protocols.payments.PaymentSession
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.script.ScriptOpCodes
import org.bitcoinj.utils.BriefLogFormatter
import org.bitcoinj.utils.MonetaryFormat
import org.bitcoinj.utils.Threading
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Protos
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import org.bouncycastle.util.encoders.Hex
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class WalletHelper {

    private val uiHelper: UIHelper = Main.INSTANCE.uiHelper

    init {
        BriefLogFormatter.init()

        if (useTor) {
            torProxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9050))
        }
    }

    lateinit var bchToSend: String

    fun setupWalletKit(seed: DeterministicSeed?, cashAcctName: String, verifyingRestore: Boolean, upgradeToBip47: Boolean) {
        walletKit = BIP47AppKit().initialize(parameters, File(System.getProperty("user.home")), Constants.WALLET_NAME, seed)
        walletKit?.setUseTor(useTor)
        walletKit?.setOnReceiveTxRunnable {
            if (isSynced) {
                uiHelper.refresh()
            }
        }
        isSynced = false
        walletKit?.peerGroup?.setBloomFilterFalsePositiveRate(0.01)
        walletKit?.peerGroup?.isBloomFilteringEnabled = true
        Platform.runLater { Main.INSTANCE.walletHelper.onBitcoinSetup() }
        uiHelper.controller.xpubLabel!!.text = wallet.watchingKey.serializePubB58(parameters)
        if (Main.isNewUser) {
            val paymentCode = walletKit!!.paymentCode
            val address = wallet.currentReceiveAddress().toString()
            Main.INSTANCE.netHelper.registerCashAccount(cashAcctName, paymentCode, address)
            Main.INSTANCE.settings.setBoolean("usingNewBlockStore", true)
            Main.INSTANCE.settings.setBoolean("usingNewBlockStoreSlp", true)
            setupSlpWalletKit(wallet.keyChainSeed)
        } else {
            val keychainSeed = wallet.keyChainSeed

            if (!keychainSeed.isEncrypted) {
                /*
                If the saved setting we got is true, but our wallet is unencrypted, then we set our saved setting to false.
                 */
                if (encrypted) {
                    encrypted = false
                    //uiHelper.controller.encryptionCheckbox!!.isSelected = encrypted
                    Main.INSTANCE.settings.setBoolean("useEncryption", encrypted)
                }

                setupSlpWalletKit(wallet.keyChainSeed)
            }

            if (!verifyingRestore) {
                if (seed == null)
                    uiHelper.refresh()
            } else {
                if(upgradeToBip47) {
                    val paymentCode = walletKit!!.paymentCode
                    val address = wallet.currentReceiveAddress().toString()
                    Main.INSTANCE.settings.setBoolean("isNewUser", false)
                    Main.INSTANCE.netHelper.registerCashAccount(cashAcctName.split("#")[0], paymentCode, address)
                }

                Main.INSTANCE.settings.setBoolean("usingNewBlockStore", true)
                Main.INSTANCE.settings.setBoolean("usingNewBlockStoreSlp", true)
            }
        }

        wallet.addCoinsSentEventListener { wallet, transaction, prevCoin, newCoin ->
            if (isSynced) {
                Main.INSTANCE.uiHelper.refresh()
                Main.INSTANCE.uiHelper.setSendingDisplay()
                playAudio("send_coins.mp3")
            }
        }

        wallet.addCoinsReceivedEventListener { wallet, transaction, prevCoin, newCoin ->
            if (isSynced) {
                Main.INSTANCE.uiHelper.refresh()

                if (transaction.purpose == Transaction.Purpose.UNKNOWN) {
                    playAudio("coins_received.mp3")
                }
            }
        }

        refreshTimer.schedule(object : TimerTask() {
            override fun run() {
                Main.INSTANCE.uiHelper.refresh()
            }
        }, 0, 60000)

        this.setupNodeOnStart()

        walletKit!!.setDownloadProgressTracker(object : DownloadProgressTracker() {
            override fun progress(pct: Double, blocksLeft: Int, date: Date) {
                super.progress(pct, blocksLeft, date)
                val pctInt = pct.toInt()

                if (pctInt < 100) {
                    Platform.runLater {
                        Main.INSTANCE.uiHelper.controller.connectionStatus!!.text = "Syncing... $pctInt%"
                    }
                } else {
                    isSynced = true
                    Platform.runLater {
                        Main.INSTANCE.uiHelper.controller.connectionStatus!!.text = ""
                    }
                }
            }

            override fun doneDownload() {
                super.doneDownload()
                isSynced = true
                Platform.runLater {
                    Main.INSTANCE.uiHelper.controller.connectionStatus!!.text = ""
                }
            }
        })

        val checkpointsInputStream = Main::class.java.getResourceAsStream("checkpoints.txt")
        walletKit!!.setCheckpoints(checkpointsInputStream)
        walletKit!!.startAsync()
    }

    fun setupSlpWalletKit(seed: DeterministicSeed) {
        slpWalletKit = SlpAppKit().initialize(parameters, File(System.getProperty("user.home")), "users_slp_wallet", seed)
        slpWalletKit?.setUseTor(useTor)
        uiHelper.refresh()
        slpWalletKit!!.setDownloadProgressTracker(object : DownloadProgressTracker() {
            override fun progress(pct: Double, blocksSoFar: Int, date: Date) {
                super.progress(pct, blocksSoFar, date)
                val percentage = pct.toInt()
                /*activity.runOnUiThread {
                activity.displayPercentageSlp(percentage)
            }*/
                //TODO display SLP connection status somewhere
                println(percentage)
            }

            override fun doneDownload() {
                super.doneDownload()
                /*activity.runOnUiThread {
                activity.displayDownloadContentSlp(false)
                activity.refresh()
            }*/
                //TODO display SLP connection status somewhere
                println("SLP WALLET SYNCED")
            }
        })
        setupSlpNodeOnStart()
        val checkpointsInputStream = Main::class.java.getResourceAsStream("checkpoints.txt")
        slpWalletKit!!.setCheckpoints(checkpointsInputStream)
        this.setupSlpWalletListeners(getSlpWallet())
        slpWalletKit!!.startAsync()
    }

    private fun setupSlpWalletListeners(wallet: Wallet) {
        getSlpWallet().addCoinsSentEventListener { wallet12, transaction, prevCoin, newCoin ->
            if (isSynced) {
                Main.INSTANCE.uiHelper.refresh()
                Main.INSTANCE.uiHelper.setSendingDisplay()
                playAudio("send_coins.mp3")
            }
        }

        getSlpWallet().addCoinsReceivedEventListener { wallet12, transaction, prevCoin, newCoin ->
            if (isSynced) {
                Main.INSTANCE.uiHelper.refresh()

                if (transaction.purpose == Transaction.Purpose.UNKNOWN) {
                    playAudio("coins_received.mp3")
                }
            }
        }
    }

    private fun onBitcoinSetup() {
        uiHelper.controller.txGrid!!.styleClass.add("tx_grid_pane")

        uiHelper.controller.receiveTab!!.setOnSelectionChanged {
            if (uiHelper.controller.receiveTab!!.isSelected) {
                val address = walletKit?.paymentCode
                Platform.runLater { uiHelper.updateAddress(address) }
                Main.INSTANCE.uiHelper.refresh()
            }
        }

        uiHelper.controller.sendTab!!.setOnSelectionChanged {
            if (uiHelper.controller.sendTab!!.isSelected) {
                if (addOpReturn) {
                    if (!uiHelper.controller.sendTabVbox!!.children.contains(uiHelper.controller.opReturnBox)) {
                        uiHelper.controller.sendTabVbox!!.children.add(1, uiHelper.controller.opReturnBox)
                    }
                } else {
                    uiHelper.controller.sendTabVbox!!.children.remove(uiHelper.controller.opReturnBox)
                }

                uiHelper.controller.clearUtxosBtn!!.isVisible = selectedUtxos.size > 0

                Main.INSTANCE.uiHelper.refresh()
            }
        }

        uiHelper.controller.settingsTab!!.setOnSelectionChanged {
            if (uiHelper.controller.settingsTab!!.isSelected) {
                Main.INSTANCE.uiHelper.refresh()
            }
        }

        uiHelper.controller.historyTab!!.setOnSelectionChanged {
            if (uiHelper.controller.historyTab!!.isSelected) {
                Main.INSTANCE.uiHelper.refresh()
            }
        }

        uiHelper.controller.toolsTab!!.setOnSelectionChanged {
            if (uiHelper.controller.toolsTab!!.isSelected) {
                Main.INSTANCE.uiHelper.refresh()
                uiHelper.controller.sweepWalletVbox!!.children.remove(uiHelper.controller.bip38PasswordText)
            }
        }

        uiHelper.controller.slpTokensTab!!.setOnSelectionChanged {
            if (uiHelper.controller.slpTokensTab!!.isSelected) {
                Main.INSTANCE.uiHelper.refresh()
            }
        }

        uiHelper.controller.newSeed!!.isDisable = false
        uiHelper.controller.recoverBtn!!.isDisable = false
    }

    fun getBalance(wallet: Wallet): Coin {
        return wallet.getBalance(Wallet.BalanceType.ESTIMATED)
    }

    fun send() {
        val amount = uiHelper.controller.toAmount!!.text
        val amtDblToFrmt: Double

        amtDblToFrmt = if (!TextUtils.isEmpty(amount)) {
            java.lang.Double.parseDouble(amount)
        } else {
            0.0
        }

        val formatter = DecimalFormat("#.########", DecimalFormatSymbols(Locale.US))
        val amtDblFrmt = formatter.format(amtDblToFrmt)
        var amtToSend = java.lang.Double.parseDouble(amtDblFrmt)

        if (uiHelper.controller.sendType!!.value == "USD" || uiHelper.controller.sendType!!.value == "EUR" || uiHelper.controller.sendType!!.value == "AUD") {
            object : Thread() {
                override fun run() {
                    if (uiHelper.controller.sendType!!.value == "USD") {
                        val usdToBch = amtToSend / Main.INSTANCE.netHelper.price
                        bchToSend = formatter.format(usdToBch)
                        processPayment()
                    }

                    if (uiHelper.controller.sendType!!.value == "EUR") {
                        val usdToBch = amtToSend / Main.INSTANCE.netHelper.priceEur
                        bchToSend = formatter.format(usdToBch)
                        processPayment()
                    }

                    if (uiHelper.controller.sendType!!.value == "AUD") {
                        val usdToBch = amtToSend / Main.INSTANCE.netHelper.priceAud
                        bchToSend = formatter.format(usdToBch)
                        processPayment()
                    }
                }
            }.start()
        } else {
            if (uiHelper.controller.sendType!!.value == MonetaryFormat.CODE_BTC) {
                println("No formatting needed")
                bchToSend = formatter.format(amtToSend)
            }

            if (uiHelper.controller.sendType!!.value == MonetaryFormat.CODE_MBTC) {
                val mBTCToSend = amtToSend
                amtToSend = mBTCToSend / 1000.0
                bchToSend = formatter.format(amtToSend)
            }

            if (uiHelper.controller.sendType!!.value == MonetaryFormat.CODE_UBTC) {
                val uBTCToSend = amtToSend
                amtToSend = uBTCToSend / 1000000.0
                bchToSend = formatter.format(amtToSend)
            }

            if (uiHelper.controller.sendType!!.value == "sats") {
                val satsToSend = amtToSend.toLong()
                amtToSend = satsToSend / 100000000.0
                bchToSend = formatter.format(amtToSend)
            }

            processPayment()
        }
    }

    private fun processPayment() {
        println(bchToSend)
        if (!TextUtils.isEmpty(uiHelper.controller.toAmount!!.text) && !TextUtils.isEmpty(bchToSend) && Main.INSTANCE.uiHelper.controller.toAmount!!.text != null && bchToSend != "") {
            val amtCheckVal = java.lang.Double.parseDouble(Coin.parseCoin(bchToSend).toPlainString())

            if (TextUtils.isEmpty(uiHelper.controller.toAddress!!.text)) {
                throwSendError("Please enter a recipient.")
            } else if (amtCheckVal < 0.00001) {
                throwSendError("Enter a valid amount. Minimum is 0.00001 BCH")
            } else if (Main.INSTANCE.walletHelper.getBalance(WalletHelper.wallet).isLessThan(
                    Coin.parseCoin(
                        amtCheckVal.toString()
                    )
                )
            ) {
                throwSendError("Insufficient balance!")
            } else {
                val recipientText = uiHelper.controller.toAddress!!.text
                val uri = URIHelper(recipientText, false)
                val address = uri.address

                val amount = if (uri.amount != "null")
                    uri.amount
                else
                    "null"

                if (amount != "null")
                    bchToSend = amount

                if (address.startsWith("http")) {
                    Main.INSTANCE.uiHelper.controller.toAddress!!.text = address
                    Main.INSTANCE.uiHelper.controller.sendType!!.value =
                        Main.INSTANCE.uiHelper.controller.displayUnits!!.value
                    Main.INSTANCE.settings.setString("sendType", Main.INSTANCE.uiHelper.controller.sendType!!.value)

                    this.processBIP70(address)
                } else if (address.startsWith("+")) {
                    val rawNumber = URIHelper().getRawPhoneNumber(address)
                    val numberString = rawNumber.replace("+", "")
                    var amtToSats = java.lang.Double.parseDouble(bchToSend)
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
                        sendCoins(bchToSend, toAddressStripped)
                    } else {
                        try {
                            sendCoins(bchToSend, address)
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

    fun throwSendError(text: String) {
        GuiUtils.informationalAlert("Crescent Cash", text)
        Main.INSTANCE.uiHelper.setEnableSending()
    }

    private fun sendCoins(amount: String, toAddress: String) {
        object : Thread() {
            override fun run() {
                if (toAddress.contains("#")) {
                    var address: String? = null
                    try {
                        address = org.bitcoinj.net.NetHelper().getCashAccountAddress(parameters, toAddress)
                    } catch (e: Exception) {
                        throwSendError("Error getting Cash Account")
                    }

                    if(address != null) {
                        if (Address.isValidPaymentCode(address)) {
                            val canSend = walletKit?.canSendToPaymentCode(address)
                            if(canSend != null) {
                                if(canSend) {
                                    this@WalletHelper.attemptBip47Payment(amount, address)
                                } else {
                                    val notification = walletKit?.makeNotificationTransaction(address, true)
                                    walletKit?.broadcastTransaction(notification?.tx)
                                    walletKit?.putPaymenCodeStatusSent(address, notification?.tx)
                                    this@WalletHelper.attemptBip47Payment(amount, address)
                                }
                            }
                        } else if (Address.isValidCashAddr(parameters, address) || Address.isValidLegacyAddress(parameters, address) && (!LegacyAddress.fromBase58(parameters, address).p2sh || allowLegacyP2SH)) {
                            this@WalletHelper.finalizeTransaction(amount, address)
                        }
                    } else {
                        throwSendError("Invalid address!")
                    }
                } else {
                    if (Address.isValidPaymentCode(toAddress)) {
                        val canSend = walletKit?.canSendToPaymentCode(toAddress)
                        if(canSend != null) {
                            if(canSend) {
                                this@WalletHelper.attemptBip47Payment(amount, toAddress)
                            } else {
                                val notification = walletKit?.makeNotificationTransaction(toAddress, true)
                                walletKit?.broadcastTransaction(notification?.tx)
                                walletKit?.putPaymenCodeStatusSent(toAddress, notification?.tx)
                                this@WalletHelper.attemptBip47Payment(amount, toAddress)
                            }
                        }
                    } else if(Address.isValidCashAddr(parameters, toAddress) || Address.isValidLegacyAddress(parameters, toAddress) && (!LegacyAddress.fromBase58(parameters, toAddress).p2sh || allowLegacyP2SH)) {
                        this@WalletHelper.finalizeTransaction(amount, toAddress)
                    }
                }
            }
        }.start()
    }

    private fun attemptBip47Payment(amount: String, paymentCode: String) {
        val paymentChannel: BIP47Channel? = walletKit?.getBip47MetaForPaymentCode(paymentCode)
        var depositAddress: String? = null
        if (paymentChannel != null) {
            if(paymentChannel.isNotificationTransactionSent) {
                depositAddress = walletKit?.getCurrentOutgoingAddress(paymentChannel)
                if(depositAddress != null) {
                    println("Received user's deposit address $depositAddress")
                    paymentChannel.incrementOutgoingIndex()
                    walletKit?.saveBip47MetaData()
                    this.finalizeTransaction(amount, depositAddress)
                }
            } else {
                val notification = walletKit?.makeNotificationTransaction(paymentCode, true)
                walletKit?.broadcastTransaction(notification?.tx)
                walletKit?.putPaymenCodeStatusSent(paymentCode, notification?.tx)
                this.attemptBip47Payment(amount, paymentCode)
            }
        }
    }

    private fun finalizeTransaction(amount: String, recipientAddress: String) {
        val coinAmt = Coin.parseCoin(amount)

        if (coinAmt.getValue() > 0.0) {
            try {
                val cachedAddOpReturn = addOpReturn
                val req: SendRequest

                if (useTor) {
                    if (selectedUtxos.size == 0) {
                        if (coinAmt == getBalance(walletKit!!.getvWallet())) {
                            addOpReturn = false
                            req = SendRequest.emptyWallet(parameters, recipientAddress, torProxy)
                        } else {
                            req = SendRequest.to(parameters, recipientAddress, coinAmt, torProxy)
                        }
                    } else {
                        if (coinAmt == getMaxValueOfSelectedUtxos()) {
                            addOpReturn = false
                            req = SendRequest.emptyWallet(parameters, recipientAddress, torProxy)
                        } else {
                            req = SendRequest.to(parameters, recipientAddress, coinAmt, torProxy)
                        }
                    }
                } else {
                    if (selectedUtxos.size == 0) {
                        if (coinAmt == getBalance(walletKit!!.getvWallet())) {
                            addOpReturn = false
                            req = SendRequest.emptyWallet(parameters, recipientAddress)
                        } else {
                            req = SendRequest.to(parameters, recipientAddress, coinAmt)
                        }
                    } else {
                        if (coinAmt == getMaxValueOfSelectedUtxos()) {
                            addOpReturn = false
                            req = SendRequest.emptyWallet(parameters, recipientAddress)
                        } else {
                            req = SendRequest.to(parameters, recipientAddress, coinAmt)
                        }
                    }
                }

                req.ensureMinRequiredFee = false
                req.utxos = selectedUtxos
                req.feePerKb = Coin.valueOf(java.lang.Long.parseLong(1.toString() + "") * 1000L)

                if (addOpReturn) {
                    if (uiHelper.controller.sendTabVbox!!.children.contains(uiHelper.controller.opReturnBox)) {
                        val opReturnText = uiHelper.controller.opReturnText!!.text.toString()
                        if (opReturnText.isNotEmpty()) {
                            var scriptBuilder = ScriptBuilder().op(ScriptOpCodes.OP_RETURN)
                            val opPushParser = OpPushParser(opReturnText)
                            for (x in 0 until opPushParser.pushData.size) {
                                val opPush = opPushParser.pushData[x]

                                if (opPush.binaryData.size <= Constants.MAX_OP_RETURN) {
                                    scriptBuilder = scriptBuilder.data(opPush.binaryData)
                                }
                            }

                            req.tx.addOutput(Coin.ZERO, scriptBuilder.build())
                        }
                    }
                }

                addOpReturn = cachedAddOpReturn
                val tx = walletKit!!.getvWallet().sendCoinsOffline(req)
                val txHexBytes = Hex.encode(tx.bitcoinSerialize())
                val txHex = String(txHexBytes, StandardCharsets.UTF_8)
                broadcastTxToPeers(tx)

                if (!useTor) {
                    Main.INSTANCE.netHelper.broadcastTransaction(
                        txHex,
                        "https://rest.bitcoin.com/v2/rawtransactions/sendRawTransaction"
                    )
                }

                Main.INSTANCE.netHelper.broadcastTransaction(
                    txHex,
                    "https://rest.imaginary.cash/v2/rawtransactions/sendRawTransaction"
                )
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

    private fun processBIP70(url: String) {
        try {
            //for some reason CoinText payments can't be verified on Desktop, so we disable verifyPki here.
            val future: ListenableFuture<PaymentSession> = PaymentSession.createFromUrl(url, false)

            val session = future.get()
            if (session.isExpired) {
                GuiUtils.informationalAlert("Crescent Cash", "Session expired!")
                Main.INSTANCE.uiHelper.setEnableSending()
            }

            val req = session.sendRequest
            wallet.completeTx(req)

            val ack = session.sendPayment(ImmutableList.of(req.tx), wallet.freshReceiveAddress(), null)
            if (ack != null) {
                Futures.addCallback<PaymentProtocol.Ack>(ack, object : FutureCallback<PaymentProtocol.Ack> {
                    override fun onSuccess(ack: PaymentProtocol.Ack?) {
                        wallet.commitTx(req.tx)
                        Main.INSTANCE.uiHelper.setSendingDisplay()
                    }

                    override fun onFailure(throwable: Throwable) {
                        GuiUtils.informationalAlert("Crescent Cash", "An error occurred.")
                        Main.INSTANCE.uiHelper.setEnableSending()
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
            GuiUtils.informationalAlert("Crescent Cash", "You do not have enough BCH!")
            Main.INSTANCE.uiHelper.setEnableSending()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun isAddressMine(address: String): Boolean {
        val addressObj = if(Address.isValidCashAddr(parameters, address))
            CashAddressFactory.create().getFromFormattedAddress(parameters, address)
        else
            LegacyAddress.fromBase58(parameters, address)

        return wallet.isPubKeyHashMine(addressObj.hash)
    }

    fun broadcastTxToPeers(tx: Transaction) {
        for (peer in walletKit!!.peerGroup.connectedPeers) {
            peer.sendMessage(tx)
        }

        Main.INSTANCE.uiHelper.controller.model.update(wallet)
        Main.INSTANCE.uiHelper.setSendingDisplay()
    }

    private fun playAudio(audioFile: String) {
        val hit = Media(Main::class.java.getResource(audioFile).toExternalForm())
        try {
            val mediaPlayer = MediaPlayer(hit)
            mediaPlayer.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getWalletKeys(): List<ECKey> {
        return wallet.issuedReceiveKeys
    }

    fun getPrivateKey(index: Int): ECKey {
        val keys = getWalletKeys()
        return keys[index]
    }

    fun sweepWallet(privKey: String) {
        object : Thread() {
            override fun run() {
                val utxoUtil = UtxoUtil()
                val key = getPrivateKeyFromString(privKey)
                utxoUtil.setupSweepWallet(key)
                val txCount = utxoUtil.getUtxos()
                if (txCount > 0) {
                    utxoUtil.sweep(wallet.currentReceiveAddress().toString())
                }
            }
        }.start()
    }

    fun getSlpWallet(): Wallet {
        return slpWalletKit!!.wallet
    }

    fun getPrivateKeyFromString(privKey: String): ECKey {
        val key = if (privKey.length == 51 || privKey.length == 52) {
            val dumpedPrivateKey = DumpedPrivateKey.fromBase58(MainNetParams.get(), privKey)
            dumpedPrivateKey.key
        } else {
            val privateKey = Base58.decodeToBigInteger(privKey)
            ECKey.fromPrivate(privateKey)
        }

        return key
    }

    fun signMessageWithKey(ecKey: ECKey, message: String): String {
        return ecKey.signMessage(message)
    }

    fun isSignatureValid(address: String, signature: String, message: String): Boolean {
        try {
            val finalAddress = if (address.contains("#")) {
                org.bitcoinj.net.NetHelper().getCashAccountAddress(parameters, address, true)
            } else {
                address
            }
            println(finalAddress)
            val signedAddress = ECKey.signedMessageToKey(message, signature).toAddress(MainNetParams.get()).toString()
            val addressLegacy = if (Address.isValidCashAddr(MainNetParams.get(), finalAddress)) {
                LegacyAddress.fromCashAddress(MainNetParams.get(), finalAddress).toBase58()
            } else {
                finalAddress
            }
            println(addressLegacy)
            println(signedAddress == addressLegacy)

            return signedAddress == addressLegacy
        } catch (e: Exception) {
            return false
        }
    }

    private fun setupNodeOnStart() {
        val nodeIP = Main.INSTANCE.settings.getString("networkNode", "")
        println("GETTING NODE IP: $nodeIP")
        if (nodeIP != "") {
            walletKit!!.setPeerNodes(null)
            val peerDiscovery: PeerDiscovery = object : PeerDiscovery {
                override fun getPeers(p0: Long, p1: Long, p2: TimeUnit?): List<InetSocketAddress>? {
                    return null
                }

                override fun shutdown() {

                }
            }
            slpWalletKit!!.setDiscovery(peerDiscovery)
            var node1: InetAddress? = null

            try {
                node1 = InetAddress.getByName(nodeIP)
            } catch (e: UnknownHostException) {
                e.printStackTrace()
            }

            walletKit!!.setPeerNodes(PeerAddress(parameters, node1))
        }
    }

    fun setupSlpNodeOnStart() {
        val nodeIP = Main.INSTANCE.settings.getString("networkNode", "")

        if (nodeIP != "") {
            slpWalletKit!!.setPeerNodes(null)
            val peerDiscovery: PeerDiscovery = object : PeerDiscovery {
                override fun getPeers(p0: Long, p1: Long, p2: TimeUnit?): List<InetSocketAddress>? {
                    return null
                }

                override fun shutdown() {

                }
            }
            slpWalletKit!!.setDiscovery(peerDiscovery)
            var node1: InetAddress? = null

            try {
                node1 = InetAddress.getByName(nodeIP)
            } catch (e: UnknownHostException) {
                e.printStackTrace()
            }

            slpWalletKit!!.setPeerNodes(PeerAddress(parameters, node1))
        }
    }

    fun importPrivateKey(privKey: String) {
        val key = getPrivateKeyFromString(privKey)
        wallet.importKey(key)
    }

    fun registerCashAccount(ecKey: ECKey, name: String): String {
        val req = SendRequest.createCashAccount(parameters, ecKey.toAddress(parameters).toString(), name)
        req.ensureMinRequiredFee = false
        req.feePerKb = Coin.valueOf(java.lang.Long.parseLong(1.toString() + "") * 1000L)
        val tx = walletKit!!.getvWallet().sendCoinsOffline(req)
        val txHexBytes = Hex.encode(tx.bitcoinSerialize())
        val txHex = String(txHexBytes, StandardCharsets.UTF_8)
        broadcastTxToPeers(tx)

        if (!useTor) {
            Main.INSTANCE.netHelper.broadcastTransaction(
                txHex,
                "https://rest.bitcoin.com/v2/rawtransactions/sendRawTransaction"
            )
        }

        Main.INSTANCE.netHelper.broadcastTransaction(
            txHex,
            "https://rest.imaginary.cash/v2/rawtransactions/sendRawTransaction"
        )

        return tx.hashAsString
    }

    fun getMaxValueOfSelectedUtxos(): Coin {
        var utxoAmount = 0.0
        for (x in 0 until selectedUtxos.size) {
            val utxo = selectedUtxos[x]
            utxoAmount += java.lang.Double.parseDouble(utxo.value.toPlainString())
        }

        val str = UIHelper.formatBalanceNoUnit(utxoAmount, "#.########")
        return Coin.parseCoin(str)
    }

    companion object {
        var torProxy: Proxy? = null
        var isSynced: Boolean = false
        lateinit var currentEcKey: ECKey
        var selectedUtxos: ArrayList<TransactionOutput> = ArrayList()
        var parameters: NetworkParameters = if (Main.useTestnet) TestNet3Params.get() else MainNetParams.get()
        var registeredTxHash: String? = null
        var registeredBlockHash: String? = null
        var registeredBlock: String? = null
        var walletKit: BIP47AppKit? = null
            private set
        var slpWalletKit: SlpAppKit? = null
        val wallet: Wallet
            get() = walletKit!!.getvWallet()
        var timer = Timer()
        var refreshTimer = Timer()
        var tokenList = ArrayList<Map<String, String>>()
        lateinit var currentTokenId: String
        var currentTokenPosition: Int = 0
        var currentAddressViewBCH: Boolean = true
        var currentAddressView: Boolean = true

        @JvmField
        var addOpReturn: Boolean = false
        @JvmField
        var maximumAutomaticSend: Float = 0.00f
        @JvmField
        var encrypted: Boolean = false
        @JvmField
        var useTor: Boolean = false
        @JvmField
        var allowLegacyP2SH: Boolean = false

        val SCRYPT_PARAMETERS: Protos.ScryptParameters =
            Protos.ScryptParameters.newBuilder().setP(6).setR(8).setN(32768)
                .setSalt(ByteString.copyFrom(KeyCrypterScrypt.randomSalt())).build()

        fun isProtocol(tx: Transaction, protocolId: String): Boolean {
            for (x in 0 until tx.outputs.size) {
                val output = tx.outputs[x]
                if (output.scriptPubKey.isOpReturn) {
                    if (output.scriptPubKey.chunks[1].data != null) {
                        val protocolCode = String(Hex.encode(output.scriptPubKey.chunks[1].data!!), StandardCharsets.UTF_8)
                        return protocolCode == protocolId
                    }
                }
            }

            return false
        }

        fun sentToSatoshiDice(tx: Transaction): Boolean {
            for (x in 0 until tx.outputs.size) {
                val output = tx.outputs[x]
                if (output.scriptPubKey.isSentToAddress) {
                    val addressP2PKH = output.getAddressFromP2PKHScript(this@Companion.parameters).toString()

                    if (Address.isValidLegacyAddress(this@Companion.parameters, addressP2PKH) && !Main.INSTANCE.walletHelper.isAddressMine(addressP2PKH)) {
                        val satoshiDiceAddrs = arrayListOf(
                            "1DiceoejxZdTrYwu3FMP2Ldew91jq9L2u", "1Dice115YcjDrPM9gXFW8iFV9S3j9MtERm",
                            "1Dice1FZk6Ls5LKhnGMCLq47tg1DFG763e", "1Dice1cF41TGRLoCTbtN33DSdPtTujzUzx",
                            "1Dice1wBBY22stCobuE1LJxHX5FNZ7U97N", "1Dice5ycHmxDHUFVkdKGgrwsDDK1mPES3U",
                            "1Dice7JNVnvzyaenNyNcACuNnRVjt7jBrC", "1Dice7v1M3me7dJGtTX6cqPggwGoRADVQJ",
                            "1Dice2wTatMqebSPsbG4gKgT3HfHznsHWi", "1Dice81SKu2S1nAzRJUbvpr5LiNTzn7MDV",
                            "1Dice9GgmweQWxqdiu683E7bHfpb7MUXGd"
                        )
                        return satoshiDiceAddrs.indexOf(addressP2PKH) != -1
                    }
                }
            }

            return false
        }

        fun isCashShuffle(tx: Transaction): Boolean {
            if (tx.outputs.size >= tx.inputs.size && tx.outputs.size > 1 && tx.inputs.size > 1) {
                val shuffledOutputs = ArrayList<String>()
                for (x in 0 until tx.inputs.size) {
                    if (tx.outputs[x].scriptPubKey.isSentToAddress)
                        shuffledOutputs.add(tx.outputs[x].value.toPlainString())
                    else
                        return false
                }

                val hashSet = HashSet(shuffledOutputs)
                return hashSet.size == 1
            }

            return false
        }

        fun getEntropy(bits: Int): ByteArray {
            return getEntropy(SecureRandom(), bits)
        }

        private fun getEntropy(random: SecureRandom, bits: Int): ByteArray {
            Preconditions.checkArgument(
                bits <= DeterministicSeed.MAX_SEED_ENTROPY_BITS,
                "requested entropy size too large"
            )

            val seed = ByteArray(bits / 8)
            random.nextBytes(seed)
            return seed
        }

        fun isEncryptedBIP38Key(privKey: String): Boolean {
            return try {
                BIP38PrivateKey.fromBase58(this.parameters, privKey)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
