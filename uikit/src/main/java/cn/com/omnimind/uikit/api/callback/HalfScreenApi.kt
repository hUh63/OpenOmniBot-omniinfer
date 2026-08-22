package cn.com.omnimind.uikit.api.callback

import android.view.View

interface HalfScreenApi {
    fun onCreateFlutter(path: String): View
    fun onDestroyOrGone()
    fun onNeedOpenAppMainParam(path: String?, needClear: Boolean)
}
