package cn.com.omnimind.baselib.http.interceptor

import cn.com.omnimind.baselib.http.OkHttpManager
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class HeaderInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        var requestBuilder = originalRequest.newBuilder();
        requestBuilder = requestBuilder.addHeader("Content-Type", "application/json")

        OkHttpManager.getAppVersionHeaders().forEach { (key, value) ->
            requestBuilder = requestBuilder.addHeader(key, value)
        }
//            .addHeader("Accept", "application/json")
//            .addHeader("User-Agent", "Omnibot-App")

        val request = requestBuilder.build()
        return chain.proceed(request)
    }
}
