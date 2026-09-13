package cn.com.omnimind.baselib.util

import android.os.Build

/**
 * 手机厂商枚举类
 */
enum class MobileManufacturer {
    HUAWEI,     // 华为
    XIAOMI,     // 小米
    OPPO,       // OPPO
    VIVO,       // vivo
    SAMSUNG,    // 三星
    MEIZU,      // 魅族
    ONEPLUS,    // 一加
    REALME,     // realme
    HONOR,      // 荣耀
    OTHER       // 其他厂商
}

/**
 * 手机厂商工具类
 */
class MobileManufacturerUtil {

    companion object {
        /**
         * 获取当前设备的厂商枚举
         *
         * @return MobileManufacturer 枚举值
         */
        @JvmStatic
        fun getDeviceManufacturer(): MobileManufacturer {
            val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
            val brand = Build.BRAND?.lowercase() ?: ""

            return when {
                isHuawei(manufacturer, brand) -> MobileManufacturer.HUAWEI
                isXiaomi(manufacturer, brand) -> MobileManufacturer.XIAOMI
                isOppo(manufacturer, brand) -> MobileManufacturer.OPPO
                isVivo(manufacturer, brand) -> MobileManufacturer.VIVO
                isSamsung(manufacturer, brand) -> MobileManufacturer.SAMSUNG
                isMeizu(manufacturer, brand) -> MobileManufacturer.MEIZU
                isOnePlus(manufacturer, brand) -> MobileManufacturer.ONEPLUS
                isRealme(manufacturer, brand) -> MobileManufacturer.REALME
                isHonor(manufacturer, brand) -> MobileManufacturer.HONOR
                else -> MobileManufacturer.OTHER
            }
        }

        /**
         * 判断是否为华为设备
         */
        private fun isHuawei(manufacturer: String, brand: String): Boolean {
            return manufacturer.contains("huawei") || brand.contains("huawei")
        }

        /**
         * 判断是否为小米设备
         */
        private fun isXiaomi(manufacturer: String, brand: String): Boolean {
            return manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
                    manufacturer.contains("redmi") || brand.contains("redmi")
        }

        /**
         * 判断是否为OPPO设备
         */
        private fun isOppo(manufacturer: String, brand: String): Boolean {
            return manufacturer.contains("oppo") || brand.contains("oppo")
        }

        /**
         * 判断是否为vivo设备
         */
        private fun isVivo(manufacturer: String, brand: String): Boolean {
            return manufacturer.contains("vivo") || brand.contains("vivo")
        }

        /**
         * 判断是否为三星设备
         */
        private fun isSamsung(manufacturer: String, brand: String): Boolean {
            return manufacturer.contains("samsung") || brand.contains("samsung")
        }

        /**
         * 判断是否为魅族设备
         */
        private fun isMeizu(manufacturer: String, brand: String): Boolean {
            return manufacturer.contains("meizu") || brand.contains("meizu") ||
                    manufacturer.contains("魅族") || brand.contains("魅族")
        }

        /**
         * 判断是否为一加设备
         */
        private fun isOnePlus(manufacturer: String, brand: String): Boolean {
            return manufacturer.contains("oneplus") || brand.contains("oneplus")
        }

        /**
         * 判断是否为realme设备
         */
        private fun isRealme(manufacturer: String, brand: String): Boolean {
            return manufacturer.contains("realme") || brand.contains("realme")
        }

        /**
         * 判断是否为荣耀设备
         */
        private fun isHonor(manufacturer: String, brand: String): Boolean {
            return manufacturer.contains("honor") || brand.contains("honor") ||
                    manufacturer.contains("honour") || brand.contains("honour")
        }

        /**
         * 检查当前设备是否为指定厂商
         *
         * @param manufacturer 厂商枚举
         * @return Boolean 是否匹配
         */
        @JvmStatic
        fun isManufacturer(manufacturer: MobileManufacturer): Boolean {
            return getDeviceManufacturer() == manufacturer
        }

        /**
         * 检查当前设备是否为华为系列（包括荣耀）
         */
        @JvmStatic
        fun isHuaweiSeries(): Boolean {
            val current = getDeviceManufacturer()
            return current == MobileManufacturer.HUAWEI || current == MobileManufacturer.HONOR
        }

        /**
         * 检查当前设备是否为小米系列（包括红米）
         */
        @JvmStatic
        fun isXiaomiSeries(): Boolean {
            val current = getDeviceManufacturer()
            return current == MobileManufacturer.XIAOMI || 
                   Build.BRAND?.lowercase()?.contains("redmi") == true ||
                   Build.MANUFACTURER?.lowercase()?.contains("redmi") == true
        }
    }
}