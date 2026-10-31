package cn.com.omnimind.bot.activity

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.bot.quicklog.QuickLogWidgetActionRouter
import cn.com.omnimind.bot.util.PredictiveBackGate

/**
 * 启动页 Activity
 *
 * OSS 版本：直接进入 MainActivity，不再依赖账号与在线协议流程。
 */
class LauncherActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LauncherActivity"
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        OmniLog.d(TAG, "POST_NOTIFICATIONS granted=$granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(StartupThemeResolver.resolveSplashTheme(this))
        applyResponsiveOrientation()
        super.onCreate(savedInstanceState)
        PredictiveBackGate.install(this)
        OmniLog.d(TAG, "LauncherActivity onCreate")
        requestNotificationPermissionIfNeeded()
        if (QuickLogWidgetActionRouter.consumeInto(this, intent)) {
            finish()
            return
        }
        showLoadingAndStartMain()
    }

    /**
     * Android 13+ 只声明 POST_NOTIFICATIONS 是不够的：不运行时申请，提醒闹钟的响铃通知
     * 和「任务执行中」的前台通知都会被系统静默丢弃 —— 用户到点什么都收不到，App 里也
     * 没有任何解释。这里在启动页申请一次（系统只会在未决定时弹框）。
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        runCatching {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }.onFailure { error ->
            OmniLog.w(TAG, "request notification permission failed: ${error.message}")
        }
    }

    private fun applyResponsiveOrientation() {
        val allowRotation = resources.configuration.smallestScreenWidthDp >= 600 || isInMultiWindowMode
        requestedOrientation = if (allowRotation) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    /**
     * 展示 Loading 动画并启动 MainActivity
     * 使用主题的 windowBackground 作为等待背景，Flutter 渲染完成后自然覆盖
     */
    private fun showLoadingAndStartMain() {
        OmniLog.d(TAG, "showLoadingAndStartMain")
        // 主题已设置 windowBackground，无需额外布局
        // 启动 MainActivity（Flutter 引擎）
        startMainActivity()
    }


    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            // 传递原始 Intent 的数据（用于 Deep Link 处理）
            data = this@LauncherActivity.intent.data
            action = this@LauncherActivity.intent.action
            this@LauncherActivity.intent.extras?.let { putExtras(it) }
        }
        OmniLog.d(TAG, "startMainActivity")
        startActivity(intent)
        // This activity only routes intents and never draws content. Finish
        // before onResume so the starting window can transfer to MainActivity
        // without displaying an intermediate launcher window.
        finish()
    }
}
