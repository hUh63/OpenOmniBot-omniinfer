package cn.com.omnimind.baselib.runlog

import java.security.MessageDigest

data class State(
    val stateId: String,
    val packageName: String,
    val activityName: String,
    val displayWidth: Int,
    val displayHeight: Int,
    val xml: String,
    val screenshotPath: String? = null,
) {
    fun asMap(): Map<String, Any?> = linkedMapOf<String, Any?>(
        "state_id" to stateId,
        "package_name" to packageName,
        "activity_name" to activityName,
        "display" to linkedMapOf(
            "width" to displayWidth,
            "height" to displayHeight,
        ),
        "xml" to xml,
        "screenshot_path" to screenshotPath,
    ).filterValues { value ->
        value != null && (value !is String || value.isNotEmpty())
    }

    companion object {
        fun fromMap(value: Map<String, Any?>): State {
            val display = stringMap(value["display"])
            return State(
                stateId = value["state_id"]?.toString()?.trim().orEmpty().also {
                    require(it.isNotEmpty()) { "state_id_required" }
                },
                packageName = value["package_name"]?.toString()?.trim().orEmpty(),
                activityName = value["activity_name"]?.toString()?.trim().orEmpty(),
                displayWidth = positiveInt(display["width"], "state_display_width_required"),
                displayHeight = positiveInt(display["height"], "state_display_height_required"),
                xml = value["xml"]?.toString().orEmpty(),
                screenshotPath = value["screenshot_path"]?.toString()?.trim()
                    ?.takeIf(String::isNotEmpty),
            )
        }

        fun create(
            packageName: String,
            activityName: String,
            displayWidth: Int,
            displayHeight: Int,
            xml: String,
            screenshotPath: String? = null,
        ): State {
            require(displayWidth > 0 && displayHeight > 0) { "state_display_invalid" }
            val identity = listOf(
                packageName,
                activityName,
                xml,
                displayWidth.toString(),
                displayHeight.toString(),
            ).joinToString("\u0000")
            return State(
                stateId = "state_${sha256(identity).take(20)}",
                packageName = packageName,
                activityName = activityName,
                displayWidth = displayWidth,
                displayHeight = displayHeight,
                xml = xml,
                screenshotPath = screenshotPath,
            )
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

        private fun stringMap(value: Any?): Map<String, Any?> =
            (value as? Map<*, *>)
                ?.entries
                ?.associateTo(linkedMapOf()) { (key, item) -> key.toString() to item }
                .orEmpty()

        private fun positiveInt(value: Any?, error: String): Int {
            val number = when (value) {
                is Number -> value.toInt()
                else -> value?.toString()?.trim()?.toIntOrNull()
            }
            require(number != null && number > 0) { error }
            return number
        }
    }
}
