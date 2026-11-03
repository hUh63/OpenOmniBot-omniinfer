package cn.com.omnimind.bot.agent.personal

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import cn.com.omnimind.bot.workspace.PublicStorageAccess
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import java.io.File

internal class PersonalContextDocuments(private val context: Context) {
    private val resolver get() = context.contentResolver
    private val preferences get() = context.getSharedPreferences("personal_context_documents", Context.MODE_PRIVATE)

    fun remember(uri: Uri, flags: Int) {
        require(uri.scheme == "content")
        resolver.takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        preferences.edit().putStringSet("roots", storedRoots() + uri.toString()).apply()
    }

    private fun storedRoots(): Set<String> = preferences.getStringSet("roots", emptySet()).orEmpty().toSet()

    fun grants(): List<String> {
        val persisted = resolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
        return storedRoots().filter { it in persisted }.sorted()
    }

    fun revokeAll() {
        for (uri in grants()) resolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        preferences.edit().remove("roots").apply()
    }

    fun requireReadable(uri: Uri) {
        if (uri.scheme == "content" && grants().any { rootText ->
                val root = Uri.parse(rootText)
                root == uri || (DocumentsContract.isTreeUri(root) && root.authority == uri.authority &&
                    DocumentsContract.isDocumentUri(context, uri) &&
                    (DocumentsContract.getDocumentId(uri) == DocumentsContract.getTreeDocumentId(root) ||
                        DocumentsContract.isChildDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(root, DocumentsContract.getTreeDocumentId(root)), uri)))
            }) return
        throw PersonalContextException("permission_required", "Select this file or its parent directory in Personal context settings.", "documents")
    }

    suspend fun query(args: JsonObject): Map<String, Any?> {
        if (args.text("path") != null) return queryPublic(args)
        val page = ContextPage.from(args)
        val uriText = args.text("uri")
        if (uriText.isNullOrBlank()) {
            val roots = grants().map { mapOf<String, Any?>("uri" to it, "directory" to DocumentsContract.isTreeUri(Uri.parse(it))) }.toMutableList()
            if (hasPublicAccess()) roots += mapOf("path" to Environment.getExternalStorageDirectory().absolutePath, "directory" to true)
            return page.result("documents", roots.drop(page.offset).take(page.limit + 1), mapOf("scope" to "authorized_roots"))
        }
        val uri = Uri.parse(uriText)
        requireReadable(uri)
        val documentUri = if (DocumentsContract.isTreeUri(uri) && !DocumentsContract.isDocumentUri(context, uri))
            DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)) else uri
        if (!DocumentsContract.isTreeUri(uri) || resolver.getType(documentUri) != DocumentsContract.Document.MIME_TYPE_DIR) {
            val rows = resolver.contextQuery(documentUri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)) { cursor, check ->
                check()
                if (!cursor.moveToFirst()) emptyList() else listOf(mapOf("uri" to documentUri.toString(), "name" to cursor.value(OpenableColumns.DISPLAY_NAME), "sizeBytes" to cursor.longValue(OpenableColumns.SIZE), "mimeType" to resolver.getType(documentUri), "directory" to false))
            }
            return page.result("documents", rows.drop(page.offset))
        }
        val documentId = if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.getDocumentId(uri) else DocumentsContract.getTreeDocumentId(uri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, documentId)
        val nameFilter = args.text("query").orEmpty()
        val rows = resolver.contextQuery(children, arrayOf("document_id", "_display_name", "mime_type", "_size", "last_modified")) { cursor, check ->
            val result = mutableListOf<Map<String, Any?>>()
            var matched = 0
            while (cursor.moveToNext() && result.size <= page.limit) {
                check()
                val name = cursor.value("_display_name").orEmpty()
                if (!name.contains(nameFilter, ignoreCase = true)) continue
                if (matched++ < page.offset) continue
                val id = cursor.value("document_id") ?: continue
                result += mapOf("uri" to DocumentsContract.buildDocumentUriUsingTree(uri, id).toString(), "name" to name,
                    "mimeType" to cursor.value("mime_type"), "directory" to (cursor.value("mime_type") == DocumentsContract.Document.MIME_TYPE_DIR),
                    "sizeBytes" to cursor.longValue("_size"), "modifiedAt" to cursor.longValue("last_modified"))
            }
            result
        }
        requireReadable(uri)
        return page.result("documents", rows, mapOf("scope" to "selected_directory_children", "uri" to uriText))
    }

    private fun hasPublicAccess(): Boolean = if (Build.VERSION.SDK_INT >= 30) PublicStorageAccess.isGranted()
        else PersonalContextAccess(context).granted(Manifest.permission.READ_EXTERNAL_STORAGE)

    fun requirePublicFile(path: String): File {
        if (!hasPublicAccess()) throw PersonalContextException("permission_required", "Enable public file access in App permissions.", "public_storage")
        val root = Environment.getExternalStorageDirectory().canonicalFile
        val file = File(path).canonicalFile
        require(file == root || file.path.startsWith(root.path + File.separator)) { "Path must be inside shared storage" }
        val relative = file.relativeTo(root).invariantSeparatorsPath
        require(relative != "Android/data" && !relative.startsWith("Android/data/") && relative != "Android/obb" && !relative.startsWith("Android/obb/")) { "Other apps' private directories are unavailable" }
        return file
    }

    private suspend fun queryPublic(args: JsonObject): Map<String, Any?> {
        val directory = requirePublicFile(args.text("path", true)!!)
        require(directory.isDirectory) { "path must identify a directory" }
        val page = ContextPage.from(args)
        val query = args.text("query").orEmpty()
        val children = directory.listFiles() ?: throw PersonalContextException("provider_unavailable", "Cannot read this directory.")
        val rows = mutableListOf<Map<String, Any?>>()
        var matched = 0
        for (file in children.sortedBy { it.name.lowercase() }) {
            currentCoroutineContext().ensureActive()
            if (!file.name.contains(query, ignoreCase = true)) continue
            if (matched++ < page.offset) continue
            rows += mapOf("path" to file.absolutePath, "name" to file.name, "directory" to file.isDirectory, "sizeBytes" to file.length(), "modifiedAt" to file.lastModified())
            if (rows.size > page.limit) break
        }
        return page.result("documents", rows, mapOf("scope" to "public_directory_children", "path" to directory.absolutePath))
    }
}
