package cn.com.omnimind.assists.api.bean

/**
 * {
 *   "code": "200",
 *   "message": "操作成功",
 *   "result": {
 *     "message": "Hello! I'm doing well, thank you for asking. How can I help you today?"
 *   }
 * }
 */
data class LMBean( val code: String, val message: String, val result: ResultBean)
data class ResultBean( val message: String)


