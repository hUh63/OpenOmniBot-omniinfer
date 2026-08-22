package cn.com.omnimind.baselib.util

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import BaseApplication

object DisplayUtil {

    /**
     * 获取屏幕宽度（物理像素）
     * 使用 WindowManager 获取，确保在屏幕旋转、多窗口等场景下也能获取到正确的值
     * 
     * 注意：此方法会返回屏幕的物理宽度，包括系统UI（状态栏、导航栏）的尺寸
     * 
     * @return 屏幕宽度（像素）
     */
    public fun getScreenWidth(): Int {
        return try {
            val windowManager = BaseApplication.instance.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val screenSize = Point()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 使用新的 API，获取最大窗口尺寸（不受多窗口影响）
                val windowMetrics = windowManager.maximumWindowMetrics
                windowMetrics.bounds.width()
            } else {
                // 旧版本使用 getRealSize，获取屏幕实际尺寸（包括系统UI）
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealSize(screenSize)
                screenSize.x
            }
        } catch (e: Exception) {
            // 降级方案：使用 displayMetrics（可能不准确，但至少能返回一个值）
            // 注意：此方法在屏幕旋转后可能返回旧值
            BaseApplication.instance.resources.displayMetrics.widthPixels
        }
    }
    
    /**
     * 获取屏幕高度（物理像素）
     * 使用 WindowManager 获取，确保在屏幕旋转、多窗口等场景下也能获取到正确的值
     * 
     * 注意：此方法会返回屏幕的物理高度，包括系统UI（状态栏、导航栏）的尺寸
     * 
     * @return 屏幕高度（像素）
     */
    public fun getScreenHeight(): Int {
        return try {
            val windowManager = BaseApplication.instance.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val screenSize = Point()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 使用新的 API，获取最大窗口尺寸（不受多窗口影响）
                val windowMetrics = windowManager.maximumWindowMetrics
                windowMetrics.bounds.height()
            } else {
                // 旧版本使用 getRealSize，获取屏幕实际尺寸（包括系统UI）
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealSize(screenSize)
                screenSize.y
            }
        } catch (e: Exception) {
            // 降级方案：使用 displayMetrics（可能不准确，但至少能返回一个值）
            // 注意：此方法在屏幕旋转后可能返回旧值
            BaseApplication.instance.resources.displayMetrics.heightPixels
        }
    }
}