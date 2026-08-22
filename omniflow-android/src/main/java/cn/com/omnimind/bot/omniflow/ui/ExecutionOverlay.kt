package cn.com.omnimind.bot.omniflow.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import cn.com.omnimind.androidgui.AndroidGuiOverlayHost
import cn.com.omnimind.baselib.util.OmniLog
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal object ExecutionOverlay {
    private const val TAG = "OmniFlowOverlay"
    private val DEFAULT_GRAVITY = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    private var activeSession: Session? = null

    fun show(
        context: Context,
        goal: String,
        initialPhase: ExecutionPhase,
        onComplete: () -> Unit,
        onStop: () -> Unit,
    ): Session? = synchronized(this) {
        activeSession?.dismissLocked()
        val appContext = context.applicationContext
        val overlayHandle = AndroidGuiOverlayHost.resolve(appContext)
        val host = ExecutionOverlayHostPolicy.resolve(
            accessibilityServiceAvailable = overlayHandle.trusted,
            applicationOverlayAllowed = Settings.canDrawOverlays(appContext),
        ) ?: return@synchronized null
        OmniLog.d(TAG, "show GUI controls with ${host.name.lowercase()} host")
        val windowContext = when (host) {
            ExecutionOverlayHost.ACCESSIBILITY -> overlayHandle.context
            ExecutionOverlayHost.APPLICATION -> appContext
        }
        val manager = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val session = Session(manager, onComplete, onStop, initialPhase)
        val view = buildView(windowContext, goal, session)
        val params = WindowManager.LayoutParams().apply {
            type = host.windowType
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            gravity = DEFAULT_GRAVITY
            width = windowContext.resources.displayMetrics.widthPixels - windowContext.dp(32)
            height = WindowManager.LayoutParams.WRAP_CONTENT
            y = windowContext.dp(32)
        }
        runCatching {
            manager.addView(view, params)
            session.attach(view, params)
            activeSession = session
            session
        }.onFailure { error ->
            OmniLog.w(TAG, "show GUI controls failed: ${error.message}")
        }.getOrNull()
    }

    private fun buildView(context: Context, goal: String, session: Session): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(14), context.dp(10), context.dp(12), context.dp(10))
            elevation = context.dp(8).toFloat()
            background = rounded(Color.WHITE, context.dp(18).toFloat(), "#80A9FF")
        }
        val title = TextView(context).apply {
            text = goal.trim().ifBlank { "视觉任务执行中" }.take(64)
            setTextColor(Color.parseColor("#202F51"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            setOnTouchListener { _, event ->
                session.onTitleDrag(event, context.dp(20).toFloat())
            }
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(8), 0, 0)
        }
        val status = TextView(context).apply {
            text = session.statusLabel
            setTextColor(Color.parseColor("#5F6875"))
            textSize = 11f
        }
        val pause = action(context, "接管", "#F3F4F5", "#202F51") {
            session.togglePaused()
        }
        val stop = action(context, "停止", "#FFF0F0", "#C73636") {
            session.requestStop()
        }
        val complete = action(context, "已完成", "#EAF7EE", "#257A43") {
            session.requestComplete()
        }
        row.addView(
            status,
            LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(pause)
        row.addView(
            complete,
            LinearLayout.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = context.dp(8) },
        )
        row.addView(
            stop,
            LinearLayout.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = context.dp(8) },
        )
        container.addView(title)
        container.addView(row)
        session.bind(title, status, pause, complete, stop)
        return container
    }

    private fun action(
        context: Context,
        label: String,
        backgroundColor: String,
        textColor: String,
        onClick: () -> Unit,
    ): TextView = TextView(context).apply {
        text = label
        setTextColor(Color.parseColor(textColor))
        textSize = 12f
        gravity = Gravity.CENTER
        minWidth = context.dp(62)
        setPadding(context.dp(12), context.dp(7), context.dp(12), context.dp(7))
        background = rounded(Color.parseColor(backgroundColor), context.dp(15).toFloat())
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radius: Float, strokeColor: String? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            strokeColor?.let { setStroke(1, Color.parseColor(it)) }
        }

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    class Session internal constructor(
        private val manager: WindowManager,
        private val onComplete: () -> Unit,
        private val onStop: () -> Unit,
        initialPhase: ExecutionPhase,
    ) {
        private val statusState = ExecutionStatusState(initialPhase)
        private val paused = MutableStateFlow(false)
        private val stopped = AtomicBoolean(false)
        private val terminalRequested = AtomicBoolean(false)
        private var view: View? = null
        private var params: WindowManager.LayoutParams? = null
        private var title: TextView? = null
        private var status: TextView? = null
        private var pause: TextView? = null
        private var complete: TextView? = null
        private var stop: TextView? = null
        private var dragStartY: Float? = null
        private var manuallyPositioned = false

        internal val statusLabel: String
            get() = statusState.label

        internal fun attach(view: View, params: WindowManager.LayoutParams) {
            this.view = view
            this.params = params
        }

        internal fun bind(
            title: TextView,
            status: TextView,
            pause: TextView,
            complete: TextView,
            stop: TextView,
        ) {
            this.title = title
            this.status = status
            this.pause = pause
            this.complete = complete
            this.stop = stop
        }

        suspend fun awaitRunning() {
            paused.filter { value -> !value }.first()
            if (stopped.get()) throw CancellationException("GUI task stopped")
        }

        fun update(message: String) {
            val text = message.trim().take(64)
            if (text.isEmpty() || stopped.get() || terminalRequested.get()) return
            view?.post { title?.text = text }
        }

        fun updatePhase(phase: ExecutionPhase) {
            statusState.updatePhase(phase)
            if (stopped.get() || terminalRequested.get()) return
            view?.post {
                if (!stopped.get()) status?.text = statusState.label
            }
        }

        suspend fun avoidTarget(relativeY: Double?) {
            val targetY = relativeY ?: return
            val moved = withContext(Dispatchers.Main.immediate) {
                if (manuallyPositioned) return@withContext false
                val attached = view ?: return@withContext false
                val layout = params ?: return@withContext false
                val gravity = executionOverlayGravityForTarget(targetY)
                if (layout.gravity == gravity) return@withContext false
                layout.gravity = gravity
                manager.updateViewLayout(attached, layout)
                true
            }
            if (moved) delay(80L)
        }

        suspend fun restoreDefaultPosition() {
            withContext(Dispatchers.Main.immediate) {
                if (manuallyPositioned) return@withContext
                val attached = view ?: return@withContext
                val layout = params ?: return@withContext
                if (layout.gravity == DEFAULT_GRAVITY) return@withContext
                layout.gravity = DEFAULT_GRAVITY
                manager.updateViewLayout(attached, layout)
            }
        }

        suspend fun hideForScreenshot() {
            val hidden = withContext(Dispatchers.Main.immediate) {
                if (stopped.get()) return@withContext false
                val attached = view ?: return@withContext false
                attached.visibility = View.INVISIBLE
                true
            }
            if (hidden) delay(32L)
        }

        suspend fun showAfterScreenshot() {
            withContext(Dispatchers.Main.immediate) {
                if (stopped.get()) return@withContext
                view?.visibility = View.VISIBLE
            }
        }

        internal fun onTitleDrag(event: MotionEvent, thresholdPx: Float): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> dragStartY = event.rawY
                MotionEvent.ACTION_UP -> {
                    val deltaY = event.rawY - (dragStartY ?: event.rawY)
                    dragStartY = null
                    if (abs(deltaY) >= thresholdPx) {
                        moveTo(if (deltaY < 0f) Gravity.TOP else Gravity.BOTTOM)
                    }
                }
                MotionEvent.ACTION_CANCEL -> dragStartY = null
            }
            return true
        }

        private fun moveTo(verticalGravity: Int) {
            val attached = view ?: return
            val layout = params ?: return
            layout.gravity = verticalGravity or Gravity.CENTER_HORIZONTAL
            manuallyPositioned = true
            manager.updateViewLayout(attached, layout)
        }

        internal fun togglePaused() {
            if (stopped.get()) return
            paused.value = !paused.value
            renderPaused()
        }

        fun requestStop() {
            if (!terminalRequested.compareAndSet(false, true)) return
            stopped.set(true)
            paused.value = false
            view?.post {
                status?.text = "正在停止"
                pause?.isEnabled = false
                complete?.isEnabled = false
                stop?.isEnabled = false
            }
            onStop()
        }

        fun requestComplete() {
            if (!terminalRequested.compareAndSet(false, true)) return
            paused.value = false
            view?.post {
                status?.text = "正在完成"
                pause?.isEnabled = false
                complete?.isEnabled = false
                stop?.isEnabled = false
            }
            onComplete()
        }

        suspend fun finish(message: String, visibleMs: Long = 900L) {
            if (visibleMs <= 0L) {
                withContext(Dispatchers.Main) { dismiss() }
                return
            }
            withContext(Dispatchers.Main) {
                stopped.set(true)
                paused.value = false
                title?.text = message.take(64)
                status?.text = message.take(24)
                pause?.visibility = View.GONE
                complete?.visibility = View.GONE
                stop?.visibility = View.GONE
            }
            delay(visibleMs)
            withContext(Dispatchers.Main) { dismiss() }
        }

        fun dismiss() = synchronized(ExecutionOverlay) {
            dismissLocked()
            if (activeSession === this) activeSession = null
        }

        internal fun dismissLocked() {
            val attached = view ?: return
            runCatching { manager.removeViewImmediate(attached) }
            view = null
            params = null
        }

        private fun renderPaused() {
            val isPaused = paused.value
            statusState.setPaused(isPaused)
            pause?.text = if (isPaused) "继续" else "接管"
            status?.text = statusState.label
        }
    }
}

internal fun executionOverlayGravityForTarget(relativeY: Double): Int =
    if (relativeY >= 500.0) {
        Gravity.TOP or Gravity.CENTER_HORIZONTAL
    } else {
        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    }

internal enum class ExecutionOverlayHost(val windowType: Int) {
    ACCESSIBILITY(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY),
    APPLICATION(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY),
}

internal object ExecutionOverlayHostPolicy {
    fun resolve(
        accessibilityServiceAvailable: Boolean,
        applicationOverlayAllowed: Boolean,
    ): ExecutionOverlayHost? = when {
        accessibilityServiceAvailable -> ExecutionOverlayHost.ACCESSIBILITY
        applicationOverlayAllowed -> ExecutionOverlayHost.APPLICATION
        else -> null
    }
}
