package cn.com.omnimind.baselib.util

fun Int.dpToPx(): Int {
    return (this * BaseApplication.instance.resources.displayMetrics.density + 0.5f).toInt()
}
fun Int.dpToFloatPx(): Float {
    return (this * BaseApplication.instance.resources.displayMetrics.density + 0.5f)
}
fun Float.dpToFloatPx(): Float {
    return (this * BaseApplication.instance.resources.displayMetrics.density + 0.5f)
}
