package app.crescentcash.src.ui

import app.crescentcash.src.Main

class ScreenManager {
    companion object {
        fun setScreens(firstStartPane: Boolean, restorePane: Boolean, tabPane: Boolean, connectionStatus: Boolean, balanceLabel: Boolean, createWalletPane: Boolean) {
            Main.INSTANCE.uiHelper.controller.firstStartPane!!.isVisible = firstStartPane
            Main.INSTANCE.uiHelper.controller.restorePane!!.isVisible = restorePane
            Main.INSTANCE.uiHelper.controller.tabPane!!.isVisible = tabPane
            Main.INSTANCE.uiHelper.controller.connectionStatus!!.isVisible = connectionStatus
            Main.INSTANCE.uiHelper.controller.balanceLabel!!.isVisible = balanceLabel
            Main.INSTANCE.uiHelper.controller.createWalletPane!!.isVisible = createWalletPane
        }
    }
}