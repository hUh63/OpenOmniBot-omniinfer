package cn.com.omnimind.bot.util

import android.content.Intent
import cn.com.omnimind.bot.ui.channel.ChannelManager

object SchemeUtil {

    /**
     * 处理通过 Intent extras 传递的路由参数（应用内跳转）。
     */
    fun pushRoute(intent: Intent, channelManager: ChannelManager, pagePath: String?) {
        var route = intent.extras?.getString("route")
        if (!pagePath.isNullOrEmpty()) {
            route = pagePath
        }
        val needClear = intent.extras?.getBoolean("needClear", false)
        if (route?.isNotEmpty() == true) {
            if (needClear == true) {
                channelManager.getUIRouterChannel().clearAndNavigateTo(route)
            } else {
                channelManager.getUIRouterChannel().resetToHomeAndPush(route)
            }
        }
    }
}
