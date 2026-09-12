package cn.com.omnimind.bot.workspace

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object WorkspaceStorageAccess {
    const val REQUIRED_PERMISSION_NAME = "内置 workspace"

    fun isGranted(context: Context): Boolean {
        return true
    }

    fun requiredPermissionNames(): List<String> = listOf(REQUIRED_PERMISSION_NAME)

    fun buildSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun buildFallbackSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun looksLikePermissionError(throwable: Throwable): Boolean {
        return false
    }
}
