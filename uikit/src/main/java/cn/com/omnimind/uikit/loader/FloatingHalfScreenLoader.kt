package cn.com.omnimind.uikit.loader

import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.uikit.UIKit
import cn.com.omnimind.uikit.loader.cat.DraggableBallInstance

class FloatingHalfScreenLoader private constructor() {
    companion object {
        private const val TAG = "FloatingHalfScreenLoader"
        private var instance: FloatingHalfScreenLoader? = null

        private fun getInstance(): FloatingHalfScreenLoader {
            return instance ?: FloatingHalfScreenLoader().also { instance = it }
        }

        fun loadFloatingHalfScreen(path: String) {
            getInstance().show(path)
        }

        fun destroyInstance() {
            instance?.destroy()
            instance = null
        }

        fun isShowing(): Boolean = instance?.attached == true

        fun hideForExternalActivity(): Boolean {
            return instance?.hideForExternalActivity() ?: DraggableBallInstance.hideForExternalActivity()
        }

        fun restoreAfterExternalActivity(): Boolean {
            return instance?.restoreAfterExternalActivity()
                ?: DraggableBallInstance.restoreAfterExternalActivity()
        }
    }

    private val context
        get() = requireNotNull(UIKit.appContext) { "UIKit is not initialized" }
    private val windowManager
        get() = context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager

    private var flutterView: View? = null
    private var container: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var attached = false
    private var hidden = false
    private var petHidden = false

    private fun show(path: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { show(path) }
            return
        }
        destroy()
        val view = UIKit.halfScreenApi?.onCreateFlutter(path) ?: return
        flutterView = view.apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(view)
        }
        params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        runCatching {
            windowManager.addView(container, params)
            attached = true
            hidden = false
        }.onFailure { error ->
            OmniLog.e(TAG, "Unable to show chat overlay", error)
            destroy()
        }
    }

    private fun hideForExternalActivity(): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { hideForExternalActivity() }
            return false
        }
        petHidden = DraggableBallInstance.hideForExternalActivity()
        val view = container ?: return petHidden
        if (!attached) return petHidden
        return runCatching {
            windowManager.removeView(view)
            attached = false
            hidden = true
            true
        }.getOrElse {
            OmniLog.e(TAG, "Unable to hide chat overlay", it)
            petHidden
        }
    }

    private fun restoreAfterExternalActivity(): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { restoreAfterExternalActivity() }
            return false
        }
        val petRestored = if (petHidden) {
            petHidden = false
            DraggableBallInstance.restoreAfterExternalActivity()
        } else {
            false
        }
        val view = container ?: return petRestored
        val layoutParams = params ?: return petRestored
        if (!hidden || attached) return petRestored
        return runCatching {
            windowManager.addView(view, layoutParams)
            attached = true
            hidden = false
            true
        }.getOrElse {
            OmniLog.e(TAG, "Unable to restore chat overlay", it)
            petRestored
        }
    }

    private fun destroy() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { destroy() }
            return
        }
        UIKit.halfScreenApi?.onDestroyOrGone()
        container?.let { view ->
            if (attached) {
                runCatching { windowManager.removeView(view) }
            }
            view.removeAllViews()
        }
        container = null
        flutterView = null
        params = null
        attached = false
        hidden = false
        petHidden = false
    }
}
