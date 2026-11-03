package cn.com.omnimind.bot.agent.personal

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.MediaStore
import android.provider.Telephony
import kotlinx.serialization.json.JsonObject

internal class PersonalContextProviders(private val context: Context) {
    private val resolver get() = context.contentResolver
    private val access get() = PersonalContextAccess(context)

    suspend fun messages(args: JsonObject): Map<String, Any?> = records(
        args, "sms", Telephony.Sms.CONTENT_URI,
        linkedMapOf("_id" to "id", "address" to "address", "body" to "body", "date" to "timestamp", "type" to "type", "read" to "read"),
        setOf("date", "type", "read"), listOf("address", "body"), "date",
    )

    suspend fun calls(args: JsonObject): Map<String, Any?> = records(
        args, "call_log", CallLog.Calls.CONTENT_URI,
        linkedMapOf("_id" to "id", "number" to "number", "name" to "name", "date" to "timestamp", "duration" to "durationSeconds", "type" to "type"),
        setOf("date", "duration", "type"), listOf("number", "name"), "date",
    )

    private suspend fun records(args: JsonObject, source: String, uri: Uri, columns: LinkedHashMap<String, String>, numeric: Set<String>, searchable: List<String>, timeColumn: String): Map<String, Any?> {
        access.requireAccess(source)
        val page = ContextPage.from(args)
        val range = ContextTimeRange.from(args, defaultDays = 30)
        val clauses = mutableListOf("$timeColumn>=?", "$timeColumn<?")
        val values = mutableListOf(range.start.toString(), range.end.toString())
        args.text("query")?.takeIf { it.isNotBlank() }?.let { query ->
            clauses += searchable.joinToString(" OR ", "(", ")") { "$it LIKE ? ESCAPE '\\'" }
            repeat(searchable.size) { values += likePattern(query) }
        }
        val rows = resolver.contextQuery(uri, columns.keys.toTypedArray(), clauses.joinToString(" AND "), values.toTypedArray(), "$timeColumn DESC, _id DESC") { cursor, check ->
            buildList {
                var skipped = 0
                while (cursor.moveToNext() && size <= page.limit) {
                    check()
                    if (skipped++ < page.offset) continue
                    add(columns.entries.associate { (column, key) -> key to if (column in numeric) cursor.longValue(column) else cursor.value(column) })
                }
            }
        }
        access.requireAccess(source)
        return page.result(source, rows, mapOf("scope" to "system_provider", "range" to range.payload()))
    }

    fun requireMedia(uri: Uri): String {
        require(uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) { "Unsupported media URI" }
        val segments = uri.pathSegments
        require(segments.size == 4 && segments[0] == "external" && segments[2] == "media" && segments[3].toLongOrNull() != null) { "Use a media URI returned by context_media_query" }
        val kind = when (segments[1]) { "images" -> "images"; "video" -> "videos"; "audio" -> "audio"; else -> throw IllegalArgumentException("Unsupported media collection") }
        requireMediaKind(kind)
        return kind
    }

    private fun requireMediaKind(kind: String) {
        val source = if (kind == "audio") "media_audio" else "media_visual"
        access.requireAccess(source)
        if (Build.VERSION.SDK_INT >= 33 && kind != "audio") {
            val permission = if (kind == "images") android.Manifest.permission.READ_MEDIA_IMAGES else android.Manifest.permission.READ_MEDIA_VIDEO
            if (!access.granted(permission) && !(Build.VERSION.SDK_INT >= 34 && access.granted(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))) {
                throw PersonalContextException("permission_required", "Access to this media type has not been granted.", source)
            }
        }
    }

    suspend fun media(args: JsonObject): Map<String, Any?> {
        val kind = args.text("kind") ?: "images"
        require(kind in setOf("images", "videos", "audio")) { "kind must be images, videos or audio" }
        requireMediaKind(kind)
        val uri = when (kind) {
            "images" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            "videos" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val page = ContextPage.from(args)
        val clauses = mutableListOf<String>()
        val values = mutableListOf<String>()
        args.text("query")?.takeIf { it.isNotBlank() }?.let { clauses += "_display_name LIKE ? ESCAPE '\\'"; values += likePattern(it) }
        val range = if (args.containsKey("startAt") || args.containsKey("endAt")) ContextTimeRange.from(args, defaultDays = 30) else null
        range?.let {
            clauses += "date_modified>=? AND date_modified<?"
            values += Math.floorDiv(it.start + 999, 1000).toString(); values += Math.floorDiv(it.end + 999, 1000).toString()
        }
        val rows = resolver.contextQuery(uri, arrayOf("_id", "_display_name", "mime_type", "_size", "date_added", "date_modified"),
            clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "), values.toTypedArray(), "date_modified DESC, _id DESC") { cursor, check ->
            buildList {
                var skipped = 0
                while (cursor.moveToNext() && size <= page.limit) {
                    check()
                    if (skipped++ < page.offset) continue
                    val id = cursor.longValue("_id") ?: continue
                    add(mapOf("uri" to ContentUris.withAppendedId(uri, id).toString(), "name" to cursor.value("_display_name"), "mimeType" to cursor.value("mime_type"),
                        "sizeBytes" to cursor.longValue("_size"), "addedAt" to cursor.longValue("date_added")?.times(1000), "modifiedAt" to cursor.longValue("date_modified")?.times(1000)))
                }
            }
        }
        requireMediaKind(kind)
        return page.result("media", rows, mapOf("kind" to kind, "scope" to access.snapshot(if (kind == "audio") "media_audio" else "media_visual").payload(),
            "timeField" to "modifiedAt", "range" to range?.payload(), "contentTool" to "context_file_import"))
    }
}
