package cn.com.omnimind.baselib

import cn.com.omnimind.baselib.util.FileUtil
import java.io.File

object Constants {
    // 文件相关
    const val APP_ICON_FILENAME_PREFIX = "app_icon_"//第三方app应用图标前缀
    const val SCREENSHOT_PREFIX = "screenshot_"//截屏文件前缀

    val APP_ICON_DIR = File(BaseApplication.instance.filesDir, "app_icon")//第三方app应用图标目录
    val SCREENSHOT_DIR = File(BaseApplication.instance.filesDir, "screenshot")//截屏目录
    val CASE_FILE_DIR = File(BaseApplication.instance.filesDir, "caseFile")

    init {
        FileUtil.ensureDir(APP_ICON_DIR)
        FileUtil.ensureDir(SCREENSHOT_DIR)
        FileUtil.ensureDir(CASE_FILE_DIR)

    }


    // 时间相关
    const val ONE_SECOND_MS = 1000L
    const val ONE_MINUTE_MS = 60 * ONE_SECOND_MS
    const val ONE_HOUR_MS = 60 * ONE_MINUTE_MS
    const val ONE_DAY_MS = 24 * ONE_HOUR_MS
}
