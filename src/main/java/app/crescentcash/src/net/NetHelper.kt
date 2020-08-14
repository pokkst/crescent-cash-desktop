package app.crescentcash.src.net

import app.crescentcash.src.Main
import app.crescentcash.src.async.AsyncTaskVerifyWallet
import app.crescentcash.src.hash.HashHelper
import app.crescentcash.src.json.JSONHelper
import app.crescentcash.src.ui.UIHelper
import app.crescentcash.src.utils.Constants
import app.crescentcash.src.utils.GuiUtils
import app.crescentcash.src.wallet.WalletHelper
import com.google.common.base.Splitter
import org.apache.http.util.TextUtils
import org.bitcoinj.core.ECKey
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.Charset
import java.util.*

class NetHelper {

    private val uiHelper: UIHelper = Main.INSTANCE.uiHelper
    private val cashAcctServers = arrayOf(
            "https://cashacct.imaginary.cash",
            "https://cashaccounts.bchdata.cash",
            "https://cashacct.electroncash.dk"
    )
    private val blockExplorers = arrayOf("rest.bitcoin.com")
    private val blockExplorerAPIURL = arrayOf(
            "https://rest.bitcoin.com/v2/transaction/details/"
    )
    private val registerURL = "https://api.cashaccount.info/register/"

    val price: Double
        get() {
            return readPriceFromUrl("https://api.cryptowat.ch/markets/coinbase-pro/bchusd/price")
        }

    val priceEur: Double
        get() {
            return readPriceFromUrl("https://api.cryptowat.ch/markets/coinbase-pro/bcheur/price")
        }

    val priceAud: Double
        get() {
            return readPriceFromUrl("https://min-api.cryptocompare.com/data/price?fsym=BCH&tsyms=AUD")
        }

    private fun readPriceFromUrl(url: String): Double {
        var price = 0.0
        val `is`: InputStream?
        try {
            `is` =
                    if (WalletHelper.useTor) URL(url).openConnection(WalletHelper.torProxy).getInputStream() else URL(
                            url
                    ).openConnection().getInputStream()
            val jsonText = JSONHelper.readJSONFile(BufferedReader(InputStreamReader(`is`, Charset.forName("UTF-8"))))

            val priceStr = when {
                url.contains("min-api.cryptocompare.com") -> {
                    val json = JSONObject(jsonText)
                    json.getDouble("AUD")
                }
                else -> {
                    val json = JSONObject(jsonText)
                    json.getJSONObject("result").getDouble("price")
                }
            }

            price = priceStr
            `is`.close()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }

        return price
    }

    fun registerCashAccount(cashAcctName: String, paymentCode: String, address: String) {
        object : Thread() {
            override fun run() {
                if (!cashAcctName.contains("#")) {
                    val json = JSONObject()
                    try {
                        json.put("name", cashAcctName)

                        val paymentsArray = JSONArray()
                        paymentsArray.put(paymentCode)
                        paymentsArray.put(address)

                        json.put("payments", paymentsArray)

                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }

                    var url: URL? = null
                    try {
                        url = URL(registerURL)
                    } catch (e: MalformedURLException) {
                        e.printStackTrace()
                    }

                    var connection: HttpURLConnection? = null

                    try {
                        if (url != null) {
                            connection =
                                    if (WalletHelper.useTor) url.openConnection(WalletHelper.torProxy) as HttpURLConnection else url.openConnection() as HttpURLConnection
                        }
                        if (connection != null) {
                            connection.doOutput = true
                            connection.doInput = true
                            connection.instanceFollowRedirects = false
                            connection.requestMethod = "POST"
                            connection.setRequestProperty("Content-Type", "application/json")
                            connection.setRequestProperty("charset", "utf-8")
                            connection.setRequestProperty("Accept", "application/json")
                            connection.setRequestProperty(
                                    "Content-Length",
                                    Integer.toString(json.toString().toByteArray().size)
                            )
                            connection.setRequestProperty(
                                    "User-Agent",
                                    "Mozilla/5.0 (Windows NT 6.1; rv:60.0) Gecko/20100101 Firefox/60.0"
                            )

                            connection.useCaches = false

                            connection.connectTimeout = 60000
                            connection.readTimeout = 60000

                            connection.connect()

                            val wr = DataOutputStream(connection.outputStream)
                            wr.write(json.toString().toByteArray())
                            wr.flush()
                            wr.close()

                            val rd = BufferedReader(InputStreamReader(connection.inputStream))
                            val res = StringBuilder()
                            while (true) {
                                val line = rd.readLine()

                                if (line != null)
                                    res.append(line)
                                else
                                    break
                            }

                            wr.flush()
                            wr.close()


                            val responseJson = res.toString()
                            println(responseJson)

                            val jsonHelper = JSONHelper()

                            WalletHelper.registeredTxHash = jsonHelper.getRegisterTxHash(responseJson)
                            println(WalletHelper.registeredTxHash)
                            if(WalletHelper.registeredTxHash != null) {
                                Main.usingBip47CashAccount = true
                                println(WalletHelper.registeredTxHash)
                                Main.INSTANCE.settings.setString("cashAccount", "$cashAcctName#???")
                                Main.INSTANCE.settings.setString("cashEmoji", "$cashAcctName?")
                                Main.INSTANCE.settings.setString("cashAcctTx", WalletHelper.registeredTxHash!!)
                                Main.INSTANCE.settings.setBoolean("isNewUser", false)
                                Main.INSTANCE.settings.setBoolean("bip47CashAcct", true)
                                uiHelper.refresh()

                                WalletHelper.timer.schedule(object : TimerTask() {
                                    override fun run() {
                                        checkForAccountIdentity(cashAcctName, true)
                                    }
                                }, 0, 150000)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                }
            }
        }.start()

    }

    fun prepareWalletForVerification() {
        val cashAcctName = uiHelper.controller.handleRestoreField!!.text.trim()

        if (!TextUtils.isEmpty(cashAcctName)) {
            if (cashAcctName.contains("#")) {
                val seedStr = uiHelper.controller.newSeed!!.text.trim()

                val creationTime = 1560281760L
                val seed = DeterministicSeed(Splitter.on(' ').splitToList(seedStr), null, "", creationTime)

                val length = Splitter.on(' ').splitToList(seedStr).size

                if (length == 12) {
                    Main.isNewUser = false
                    val tempWallet = Wallet.fromSeed(WalletHelper.parameters, seed)
                    val task = AsyncTaskVerifyWallet(tempWallet, cashAcctName, seed)
                    task.execute()
                }
            } else {
                GuiUtils.informationalAlert("Crescent Cash", "Please include the identifier!")
            }
        }
    }

    fun getCashAccountEmoji(cashAccount: String): String {
        val randExplorer = Random().nextInt(cashAcctServers.size)
        val lookupServer = cashAcctServers[randExplorer]
        println(lookupServer)
        var emoji = ""

        val splitAccount = cashAccount.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val name = splitAccount[0]
        val block = splitAccount[1]

        if (!lookupServer.contains("rest.bitcoin.com")) {
            if (!block.contains(".")) {

                var `is`: InputStream? = null
                try {
                    `is` =
                            if (WalletHelper.useTor) URL("$lookupServer/account/$block/$name").openConnection(WalletHelper.torProxy).getInputStream()
                            else
                                URL("$lookupServer/account/$block/$name").openConnection().getInputStream()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                try {
                    val rd = BufferedReader(InputStreamReader(`is`, Charset.forName("UTF-8")))
                    val jsonText = JSONHelper.readJSONFile(rd)
                    val json = JSONObject(jsonText)
                    emoji = json.getJSONObject("information").getString("emoji")
                } catch (e: JSONException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    try {
                        `is`?.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                }
            } else {
                val splitBlock = block.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val mainBlock = splitBlock[0]
                val blockCollision = splitBlock[1]


                var `is`: InputStream? = null
                try {
                    `is` =
                            if (WalletHelper.useTor) URL("$lookupServer/account/$mainBlock/$name/$blockCollision").openConnection(
                                    WalletHelper.torProxy
                            ).getInputStream()
                            else
                                URL("$lookupServer/account/$mainBlock/$name/$blockCollision").openConnection().getInputStream()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                try {
                    val rd = BufferedReader(InputStreamReader(`is`, Charset.forName("UTF-8")))
                    val jsonText = JSONHelper.readJSONFile(rd)
                    val json = JSONObject(jsonText)
                    emoji = json.getJSONObject("information").getString("emoji")
                } catch (e: JSONException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    try {
                        `is`?.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                }
            }
        }

        return emoji
    }

    fun checkForAccountIdentity(name: String, timer: Boolean) {
        object : Thread() {
            override fun run() {
                println("Checking for account...")
                if(WalletHelper.registeredTxHash != null) {
                    val hashHelper = HashHelper()
                    val registeredBlock = getTransactionData(WalletHelper.registeredTxHash!!, "block_height", "blockheight")
                    println(registeredBlock)
                    if(registeredBlock != null) {
                        WalletHelper.registeredBlock = registeredBlock
                        val registeredBlockHash = getTransactionData(WalletHelper.registeredTxHash!!, "block_hash", "blockhash")
                        println(registeredBlockHash)

                        if(registeredBlockHash != null) {
                            WalletHelper.registeredBlockHash = registeredBlockHash
                            val accountIdentity = Integer.parseInt(registeredBlock) - Constants.CASH_ACCOUNT_GENESIS_MODIFIED
                            val collisionIdentifier = hashHelper.getCashAccountCollision(registeredBlockHash, WalletHelper.registeredTxHash!!)
                            val identifier = getCashAccountIdentifier("$name#$accountIdentity.$collisionIdentifier")

                            if(identifier != null) {
                                println(identifier)
                                val emoji = hashHelper.getCashAccountEmoji(registeredBlockHash, WalletHelper.registeredTxHash!!)
                                Main.INSTANCE.settings.setString("cashAccount", identifier)
                                Main.INSTANCE.settings.setString("cashEmoji", emoji)
                                Main.INSTANCE.uiHelper.refresh()
                                Main.cashAccountSaved = true

                                if(timer)
                                    WalletHelper.timer.cancel()
                            }
                        }
                    }
                }
            }
        }.start()
    }

    fun getTransactionData(transactionHash: String, variable_one: String, variable_two: String): String? {
        val randExplorer = Random().nextInt(blockExplorers.size)
        val blockExplorer = blockExplorers[randExplorer]
        val blockExplorerURL = blockExplorerAPIURL[randExplorer]

        val txHash = transactionHash.toLowerCase(Locale.US)
        val block = getVariable(blockExplorerURL + txHash, blockExplorer, variable_one, variable_two)

        return if (block == "-1") null else block
    }

    private fun getVariable(url: String, blockExplorer: String, variable_one: String, variable_two: String): String {
        val json: JSONObject? = JSONHelper().getJsonObject(url)

        if (json != null) {
            if (blockExplorer == "rest.bitcoin.com") {
                return try {
                    if(variable_two == "blockheight")
                        json.getInt(variable_two).toString()
                    else
                        json.getString(variable_two)
                } catch (e: JSONException) {
                    "-1"
                }
            }
        } else
            return "-1"

        return "-1"
    }

    private fun getCashAccountIdentifier(cashAccount: String): String? {
        val randExplorer = Random().nextInt(cashAcctServers.size)
        val lookupServer = cashAcctServers[randExplorer]
        val identity: String?
        val splitAccount = cashAccount.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val name = splitAccount[0]
        val block = splitAccount[1]
        val urlString: String

        if (!lookupServer.contains("rest.bitcoin.com")) {
            urlString = if (block.contains(".")) {
                val splitBlock = block.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val mainBlock = splitBlock[0]
                val blockCollision = splitBlock[1]
                "$lookupServer/account/$mainBlock/$name/$blockCollision"
            } else {
                "$lookupServer/account/$block/$name"
            }
        } else {
            urlString = if (block.contains(".")) {
                val splitBlock = block.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val mainBlock = splitBlock[0]
                val blockCollision = splitBlock[1]
                "$lookupServer/lookup/$name/$mainBlock/$blockCollision"
            } else {
                "$lookupServer/lookup/$name/$block"
            }
        }

        val json = JSONHelper().getJsonObject(urlString)

        identity = json?.getString("identifier")?.replace(";", "")

        return identity
    }


    fun getTransactionsBlock(transactionHash: String): String {
        val randExplorer = Random().nextInt(blockExplorers.size)
        val blockExplorer = blockExplorers[randExplorer]
        val blockExplorerURL = blockExplorerAPIURL[randExplorer]

        var block = ""
        val txHash = transactionHash.toLowerCase()
        var `is`: InputStream? = null
        try {
            `is` = if (WalletHelper.useTor)
                URL(blockExplorerURL + txHash).openConnection(WalletHelper.torProxy).getInputStream()
            else
                URL(blockExplorerURL + txHash).openConnection().getInputStream()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        try {
            val rd = BufferedReader(InputStreamReader(`is`!!, Charset.forName("UTF-8")))
            val jsonText = JSONHelper.readJSONFile(rd)
            val json = JSONObject(jsonText)

            if (blockExplorer == "btc.com") {
                block = json.getJSONObject("data").getInt("block_height").toString() + ""
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                `is`?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }

        return if (block == "-1") "???" else block
    }

    fun getTransactionsBlockHash(transactionHash: String): String {
        val randExplorer = Random().nextInt(blockExplorers.size)
        val blockExplorer = blockExplorers[randExplorer]
        val blockExplorerURL = blockExplorerAPIURL[randExplorer]

        var block = ""
        val txHash = transactionHash.toLowerCase()
        var `is`: InputStream? = null
        try {
            `is` = if (WalletHelper.useTor)
                URL(blockExplorerURL + txHash).openConnection(WalletHelper.torProxy).getInputStream()
            else
                URL(blockExplorerURL + txHash).openConnection().getInputStream()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        try {
            val rd = BufferedReader(InputStreamReader(`is`!!, Charset.forName("UTF-8")))
            val jsonText = JSONHelper.readJSONFile(rd)
            val json = JSONObject(jsonText)

            if (blockExplorer == "btc.com") {
                block = json.getJSONObject("data").getString("block_hash")
            }
        } catch (e: JSONException) {
            block = "???"
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                `is`?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }

        return if (block == "-1") "???" else block
    }

    fun broadcastTransaction(hex: String, baseUrl: String) {
        object : Thread() {
            override fun run() {
                try {
                    val url = URL("$baseUrl/$hex")

                    with(if (WalletHelper.useTor) url.openConnection(WalletHelper.torProxy) as HttpURLConnection else url.openConnection() as HttpURLConnection) {
                        requestMethod = "GET"

                        if (responseCode == 200) {
                            Main.INSTANCE.uiHelper.controller.model.update(WalletHelper.wallet)
                            Main.INSTANCE.uiHelper.setSendingDisplay()
                        }

                    }
                } catch (e: FileNotFoundException) {
                    Main.INSTANCE.walletHelper.throwSendError("Failed to broadcast transaction.")
                    e.printStackTrace()
                }
            }
        }.start()
    }

    fun checkForCashAccount(ecKey: ECKey, txHash: String, name: String): String {
        try {
            val blockHeight = getTransactionData(txHash, "block_height", "blockheight")

            if (blockHeight == null) {
                return "$name#???"
            } else if (blockHeight != "") {
                val blockHash = getTransactionData(txHash, "block_hash", "blockhash")

                if(blockHash != null) {
                    val accountIdentity = Integer.parseInt(blockHeight) - Constants.CASH_ACCOUNT_GENESIS_MODIFIED
                    val hashHelper = HashHelper()
                    val collisionIdentifier = hashHelper.getCashAccountCollision(blockHash, txHash)
                    val identifier = this.getCashAccountIdentifier("$name#$accountIdentity.$collisionIdentifier")

                    return if (identifier != null) {
                        Main.INSTANCE.settings.setString("cashacct_${ecKey.toAddress(WalletHelper.parameters)}", identifier)
                        identifier
                    } else {
                        "$name#???"
                    }
                } else {
                    return "$name#???"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return "$name#???"
        }

        return "$name#???"
    }

    fun reverseLookupCashAccount(cashAddr: String, legacyAddr: String): String {
        val json: JSONObject? = JSONHelper().getJsonObject("https://rest.bitcoin.com/v2/cashAccounts/reverselookup/$cashAddr")
        if (json != null) {
            val results = json.getJSONArray("results")

            if (results.length() > 0) {
                val name = results.getJSONObject(0).getString("nameText")
                val accountNumber = results.getJSONObject(0).getInt("accountNumber")
                val accountHash = results.getJSONObject(0).getString("accountHash")
                val collisionLength = results.getJSONObject(0).getInt("accountCollisionLength")
                val collisionIdentifier = accountHash.substring(0, collisionLength)

                return if (collisionIdentifier.isNotEmpty()) {
                    val cashAcct = "$name#$accountNumber.$collisionIdentifier"
                    Main.INSTANCE.settings.setString("cashacct_$legacyAddr", cashAcct)
                    cashAcct
                } else {
                    val cashAcct = "$name#$accountNumber"
                    Main.INSTANCE.settings.setString("cashacct_$legacyAddr", cashAcct)
                    cashAcct
                }
            }
        }

        Main.INSTANCE.settings.setString("cashacct_$legacyAddr", "none_found")
        return "No Cash Account"
    }

}