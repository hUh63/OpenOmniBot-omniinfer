package cn.com.omnimind.bot.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.Lifecycle.Event.ON_START
import androidx.lifecycle.Lifecycle.Event.ON_STOP

/**
 * App前后台状态监听工具类
 */
class NestedBackgroundStateUtil private constructor() : Application.ActivityLifecycleCallbacks, LifecycleObserver {
    private var listener: NestedBackgroundStateListener? = null
    private var activityCount = 0
    private var isInBackground = false

    companion object {
        @Volatile
        private var INSTANCE: NestedBackgroundStateUtil? = null

        fun getInstance(): NestedBackgroundStateUtil {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NestedBackgroundStateUtil().also { INSTANCE = it }
            }
        }

        /**
         * 添加App前后台状态监听器
         * @param listener 前后台状态监听器
         */
        fun addAppStateChangedListener(listener: NestedBackgroundStateListener) {
            val instance = getInstance()
            instance.listener = listener
        }

        /**
         * 移除App前后台状态监听器
         * @param listener 需要移除的监听器
         */
        fun removeAppStateChangedListener(listener: NestedBackgroundStateListener) {
            val instance = getInstance()
            if (instance.listener == listener) {
                instance.listener = null
            }
        }

        /**
         * 初始化监听器
         * @param application Application实例
         */
        fun init(application: Application) {
            val instance = getInstance()
            application.registerActivityLifecycleCallbacks(instance)
            ProcessLifecycleOwner.get().lifecycle.addObserver(instance)
        }
    }

    @OnLifecycleEvent(ON_START)
    fun onMoveToForeground() {
        if (isInBackground) {
            isInBackground = false
            listener?.onExitNestedBackground()
        }
    }

    @OnLifecycleEvent(ON_STOP)
    fun onMoveToBackground() {
        if (activityCount == 0 && !isInBackground) {
            isInBackground = true
            listener?.onEnterNestedBackground()
        }
    }

    override fun onActivityStarted(activity: Activity) {
        activityCount++
        if (isInBackground) {
            isInBackground = false
            listener?.onExitNestedBackground()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        activityCount--
        if (activityCount == 0 && !isInBackground) {
            isInBackground = true
            listener?.onEnterNestedBackground()
        }
    }

    // 其他不需要的生命周期方法
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    /**
     * 前后台状态监听器接口
     */
    interface NestedBackgroundStateListener {
        /**
         * 当进入后台时调用
         */
        fun onEnterNestedBackground()

        /**
         * 当退出后台进入前台时调用
         */
        fun onExitNestedBackground()
    }
}