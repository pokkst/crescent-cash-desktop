package app.crescentcash.src.ui

import app.crescentcash.src.Main
import app.crescentcash.src.wallet.WalletHelper
import com.talanlabs.avatargenerator.Avatar
import com.talanlabs.avatargenerator.SquareAvatar
import com.talanlabs.avatargenerator.element.SquareElementRegistry
import com.talanlabs.avatargenerator.utils.AvatarUtils
import com.vdurmont.emoji.EmojiManager
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.embed.swing.SwingFXUtils
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.paint.Color
import org.bitcoinj.core.slp.*
import org.bitcoinj.core.Transaction
import org.bitcoinj.utils.MonetaryFormat
import org.bitcoinj.wallet.Wallet
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class UIModel {
    val transactions: ObservableList<Transaction> = FXCollections.observableArrayList<Transaction>()
    val tokens: ObservableList<SlpTokenBalance> = FXCollections.observableArrayList<SlpTokenBalance>()
    var refreshingTokens: Boolean = false

    fun update(wallet: Wallet) {
        val balInBch =
            java.lang.Double.parseDouble(Main.INSTANCE.walletHelper.getBalance(WalletHelper.wallet).toPlainString())
        var amountDbl: Double = balInBch
        var amountStr: String = balInBch.toString()
        val balInBchSlp = if(WalletHelper.slpWalletKit != null)
            java.lang.Double.parseDouble(Main.INSTANCE.walletHelper.getBalance(WalletHelper.slpWalletKit!!.wallet).toPlainString())
        else
            0.0
        var amountDblSlp: Double = balInBchSlp
        var amountStrSlp: String = balInBchSlp.toString()
        Main.INSTANCE.uiHelper.controller.sendType!!.items[0] = Main.INSTANCE.uiHelper.controller.displayUnits!!.value
        Main.INSTANCE.uiHelper.controller.sendType!!.items[1] = Main.INSTANCE.uiHelper.controller.fiatType!!.value
        Main.INSTANCE.uiHelper.controller.sendType!!.value = Main.INSTANCE.settings.getString("sendType", "BCH")

        when (Main.INSTANCE.uiHelper.controller.displayUnits!!.value) {
            MonetaryFormat.CODE_BTC -> {
                amountStr = UIHelper.formatBalance(amountDbl, "#,###.########")
                amountStrSlp = UIHelper.formatBalance(amountDblSlp, "#,###.########")
            }
            MonetaryFormat.CODE_MBTC -> {
                amountDbl *= 1000.0
                amountDblSlp *= 1000.0
                amountStr = UIHelper.formatBalance(amountDbl, "#,###.#####")
                amountStrSlp = UIHelper.formatBalance(amountDblSlp, "#,###.#####")
            }
            MonetaryFormat.CODE_UBTC -> {
                amountDbl *= 1000000.0
                amountDblSlp *= 1000000.0
                amountStr = UIHelper.formatBalance(amountDbl, "#,###.##")
                amountStrSlp = UIHelper.formatBalance(amountDblSlp, "#,###.##")
            }
            "sats" -> {
                amountDbl *= 100000000.0
                amountDblSlp *= 100000000.0
                amountStr = UIHelper.formatBalance(amountDbl, "#,###")
                amountStrSlp = UIHelper.formatBalance(amountDblSlp, "#,###")
            }
        }

        object : Thread() {
            override fun run() {
                val usdPrice = Main.INSTANCE.netHelper.price
                val eurPrice = Main.INSTANCE.netHelper.priceEur
                val audPrice = Main.INSTANCE.netHelper.priceAud
                val balInUsd = UIHelper.formatBalanceNoUnit(balInBch * usdPrice, "#,###.##")
                val balInEur = UIHelper.formatBalanceNoUnit(balInBch * eurPrice, "#,###.##")
                val balInAud = UIHelper.formatBalanceNoUnit(balInBch * audPrice, "#,###.##")
                val balInUsdSlp = UIHelper.formatBalanceNoUnit(balInBchSlp * usdPrice, "#,###.##")
                val balInEurSlp = UIHelper.formatBalanceNoUnit(balInBchSlp * eurPrice, "#,###.##")
                val balInAudSlp = UIHelper.formatBalanceNoUnit(balInBchSlp * audPrice, "#,###.##")
                Platform.runLater {
                    val tokenCount = WalletHelper.slpWalletKit?.slpBalances?.size

                    when (UIHelper.fiat) {
                        "USD" -> {
                            Main.INSTANCE.uiHelper.controller.balance!!.text = "BCH Wallet: $amountStr ($$balInUsd)"
                            Main.INSTANCE.uiHelper.controller.balanceSlp!!.text = "SLP Wallet: $amountStrSlp ($$balInUsdSlp) + $tokenCount tokens"
                        }
                        "EUR" -> {
                            Main.INSTANCE.uiHelper.controller.balance!!.text = "BCH Wallet: $amountStr (€$balInEur)"
                            Main.INSTANCE.uiHelper.controller.balanceSlp!!.text = "SLP Wallet: $amountStrSlp (€$balInEurSlp) + $tokenCount tokens"
                        }
                        "AUD" -> {
                            Main.INSTANCE.uiHelper.controller.balance!!.text = "BCH Wallet: $amountStr (AUD$$balInAud)"
                            Main.INSTANCE.uiHelper.controller.balanceSlp!!.text = "SLP Wallet: $amountStrSlp (AUD$$balInAudSlp) + $tokenCount tokens"
                        }
                    }
                }
            }
        }.start()

        if(WalletHelper.currentAddressView) {
            //SLP
            Main.INSTANCE.uiHelper.controller.newReceiveAddressSlp!!.text = WalletHelper.slpWalletKit?.currentSlpReceiveAddress().toString()
        } else {
            //BCH
            Main.INSTANCE.uiHelper.controller.newReceiveAddressSlp!!.text = WalletHelper.slpWalletKit?.wallet?.currentReceiveAddress().toString()
        }

        if(WalletHelper.currentAddressViewBCH) {
            //Payment Code
            Main.INSTANCE.uiHelper.controller.newReceiveAddress!!.text = WalletHelper.walletKit?.paymentCode
        } else {
            //BCH
            Main.INSTANCE.uiHelper.controller.newReceiveAddress!!.text = WalletHelper.wallet.currentReceiveAddress().toString()
        }

        Main.INSTANCE.uiHelper.generateQR(Main.INSTANCE.uiHelper.controller.newReceiveAddress!!.text, 150, 150)
        Main.INSTANCE.uiHelper.generateQRSlp(Main.INSTANCE.uiHelper.controller.newReceiveAddressSlp!!.text, 200, 200)
        transactions.clear()
        transactions.setAll(wallet.getRecentTransactions(0, false))
        Main.INSTANCE.uiHelper.controller.txGrid!!.children.clear()
        for (x in transactions.indices) {
            val tx = transactions[x]
            val isSatoshiDice: Boolean
            val isCashShuffle: Boolean
            val isCashAcct: Boolean
            val isCashFusion: Boolean

            var img: Image?
            var typeImg: Image? = null
            val confirmations = tx.confidence.depthInBlocks
            val imgStr = if (confirmations >= 6)
                "6"
            else
                confirmations.toString() + ""

            img = Image(Main::class.java.getResourceAsStream("$imgStr.png"))

            val txConfImg = ImageView(img)
            txConfImg.fitWidth = 24.0
            txConfImg.fitHeight = 24.0

            val dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm")
            val txDate = Label(dateFormatted.format(transactions[x].updateTime) + "")
            val txHash = Label("")
            val value = tx.getValue(WalletHelper.wallet)
            val amtString = MonetaryFormat.BTC.format(value).toString().replace("BCH ", "")
            var amtDbl = java.lang.Double.parseDouble(amtString)
            var amt = ""

            when (Main.INSTANCE.uiHelper.controller.displayUnits!!.value) {
                MonetaryFormat.CODE_BTC -> {
                    amt = UIHelper.formatBalanceNoUnit(amtDbl, "#,###.########")
                }
                MonetaryFormat.CODE_MBTC -> {
                    amtDbl *= 1000.0
                    amt = UIHelper.formatBalanceNoUnit(amtDbl, "#,###.#####")
                }
                MonetaryFormat.CODE_UBTC -> {
                    amtDbl *= 1000000.0
                    amt = UIHelper.formatBalanceNoUnit(amtDbl, "#,###.##")
                }
                "sats" -> {
                    amtDbl *= 100000000.0
                    amt = UIHelper.formatBalanceNoUnit(amtDbl, "#,###")
                }
            }

            val txAmt = Label(amt)
            val viewTxBtn = Button("View")

            if (value.isNegative) {
                isCashShuffle = WalletHelper.isCashShuffle(tx)
                isSatoshiDice = WalletHelper.sentToSatoshiDice(tx)
                isCashAcct = WalletHelper.isProtocol(tx, "01010101")
                isCashFusion = WalletHelper.isProtocol(tx, "46555a00")
                txAmt.textFill = Color.web("#f00")
            } else {
                isCashShuffle = false
                isSatoshiDice = WalletHelper.isProtocol(tx, "02446365")
                isCashAcct = false
                isCashFusion = false
                txAmt.textFill = Color.web("#080")
            }

            viewTxBtn.setOnMouseClicked { event -> printHash(x) }
            Main.INSTANCE.uiHelper.controller.txGrid!!.add(txConfImg, 0, x, 1, 1)

            if (isSatoshiDice || isCashShuffle || isCashAcct || isCashFusion) {
                when {
                    isSatoshiDice -> typeImg = Image(Main::class.java.getResourceAsStream("satoshidice.png"))
                    isCashShuffle -> typeImg = Image(Main::class.java.getResourceAsStream("cashshuffle_icon.png"))
                    isCashFusion -> typeImg = Image(Main::class.java.getResourceAsStream("cashfusion_icon.png"))
                    isCashAcct -> typeImg = Image(Main::class.java.getResourceAsStream("cashacct.png"))
                }

                val txTypeImg = ImageView(typeImg)
                txTypeImg.fitWidth = 24.0
                txTypeImg.fitHeight = 24.0
                Main.INSTANCE.uiHelper.controller.txGrid!!.add(txTypeImg, 1, x, 1, 1)
            }

            Main.INSTANCE.uiHelper.controller.txGrid!!.add(txDate, 2, x, 1, 1)
            Main.INSTANCE.uiHelper.controller.txGrid!!.add(txHash, 3, x, 1, 1)
            Main.INSTANCE.uiHelper.controller.txGrid!!.add(txAmt, 4, x, 1, 1)
            Main.INSTANCE.uiHelper.controller.txGrid!!.add(viewTxBtn, 5, x, 1, 1)
        }
    }

    fun refreshSlpTokens() {
        tokens.clear()
        Main.INSTANCE.uiHelper.controller.slpTokens?.items?.clear()

        if(!refreshingTokens) {
            object : Thread() {
                override fun run() {
                    refreshingTokens = true
                    WalletHelper.slpWalletKit?.recalculateSlpUtxos()
                    val listOfTokens = ArrayList<SlpTokenBalance>()
                    listOfTokens.add(0, SlpTokenBalance("", 0.0))

                    if (WalletHelper.slpWalletKit != null) {
                        for (token in WalletHelper.slpWalletKit?.slpBalances!!) {
                            listOfTokens.add(token)
                        }
                    }

                    tokens.setAll(listOfTokens)

                    Bindings.bindContent(Main.INSTANCE.uiHelper.controller.slpTokens!!.items, tokens)

                    Main.INSTANCE.uiHelper.controller.slpTokens!!.setCellFactory { param ->
                        object : ListCell<SlpTokenBalance>() {
                            override fun updateItem(bal: SlpTokenBalance?, empty: Boolean) {
                                super.updateItem(item, empty)
                                if (empty || param.editingIndex > tokens.size) {
                                    text = null
                                    graphic = null
                                } else {
                                    Platform.runLater {
                                        var imageView: ImageView? = null
                                        var image: Image?
                                        try {
                                            image = Image(Main::class.java.getResourceAsStream("slp/slp${bal?.tokenId}.png"))
                                        } catch (e: Exception) {
                                            image = if (bal?.tokenId != "") {
                                                val avatar = Avatar.newBuilder().elementRegistry(
                                                    SquareElementRegistry(
                                                        4,
                                                        AvatarUtils.defaultColors
                                                    )
                                                ).build()
                                                val tokenIdSubString = bal?.tokenId?.substring(6)
                                                if (tokenIdSubString != null) {
                                                    val bigInt = BigInteger(tokenIdSubString, 16)
                                                    SwingFXUtils.toFXImage(avatar.create(bigInt.toLong()), null)
                                                } else {
                                                    null
                                                }
                                            } else {
                                                Image(Main::class.java.getResourceAsStream("bch.png"))
                                            }
                                        }

                                        if(image != null) {
                                            imageView = ImageView()
                                        }
                                        imageView?.image = image
                                        imageView?.fitWidth = 32.0
                                        imageView?.fitHeight = 32.0
                                        graphic = imageView

                                        text = " " + if (bal?.tokenId == "") {
                                            if (WalletHelper.slpWalletKit != null) {
                                                var bchBalance = java.lang.Double.parseDouble(
                                                    WalletHelper.slpWalletKit!!.wallet.getBalance(Wallet.BalanceType.ESTIMATED).toPlainString()
                                                )
                                                var bchBalanceStr = ""

                                                when (Main.INSTANCE.uiHelper.controller.displayUnits!!.value) {
                                                    MonetaryFormat.CODE_BTC -> {
                                                        bchBalanceStr =
                                                            UIHelper.formatBalanceNoUnit(bchBalance, "#,###.########")
                                                    }
                                                    MonetaryFormat.CODE_MBTC -> {
                                                        bchBalance *= 1000
                                                        bchBalanceStr =
                                                            UIHelper.formatBalanceNoUnit(bchBalance, "#,###.#####")
                                                    }
                                                    MonetaryFormat.CODE_UBTC -> {
                                                        bchBalance *= 1000000
                                                        bchBalanceStr =
                                                            UIHelper.formatBalanceNoUnit(bchBalance, "#,###.##")
                                                    }
                                                    "sats" -> {
                                                        bchBalance *= 100000000
                                                        bchBalanceStr =
                                                            UIHelper.formatBalanceNoUnit(bchBalance, "#,###")
                                                    }
                                                }
                                                bchBalanceStr + " ${Main.INSTANCE.uiHelper.controller.displayUnits!!.value}"
                                            } else {
                                                null
                                            }
                                        } else {
                                            val balString = bal?.balance
                                            val slpToken = WalletHelper.slpWalletKit?.getSlpToken(bal?.tokenId)

                                            if (balString != null) {
                                                String.format(
                                                    Locale.ENGLISH,
                                                    "%.${slpToken?.decimals ?: 0}f",
                                                    java.lang.Double.parseDouble(balString.toString())
                                                ) + " " + slpToken?.ticker
                                            } else {
                                                null
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    refreshingTokens = false
                }
            }.start()
        } else {
            println("Thread is already running.")
        }
    }

    private fun printHash(txIndex: Int) {
        UIController.transactionSelected = txIndex
        Main.INSTANCE.uiHelper.overlayUI<Any>("transaction.fxml")
    }
}
