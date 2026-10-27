package cn.com.omnimind.bot.ui.channel

import android.util.Log
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * `MethodChannel.Result` 的「只回一次包」包装。
 *
 * 同一个 Result 被 reply 两次时，Flutter 会抛
 * `IllegalStateException: Reply already submitted`。这类代码几乎都写在协程里
 * （`scope.launch { try { … } catch (t: Throwable) { result.error(…) } }`），
 * 异常没有人接住就直接变成 FATAL —— 用户看到的是「点某个按钮闪退」。
 *
 * 典型触发：try 块里 `result.success(...)` 已经回过包，紧接着同一 try 里的
 * 后续语句（导航、字段序列化、reply 自身）抛异常，catch 又回一次。
 *
 * 用法：handler 入口 `val once = OnceResult(result, TAG)`，之后一律用 `once`。
 */
internal class OnceResult(
    private val delegate: MethodChannel.Result,
    private val tag: String,
) : MethodChannel.Result {
    private val replied = AtomicBoolean(false)

    val hasReplied: Boolean get() = replied.get()

    override fun success(result: Any?) = submit("success") { delegate.success(result) }

    override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) =
        submit("error($errorCode)") { delegate.error(errorCode, errorMessage, errorDetails) }

    override fun notImplemented() = submit("notImplemented") { delegate.notImplemented() }

    private fun submit(what: String, block: () -> Unit) {
        if (!replied.compareAndSet(false, true)) {
            Log.w(tag, "duplicate reply dropped: $what")
            return
        }
        // delegate 自己也可能抛（engine 已 detach）；不能让它冒到协程里变 FATAL。
        runCatching(block).onFailure { Log.w(tag, "reply $what failed: ${it.message}") }
    }
}
