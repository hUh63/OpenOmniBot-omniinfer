package cn.com.omnimind.bot.webchat

import android.content.Context
import cn.com.omnimind.bot.agent.AgentWorkspaceDescriptor
import cn.com.omnimind.bot.agent.AgentWorkspaceManager
import java.io.File

/** 预览窗口的字符上限；top-level 的流式切片函数也要用它，故提到文件级。 */
private const val PREVIEW_MAX_CHARS = 200_000

class WorkspaceFileService(
    context: Context
) {
    private companion object {
        /** 超过这个体积就不再整文件读入预览（String 还会按 UTF-16 再翻一倍）。 */
        const val DEFAULT_PREVIEW_MAX_BYTES = 1L * 1024L * 1024L
        const val DEFAULT_PREVIEW_MAX_CHARS = PREVIEW_MAX_CHARS
    }

    private val workspaceManager = AgentWorkspaceManager(context)

    fun getWorkspaceDescriptor(
        conversationId: Long? = null,
        agentRunId: String = "webchat"
    ): AgentWorkspaceDescriptor {
        return workspaceManager.buildWorkspaceDescriptor(
            conversationId = conversationId,
            agentRunId = agentRunId
        )
    }

    fun bootstrapPayload(): Map<String, Any?> {
        val workspace = getWorkspaceDescriptor()
        return linkedMapOf(
            "workspace" to workspace.toPayload(),
            "root" to filePayload(File(workspace.androidRootPath), workspace)
        )
    }

    fun list(
        path: String?,
        recursive: Boolean = false,
        maxDepth: Int? = null,
        limit: Int? = null
    ): Map<String, Any?> {
        val workspace = getWorkspaceDescriptor()
        val directory = if (path.isNullOrBlank()) {
            File(workspace.androidRootPath)
        } else {
            workspaceManager.resolvePath(
                inputPath = path,
                workspace = workspace
            )
        }
        require(directory.exists() && directory.isDirectory) {
            "目录不存在：${directory.absolutePath}"
        }
        val items = selectWorkspaceFiles(
            directory = directory,
            recursive = recursive,
            maxDepth = maxDepth,
            limit = limit
        )
        return linkedMapOf(
            "path" to (workspaceManager.shellPathForAndroid(directory) ?: directory.absolutePath),
            "androidPath" to directory.absolutePath,
            "items" to items.map { filePayload(it, workspace) }
        )
    }

    fun stat(path: String): Map<String, Any?> {
        val workspace = getWorkspaceDescriptor()
        val file = workspaceManager.resolvePath(
            inputPath = path,
            workspace = workspace,
            allowRootDirectories = true
        )
        require(file.exists()) { "路径不存在：${file.absolutePath}" }
        return filePayload(file, workspace)
    }

    fun readFile(
        path: String,
        maxChars: Int? = null,
        offset: Int = 0,
        lineStart: Int? = null,
        lineCount: Int? = null
    ): Map<String, Any?> {
        val workspace = getWorkspaceDescriptor()
        val file = workspaceManager.resolvePath(
            inputPath = path,
            workspace = workspace
        )
        require(file.exists() && file.isFile) { "文件不存在：${file.absolutePath}" }
        // 只请求「一段」时走流式读。原来无论请求多少字符都先 readText()，
        // 工作区里上百 MB 的日志/模型文件会让预览直接把内存打爆（String 还要
        // 按 UTF-16 再翻一倍）。只有真正要全文的请求才整文件读入。
        if (maxChars != null || offset > 0 || lineStart != null) {
            val streamedSlice = sliceWorkspaceFileByStream(
                file = file,
                maxChars = maxChars,
                offset = offset,
                lineStart = lineStart,
                lineCount = lineCount
            )
            return linkedMapOf(
                "file" to filePayload(file, workspace),
                "content" to streamedSlice.content,
                "truncated" to streamedSlice.truncated
            )
        }
        // 不带任何切片参数时以前是整文件 readText()：工作区里一个几百 MB 的日志、
        // 模型或 SQL dump 就能把这个进程打爆（预览一次、整个 App 连同正在跑的 Agent
        // 一起被杀）。超过阈值时按默认窗口流式读取，并如实标记 truncated。
        if (file.length() > DEFAULT_PREVIEW_MAX_BYTES) {
            val streamedSlice = sliceWorkspaceFileByStream(
                file = file,
                maxChars = DEFAULT_PREVIEW_MAX_CHARS,
                offset = 0,
                lineStart = null,
                lineCount = null
            )
            return linkedMapOf(
                "file" to filePayload(file, workspace),
                "content" to streamedSlice.content,
                "truncated" to streamedSlice.truncated
            )
        }
        val content = file.readText()
        val renderedContent = sliceWorkspaceFileText(
            content = content,
            maxChars = maxChars,
            offset = offset,
            lineStart = lineStart,
            lineCount = lineCount
        )
        return linkedMapOf(
            "file" to filePayload(file, workspace),
            "content" to renderedContent.content,
            "truncated" to renderedContent.truncated
        )
    }

    fun writeFile(
        path: String,
        content: String,
        append: Boolean = false
    ): Map<String, Any?> {
        val workspace = getWorkspaceDescriptor()
        val file = workspaceManager.resolvePath(
            inputPath = path,
            workspace = workspace
        )
        file.parentFile?.mkdirs()
        if (append) {
            file.appendText(content)
        } else {
            file.writeText(content)
        }
        publishWorkspaceChanged("write", file, workspace)
        return filePayload(file, workspace)
    }

    fun move(
        sourcePath: String,
        targetPath: String,
        overwrite: Boolean = false
    ): Map<String, Any?> {
        val workspace = getWorkspaceDescriptor()
        val source = workspaceManager.resolvePath(
            inputPath = sourcePath,
            workspace = workspace
        )
        val target = workspaceManager.resolvePath(
            inputPath = targetPath,
            workspace = workspace
        )
        require(source.exists()) { "源文件不存在：${source.absolutePath}" }
        // 同路径 = 自己覆盖自己（原来会先把目标 deleteRecursively 掉，源就没了，
        // 接着 copy 因源不存在失败：文件不可恢复地丢失）；目标在源目录内部 = 把目录
        // 拷进自己的子目录，磁盘被递归撑爆。两种都在动手前拦掉。
        val sourceCanonical = source.canonicalFile
        val targetCanonical = target.canonicalFile
        require(sourceCanonical != targetCanonical) { "源与目标相同：${target.absolutePath}" }
        require(!targetCanonical.path.startsWith(sourceCanonical.path + File.separator)) {
            "目标不能位于源目录内部：${target.absolutePath}"
        }
        require(overwrite || !target.exists()) { "目标已存在：${target.absolutePath}" }
        target.parentFile?.mkdirs()
        // 先拷到同父目录的临时名，成功后再替换目标、最后删源：任何一步失败都不会
        // 让源文件先消失。
        val staging = File(targetCanonical.parentFile, ".${targetCanonical.name}.omnibot-move")
        staging.deleteRecursively()
        try {
            source.copyRecursively(staging, overwrite = true)
            if (target.exists()) {
                require(target.deleteRecursively()) { "无法覆盖目标：${target.absolutePath}" }
            }
            require(staging.renameTo(target)) { "重命名失败：${target.absolutePath}" }
            source.deleteRecursively()
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
        publishWorkspaceChanged("move", target, workspace)
        return linkedMapOf(
            "source" to filePayload(source, workspace),
            "target" to filePayload(target, workspace)
        )
    }

    fun delete(
        path: String,
        recursive: Boolean = false
    ) {
        val workspace = getWorkspaceDescriptor()
        val file = workspaceManager.resolvePath(
            inputPath = path,
            workspace = workspace
        )
        require(file.exists()) { "路径不存在：${file.absolutePath}" }
        if (file.isDirectory) {
            require(recursive) { "删除目录需要 recursive=true" }
            file.deleteRecursively()
        } else {
            require(file.delete()) { "删除失败：${file.absolutePath}" }
        }
        publishWorkspaceChanged("delete", file, workspace)
    }

    fun resolveDownloadFile(path: String): Pair<File, String> {
        val workspace = getWorkspaceDescriptor()
        val file = workspaceManager.resolvePath(
            inputPath = path,
            workspace = workspace
        )
        require(file.exists() && file.isFile) { "文件不存在：${file.absolutePath}" }
        return file to workspaceManager.guessMimeType(file)
    }

    private fun publishWorkspaceChanged(
        action: String,
        file: File,
        workspace: AgentWorkspaceDescriptor
    ) {
        val payload = linkedMapOf<String, Any?>(
            "action" to action,
            "path" to (workspaceManager.shellPathForAndroid(file) ?: file.absolutePath),
            "workspaceId" to workspace.id
        )
        RealtimeHub.publish("workspace_changed", payload)
    }

    private fun filePayload(
        file: File,
        workspace: AgentWorkspaceDescriptor
    ): Map<String, Any?> {
        val canonical = file.canonicalFile
        val displayName = file.name.ifBlank { canonical.name.ifBlank { "/" } }
        val mimeType = if (canonical.isFile) {
            workspaceManager.guessMimeType(canonical)
        } else {
            "inode/directory"
        }
        return linkedMapOf(
            "name" to displayName,
            "path" to (workspaceManager.shellPathForAndroid(canonical) ?: canonical.absolutePath),
            "androidPath" to canonical.absolutePath,
            "uri" to workspaceManager.uriForFile(canonical),
            "isDirectory" to canonical.isDirectory,
            "isFile" to canonical.isFile,
            "size" to if (canonical.isFile) canonical.length() else 0L,
            "lastModified" to canonical.lastModified(),
            "mimeType" to mimeType,
            "previewKind" to workspaceManager.previewKindForMime(mimeType),
            "workspaceId" to workspace.id
        )
    }

    private fun AgentWorkspaceDescriptor.toPayload(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "rootPath" to rootPath,
            "androidRootPath" to androidRootPath,
            "uriRoot" to uriRoot,
            "currentCwd" to currentCwd,
            "androidCurrentCwd" to androidCurrentCwd,
            "shellRootPath" to shellRootPath,
            "retentionPolicy" to retentionPolicy
        )
    }
}

/**
 * WebChat is a workspace viewer, not a second policy layer over the Agent
 * filesystem. Limits are opt-in request parameters; omitting them returns the
 * complete requested tree.
 */
internal fun selectWorkspaceFiles(
    directory: File,
    recursive: Boolean,
    maxDepth: Int?,
    limit: Int?,
): List<File> {
    val requestedDepth = maxDepth?.takeIf { it > 0 }
    val requestedLimit = limit?.takeIf { it > 0 }
    return if (recursive) {
        val walk = requestedDepth?.let { directory.walkTopDown().maxDepth(it) }
            ?: directory.walkTopDown()
        val files = walk.drop(1)
        (requestedLimit?.let(files::take) ?: files).toList()
    } else {
        val files = directory.listFiles()
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()
        requestedLimit?.let { files.take(it) } ?: files
    }
}

internal data class WorkspaceFileTextSlice(
    val content: String,
    val truncated: Boolean,
)

/**
 * 流式切片：按请求只读取需要的那一段，避免大文件整份进内存。
 * 语义与 [sliceWorkspaceFileText] 对齐（同样的 offset / lineStart / lineCount /
 * maxChars 优先级：lineStart 优先，其次 offset，最后 maxChars 截断）。
 */
internal fun sliceWorkspaceFileByStream(
    file: File,
    maxChars: Int?,
    offset: Int,
    lineStart: Int?,
    lineCount: Int?,
): WorkspaceFileTextSlice {
    val charLimit = maxChars?.takeIf { it > 0 }
    if (lineStart != null) {
        val from = (lineStart - 1).coerceAtLeast(0)
        val want = lineCount?.coerceAtLeast(1)
        // 不指定 lineCount 时也必须受字符预算约束：原来这里从 lineStart 一直读到文件尾
        // 全塞进 ArrayList（每行一个 String 对象，比 readText 还费内存），一个几百 MB 的
        // 日志就能把进程 OOM 掉、连带正在跑的 Agent 一起被杀。
        val budget = charLimit ?: PREVIEW_MAX_CHARS
        val lines = ArrayList<String>()
        var consumed = 0
        var moreLines = false
        file.bufferedReader().use { reader ->
            var index = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (index >= from) {
                    if (want != null && lines.size >= want) {
                        moreLines = true
                        break
                    }
                    if (consumed > 0 && consumed + line.length + 1 > budget) {
                        moreLines = true
                        break
                    }
                    lines.add(line)
                    consumed += line.length + 1
                    if (consumed >= budget) {
                        moreLines = true
                        break
                    }
                }
                index++
            }
        }
        val sliced = lines.joinToString("\n")
        val rendered = sliced.take(budget)
        return WorkspaceFileTextSlice(
            content = rendered,
            truncated = moreLines || rendered.length < sliced.length
        )
    }
    if (offset > 0 || charLimit != null) {
        val builder = StringBuilder()
        var skipped = 0L
        var truncated = false
        file.bufferedReader().use { reader ->
            val buffer = CharArray(8192)
            while (true) {
                val read = reader.read(buffer)
                if (read <= 0) break
                var start = 0
                if (skipped < offset) {
                    val need = (offset - skipped).coerceAtMost(read.toLong()).toInt()
                    skipped += need
                    start = need
                }
                if (start < read) {
                    val remaining = charLimit?.let { it - builder.length }
                    if (remaining != null && remaining <= 0) {
                        truncated = true
                        break
                    }
                    val take = if (remaining == null) read - start else minOf(remaining, read - start)
                    builder.append(buffer, start, take)
                    if (remaining != null && take < read - start) {
                        truncated = true
                        break
                    }
                }
                if (charLimit != null && builder.length >= charLimit) {
                    truncated = true
                    break
                }
            }
        }
        return WorkspaceFileTextSlice(content = builder.toString(), truncated = truncated)
    }
    return sliceWorkspaceFileText(
        content = file.readText(),
        maxChars = maxChars,
        offset = offset,
        lineStart = lineStart,
        lineCount = lineCount
    )
}

internal fun sliceWorkspaceFileText(
    content: String,
    maxChars: Int?,
    offset: Int,
    lineStart: Int?,
    lineCount: Int?,
): WorkspaceFileTextSlice {
    val sliced = when {
        lineStart != null -> {
            val lines = content.lines()
            val from = (lineStart - 1).coerceAtLeast(0).coerceAtMost(lines.size)
            val until = if (lineCount != null) {
                (from + lineCount.coerceAtLeast(1)).coerceAtMost(lines.size)
            } else {
                lines.size
            }
            lines.subList(from, until).joinToString("\n")
        }
        offset > 0 -> content.drop(offset.coerceAtLeast(0))
        else -> content
    }
    val rendered = maxChars
        ?.takeIf { it > 0 }
        ?.let { sliced.take(it) }
        ?: sliced
    return WorkspaceFileTextSlice(
        content = rendered,
        truncated = rendered.length < sliced.length
    )
}
