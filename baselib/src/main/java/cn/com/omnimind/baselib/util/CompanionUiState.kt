package cn.com.omnimind.baselib.util

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Companion UI state flags shared across modules.
 */
object CompanionUiState {
    private val suppressStartMessage = AtomicBoolean(false)

    fun setSuppressStartMessage(suppress: Boolean) {
        suppressStartMessage.set(suppress)
    }

    fun shouldSuppressStartMessage(): Boolean {
        return suppressStartMessage.get()
    }
}
