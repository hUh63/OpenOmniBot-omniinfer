package cn.com.omnimind.bot.agent.runtime

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.InputStream
import java.util.UUID

internal const val NA_ATTACHMENT_MAX_BYTES = 10L * 1024 * 1024
internal class NaAttachmentCancelled : Exception()

/** Preparation belongs to the existing prompt reservation. Validate every file
 * before uploading, and admit the message only after every PUT succeeds.
 * Temporary copies bound reads from content providers whose size is unknown.
 */
internal suspend fun <T> naSubmitWithAttachments(
    text: String,
    attachments: List<Map<String, Any?>>,
    reservation: String,
    cacheDirectory: File,
    open: (Map<String, Any?>) -> InputStream,
    upload: suspend (String, File, () -> Unit) -> Unit,
    checkActive: () -> Unit,
    includeReferences: Boolean = true,
    cleanup: suspend () -> Unit = {},
    submit: suspend (String) -> T,
): T = withContext(Dispatchers.IO) {
    checkActive()
    if (attachments.isEmpty()) return@withContext submit(text)
    require(attachments.size <= 20) { "一次最多上传 20 个附件。" }
    val paths = attachments.mapIndexed { index, attachment ->
        val size = (attachment["size"] as? Number)?.toLong()
        require(size == null || size <= NA_ATTACHMENT_MAX_BYTES) { "附件不能超过 10 MiB，请重新选择。" }
        val name = naAttachmentFilename((attachment["name"] ?: attachment["fileName"])?.toString().orEmpty())
        "attachment-${UUID.nameUUIDFromBytes("$reservation/$index".toByteArray(Charsets.UTF_8))}-$name"
    }
    // Match Na's existing web attachment contract; never send phone-local paths.
    val message = (listOf(text.trim()).filter { it.isNotEmpty() } + paths.map { "[附件] $it" }).joinToString("\n\n")
    require(message.length <= 16000) { "消息和附件名称过长，请减少附件或缩短消息。" }
    val staged = mutableListOf<File>()
    try {
        attachments.forEach { attachment ->
            checkActive()
            val file = File.createTempFile("na-upload-", ".tmp", cacheDirectory)
            staged += file
            open(attachment).use { source ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        checkActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= NA_ATTACHMENT_MAX_BYTES) { "附件不能超过 10 MiB，请重新选择。" }
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
        staged.forEachIndexed { index, file ->
            checkActive()
            upload(paths[index], file, checkActive)
        }
        checkActive()
        submit(if (includeReferences) message else text.trim())
    } catch (error: Throwable) {
        // The server owns the committed/pending distinction, including a lost
        // POST response. Logout/offline failures are covered by its expiry job.
        withContext(NonCancellable) { runCatching { withTimeout(10000) { cleanup() } } }
        throw error
    } finally {
        staged.forEach { it.delete() }
    }
}

internal fun naAttachmentFilename(raw: String): String {
    val name = raw.replace('\\', '/').substringAfterLast('/').trim()
    val safe = name.map { if (it.isLetterOrDigit() || it in "._- ") it else '_' }.joinToString("")
        .trim('.', ' ').ifEmpty { "attachment" }
    // Keep extensions while leaving ample room for the UUID and UTF-8 filenames.
    val extension = safe.substringAfterLast('.', "").takeIf { it.length in 1..12 && it.all(Char::isLetterOrDigit) }
    val suffix = extension?.let { ".$it" }.orEmpty()
    val stem = if (suffix.isEmpty()) safe else safe.removeSuffix(suffix)
    var prefix = ""
    for (character in stem) {
        if ((prefix + character + suffix).toByteArray(Charsets.UTF_8).size > 120) break
        prefix += character
    }
    return prefix.ifEmpty { "attachment" } + suffix
}

internal suspend fun naUploadAttachment(
    connection: NaCloudConnection, client: OkHttpClient, path: String, file: File, checkActive: () -> Unit,
    owner: Map<String, String> = emptyMap(),
) {
    val id = path.removePrefix("attachment-").take(36)
    val url = if (owner.isEmpty()) (connection.apiBaseUrl + "/api/files/content").toHttpUrl().newBuilder()
        .addQueryParameter("path", path).build()
    else (connection.apiBaseUrl + "/api/files/uploads/$id").toHttpUrl().newBuilder().apply {
        owner.forEach { (key, value) -> addQueryParameter(key, value) }
        addQueryParameter("name", path.removePrefix("attachment-$id-"))
    }.build()
    val body = object : RequestBody() {
        override fun contentType() = "application/octet-stream".toMediaType()
        override fun contentLength() = file.length()
        override fun writeTo(sink: BufferedSink) {
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    checkActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    sink.write(buffer, 0, count)
                }
            }
        }
    }
    connection.execute(client, Request.Builder().url(url).put(body).build()) { response ->
        check(response.isSuccessful) {
            when (response.code) {
                413 -> "附件不能超过 10 MiB，请重新选择。"
                507 -> "云端空间不足，请清理文件后再试。"
                else -> "附件上传失败（HTTP ${response.code}），消息尚未发送，请重试。"
            }
        }
        val result = runCatching { Gson().fromJson(response.body.string(), Map::class.java) }.getOrNull()
        check(result?.get("saved") == true) { "未能确认附件上传成功，消息尚未发送，请重试。" }
    }
    checkActive()
}
