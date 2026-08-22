import com.android.build.api.dsl.ApplicationExtension
import org.gradle.kotlin.dsl.configure

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
}

val forcedCoroutinesVersion = libs.versions.kotlinxCoroutines.get()
val androidNdkVersion = providers.gradleProperty("OMNI_NDK_VERSION").getOrElse("28.2.13676358")

subprojects {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        val storageUrl: String =
            System.getenv("FLUTTER_STORAGE_BASE_URL") ?: "https://storage.googleapis.com"
        maven(url = "$storageUrl/download.flutter.io")
    }

    configurations.configureEach {
        resolutionStrategy.force(
            "org.jetbrains.kotlinx:kotlinx-coroutines-core:$forcedCoroutinesVersion",
            "org.jetbrains.kotlinx:kotlinx-coroutines-android:$forcedCoroutinesVersion",
        )
    }

    if (path == ":app") {
        plugins.withId("com.android.application") {
            extensions.configure<ApplicationExtension> {
                ndkVersion = androidNdkVersion
            }
        }
    }
}
