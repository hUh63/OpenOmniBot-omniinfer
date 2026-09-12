package cn.com.omnimind.uikit.api.uieventimpl

import android.content.Context
import cn.com.omnimind.uikit.api.uievent.UIChatEvent
import cn.com.omnimind.uikit.loader.FloatingHalfScreenLoader
import cn.com.omnimind.uikit.loader.cat.DraggableBallInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UIChatEventImpl : UIChatEvent {
    override fun onUIInit(context: Context) = Unit

    override suspend fun dismissHalfScreen() = withContext(Dispatchers.Main) {
        FloatingHalfScreenLoader.destroyInstance()
    }

    override suspend fun closeChatBotBg() = withContext(Dispatchers.Main) {
        DraggableBallInstance.collapse()
        FloatingHalfScreenLoader.destroyInstance()
    }

    override fun closeChatBotBgInMain() {
        DraggableBallInstance.collapse()
        FloatingHalfScreenLoader.destroyInstance()
    }

    override suspend fun showChatBotHalfScreen(scene: String?) = withContext(Dispatchers.Main) {
        val route = if (scene.isNullOrBlank()) {
            "/home/command_overlay"
        } else {
            "/home/command_overlay?scene=$scene"
        }
        FloatingHalfScreenLoader.loadFloatingHalfScreen(route)
    }

    override fun dismissHalfScreenInMain() {
        FloatingHalfScreenLoader.destroyInstance()
    }

    override fun isChatBotHalfScreenShowing(): Boolean {
        return FloatingHalfScreenLoader.isShowing()
    }
}
