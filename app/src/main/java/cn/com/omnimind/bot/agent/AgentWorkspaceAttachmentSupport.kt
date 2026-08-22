package cn.com.omnimind.bot.agent

import android.util.Base64
import cn.com.omnimind.baselib.util.OmniLog
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

internal object AgentWorkspaceAttachmentSupport {
    private const val TAG = "AgentWorkspaceAttachment"

    fun prepareAttachmentsForRuntime(
        context: android.content.Context,
        taskId: String,
        rawAttachments: List<Map<String, Any?>>
    ): List<Map<String, Any?>> {
        if (rawAttachments.isEmpty()) {
            return emptyList()
        }
        return rawAttachments.map { attachment ->
            prepareSingleAttachment(context, taskId, attachment)
        }
    }

    private fun prepareSingleAttachment(
        context: android.content.Context,
        taskId: String,
        rawAttachment: Map<String, Any?>
    ): Map<String, Any?> {
        val attachment = LinkedHashMap(rawAttachment)
        val isImage = AgentImageAttachmentSupport.isImageAttachment(attachment)
        attachment["isImage"] = isImage
        val promptPath = attachment["promptPath"]?.toString()?.trim().orEmpty()
        val workspacePath = attachment["workspacePath"]?.toString()?.trim().orEmpty()
        if (promptPath.isNotEmpty() || workspacePath.isNotEmpty()) {
            if (promptPath.isEmpty() && workspacePath.isNotEmpty()) {
                attachment["promptPath"] = workspacePath
            }
            return attachment
        }

        if (!isImage) {
            attachment["sendToModel"] = false
        }

        val localPath = attachment["path"]?.toString()?.trim().orEmpty()
        if (localPath.startsWith("http://", ignoreCase = true) ||
            localPath.startsWith("https://", ignoreCase = true)
        ) {
            return attachment
        }

        val source = localPath.takeIf { it.isNotEmpty() }?.let(::File)
        if (source != null && source.exists() && source.isFile) {
            return copyIntoWorkspace(
                context = context,
                taskId = taskId,
                source = source,
                attachment = attachment
            ) ?: attachment
        }

        val dataUrl = extractDataUrl(attachment)
        if (dataUrl.isEmpty()) {
            return attachment
        }

        return copyDataUrlIntoWorkspace(
            context = context,
            taskId = taskId,
            dataUrl = dataUrl,
            attachment = attachment
        ) ?: attachment
    }

    private fun copyIntoWorkspace(
        context: android.content.Context,
        taskId: String,
        source: File,
        attachment: LinkedHashMap<String, Any?>
    ): Map<String, Any?>? {
        val workspaceManager = AgentWorkspaceManager(context)
        workspaceManager.ensureRuntimeDirectories()
        val dir = attachmentBatchDirectory(workspaceManager, taskId) ?: return null

        val preferredName = resolveAttachmentName(attachment, source.name)
        val target = File(dir, "${UUID.randomUUID()}_${sanitizeFileName(preferredName)}")
        return try {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            buildPreparedAttachment(workspaceManager, target, attachment, preferredName)
        } catch (error: Exception) {
            OmniLog.w(
                TAG,
                "Failed to copy attachment into workspace: ${source.absolutePath}: ${error.message}"
            )
            runCatching { target.delete() }
            null
        }
    }

    private fun copyDataUrlIntoWorkspace(
        context: android.content.Context,
        taskId: String,
        dataUrl: String,
        attachment: LinkedHashMap<String, Any?>
    ): Map<String, Any?>? {
        val decoded = decodeDataUrl(dataUrl) ?: return null
        val workspaceManager = AgentWorkspaceManager(context)
        workspaceManager.ensureRuntimeDirectories()
        val dir = attachmentBatchDirectory(workspaceManager, taskId) ?: return null
        val preferredName = ensureExtension(
            resolveAttachmentName(
                attachment,
                defaultDataUrlFileName(decoded.mimeType)
            ),
            decoded.mimeType
        )
        val target = File(dir, "${UUID.randomUUID()}_${sanitizeFileName(preferredName)}")
        return try {
            decoded.bytes.use { source ->
                target.outputStream().use { sink ->
                    source.copyTo(sink)
                }
            }
            buildPreparedAttachment(
                workspaceManager = workspaceManager,
                target = target,
                attachment = attachment,
                preferredName = preferredName,
                mimeTypeHint = decoded.mimeType
            )
        } catch (error: Exception) {
            OmniLog.w(TAG, "Failed to persist dataUrl attachment: ${error.message}")
            runCatching { target.delete() }
            null
        }
    }

    private fun attachmentBatchDirectory(
        workspaceManager: AgentWorkspaceManager,
        taskId: String
    ): File? {
        val batchName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(
            workspaceManager.attachmentsDirectory(),
            "${sanitizeSegment(taskId)}/$batchName"
        )
        if (!dir.exists() && !dir.mkdirs()) {
            OmniLog.w(TAG, "Failed to create workspace attachment dir: ${dir.absolutePath}")
            return null
        }
        return dir
    }

    private fun buildPreparedAttachment(
        workspaceManager: AgentWorkspaceManager,
        target: File,
        attachment: LinkedHashMap<String, Any?>,
        preferredName: String,
        mimeTypeHint: String = ""
    ): Map<String, Any?> {
        val shellPath = workspaceManager.shellPathForAndroid(target) ?: target.absolutePath
        return LinkedHashMap(attachment).apply {
            put("path", target.absolutePath)
            put("promptPath", shellPath)
            put("workspacePath", shellPath)
            if (attachment["size"] == null && attachment["sizeBytes"] == null) {
                put("size", target.length())
            }
            val mimeType = attachment["mimeType"]?.toString()?.trim().orEmpty()
                .ifEmpty { mimeTypeHint }
                .ifEmpty { workspaceManager.guessMimeType(target) }
            if (mimeType.isNotEmpty()) {
                put("mimeType", mimeType)
            }
            if (attachment["name"]?.toString()?.trim().isNullOrEmpty()) {
                put("name", preferredName)
            }
            if (attachment["fileName"]?.toString()?.trim().isNullOrEmpty()) {
                put("fileName", preferredName)
            }
        }
    }

    private fun extractDataUrl(attachment: Map<String, Any?>): String {
        val direct = attachment["dataUrl"]?.toString()?.trim().orEmpty()
        if (direct.startsWith("data:", ignoreCase = true)) {
            return direct
        }
        val path = attachment["path"]?.toString()?.trim().orEmpty()
        if (path.startsWith("data:", ignoreCase = true)) {
            return path
        }
        val url = attachment["url"]
        val nestedUrl = when (url) {
            is Map<*, *> -> url["url"]?.toString()?.trim().orEmpty()
            else -> url?.toString()?.trim().orEmpty()
        }
        return nestedUrl.takeIf { it.startsWith("data:", ignoreCase = true) }.orEmpty()
    }

    private data class DecodedDataUrl(
        val mimeType: String,
        val bytes: InputStream
    )

    private fun decodeDataUrl(dataUrl: String): DecodedDataUrl? {
        val commaIndex = dataUrl.indexOf(',')
        if (commaIndex <= 0) {
            return null
        }
        val meta = dataUrl.substring(5, commaIndex)
        val payload = dataUrl.substring(commaIndex + 1).replace(Regex("\\s+"), "")
        val isBase64 = meta.split(';').any { it.equals("base64", ignoreCase = true) }
        if (!isBase64) {
            return null
        }
        val mimeType = meta.substringBefore(';').trim().ifEmpty {
            "application/octet-stream"
        }
        val bytes = runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull()
            ?: return null
        return DecodedDataUrl(mimeType, ByteArrayInputStream(bytes))
    }

    private fun defaultDataUrlFileName(mimeType: String): String {
        return "attachment.${extensionForMimeType(mimeType)}"
    }

    private fun ensureExtension(fileName: String, mimeType: String): String {
        val normalized = fileName.trim().ifEmpty { defaultDataUrlFileName(mimeType) }
        val base = normalized.substringBeforeLast('/', normalized)
            .substringBeforeLast('\\', normalized)
        if (base.substringAfterLast('.', "").isNotEmpty()) {
            return normalized
        }
        return "$normalized.${extensionForMimeType(mimeType)}"
    }

    private fun extensionForMimeType(mimeType: String): String {
        return when (mimeType.lowercase(Locale.US)) {
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            "text/plain" -> "txt"
            "text/markdown" -> "md"
            "application/pdf" -> "pdf"
            else -> "bin"
        }
    }

    private fun resolveAttachmentName(
        attachment: Map<String, Any?>,
        fallback: String
    ): String {
        val name = attachment["name"]?.toString()?.trim().orEmpty()
        if (name.isNotEmpty()) {
            return name
        }
        val fileName = attachment["fileName"]?.toString()?.trim().orEmpty()
        if (fileName.isNotEmpty()) {
            return fileName
        }
        return fallback
    }

    private fun sanitizeSegment(value: String): String {
        val normalized = value.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
        return normalized.ifEmpty { "agent" }
    }

    private fun sanitizeFileName(value: String): String {
        val normalized = value.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return normalized.ifEmpty { "attachment" }
    }
}
