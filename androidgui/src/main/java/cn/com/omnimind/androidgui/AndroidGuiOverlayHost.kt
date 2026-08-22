package cn.com.omnimind.androidgui

import android.content.Context
import android.os.Build
import android.view.WindowManager
import cn.com.omnimind.accessibility.service.AssistsService

data class AndroidGuiOverlayHandle(
    val context: Context,
    val windowType: Int,
    val trusted: Boolean,
)

object AndroidGuiOverlayHost {
    fun resolve(fallbackContext: Context): AndroidGuiOverlayHandle {
        AssistsService.readyInstance()?.let { service ->
            return AndroidGuiOverlayHandle(
                context = service,
                windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                trusted = true,
            )
        }
        val context = fallbackContext.applicationContext ?: fallbackContext
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return AndroidGuiOverlayHandle(
            context = context,
            windowType = windowType,
            trusted = false,
        )
    }
}
