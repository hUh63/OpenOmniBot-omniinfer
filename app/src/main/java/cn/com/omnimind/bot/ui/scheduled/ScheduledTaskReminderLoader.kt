package cn.com.omnimind.bot.ui.scheduled

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.baselib.util.VibrationUtil
import cn.com.omnimind.bot.App

class ScheduledTaskReminderLoader private constructor(
    private val context: Context,
) {

    companion object {
        private const val TAG = "[ScheduledTaskReminderLoader]"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ScheduledTaskReminderLoader? = null

        private fun getInstance(): ScheduledTaskReminderLoader {
            if (instance == null) {
                instance = ScheduledTaskReminderLoader(App.instance.applicationContext)
            }
            return instance!!
        }

        fun show(
            taskId: String,
            taskName: String,
            countdownSeconds: Int = 5,
            onCancel: (String) -> Unit,
            onExecuteNow: (String) -> Unit,
        ): Boolean {
            return getInstance().show(
                taskId = taskId,
                taskName = taskName,
                countdownSeconds = countdownSeconds,
                onCancel = onCancel,
                onExecuteNow = onExecuteNow,
            )
        }

        fun hide() {
            instance?.hide()
        }

        fun destroyInstance() {
            instance?.destroy()
            instance = null
        }
    }

    private var reminderView: ScheduledTaskReminderView? = null
    private var windowManager: WindowManager? = null
    private var isAttachedToWindow = false
    private var currentTaskId: String? = null

    fun show(
        taskId: String,
        taskName: String,
        countdownSeconds: Int,
        onCancel: (String) -> Unit,
        onExecuteNow: (String) -> Unit,
    ): Boolean {
        currentTaskId = taskId
        return try {
            ensureView(onCancel, onExecuteNow)
            attachIfNeeded()
            VibrationUtil.vibrateNormal()
            reminderView?.showReminder(taskName, countdownSeconds)
            true
        } catch (e: Exception) {
            OmniLog.e(TAG, "show failed: ${e.message}")
            false
        }
    }

    fun hide() {
        try {
            reminderView?.hideReminder()
            reminderView?.postDelayed({
                detachView()
            }, 260)
        } catch (e: Exception) {
            OmniLog.e(TAG, "hide failed: ${e.message}")
        }
    }

    private fun ensureView(
        onCancel: (String) -> Unit,
        onExecuteNow: (String) -> Unit,
    ) {
        if (reminderView == null) {
            reminderView = ScheduledTaskReminderView(context)
        }

        reminderView?.setOnCancelClickListener {
            val id = currentTaskId.orEmpty()
            hide()
            onCancel(id)
        }

        reminderView?.setOnExecuteNowClickListener {
            val id = currentTaskId.orEmpty()
            hide()
            onExecuteNow(id)
        }

        reminderView?.setOnCountdownFinishListener {
            hide()
        }
    }

    private fun attachIfNeeded() {
        if (isAttachedToWindow) {
            return
        }
        if (windowManager == null) {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 96
        }

        val view = reminderView ?: return
        windowManager?.addView(view, params)
        isAttachedToWindow = true
        view.visibility = View.VISIBLE
    }

    private fun detachView() {
        try {
            val view = reminderView ?: return
            if (!isAttachedToWindow) {
                return
            }
            if (view.windowToken != null) {
                windowManager?.removeView(view)
            }
        } catch (e: Exception) {
            OmniLog.e(TAG, "detachView failed: ${e.message}")
        } finally {
            isAttachedToWindow = false
            currentTaskId = null
        }
    }

    private fun destroy() {
        detachView()
        reminderView = null
        windowManager = null
    }
}
