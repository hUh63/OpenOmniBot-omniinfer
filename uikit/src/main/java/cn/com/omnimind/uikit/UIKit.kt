package cn.com.omnimind.uikit

import android.content.Context
import cn.com.omnimind.uikit.api.callback.HalfScreenApi
import cn.com.omnimind.uikit.api.uievent.UIChatEvent
import cn.com.omnimind.uikit.api.uieventimpl.UIChatEventImpl

class UIKit {
    companion object {
        var halfScreenApi: HalfScreenApi? = null
        var uiChatEvent: UIChatEvent? = null
        var appContext: Context? = null

        fun init(context: Context, halfScreenApi: HalfScreenApi) {
            appContext = context.applicationContext
            UIKit.halfScreenApi = halfScreenApi
            uiChatEvent = UIChatEventImpl().also { it.onUIInit(context.applicationContext) }
        }
    }
}
