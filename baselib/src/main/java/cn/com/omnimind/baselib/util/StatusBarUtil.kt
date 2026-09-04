package cn.com.omnimind.baselib.util

import android.content.Context

class StatusBarUtil {
    companion object{

        fun getStatusBarHeight(): Int {
            var statusBarHeight = 0

            // 方法1: 标准Android资源方式
            try {
                val resourceId = BaseApplication.instance.resources.getIdentifier("status_bar_height", "dimen", "android")
                if (resourceId > 0) {
                    statusBarHeight = BaseApplication.instance.resources.getDimensionPixelSize(resourceId)
                }
            } catch (e: Exception) {
                // 忽略异常，尝试其他方法
            }

            // 方法2: 尝试多种资源名称
            if (statusBarHeight <= 0) {
                val resourceNames = arrayOf(
                    "status_bar_height_portrait",
                    "status_bar_height_landscape"
                )

                for (resourceName in resourceNames) {
                    try {
                        val resourceId = BaseApplication.instance.resources.getIdentifier(resourceName, "dimen", "android")
                        if (resourceId > 0) {
                            val height = BaseApplication.instance.resources.getDimensionPixelSize(resourceId)
                            if (height > 0) {
                                statusBarHeight = height
                                break
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略异常
                    }
                }
            }

            // 方法3: 反射方式获取
            if (statusBarHeight <= 0) {
                try {
                    val clazz = Class.forName("com.android.internal.R\$dimen")
                    val obj = clazz.newInstance()
                    val field = clazz.getField("status_bar_height")
                    val x = Integer.parseInt(field.get(obj).toString())
                    statusBarHeight = BaseApplication.instance.resources.getDimensionPixelSize(x)
                } catch (e: Exception) {
                    // 忽略异常
                }
            }

            // 方法4: 默认值兜底
            if (statusBarHeight <= 0) {
                statusBarHeight = getDefaultStatusBarHeight(BaseApplication.instance)
            }

            return statusBarHeight
        }

        private fun getDefaultStatusBarHeight(context: Context): Int {
            // 通常状态栏高度在24dp左右，根据屏幕密度计算
            return (24 * context.resources.displayMetrics.density).toInt()
        }
    }

}