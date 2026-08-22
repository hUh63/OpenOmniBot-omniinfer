package cn.com.omnimind.bot.ui.platformview

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import cn.com.omnimind.bot.agent.LiveAgentBrowserSessionManager
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class AgentBrowserPlatformViewFactory : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    companion object {
        const val VIEW_TYPE = "cn.com.omnimind.bot/agent_browser_view"

        private val registeredEngineKeys = mutableSetOf<Int>()

        @Synchronized
        fun registerWith(
            flutterEngine: FlutterEngine
        ) {
            val engineKey = System.identityHashCode(flutterEngine)
            if (!registeredEngineKeys.add(engineKey)) {
                return
            }
            flutterEngine
                .platformViewsController
                .registry
                .registerViewFactory(
                    VIEW_TYPE,
                    AgentBrowserPlatformViewFactory()
                )
        }
    }

    override fun create(
        context: Context,
        viewId: Int,
        args: Any?
    ): PlatformView {
        val params = args as? Map<*, *>
        val workspaceId = params?.get("workspaceId")?.toString()?.trim().orEmpty()
        return AgentBrowserPlatformView(
            hostContext = context,
            workspaceId = workspaceId
        )
    }
}

private class AgentBrowserPlatformView(
    hostContext: Context,
    private val workspaceId: String
) : PlatformView {
    private val container = FrameLayout(hostContext)

    init {
        container.post {
            LiveAgentBrowserSessionManager.attachActiveTabTo(
                container = container,
                hostContext = container.context,
                workspaceId = workspaceId
            )
        }
    }

    override fun getView(): View = container

    override fun dispose() {
        LiveAgentBrowserSessionManager.detachActiveTabFrom(container)
        container.removeAllViews()
    }
}
