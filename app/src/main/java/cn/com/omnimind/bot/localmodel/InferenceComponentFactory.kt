package cn.com.omnimind.bot.localmodel

import android.app.Service
import android.content.Intent
import androidx.core.app.CoreComponentFactory
import cn.com.omnimind.bot.App

/**
 * Android owns service attachment and lifecycle; only class resolution is customized.
 *
 * The `com.omniinfer.server.OmniInferService` class name is shared by two packs: the optional
 * downloadable component and the `:omniinfer-server` module that the omniinfer build flavour
 * bundles inside the APK. Route through the downloaded DexClassLoader only when that component
 * is actually installed; otherwise let the ordinary app classloader resolve the bundled class
 * (classLoader() would otherwise abort on a missing payload and take the bundled service down).
 */
class InferenceComponentFactory : CoreComponentFactory() {
    override fun instantiateService(cl: ClassLoader, className: String, intent: Intent?): Service {
        val resolved = if (className == DownloadableInferenceRuntime.SERVICE &&
            DownloadableInferenceRuntime.isInstalled(App.instance)
        ) {
            DownloadableInferenceRuntime.classLoader(App.instance)
        } else {
            cl
        }
        return super.instantiateService(resolved, className, intent)
    }
}
