package cn.com.omnimind.bot.localmodel

import android.app.Service
import android.content.Intent
import androidx.core.app.CoreComponentFactory
import cn.com.omnimind.bot.App

/**
 * Android owns service attachment and lifecycle; only class resolution is customized.
 *
 * `com.omniinfer.server.OmniInferService` is served by one engine with two possible homes: the
 * optional downloadable payload, or the `:omniinfer-server` module that the omniinfer flavour
 * bundles inside the APK. Ask [DownloadableInferenceRuntime] which loader owns the name; when it
 * answers null (no bundled engine, nothing downloaded) keep the ordinary app class loader.
 */
class InferenceComponentFactory : CoreComponentFactory() {
    override fun instantiateService(cl: ClassLoader, className: String, intent: Intent?): Service {
        val resolved = if (className == DownloadableInferenceRuntime.SERVICE) {
            DownloadableInferenceRuntime.serviceClassLoader(App.instance) ?: cl
        } else {
            cl
        }
        return super.instantiateService(resolved, className, intent)
    }
}
