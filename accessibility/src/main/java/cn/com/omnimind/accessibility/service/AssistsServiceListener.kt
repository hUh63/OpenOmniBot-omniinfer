package cn.com.omnimind.accessibility.service

import android.view.accessibility.AccessibilityEvent

interface AssistsServiceListener {
    fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    fun onServiceConnected(service: AssistsService) = Unit

    fun onInterrupt() = Unit

    fun onUnbind() = Unit
}
