package cn.com.omnimind.baselib.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import cn.com.omnimind.baselib.util.APPPackageUtil
import java.net.NetworkInterface
import java.util.Collections

/**
 * 设备信息服务 - 提供设备信息获取的核心功能
 */
object DeviceInfoService {

    private const val TAG = "DeviceInfoService"

    /**
     * 获取设备型号
     */
    fun getDeviceModel(): String {
        return android.os.Build.MODEL ?: "Unknown"
    }


    /**
     * 获取Android ID
     */
    fun getAndroidId(context: Context): String? {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            Log.d(TAG, "Android ID: $androidId")
            androidId
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Android ID", e)
            null
        }
    }

    /**
     * 获取设备信息
     */
    fun getDeviceInfo(context: Context): Map<String, Any?> {
        return try {
            val deviceInfo = mapOf(
                "androidId" to Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                ),
                "brand" to android.os.Build.BRAND,
                "model" to android.os.Build.MODEL,
                "version" to android.os.Build.VERSION.RELEASE,
                "sdkVersion" to android.os.Build.VERSION.SDK_INT
            )
            Log.d(TAG, "Device Info: $deviceInfo")
            deviceInfo
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device info", e)
            emptyMap()
        }
    }

    /**
     * 获取设备IP地址
     */
    fun getIpAddress(): String? {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        val hostAddress = address.hostAddress
                        if (hostAddress != null && !hostAddress.startsWith("127.")) {
                            Log.d(TAG, "Device IP Address: $hostAddress")
                            return hostAddress
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device IP address", e)
            null
        }
    }

    /**
     * 获取应用版本信息
     */
    fun getAppVersion(context: Context): Map<String, Any> {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName, 0
            )
            val versionName = packageInfo.versionName ?: "1.0"
            val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
            val platform = "android"

            val versionInfo = mapOf(
                "versionName" to versionName,
                "versionCode" to versionCode,
                "platform" to platform,
                "manufacturer" to (android.os.Build.MANUFACTURER ?: "Unknown"),
                "brand" to (android.os.Build.BRAND ?: "Unknown"),
                "product" to (android.os.Build.PRODUCT ?: "Unknown"),
                "device" to (android.os.Build.DEVICE ?: "Unknown"),
                "model" to (android.os.Build.MODEL ?: "Unknown"),

                )
            Log.d(TAG, "App Version: $versionInfo")
            versionInfo
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app version", e)
            mapOf(
                "versionName" to "1.0",
                "versionCode" to 1L,
                "platform" to "android",
                "manufacturer" to (android.os.Build.MANUFACTURER ?: "Unknown"),
                "brand" to (android.os.Build.BRAND ?: "Unknown"),
                "product" to (android.os.Build.PRODUCT ?: "Unknown"),
                "device" to (android.os.Build.DEVICE ?: "Unknown"),
                "model" to (android.os.Build.MODEL ?: "Unknown")
            )
        }
    }

    fun getOtherInfo(): String {
        val deviceInfo = mapOf(
            "hardware" to (android.os.Build.HARDWARE ?: "Unknown"),
            "board" to (android.os.Build.BOARD ?: "Unknown"),
            "display" to (android.os.Build.DISPLAY ?: "Unknown"),
            "fingerprint" to (android.os.Build.FINGERPRINT ?: "Unknown"),
            "host" to (android.os.Build.HOST ?: "Unknown"),
            "id" to (android.os.Build.ID ?: "Unknown"),
            "tags" to (android.os.Build.TAGS ?: "Unknown"),
            "type" to (android.os.Build.TYPE ?: "Unknown"),
            "user" to (android.os.Build.USER ?: "Unknown"),
            "codename" to (android.os.Build.VERSION.CODENAME ?: "Unknown"),
            "incremental" to (android.os.Build.VERSION.INCREMENTAL ?: "Unknown")
        )
        return deviceInfo.toString()
    }

    /**
     * 获取网络类型
     * @param context 上下文
     * @return 网络类型：wifi/mobile/ethernet/unknown
     */
    fun getNetworkType(context: Context): String {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return "unknown"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return "unknown"
                val capabilities =
                    connectivityManager.getNetworkCapabilities(network) ?: return "unknown"
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                    else -> "unknown"
                }
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                when (networkInfo?.type) {
                    ConnectivityManager.TYPE_WIFI -> "wifi"
                    ConnectivityManager.TYPE_MOBILE -> "mobile"
                    ConnectivityManager.TYPE_ETHERNET -> "ethernet"
                    else -> "unknown"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get network type: ${e.message}", e)
            "unknown"
        }
    }

    /**
     * 获取环境类型
     * @return 环境类型：prod/dev
     */
    fun getEnv(): String {
        return if (APPPackageUtil.isAppDebug()) "dev" else "prod"
    }

    /**
     * 获取操作系统名称
     * @return 操作系统名称，Android返回"Android"
     */
    fun getOsName(): String {
        return "Android"
    }

    /**
     * 获取操作系统版本
     * @return 操作系统版本号
     */
    fun getOsVersion(): String {
        return Build.VERSION.RELEASE
    }

}
