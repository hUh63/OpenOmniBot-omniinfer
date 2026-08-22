package cn.com.omnimind.baselib.util


fun Int.getResString(): String {
    return BaseApplication.instance.resources.getString(this)
}

fun Int.getResColor(): Int {
    return BaseApplication.instance.resources.getColor(this, null)
}
