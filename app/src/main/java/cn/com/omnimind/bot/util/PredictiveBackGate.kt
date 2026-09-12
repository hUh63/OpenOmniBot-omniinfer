package cn.com.omnimind.bot.util

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * 预测性返回手势的运行时开关门控。
 *
 * manifest 中 android:enableOnBackInvokedCallback="true" 是静态属性,无法运行时切换;
 * 依据官方文档,当存在已启用的 OnBackPressedCallback 时系统不会播放预测性返回动画,
 * 因此"关闭"档通过注册消费型回调来回退到旧行为(直接 finish,无动画)。
 * 文档: https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture
 */
object PredictiveBackGate {
    private const val PREFS_NAME = "FlutterSharedPreferences"
    private const val KEY_PREDICTIVE_BACK = "flutter.predictive_back_enabled"

    /** 开关状态,默认开启。设置项在 Flutter 设置页修改,经 shared_preferences 持久化。 */
    fun isPredictiveBackEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREDICTIVE_BACK, true)

    /**
     * 为不含自身返回拦截逻辑的 Activity 安装旧模式回调:
     * 开关 ON 时回调禁用(系统播放预测性返回动画并执行默认 finish);
     * 开关 OFF 时回调启用并直接 finish()(与旧版本行为一致,无动画)。
     * 每次 ON_RESUME 重新同步,以感知用户在 Flutter 设置页的改动。
     */
    fun install(activity: ComponentActivity) {
        val callback = object : OnBackPressedCallback(!isPredictiveBackEnabled(activity)) {
            override fun handleOnBackPressed() {
                activity.finish()
            }
        }
        activity.onBackPressedDispatcher.addCallback(activity, callback)
        activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                callback.isEnabled = !isPredictiveBackEnabled(activity)
            }
        })
    }
}
