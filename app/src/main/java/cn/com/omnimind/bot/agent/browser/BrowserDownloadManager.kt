package cn.com.omnimind.bot.agent

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class BrowserDownloadManager(
    context: Context,
    workspaceId: String,
    private val store: BrowserHostStore,
    private val onChanged: () -> Unit
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val okHttpClient = OkHttpClient()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val downloadsDir = File(appContext.filesDir, "browser_downloads/${safeWorkspaceDir(workspaceId)}")

    init {
        downloadsDir.mkdirs()
    }

    fun snapshot(): List<BrowserDownloadTaskRecord> = store.listDownloads()

    fun activeCount(): Int {
        return snapshot().count { it.status == STATUS_QUEUED || it.status == STATUS_DOWNLOADING }
    }

    fun failedCount(): Int = snapshot().count { it.status == STATUS_FAILED }

    fun overallProgress(): Double? {
        val active = snapshot().filter {
            (it.status == STATUS_QUEUED || it.status == STATUS_DOWNLOADING) && it.totalBytes > 0L
        }
        if (active.isEmpty()) {
            return null
        }
        val total = active.sumOf { it.totalBytes }
        if (total <= 0L) {
            return null
        }
        val downloaded = active.sumOf { it.downloadedBytes.coerceAtMost(it.totalBytes) }
        return downloaded.toDouble() / total.toDouble()
    }

    fun latestCompletedFileName(): String? {
        return snapshot()
            .filter { it.status == STATUS_COMPLETED }
            .maxByOrNull { it.updatedAt }
            ?.fileName
    }

    fun enqueueHttpDownload(
        url: String,
        fileName: String,
        mimeType: String? = null,
        headers: Map<String, String> = emptyMap()
    ): BrowserDownloadTaskRecord {
        val now = System.currentTimeMillis()
        val targetFile = buildDownloadFile(fileName)
        val record = BrowserDownloadTaskRecord(
            id = UUID.randomUUID().toString(),
            url = url,
            fileName = targetFile.name,
            mimeType = mimeType,
            destinationPath = targetFile.absolutePath,
            status = STATUS_QUEUED,
            createdAt = now,
            updatedAt = now,
            supportsResume = true
        )
        store.upsertDownload(record)
        onChanged()
        launchOrResume(record.id, headers)
        return record
    }

    fun saveInlineDownload(
        sourceUrl: String,
        fileName: String,
        mimeType: String?,
        bytes: ByteArray
    ): BrowserDownloadTaskRecord {
        val now = System.currentTimeMillis()
        val targetFile = buildDownloadFile(fileName)
        targetFile.writeBytes(bytes)
        val record = BrowserDownloadTaskRecord(
            id = UUID.randomUUID().toString(),
            url = sourceUrl,
            fileName = targetFile.name,
            mimeType = mimeType,
            destinationPath = targetFile.absolutePath,
            status = STATUS_COMPLETED,
            createdAt = now,
            updatedAt = now,
            downloadedBytes = bytes.size.toLong(),
            totalBytes = bytes.size.toLong(),
            supportsResume = false
        )
        store.upsertDownload(record)
        onChanged()
        return record
    }

    fun pause(taskId: String): Boolean {
        val record = snapshot().firstOrNull { it.id == taskId } ?: return false
        if (record.status != STATUS_QUEUED && record.status != STATUS_DOWNLOADING) {
            return false
        }
        activeJobs.remove(taskId)?.cancel()
        store.upsertDownload(record.copy(status = STATUS_PAUSED, updatedAt = System.currentTimeMillis()))
        onChanged()
        return true
    }

    fun cancel(taskId: String): Boolean {
        val record = snapshot().firstOrNull { it.id == taskId } ?: return false
        activeJobs.remove(taskId)?.cancel()
        store.upsertDownload(record.copy(status = STATUS_CANCELED, updatedAt = System.currentTimeMillis()))
        onChanged()
        return true
    }

    fun resume(taskId: String): Boolean {
        val record = snapshot().firstOrNull { it.id == taskId } ?: return false
        if (!record.supportsResume) {
            return retry(taskId)
        }
        if (record.status != STATUS_PAUSED && record.status != STATUS_CANCELED && record.status != STATUS_FAILED) {
            return false
        }
        launchOrResume(taskId)
        return true
    }

    fun retry(taskId: String): Boolean {
        val record = snapshot().firstOrNull { it.id == taskId } ?: return false
        File(record.destinationPath).takeIf { it.exists() }?.delete()
        store.upsertDownload(
            record.copy(
                status = STATUS_QUEUED,
                updatedAt = System.currentTimeMillis(),
                downloadedBytes = 0L,
                totalBytes = 0L,
                errorMessage = null
            )
        )
        onChanged()
        launchOrResume(taskId)
        return true
    }

    fun delete(
        taskId: String,
        deleteFile: Boolean
    ): Boolean {
        val record = snapshot().firstOrNull { it.id == taskId } ?: return false
        activeJobs.remove(taskId)?.cancel()
        if (deleteFile) {
            val file = File(record.destinationPath)
            runCatching { file.delete() }
            // 分片与 ETag sidecar 也要清，否则删了任务还占着几 GB 空间。
            runCatching { File(file.parentFile, file.name + ".part").delete() }
            runCatching { File(file.parentFile, file.name + ".part.etag").delete() }
        }
        store.removeDownload(taskId)
        onChanged()
        return true
    }

    fun shutdown() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        scope.cancel()
    }

    private fun launchOrResume(
        taskId: String,
        headers: Map<String, String> = emptyMap()
    ) {
        activeJobs[taskId]?.cancel()
        activeJobs[taskId] = scope.launch {
            val original = snapshot().firstOrNull { it.id == taskId } ?: return@launch
            val targetFile = File(original.destinationPath)
            // 先写 <name>.part，完成后再改名：直接写最终路径时，中途杀进程/断网会留下一个
            // 「看起来完整」的半截文件（用户点开是坏的），而续传又会把两个版本拼在一起。
            val partFile = File(targetFile.parentFile, targetFile.name + ".part")
            val etagFile = File(targetFile.parentFile, targetFile.name + ".part.etag")
            if (!partFile.exists() && targetFile.exists() && targetFile.length() > 0L) {
                // 旧版本直接写最终文件：把它当作已下载的分片继续，不必从头再下。
                targetFile.renameTo(partFile)
            }
            val existingSize = if (partFile.exists()) partFile.length() else 0L
            val ifRange = if (existingSize > 0L) {
                runCatching { etagFile.readText().trim() }.getOrNull().orEmpty()
            } else 
            update(
                original.copy(
                    status = STATUS_DOWNLOADING,
                    updatedAt = System.currentTimeMillis(),
                    downloadedBytes = existingSize,
                    errorMessage = null
                )
            )
            try {
                val requestBuilder = Request.Builder().url(original.url)
                headers.forEach { (name, value) ->
                    requestBuilder.header(name, value)
                }
                if (existingSize > 0L) {
                    requestBuilder.header("Range", "bytes=$existingSize-")
                    // 远端文件换了（ETag 变化）时 If-Range 会让服务器回 200 全量，
                    // 而不是把新旧两段拼成一个损坏文件。
                    if (ifRange.isNotEmpty()) requestBuilder.header("If-Range", ifRange)
                }
                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful && response.code !in 200..299 && response.code != 206) {
                    // 不关闭 response 的话连接不能归还、也不能复用：反复失败会堆 socket
                    // 直到 GC。
                    response.close()
                    throw IllegalStateException("HTTP ${response.code}")
                }
                val responseBody = response.body ?: throw IllegalStateException("empty_response_body")
                val contentLength = responseBody.contentLength()
                val append = existingSize > 0L && response.code == 206
                if (!append && partFile.exists()) {
                    partFile.delete()
                }
                targetFile.parentFile?.mkdirs()
                runCatching {
                    val etag = response.header("ETag")?.takeIf { it.isNotBlank() }
                    if (etag != null) etagFile.writeText(etag) else etagFile.delete()
                }
                val sink = FileOutputStream(partFile, append).buffered()
                var downloaded = if (append) existingSize else 0L
                // 每读一个 8KB 分片就 update() 一次（持久化 + 通知上层）：500MB 文件约
                // 6 万次写库与 6 万次 UI 通知，写入放大还把列表刷到卡顿。按时间/字节节流，
                // 完成与失败路径仍会写最终状态。
                var lastProgressPersistAtMs = 0L
                var lastProgressPersistBytes = 0L
                val expectedTotal = if (contentLength > 0L) {
                    if (append) existingSize + contentLength else contentLength
                } else {
                    0L
                }
                sink.use { output ->
                    responseBody.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) {
                                break
                            }
                            output.write(buffer, 0, read)
                            downloaded += read.toLong()
                            val progressNow = System.currentTimeMillis()
                            if (progressNow - lastProgressPersistAtMs >= 300L ||
                                downloaded - lastProgressPersistBytes >= 512L * 1024L
                            ) {
                                lastProgressPersistAtMs = progressNow
                                lastProgressPersistBytes = downloaded
                                update(
                                    original.copy(
                                        status = STATUS_DOWNLOADING,
                                        updatedAt = progressNow,
                                        downloadedBytes = downloaded,
                                        totalBytes = expectedTotal,
                                        mimeType = response.header("Content-Type") ?: original.mimeType,
                                        errorMessage = null
                                    )
                                )
                            }
                        }
                    }
                }
                // 只有真正写完才把 .part 改名成目标文件：之前半截数据也会被当成完成。
                if (!partFile.renameTo(targetFile)) {
                    runCatching { targetFile.delete() }
                    check(partFile.renameTo(targetFile)) { "download_rename_failed" }
                }
                runCatching { etagFile.delete() }
                update(
                    original.copy(
                        status = STATUS_COMPLETED,
                        updatedAt = System.currentTimeMillis(),
                        downloadedBytes = downloaded,
                        totalBytes = if (expectedTotal > 0L) expectedTotal else downloaded,
                        mimeType = response.header("Content-Type") ?: original.mimeType,
                        errorMessage = null
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val latest = snapshot().firstOrNull { it.id == taskId } ?: original
                update(
                    latest.copy(
                        status = STATUS_FAILED,
                        updatedAt = System.currentTimeMillis(),
                        errorMessage = error.message ?: "download_failed"
                    )
                )
            } finally {
                activeJobs.remove(taskId)
            }
        }
    }

    private fun update(record: BrowserDownloadTaskRecord) {
        store.upsertDownload(record)
        onChanged()
    }

    private fun buildDownloadFile(fileName: String): File {
        val sanitized = fileName.trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "download_${UUID.randomUUID().toString().take(8)}.bin" }
        var candidate = File(downloadsDir, sanitized)
        if (!candidate.exists()) {
            return candidate
        }
        val base = sanitized.substringBeforeLast('.', sanitized)
        val extension = sanitized.substringAfterLast('.', "")
        var index = 1
        while (candidate.exists()) {
            val nextName = if (extension.isBlank()) {
                "${base}_$index"
            } else {
                "${base}_$index.$extension"
            }
            candidate = File(downloadsDir, nextName)
            index += 1
        }
        return candidate
    }

    private fun safeWorkspaceDir(workspaceId: String): String {
        return workspaceId.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9._-]"), "_")
            .ifBlank { "default" }
    }

    companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_PAUSED = "paused"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELED = "canceled"
    }
}
