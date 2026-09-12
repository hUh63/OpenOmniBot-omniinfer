package cn.com.omnimind.bot.webchat

import android.content.Context
import io.ktor.http.ContentType
import java.io.File

data class WebChatAvatarPayload(
    val bytes: ByteArray,
    val contentType: ContentType
)

/**
 * Resolves the same Agent avatar currently selected by the Flutter UI.
 *
 * Flutter's legacy SharedPreferences API stores prefixed keys in
 * `FlutterSharedPreferences`. Preset images already ship in Flutter's APK asset
 * bundle, while cropped custom avatars live under the app-private support
 * directory. WebChat therefore reuses both sources without packaging another
 * copy of the avatar resources.
 */
class WebChatAvatarService(private val context: Context) {

    fun load(): WebChatAvatarPayload {
        val preferences = context.getSharedPreferences(
            FLUTTER_SHARED_PREFERENCES,
            Context.MODE_PRIVATE
        )
        val customPath = runCatching {
            preferences.getString(CUSTOM_AVATAR_KEY, null)?.trim().orEmpty()
        }.getOrDefault("")
        resolveCustomAvatar(customPath)?.let { return it }

        val rawPresetIndex = runCatching {
            preferences.getLong(PRESET_AVATAR_KEY, 0L)
        }.getOrDefault(0L)
        val assetPath = PRESET_AVATAR_ASSETS[normalizePresetIndex(rawPresetIndex)]
        return WebChatAvatarPayload(
            bytes = context.assets.open(assetPath).use { it.readBytes() },
            contentType = ContentType.Image.PNG
        )
    }

    private fun resolveCustomAvatar(path: String): WebChatAvatarPayload? {
        if (path.isBlank()) return null
        val file = File(path)
        val managedDirectory = File(context.filesDir, CUSTOM_AVATAR_DIRECTORY)
        if (!isManagedCustomAvatar(file, managedDirectory) || !file.isFile) return null
        return runCatching {
            WebChatAvatarPayload(
                bytes = file.readBytes(),
                contentType = ContentType.Image.PNG
            )
        }.getOrNull()
    }

    companion object {
        private const val FLUTTER_SHARED_PREFERENCES = "FlutterSharedPreferences"
        private const val PRESET_AVATAR_KEY = "flutter.agentAvatarIndex"
        private const val CUSTOM_AVATAR_KEY = "flutter.agentAvatarCustomImagePath"
        private const val CUSTOM_AVATAR_DIRECTORY = "agent_avatars"

        internal val PRESET_AVATAR_ASSETS = listOf(
            "flutter_assets/assets/avatar/default_avatar1.png",
            "flutter_assets/assets/avatar/default_avatar2.png",
            "flutter_assets/assets/avatar/default_avatar3.png",
            "flutter_assets/assets/avatar/default_avatar4.png",
            "flutter_assets/assets/avatar/default_avatar5.png",
            "flutter_assets/assets/avatar/default_avatar6.png"
        )

        internal fun normalizePresetIndex(index: Long): Int {
            return index.toInt().takeIf { it in PRESET_AVATAR_ASSETS.indices } ?: 0
        }

        internal fun isManagedCustomAvatar(file: File, managedDirectory: File): Boolean {
            return runCatching {
                file.canonicalFile.parentFile == managedDirectory.canonicalFile
            }.getOrDefault(false)
        }
    }
}
