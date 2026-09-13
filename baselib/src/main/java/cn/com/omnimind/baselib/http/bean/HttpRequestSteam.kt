package cn.com.omnimind.baselib.http.bean

import kotlinx.coroutines.flow.Flow
import okhttp3.sse.EventSource

data class HttpRequestSteam<T>(var flow: Flow<T>, var eventSource: EventSource)
