package app.crescentcash.src.utils

import app.crescentcash.src.Main
import app.crescentcash.src.ui.UIHelper
import app.crescentcash.src.wallet.WalletHelper
import java.io.*
import java.util.*

class SettingsHelper(fileName: String) {

    private var saveFileName = ""
    var propsFile: Properties? = null
        private set

    init {
        this.saveFileName = fileName
        loadProperties()
    }

    fun loadSettings() {
        Main.isNewUser = !File(System.getProperty("user.home") + "/users_wallet.wallet").exists()
        Main.useTestnet = getBoolean("testnet", false)
        WalletHelper.addOpReturn = getBoolean("addOpReturn", false)
        WalletHelper.maximumAutomaticSend = getFloat("maximumAutomaticSend", 0.00f)
        WalletHelper.encrypted = getBoolean("useEncryption", false)
        WalletHelper.useTor = getBoolean("useTor", false)
        WalletHelper.allowLegacyP2SH = getBoolean("allowLegacyP2SH", false)
        UIHelper.fiat = getString("fiat", "USD")

        saveProperties()
    }

    fun setString(prop: String, data: String) {
        propsFile!!.setProperty(prop, data)
        saveProperties()
    }

    fun setBoolean(prop: String, data: Boolean) {
        propsFile!!.setProperty(prop, "" + data)
        saveProperties()
    }

    fun setInt(prop: String, data: Int) {
        propsFile!!.setProperty(prop, "" + data)
        saveProperties()
    }

    fun setFloat(prop: String, data: Float) {
        propsFile!!.setProperty(prop, "" + data)
        saveProperties()
    }

    fun setDouble(prop: String, data: Double) {
        propsFile!!.setProperty(prop, "" + data)
        saveProperties()
    }

    fun getString(prop: String, defaultValue: String): String {
        val property = propsFile!!.getProperty(prop)

        return if (property == null) {
            setString(prop, defaultValue)
            defaultValue
        } else {
            propsFile!!.getProperty(prop)
        }
    }

    fun getBoolean(prop: String, defaultValue: Boolean): Boolean {
        val property = propsFile!!.getProperty(prop)

        return if (property == null) {
            setBoolean(prop, defaultValue)
            defaultValue
        } else {
            java.lang.Boolean.parseBoolean(propsFile!!.getProperty(prop))
        }
    }

    fun getInt(prop: String, defaultValue: Int): Int {
        val property = propsFile!!.getProperty(prop)

        return if (property == null) {
            setInt(prop, defaultValue)
            defaultValue
        } else {
            Integer.parseInt(propsFile!!.getProperty(prop))
        }
    }

    fun getFloat(prop: String, defaultValue: Float): Float {
        val property = propsFile!!.getProperty(prop)

        return if (property == null) {
            setFloat(prop, defaultValue)
            defaultValue
        } else {
            java.lang.Float.parseFloat(propsFile!!.getProperty(prop))
        }
    }

    fun getDouble(prop: String, defaultValue: Double): Double {
        val property = propsFile!!.getProperty(prop)

        return if (property == null) {
            setDouble(prop, defaultValue)
            defaultValue
        } else {
            java.lang.Double.parseDouble(propsFile!!.getProperty(prop))
        }
    }

    fun loadProperties() {
        if (propsFile == null) {
            propsFile = Properties()
            val `in`: FileInputStream

            try {
                val saveFolder = File(System.getProperty("user.home") + "/data/")
                saveFolder.mkdir()
                `in` = FileInputStream(System.getProperty("user.home") + "/data/$saveFileName")
                propsFile!!.load(`in`)
                `in`.close()
            } catch (ex: FileNotFoundException) {
                saveProperties()
            } catch (e: IOException) {
                // Handle the IOException.
            }

        }
    }

    fun saveProperties() {
        try {
            val saveFolder = File(System.getProperty("user.home") + "/data/")
            saveFolder.mkdir()
            val out = FileOutputStream(System.getProperty("user.home") + "/data/$saveFileName")
            propsFile!!.store(out, null)
            out.close()
        } catch (e: FileNotFoundException) {
            // Handle the FileNotFoundException.
        } catch (e: IOException) {
            // Handle the IOException.
        }

    }

    companion object {

        val saveFile = "CrescentCash.properties"
    }

}