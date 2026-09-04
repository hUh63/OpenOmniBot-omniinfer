package cn.com.omnimind.baselib.runlog

/** Converts only between Android pixels and canonical 0..1000 coordinates. */
object ActionCoordinateCodec {
    data class DisplaySize(val width: Double, val height: Double)

    fun toRelative(
        args: Map<String, Any?>,
        displaySize: DisplaySize,
    ): Map<String, Any?> = convert(args, displaySize) { value, dimension ->
        value / dimension * 1000.0
    }

    fun toScreenPixels(
        args: Map<String, Any?>,
        displaySize: DisplaySize,
    ): Map<String, Any?> {
        COORDINATES.keys.forEach { name ->
            val raw = args[name] ?: return@forEach
            val number = (raw as? Number)?.toDouble()?.takeIf(Double::isFinite)
                ?: error("action_coordinate_invalid:$name")
            require(number in 0.0..1000.0) {
                "canonical_action_arg_range_invalid:$name"
            }
        }
        return convert(args, displaySize) { value, dimension ->
            value / 1000.0 * dimension
        }
    }

    private fun convert(
        args: Map<String, Any?>,
        displaySize: DisplaySize,
        transform: (Double, Double) -> Double,
    ): Map<String, Any?> {
        require(displaySize.width > 0.0 && displaySize.height > 0.0) {
            "action_coordinate_display_invalid"
        }
        return linkedMapOf<String, Any?>().apply {
            putAll(args)
            COORDINATES.forEach { (name, axis) ->
                val raw = args[name] ?: return@forEach
                val number = (raw as? Number)?.toDouble()?.takeIf(Double::isFinite)
                    ?: error("action_coordinate_invalid:$name")
                val converted = transform(
                    number,
                    if (axis == Axis.X) displaySize.width else displaySize.height,
                )
                put(name, if (converted % 1.0 == 0.0) converted.toLong() else converted)
            }
        }
    }

    private enum class Axis { X, Y }

    private val COORDINATES = mapOf(
        "x" to Axis.X,
        "x1" to Axis.X,
        "x2" to Axis.X,
        "y" to Axis.Y,
        "y1" to Axis.Y,
        "y2" to Axis.Y,
    )
}
