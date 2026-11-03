package cn.com.omnimind.bot.agent.personal

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.*
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

internal data class ContactPatch(
    val name: String?, val phones: List<String>?, val emails: List<String>?,
    val organization: String?, val jobTitle: String?, val addresses: List<String>?,
) {
    companion object {
        fun from(args: JsonObject, create: Boolean): ContactPatch {
            fun strings(key: String): List<String>? {
                if (!args.containsKey(key)) return null
                val values = args[key] as? JsonArray ?: throw IllegalArgumentException("$key must be an array")
                require(values.size <= 50) { "$key supports at most 50 values" }
                return values.map {
                    require(it is JsonPrimitive && it.isString && it.content.isNotBlank() && it.content.length <= 2000) { "$key must contain nonempty strings" }
                    it.content.trim()
                }.distinct()
            }
            val patch = ContactPatch(args.text("name", create), strings("phones"), strings("emails"), args.text("organization"), args.text("jobTitle"), strings("addresses"))
            require(patch.name == null || patch.name.isNotBlank()) { "name must not be blank" }
            require(create || listOf("name", "phones", "emails", "organization", "jobTitle", "addresses").any(args::containsKey)) { "No fields to update" }
            return patch
        }
    }
}

internal class PersonalContacts(private val context: Context) {
    private val resolver get() = context.contentResolver
    private val access get() = PersonalContextAccess(context)

    suspend fun query(args: JsonObject): Map<String, Any?> {
        access.requireAccess("contacts_read")
        val page = ContextPage.from(args)
        val query = args.text("query").orEmpty()
        val contactId = args.text("contactId")?.let { requireNotNull(it.toLongOrNull()) { "contactId must be numeric" } }
        val selection = mutableListOf("${Data.MIMETYPE} IN (?,?,?,?,?)")
        val values = mutableListOf(StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE)
        if (query.isNotBlank()) {
            selection += "(${Data.DISPLAY_NAME} LIKE ? ESCAPE '\\' OR ${Data.DATA1} LIKE ? ESCAPE '\\')"
            values += likePattern(query); values += likePattern(query)
        }
        if (contactId != null) { selection += "${Data.CONTACT_ID}=?"; values += contactId.toString() }
        val contacts = resolver.contextQuery(Data.CONTENT_URI, arrayOf(Data.CONTACT_ID, Data.LOOKUP_KEY, Data.DISPLAY_NAME), selection.joinToString(" AND "), values.toTypedArray(), "${Data.DISPLAY_NAME} COLLATE LOCALIZED, ${Data.CONTACT_ID}") { cursor, check ->
            val seen = linkedSetOf<Long>()
            val rows = mutableListOf<MutableMap<String, Any?>>()
            while (cursor.moveToNext() && rows.size <= page.limit) {
                check()
                val id = cursor.longValue(Data.CONTACT_ID) ?: continue
                if (!seen.add(id) || seen.size <= page.offset) continue
                rows += mutableMapOf("contactId" to id.toString(), "lookupKey" to cursor.value(Data.LOOKUP_KEY), "name" to cursor.value(Data.DISPLAY_NAME))
            }
            rows
        }
        for (contact in contacts.take(page.limit)) {
            currentCoroutineContext().ensureActive()
            val id = contact.getValue("contactId").toString()
            val raw = resolver.contextQuery(RawContacts.CONTENT_URI,
                arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE, RawContacts.RAW_CONTACT_IS_READ_ONLY, RawContacts.VERSION),
                "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0", arrayOf(id), "${RawContacts._ID}") { cursor, check ->
                buildList {
                    while (cursor.moveToNext()) {
                        check()
                        add(mutableMapOf<String, Any?>("rawContactId" to cursor.value(RawContacts._ID), "accountName" to cursor.value(RawContacts.ACCOUNT_NAME), "accountType" to cursor.value(RawContacts.ACCOUNT_TYPE),
                            "readOnly" to (cursor.longValue(RawContacts.RAW_CONTACT_IS_READ_ONLY) == 1L), "version" to cursor.longValue(RawContacts.VERSION)))
                    }
                }
            }
            val data = resolver.contextQuery(Data.CONTENT_URI, arrayOf(Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA4), "${Data.CONTACT_ID}=?", arrayOf(id)) { cursor, check ->
                buildList { while (cursor.moveToNext()) {
                    check()
                    add(mapOf("rawContactId" to cursor.value(Data.RAW_CONTACT_ID), "mime" to cursor.value(Data.MIMETYPE), "value" to cursor.value(Data.DATA1), "jobTitle" to cursor.value(Data.DATA4)))
                } }
            }
            for (item in raw) {
                val fields = data.filter { it["rawContactId"] == item["rawContactId"] }
                fun values(mime: String) = fields.filter { it["mime"] == mime }.mapNotNull { it["value"] }
                item["phones"] = values(Phone.CONTENT_ITEM_TYPE)
                item["emails"] = values(Email.CONTENT_ITEM_TYPE)
                item["addresses"] = values(StructuredPostal.CONTENT_ITEM_TYPE)
                item["organizations"] = fields.filter { it["mime"] == Organization.CONTENT_ITEM_TYPE }.map { mapOf("name" to it["value"], "jobTitle" to it["jobTitle"]) }
            }
            contact["rawContacts"] = raw
        }
        access.requireAccess("contacts_read")
        return page.result("contacts", contacts, mapOf("scope" to "device_contacts", "mutationIdentity" to "rawContactId and version from rawContacts"))
    }

    suspend fun write(operation: String, args: JsonObject): Map<String, Any?> {
        access.requireAccess("contacts_read")
        access.requireAccess("contacts_write")
        val create = operation == "contacts_create"
        val patch = if (operation == "contacts_delete") null else ContactPatch.from(args, create)
        val ops = arrayListOf<ContentProviderOperation>()
        val rawId = if (create) null else args.text("rawContactId", true)!!.toLongOrNull().also { require(it != null && it > 0) { "rawContactId must be positive" } }
        if (rawId != null) {
            val expectedVersion = args["version"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("version from contacts_query is required")
            val row = resolver.contextQuery(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId), arrayOf(RawContacts.RAW_CONTACT_IS_READ_ONLY, RawContacts.VERSION, RawContacts.DELETED)) { cursor, check ->
                check()
                if (!cursor.moveToFirst()) throw PersonalContextException("not_found", "Contact no longer exists.")
                Triple(cursor.longValue(RawContacts.RAW_CONTACT_IS_READ_ONLY), cursor.longValue(RawContacts.VERSION), cursor.longValue(RawContacts.DELETED))
            }
            if (row.third == 1L) throw PersonalContextException("not_found", "Contact was deleted.")
            if (row.first == 1L) throw PersonalContextException("read_only", "This account does not allow contact changes.")
            if (row.second != expectedVersion) throw PersonalContextException("stale_record", "Contact changed. Query it again before editing.")
            ops += ContentProviderOperation.newAssertQuery(RawContacts.CONTENT_URI)
                .withSelection("${RawContacts._ID}=? AND ${RawContacts.VERSION}=? AND ${RawContacts.DELETED}=0 AND ${RawContacts.RAW_CONTACT_IS_READ_ONLY}=0", arrayOf(rawId.toString(), expectedVersion.toString()))
                .withExpectedCount(1).build()
        } else {
            // 不指定伪造账户，交由系统联系人 Provider 使用默认保存账户。
            ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI).build()
        }
        if (operation == "contacts_delete") {
            ops += ContentProviderOperation.newDelete(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId!!)).withExpectedCount(1).build()
        } else {
            fun replace(mime: String, values: List<Pair<String, String?>>) {
                if (rawId != null) ops += ContentProviderOperation.newDelete(Data.CONTENT_URI).withSelection("${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?", arrayOf(rawId.toString(), mime)).build()
                for ((value, title) in values) {
                    val builder = ContentProviderOperation.newInsert(Data.CONTENT_URI).withValue(Data.MIMETYPE, mime).withValue(Data.DATA1, value)
                    if (rawId == null) builder.withValueBackReference(Data.RAW_CONTACT_ID, 0) else builder.withValue(Data.RAW_CONTACT_ID, rawId)
                    if (mime == Organization.CONTENT_ITEM_TYPE) builder.withValue(Organization.TITLE, title)
                    ops += builder.build()
                }
            }
            patch!!.name?.let { replace(StructuredName.CONTENT_ITEM_TYPE, listOf(it to null)) }
            patch.phones?.let { replace(Phone.CONTENT_ITEM_TYPE, it.map { value -> value to null }) }
            patch.emails?.let { replace(Email.CONTENT_ITEM_TYPE, it.map { value -> value to null }) }
            patch.addresses?.let { replace(StructuredPostal.CONTENT_ITEM_TYPE, it.map { value -> value to null }) }
            if (patch.organization != null || patch.jobTitle != null) {
                // 只更新主要组织行的指定字段，保留同一联系人上的其他组织。
                val existingId = if (rawId == null) null else resolver.contextQuery(Data.CONTENT_URI, arrayOf(Data._ID), "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?", arrayOf(rawId.toString(), Organization.CONTENT_ITEM_TYPE), "${Data.IS_PRIMARY} DESC, ${Data._ID} ASC") { cursor, _ ->
                    if (cursor.moveToFirst()) cursor.longValue(Data._ID) else null
                }
                if (existingId != null) {
                    val update = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Data.CONTENT_URI, existingId)).withExpectedCount(1)
                    patch.organization?.let { update.withValue(Organization.COMPANY, it) }
                    patch.jobTitle?.let { update.withValue(Organization.TITLE, it) }
                    ops += update.build()
                } else {
                    replace(Organization.CONTENT_ITEM_TYPE, listOf(patch.organization.orEmpty() to patch.jobTitle))
                }
            }
        }
        currentCoroutineContext().ensureActive()
        access.requireAccess("contacts_write")
        val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        return mapOf("success" to true, "source" to "contacts", "operation" to operation, "rawContactId" to (rawId?.toString() ?: results.first().uri?.lastPathSegment),
            "scope" to "one_raw_contact", "deleted" to (operation == "contacts_delete"))
    }
}
