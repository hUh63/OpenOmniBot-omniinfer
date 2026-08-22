package cn.com.omnimind.assists.api.enums

import cn.com.omnimind.assists.R
import cn.com.omnimind.baselib.util.getResString

enum class TaskFinishType(var message: String) {
    CANCEL(R.string.task_stop.getResString()),//用户主动取消
    FINISH(R.string.task_finish.getResString()),//任务正常完成
    ERROR(R.string.task_error.getResString())//任务异常结束
}

/**
 * 将 TaskFinishType 转换为数据库存储的 status 字符串
 */
fun TaskFinishType.toStatus(): String = when (this) {
    TaskFinishType.FINISH -> "success"
    TaskFinishType.ERROR -> "failed"
    TaskFinishType.CANCEL -> "cancelled"
}
