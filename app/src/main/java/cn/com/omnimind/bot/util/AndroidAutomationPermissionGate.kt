package cn.com.omnimind.bot.util

import android.content.Context
import android.provider.Settings
import cn.com.omnimind.androidgui.AndroidGuiEnvironment
import cn.com.omnimind.baselib.util.exception.PermissionException

internal data class AndroidAutomationPermissionCheck(
    val missingIds: List<String>,
) {
    val granted: Boolean
        get() = missingIds.isEmpty()

    val displayNames: List<String>
        get() = missingIds.map { id ->
            when (id) {
                AndroidAutomationPermissionGate.ACCESSIBILITY -> "无障碍权限"
                AndroidAutomationPermissionGate.OVERLAY -> "悬浮窗权限"
                else -> id
            }
        }

    val errorCode: String
        get() = if (AndroidAutomationPermissionGate.ACCESSIBILITY in missingIds) {
            "OOB_ACCESSIBILITY_REQUIRED"
        } else {
            "OOB_PERMISSION_REQUIRED"
        }

    val message: String
        get() = when {
            AndroidAutomationPermissionGate.ACCESSIBILITY in missingIds &&
                AndroidAutomationPermissionGate.OVERLAY in missingIds ->
                "请先开启无障碍权限和悬浮窗权限，视觉执行才能操作界面并显示任务控制条。"
            AndroidAutomationPermissionGate.ACCESSIBILITY in missingIds ->
                "请先开启无障碍权限，视觉执行才能点击、滑动和输入。"
            AndroidAutomationPermissionGate.OVERLAY in missingIds ->
                "请先开启悬浮窗权限，视觉执行才能显示任务控制条。"
            else -> ""
        }

    fun requireGranted() {
        if (!granted) throw PermissionException(message)
    }
}

internal object AndroidAutomationPermissionGate {
    const val ACCESSIBILITY = "accessibility"
    const val OVERLAY = "overlay"

    fun check(context: Context): AndroidAutomationPermissionCheck = evaluate(
        accessibilityEnabled = AndroidGuiEnvironment(context).isAccessibilityEnabled(),
        overlayEnabled = Settings.canDrawOverlays(context),
    )

    internal fun evaluate(
        accessibilityEnabled: Boolean,
        overlayEnabled: Boolean,
    ): AndroidAutomationPermissionCheck = AndroidAutomationPermissionCheck(
        missingIds = buildList {
            if (!accessibilityEnabled) add(ACCESSIBILITY)
            if (!overlayEnabled) add(OVERLAY)
        },
    )
}
