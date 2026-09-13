package cn.com.omnimind.baselib.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.tencent.mmkv.MMKV

/**
 * 震动工具类
 */
object VibrationUtil {

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                BaseApplication.instance.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            BaseApplication.instance.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * 轻微震动
     * @param context 上下文
     */
    fun vibrateLight() {
        if (!MMKV.defaultMMKV().decodeBool("app_vibrate", true)) {
            return
        }
        vibrate(100L)
    }

    /**
     * 正常震动
     * @param context 上下文
     */
    fun vibrateNormal() {
        if (!MMKV.defaultMMKV().decodeBool("app_vibrate", true)) {
            return
        }
        vibrate(300L)
    }

    /**
     * 提示性剧烈震动
     * @param context 上下文
     */
    fun vibrateStrong() {
        if (!MMKV.defaultMMKV().decodeBool("app_vibrate", true)) {
            return
        }
        val vibrator = getVibrator() ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, 100, 100, 100, 100, 100),
                intArrayOf(0, 255, 0, 255, 0, 255),
                -1
            )
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 100, 100, 100, 100, 100), -1)
        }
    }

    private fun vibrate(milliseconds: Long) {
        val vibrator = getVibrator() ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect =
                VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(milliseconds)
        }
    }
}