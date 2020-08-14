package app.crescentcash.src.uri

import app.crescentcash.src.Main
import app.crescentcash.src.wallet.WalletHelper
import org.bitcoinj.utils.MonetaryFormat
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.text.DecimalFormat
import java.util.*

class URIHelper() {
    lateinit var address: String
    lateinit var amount: String

    constructor(uri: String, tryForSend: Boolean) : this() {
        val mappedVariables = getQueryParams(uri)

        if (!uri.contains("http")) {
            if (mappedVariables["amount"] != null) {
                Main.INSTANCE.uiHelper.controller.sendType!!.value =
                        Main.INSTANCE.uiHelper.controller.displayUnits!!.value
                Main.INSTANCE.settings.setString("sendType", Main.INSTANCE.uiHelper.controller.sendType!!.value)
                val amountVariable = (mappedVariables["amount"] ?: error(""))[0]
                amount = processSendAmount(amountVariable)
                Main.INSTANCE.uiHelper.controller.toAmount!!.text = amount
            } else {
                amount = "null"
            }

            address = when {
                uri.startsWith(WalletHelper.parameters.cashAddrPrefix) -> getQueryBaseAddress(uri).replace(
                        WalletHelper.parameters.cashAddrPrefix + ":",
                        ""
                )
                uri.startsWith("cointext") -> this.getRawPhoneNumber(getQueryBaseAddress(uri))
                uri.startsWith("cashacct") -> getQueryBaseAddress(uri).replace("cashacct:", "")
                else -> getQueryBaseAddress(uri)
            }

            Main.INSTANCE.uiHelper.controller.toAddress!!.text = address

            if (tryForSend) {
                if (mappedVariables["amount"] != null && this.amount != "null") {
                    val amountAsFloat = amount.toFloat()
                    println("Amount scanned: $amountAsFloat Maximum amount set: ${WalletHelper.maximumAutomaticSend}")
                    if (amountAsFloat <= WalletHelper.maximumAutomaticSend) {
                        Main.INSTANCE.walletHelper.send()
                    }
                }
            }
        } else {
            address = if (mappedVariables["r"] != null) {
                (mappedVariables["r"] ?: error(""))[0]
            } else {
                uri
            }

            Main.INSTANCE.uiHelper.controller.sendType!!.value = Main.INSTANCE.uiHelper.controller.displayUnits!!.value
            Main.INSTANCE.settings.setString("sendType", Main.INSTANCE.uiHelper.controller.sendType!!.value)

            Main.INSTANCE.uiHelper.controller.toAddress!!.text = address
            amount = "null"
        }
    }

    fun processSendAmount(amount: String): String {
        return when (Main.INSTANCE.uiHelper.controller.sendType!!.value) {
            MonetaryFormat.CODE_BTC -> {
                convertBchToDenom(amount, 1.0)
            }
            MonetaryFormat.CODE_MBTC -> {
                convertBchToDenom(amount, 1000.0)
            }
            MonetaryFormat.CODE_UBTC -> {
                convertBchToDenom(amount, 1000000.0)
            }
            "sats" -> {
                convertBchToDenom(amount, 100000000.0)
            }
            else -> convertBchToDenom(amount, 1.0)
        }
    }

    private fun convertBchToDenom(bchAmount: String, modifier: Double): String {
        val df = DecimalFormat("#,###.########")
        var amt = java.lang.Double.parseDouble(bchAmount)
        amt *= modifier
        return df.format(amt).replace(",", "")
    }

    private fun getQueryParams(url: String): Map<String, List<String>> {
        try {
            val params = HashMap<String, List<String>>()
            val urlParts = url.split("\\?".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (urlParts.size > 1) {
                val query = urlParts[1]
                for (param in query.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                    val pair = param.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val key = URLDecoder.decode(pair[0], "UTF-8")
                    var value = ""
                    if (pair.size > 1) {
                        value = URLDecoder.decode(pair[1], "UTF-8")
                    }

                    var values: MutableList<String>? = params[key] as MutableList<String>?
                    if (values == null) {
                        values = ArrayList()
                        params[key] = values
                    }
                    values.add(value)
                }
            }

            return params
        } catch (ex: UnsupportedEncodingException) {
            throw AssertionError(ex)
        }
    }

    fun getRawPhoneNumber(address: String): String {
        val cointextString = address.replace("cointext:", "")
        val removedDashes = cointextString.replace("-", "")
        val removedOpenParenthesis = removedDashes.replace("(", "")
        val removedClosedParenthesis = removedOpenParenthesis.replace(")", "")
        var number = removedClosedParenthesis.replace(".", "")

        if (!number.contains("+")) {
            number = "+1$number"
        }

        return number
    }

    private fun getQueryBaseAddress(url: String): String {
        val urlParts = url.split("\\?".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return if (urlParts.size > 1) {
            urlParts[0]
        } else {
            url
        }
    }
}