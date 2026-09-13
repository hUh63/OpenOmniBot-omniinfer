package cn.com.omnimind.assists.api.eventapi

interface CompanionTaskEventApi : TaskEventApi {
    /**
     * 第三方APP切换
     */
    suspend fun onThirdAPPChange()

    /**
     * 三方窗口变动
     */
    fun onThirdWindowChanged()

}