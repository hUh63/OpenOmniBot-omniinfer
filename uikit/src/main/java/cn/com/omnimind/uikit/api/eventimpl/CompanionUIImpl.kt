package cn.com.omnimind.uikit.api.eventimpl

import cn.com.omnimind.assists.api.enums.TaskFinishType
import cn.com.omnimind.assists.api.eventapi.CompanionTaskEventApi
import cn.com.omnimind.uikit.api.uievent.UIBaseEvent
import cn.com.omnimind.uikit.api.uievent.UIChatEvent
import cn.com.omnimind.uikit.api.uievent.UITaskEvent

class CompanionUIImpl(
    val uiChatEvent: UIChatEvent, val uiBaseEvent: UIBaseEvent, val uiTaskEvent: UITaskEvent
) : CompanionTaskEventApi {
    override suspend fun onThirdAPPChange() {
        if (uiChatEvent.isChatBotHalfScreenShowing()) {
            uiChatEvent.dismissHalfScreen()
            uiChatEvent.closeChatBotBg()
        }
    }

    override fun onThirdWindowChanged() {
        uiBaseEvent.closeWithOutTaskDoingInMain()
    }

    override suspend fun startTask(
        isCompanionRunning: Boolean
    ) {
        uiBaseEvent.startCompanion()
    }

    override suspend fun onTaskStop(
        finishType: TaskFinishType, message: String, isCompanionRunning: Boolean
    ) {
        uiBaseEvent.finishCompanion()
    }
}