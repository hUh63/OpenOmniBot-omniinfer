package cn.com.omnimind.uikit.api.uievent

import android.content.Context

interface UIChatEvent {
    fun onUIInit(context: Context)
    suspend fun dismissHalfScreen()
    suspend fun closeChatBotBg()
    fun closeChatBotBgInMain()
    suspend fun showChatBotHalfScreen(scene: String? = null)
    fun dismissHalfScreenInMain()
    fun isChatBotHalfScreenShowing(): Boolean
}
