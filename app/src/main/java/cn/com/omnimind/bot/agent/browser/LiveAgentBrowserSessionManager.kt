package cn.com.omnimind.bot.agent

import android.content.Context
import android.view.ViewGroup

interface AgentBrowserLiveSessionHandle {
    val workspaceId: String

    /**
     * 是否正有动作在跑（含 AI 的一次 navigation 等待）。正在跑的引擎被淘汰会在
     * 中途把 WebView destroy 掉、把 mainScope cancel 掉，页面加载永远等不到 onPageFinished。
     */
    val isBusy: Boolean get() = false

    fun closeSession()
}

class LiveAgentBrowserSessionStore<T : AgentBrowserLiveSessionHandle> {
    private companion object {
        /**
         * 同时保活的 workspace 引擎上限。每个引擎都持有一整套 WebView、JS bridge、
         * 下载管理器和协程作用域，是最重的一类对象；只增不减等于「每换一个项目
         * 就多一组永不 destroy 的 WebView」，多轮使用后内存一路涨到被系统杀进程。
         * 保留 2 个（当前 + 上一个），切回去还能看到原页面。
         */
        private const val MAX_LIVE_WORKSPACES = 2
    }

    private val sessions = linkedMapOf<String, T>()
    @Volatile
    private var currentWorkspaceId: String? = null

    fun acquire(workspaceId: String, create: () -> T): T {
        var existing: T? = null
        val evicted = mutableListOf<T>()
        synchronized(this) {
            val hit = sessions[workspaceId]
            existing = hit
            currentWorkspaceId = workspaceId
            if (hit != null) {
                // 命中即「最近使用」：挪到末尾，淘汰时优先丢最旧的。
                sessions.remove(workspaceId)
                sessions[workspaceId] = hit
            } else {
                val excess = (sessions.size + 1 - MAX_LIVE_WORKSPACES).coerceAtLeast(0)
                // 只淘汰「当前没有动作在跑」的引擎。淘汰一个正在 navigate 的引擎等于把它
                // mainScope 直接 cancel：那次加载永远等不到 onPageFinished，AI 侧要白等
                // 30s 才报超时；更糟的是残留的 createTab 会在这个已销毁的引擎上再造
                // WebView（既没人 attach 也再没人 destroy）。
                val evictable = sessions.entries.filter { !it.value.isBusy }.map { it.key }
                evictable.take(excess).forEach { key ->
                    sessions.remove(key)?.let { evicted.add(it) }
                }
            }
        }
        // closeSession 会销毁 WebView（非主线程时还要 post 到主线程等待），
        // 必须在锁外执行，避免和主线程抢同一把锁。
        evicted.forEach { handle -> runCatching { handle.closeSession() } }
        return existing ?: create().also { engine ->
            synchronized(this) { sessions[workspaceId] = engine }
        }
    }

    @Synchronized
    fun current(): T? = currentWorkspaceId?.let(sessions::get)

    fun clear() {
        val closing = mutableListOf<T>()
        synchronized(this) {
            closing.addAll(sessions.values)
            sessions.clear()
            currentWorkspaceId = null
        }
        // 同 acquire：销毁 WebView 不能在持锁时做。
        closing.forEach { handle -> runCatching { handle.closeSession() } }
    }
}

object LiveAgentBrowserSessionManager {
    private val store = LiveAgentBrowserSessionStore<BrowserUseEngine>()

    fun acquireEngine(
        context: Context,
        workspaceManager: AgentWorkspaceManager,
        agentRunId: String,
        workspace: AgentWorkspaceDescriptor
    ): BrowserUseEngine {
        val engine = store.acquire(workspace.id) {
            BrowserUseEngine(
                context = context,
                workspaceManager = workspaceManager,
                agentRunId = agentRunId,
                workspace = workspace
            )
        }
        return engine
    }

    fun currentSnapshot(): Map<String, Any?> {
        return store.current()?.liveSessionSnapshot() ?: BrowserUseEngine.unavailableSnapshot()
    }

    suspend fun handleCurrentCall(
        method: String,
        arguments: Map<String, Any?> = emptyMap()
    ): Any? {
        val engine = store.current()
        return if (engine == null) {
            if (
                method == "getSnapshot" ||
                method == "getLiveBrowserSessionSnapshot" ||
                method == "capturePreviewFrame"
            ) {
                BrowserUseEngine.unavailableSnapshot()
            } else {
                throw IllegalStateException("当前没有可用的浏览器会话")
            }
        } else {
            engine.handleHostCall(method = method, arguments = arguments)
        }
    }

    suspend fun executeCurrent(request: BrowserUseRequest): BrowserUseOutcome? {
        return store.current()?.execute(request)
    }

    suspend fun captureCurrentFramePng(): ByteArray? {
        return store.current()?.captureActiveFramePng()
    }

    fun attachActiveTabTo(
        container: ViewGroup,
        hostContext: Context,
        workspaceId: String
    ): Boolean {
        val engine = store.current()
        if (engine == null || engine.workspaceId != workspaceId) {
            return false
        }
        return engine.attachActiveTabTo(container = container, hostContext = hostContext)
    }

    fun detachActiveTabFrom(container: ViewGroup? = null) {
        store.current()?.detachActiveTabFrom(container)
    }

    fun releaseRunOwnership() {
        // 浏览器会话改为全局保活；run 结束时不销毁会话。
    }
}
