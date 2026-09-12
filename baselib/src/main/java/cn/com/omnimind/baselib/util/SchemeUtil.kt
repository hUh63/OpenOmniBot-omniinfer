package cn.com.omnimind.baselib.util

import android.content.Context
import android.content.Intent
import android.net.Uri

class SchemeUtil {
    companion object {
        /**
         * 跳转到应用市场详情页
         *
         * @param context 上下文
         * @param packageName 应用包名
         */
        @JvmStatic
        fun jumpToMarket(context: Context, packageName: String) {
            val intent = Intent(Intent.ACTION_VIEW)
            val manufacturer = MobileManufacturerUtil.getDeviceManufacturer()

            val uri = when (manufacturer) {
                MobileManufacturer.XIAOMI -> {
                    // 小米应用商店
                    "https://app.xiaomi.com/details?id=$packageName"
                }

                MobileManufacturer.HUAWEI -> {
                    // 华为应用商店
                    "appmarket://details?id=$packageName"
                }

                MobileManufacturer.HONOR -> {
                    // 荣耀应用商店
                    "market://details?id=$packageName"
                }

                MobileManufacturer.OPPO -> {
                    // OPPO 应用商店
                    "market://details?id=$packageName"
                }

                MobileManufacturer.VIVO -> {
                    // vivo 应用商店
                    "vivoMarket://details?id=$packageName"
                }

                MobileManufacturer.SAMSUNG -> {
                    // 三星应用商店
                    "samsungapps://ProductDetail/$packageName"
                }

                else -> {
                    ""
                }
            }
            if (manufacturer==MobileManufacturer.XIAOMI){
                try {
                    intent.data = Uri.parse(uri)
                    // testbot/Service 场景下 context 可能不是 Activity，必须带 NEW_TASK
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val fallbackUri = "https://sj.qq.com/appdetail/$packageName"
                    intent.data = Uri.parse(fallbackUri)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                return
            }
            if (!uri.isEmpty()) {
                try {
                    intent.data = Uri.parse(uri)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val fallbackUri = "https://sj.qq.com/appdetail/$packageName"
                    intent.data = Uri.parse(fallbackUri)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            } else {
                val fallbackUri = "https://sj.qq.com/appdetail/$packageName"
                intent.data = Uri.parse(fallbackUri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }

        }
    }

    //    小米应用商店：
    //    mimarket://details?id=PackageName
    //    华为应用商店：
    //    appmarket://details?id=PackageName
    //    荣耀应用商店：
    //    market://details?id=PackageName
    //    OPPO 应用商店：
    //    market://details?id=PackageName
    //    vivo 应用商店：
    //    vivoMarket://details?id=PackageName
    //    三星应用商店：
    //    samsungapps://ProductDetail/PackageName
}