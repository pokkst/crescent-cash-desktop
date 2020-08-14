package app.crescentcash.src.ui

import app.crescentcash.src.EncryptWalletController
import app.crescentcash.src.Main
import app.crescentcash.src.UnlockWalletController
import app.crescentcash.src.controls.NotificationBarPane
import app.crescentcash.src.utils.GuiUtils.*
import app.crescentcash.src.wallet.WalletHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import javafx.application.Platform
import javafx.embed.swing.SwingFXUtils
import javafx.fxml.FXMLLoader
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.stage.Stage
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.IOException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import kotlin.collections.ArrayList

class UIHelper(private val mainWindow: Stage) {
    private val uiStack: StackPane
    var controller: UIController
    private var mainUI: Pane? = null
    private val notificationBar: NotificationBarPane

    var emojis = intArrayOf(
            128123,
            128018,
            128021,
            128008,
            128014,
            128004,
            128022,
            128016,
            128042,
            128024,
            128000,
            128007,
            128063,
            129415,
            128019,
            128039,
            129414,
            129417,
            128034,
            128013,
            128031,
            128025,
            128012,
            129419,
            128029,
            128030,
            128375,
            127803,
            127794,
            127796,
            127797,
            127809,
            127808,
            127815,
            127817,
            127819,
            127820,
            127822,
            127826,
            127827,
            129373,
            129381,
            129365,
            127805,
            127798,
            127812,
            129472,
            129370,
            129408,
            127850,
            127874,
            127853,
            127968,
            128663,
            128690,
            9973,
            9992,
            128641,
            128640,
            8986,
            9728,
            11088,
            127752,
            9730,
            127880,
            127872,
            9917,
            9824,
            9829,
            9830,
            9827,
            128083,
            128081,
            127913,
            128276,
            127925,
            127908,
            127911,
            127928,
            127930,
            129345,
            128269,
            128367,
            128161,
            128214,
            9993,
            128230,
            9999,
            128188,
            128203,
            9986,
            128273,
            128274,
            128296,
            128295,
            9878,
            9775,
            128681,
            128099,
            127838
    )

    private val stopClickPane = Pane()

    private var currentOverlay: OverlayUI<*>? = null

    init {
        handleCrashesOnThisThread()

        val location = Main.INSTANCE.javaClass.getResource("main.fxml")
        val loader = FXMLLoader(location)
        try {
            mainUI = loader.load<Pane>()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        controller = loader.getController()
        notificationBar = mainUI?.let { NotificationBarPane(it) }!!
        mainWindow.title = "Crescent Cash"
        uiStack = StackPane()
        val scene = Scene(uiStack)
        scene.stylesheets.add(Main.INSTANCE.javaClass.getResource("wallet.css").toString())
        uiStack.children.add(notificationBar)
        mainWindow.scene = scene
        mainWindow.maxWidth = 850.0
        mainWindow.maxHeight = 475.0
        mainWindow.minHeight = 475.0
        mainWindow.minWidth = 850.0
        mainWindow.isResizable = false
        mainWindow.icons.add(Image(Main.INSTANCE.javaClass.getResourceAsStream("logo.png")))
    }

    fun displayWalletScreen() {
        ScreenManager.setScreens(firstStartPane = false, restorePane = false, tabPane = true, connectionStatus = true, balanceLabel = true, createWalletPane = false)
    }

    fun displayNewUser() {
        ScreenManager.setScreens(firstStartPane = true, restorePane = false, tabPane = false, connectionStatus = false, balanceLabel = false, createWalletPane = false)
    }

    fun setSendingDisplay() {
        controller.toAddress!!.clear()
        controller.toAmount!!.clear()
        WalletHelper.selectedUtxos = ArrayList()
        if (controller.opReturnText != null) {
            controller.opReturnText!!.clear()
        }
        setEnableSending()
    }

    fun setEnableSending() {
        controller.sendBtn!!.isDisable = false
        controller.toAmount!!.isDisable = false
        controller.toAddress!!.isDisable = false
    }

    fun setScreen(scene: String) {
        when (scene) {
            "firstStartPane" -> {
                ScreenManager.setScreens(firstStartPane = true, restorePane = false, tabPane = false, connectionStatus = false, balanceLabel = false, createWalletPane = false)
            }
            "restore" -> {
                ScreenManager.setScreens(firstStartPane = false, restorePane = true, tabPane = false, connectionStatus = false, balanceLabel = false, createWalletPane = false)
            }
            "ackSeed", "startRestore", "loadWallet" -> {
                ScreenManager.setScreens(firstStartPane = false, restorePane = false, tabPane = true, connectionStatus = true, balanceLabel = true, createWalletPane = false)
            }
            "createdWalletSeed" -> {
                ScreenManager.setScreens(firstStartPane = false, restorePane = false, tabPane = false, connectionStatus = false, balanceLabel = false, createWalletPane = false)
            }
            "createWallet" -> {
                ScreenManager.setScreens(firstStartPane = false, restorePane = false, tabPane = false, connectionStatus = false, balanceLabel = false, createWalletPane = true)
            }
        }
    }

    fun updateAddress(address: String?) {
        if(address != null) {
            controller.newReceiveAddress!!.text = address
            generateQR(address, 200, 200)
        }
    }

    fun generateQR(addr: String, width: Int, height: Int) {
        val writer = QRCodeWriter()
        val bufferedImage: BufferedImage?
        try {
            val byteMatrix = writer.encode(addr, BarcodeFormat.QR_CODE, width, height)
            bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            bufferedImage.createGraphics()

            val graphics = bufferedImage.graphics as Graphics2D
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, width, height)
            graphics.color = Color.BLACK

            for (i in 0 until height) {
                for (j in 0 until width) {
                    if (byteMatrix.get(i, j)) {
                        graphics.fillRect(i, j, 1, 1)
                    }
                }
            }

            Main.INSTANCE.uiHelper.controller.qrCode!!.image = SwingFXUtils.toFXImage(bufferedImage, null)
        } catch (ex: WriterException) {
            ex.printStackTrace()
            println("FAIL")
        }

    }

    fun generateQRSlp(addr: String, width: Int, height: Int) {
        val writer = QRCodeWriter()
        val bufferedImage: BufferedImage?
        try {
            val byteMatrix = writer.encode(addr, BarcodeFormat.QR_CODE, width, height)
            bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            bufferedImage.createGraphics()

            val graphics = bufferedImage.graphics as Graphics2D
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, width, height)
            graphics.color = Color.BLACK

            for (i in 0 until height) {
                for (j in 0 until width) {
                    if (byteMatrix.get(i, j)) {
                        graphics.fillRect(i, j, 1, 1)
                    }
                }
            }

            Main.INSTANCE.uiHelper.controller.qrCodeSlp!!.image = SwingFXUtils.toFXImage(bufferedImage, null)
        } catch (ex: WriterException) {
            ex.printStackTrace()
            println("FAIL")
        }

    }

    fun displayEncryptionSetup() {
        Main.INSTANCE.uiHelper.overlayUI<EncryptWalletController>("encrypt_wallet.fxml")
    }

    fun displayUnlockScreen() {
        Main.INSTANCE.uiHelper.overlayUI<UnlockWalletController>("unlock_screen.fxml")
    }

    inner class OverlayUI<T>(var ui: Node?, var controller: T?) {

        fun show() {
            checkGuiThread()
            if (currentOverlay == null) {
                uiStack.children.add(stopClickPane)
                uiStack.children.add(ui)
                blurOut(mainUI!!)
                fadeIn(ui)
                zoomIn(ui)
            } else {
                explodeOut(currentOverlay!!.ui)
                fadeOutAndRemove(uiStack, currentOverlay!!.ui!!)
                uiStack.children.add(ui)
                ui!!.opacity = 0.0
                fadeIn(ui!!, 100)
                zoomIn(ui, 100)
            }
            currentOverlay = this
        }

        fun done() {
            checkGuiThread()
            if (ui == null) return
            explodeOut(ui)
            fadeOutAndRemove(uiStack, ui, stopClickPane)
            blurIn(mainUI!!)
            this.ui = null
            this.controller = null
            currentOverlay = null
        }
    }

    fun <T : Any> overlayUI(name: String): OverlayUI<T> {
        try {
            checkGuiThread()
            val location = Main.INSTANCE.javaClass.getResource(name)
            val loader = FXMLLoader(location)
            val ui = loader.load<Pane>()
            val controller = loader.getController<T>()
            val pair = OverlayUI(ui, controller)

            try {
                controller?.javaClass?.getField("overlayUI")?.set(controller, pair)
            } catch (ignored: IllegalAccessException) {
                ignored.printStackTrace()
            } catch (ignored: NoSuchFieldException) {
                ignored.printStackTrace()
            }

            pair.show()
            return pair
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    fun refresh() {
        val cashAccount = Main.INSTANCE.settings.getString("cashAccount", "")
        //var cashEmoji = Main.INSTANCE.settings.getString("cashEmoji", "")

        if (cashAccount.contains("#???")) {
            // cashEmoji = "?"
            val cashAcctPlain = cashAccount.replace("#???", "")
            Main.INSTANCE.netHelper.checkForAccountIdentity(cashAcctPlain, true)
        }

        controller.cashAcctText!!.text = cashAccount
        //this.displayEmoji(cashEmoji);

        Platform.runLater {
            Main.INSTANCE.uiHelper.controller.model.update(WalletHelper.wallet)
            Main.INSTANCE.uiHelper.controller.model.refreshSlpTokens()
        }
    }

    companion object {
        var fiat = "USD"

        fun formatBalance(amount: Double, pattern: String): String {
            val formatter = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
            val formattedStr = formatter.format(amount)
            return "$formattedStr ${Main.INSTANCE.uiHelper.controller.displayUnits!!.value}"
        }

        fun formatBalanceNoUnit(amount: Double, pattern: String): String {
            val formatter = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
            val formattedStr = formatter.format(amount)
            return "$formattedStr"
        }
    }
}
