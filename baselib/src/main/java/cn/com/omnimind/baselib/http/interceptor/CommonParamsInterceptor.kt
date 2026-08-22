package cn.com.omnimind.baselib.http.interceptor

import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class CommonParamsInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val newRequest: okhttp3.Request

        // 添加公共参数
        val commonParams = getCommonParams()

        newRequest = when (originalRequest.body) {
            is FormBody -> {
                // 处理表单请求
                val originalFormBody = originalRequest.body as FormBody
                val newFormBodyBuilder = FormBody.Builder()

                // 添加原有参数
                for (i in 0 until originalFormBody.size) {
                    newFormBodyBuilder.addEncoded(
                        originalFormBody.encodedName(i),
                        originalFormBody.encodedValue(i)
                    )
                }

                // 添加公共参数
                for ((key, value) in commonParams) {
                    newFormBodyBuilder.add(key, value)
                }

                originalRequest.newBuilder()
                    .method(originalRequest.method, newFormBodyBuilder.build())
                    .build()
            }
            else -> {
                // 处理URL参数
                val originalHttpUrl = originalRequest.url
                val urlBuilder = originalHttpUrl.newBuilder()

                // 添加公共参数
                for ((key, value) in commonParams) {
                    urlBuilder.addQueryParameter(key, value)
                }

                originalRequest.newBuilder()
                    .url(urlBuilder.build())
                    .build()
            }
        }

        return chain.proceed(newRequest)
    }

    private fun getCommonParams(): Map<String, String> {
        val commonParams = HashMap<String, String>()
        // 添加公共参数，如设备ID、版本号等
        commonParams["platform"] = "android"
        commonParams["version"] = "1.0.0"
        // TODO: 添加更多公共参数
        return commonParams
    }
}