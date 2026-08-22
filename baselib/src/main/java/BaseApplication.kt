import android.app.Application

open class BaseApplication : Application() {
    companion object {
        lateinit var instance: Application
        private const val TAG = "ScreenshotDetector"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
