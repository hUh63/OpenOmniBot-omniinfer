package com.omniinfer.server

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Downloads an OmniInfer engine package, verifies it and hands it to
 * [OmniInferEngineLoader]. This is the "not bundled in the APK" half of the engine
 * story: the app ships only the SDK, and the native inference libraries arrive as a
 * versioned zip.
 *
 * Contract (matches upstream OmniInfer `docs/android/engine-download.md`):
 *
 * 1. Download `<engine>.zip.sha256` and take the first whitespace-delimited token
 *    as a 64 character lowercase SHA-256.
 * 2. Stream the zip to a temporary file while hashing it, then compare.
 * 3. Extract into a staging directory under app-private storage, rejecting any
 *    entry whose canonical path escapes that directory.
 * 4. Rename the verified staging directory into place.
 * 5. Verify the manifest and every library before loading.
 *
 * Note: distribution channel rules apply. Google Play apps must not download
 * executable code from outside Play.
 */
object OmniInferEngineDownloader {
    private const val TAG = "OmniInferEngineDownloader"
    private const val ROOT_DIR = "omniinfer-engine"
    private const val STAGING_DIR = "staging"
    private const val ZIP_NAME = "engine.zip.part"
    private const val MAX_ENTRIES = 8192
    private const val MAX_EXTRACTED_BYTES = 1L shl 30

    data class Progress(val bytesRead: Long, val totalBytes: Long)

    /**
     * Download, verify, unpack and install an engine package in one call.
     *
     * @param engineUrl HTTPS URL of the engine zip. The matching checksum is
     *   fetched from `<engineUrl>.sha256`.
     * @throws OmniInferEngineException when the download, checksum, extraction or
     *   manifest verification fails.
     */
    fun downloadAndInstall(
        context: Context,
        engineUrl: String,
        verifyHashes: Boolean = true,
        onProgress: ((Progress) -> Unit)? = null,
    ): OmniInferEngineManifest {
        val root = engineRoot(context)
        val zip = File(root, ZIP_NAME)
        try {
            val expected = fetchExpectedSha256("$engineUrl.sha256")
            downloadTo(engineUrl, zip, expected, onProgress)
            return installFromZip(context, zip, verifyHashes)
        } finally {
            zip.delete()
        }
    }

    /**
     * Extract and install an engine zip that is already on disk (e.g. sideloaded by
     * the user). The zip itself is still checksum-verified per library.
     */
    fun installFromZip(
        context: Context,
        zip: File,
        verifyHashes: Boolean = true,
    ): OmniInferEngineManifest {
        if (!zip.isFile) {
            throw OmniInferEngineException("Engine zip not found: ${zip.absolutePath}")
        }
        val root = engineRoot(context)
        val staging = File(root, STAGING_DIR)
        staging.deleteRecursively()
        if (!staging.mkdirs()) {
            throw OmniInferEngineException("Cannot create engine staging dir: ${staging.absolutePath}")
        }
        extractSafely(zip, staging)

        // The engine version is only known after the manifest has been parsed.
        val manifest = OmniInferEngineLoader.verify(staging, verifyHashes)
        val target = File(root, "engine-${sanitize(manifest.engineVersion)}")
        target.deleteRecursively()
        if (!staging.renameTo(target)) {
            throw OmniInferEngineException("Cannot move verified engine into place: ${target.absolutePath}")
        }
        val installed = OmniInferEngineLoader.install(target, verifyHashes)
        pruneOldEngines(root, keep = target)
        Log.i(TAG, "Engine ${installed.engineVersion} ready at ${target.absolutePath}")
        return installed
    }

    /** Engine zip present and verified in storage (a previous download). */
    fun installedEngineDir(context: Context): File? =
        engineRoot(context).listFiles()
            .orEmpty()
            .firstOrNull { it.isDirectory && it.name.startsWith("engine-") && File(it, OmniInferEngineLoader.MANIFEST_FILE).isFile }

    private fun engineRoot(context: Context): File =
        File(context.filesDir, ROOT_DIR).apply { mkdirs() }

    private fun pruneOldEngines(root: File, keep: File) {
        root.listFiles().orEmpty()
            .filter { it.isDirectory && it.name != STAGING_DIR && it.absolutePath != keep.absolutePath }
            .forEach { stale ->
                Log.i(TAG, "Pruning previous engine dir ${stale.name}")
                stale.deleteRecursively()
            }
    }

    private fun sanitize(version: String): String =
        version.map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '_' }
            .joinToString("")

    private fun fetchExpectedSha256(url: String): String {
        val connection = openChecked(url)
        val text = try {
            connection.inputStream.use { stream -> stream.bufferedReader().readText() }
        } finally {
            connection.disconnect()
        }
        val token = text.trim().split(Regex("\\s+")).firstOrNull().orEmpty().lowercase(Locale.US)
        if (token.length != 64 || !token.all { it.isDigit() || it in 'a'..'f' }) {
            throw OmniInferEngineException(
                "Engine checksum file at $url does not contain a 64 character SHA-256.",
            )
        }
        return token
    }

    private fun downloadTo(
        url: String,
        destination: File,
        expectedSha256: String,
        onProgress: ((Progress) -> Unit)?,
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = openChecked(url)
        var total = -1L
        var read = 0L
        try {
            total = connection.contentLengthLong
            DigestInputStream(connection.inputStream, digest).use { source ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val count = source.read(buffer)
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                        read += count
                        onProgress?.invoke(Progress(read, total))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expectedSha256) {
            destination.delete()
            throw OmniInferEngineException(
                "Engine download failed SHA-256 verification (expected $expectedSha256, got $actual). " +
                    "Re-download the engine package.",
            )
        }
        Log.i(TAG, "Verified engine download: $read bytes, sha256=$actual")
    }

    private fun openChecked(url: String): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
        }
        val protocol = connection.url.protocol.lowercase(Locale.US)
        val host = connection.url.host.orEmpty()
        if (protocol != "https" && host != "127.0.0.1" && host != "localhost") {
            connection.disconnect()
            throw OmniInferEngineException(
                "Refusing to download an engine package over '$protocol' from '$host'. Use HTTPS.",
            )
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw OmniInferEngineException("Engine download failed: HTTP $code for $url")
        }
        return connection
    }

    /**
     * Extract [zip] into [destination], rejecting entries that escape it (zip-slip)
     * and capping the entry count.
     */
    private fun extractSafely(zip: File, destination: File) {
        val rootPath = destination.canonicalPath.trimEnd(File.separatorChar)
        var entries = 0
        var extracted = 0L
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                entries++
                if (entries > MAX_ENTRIES) {
                    throw OmniInferEngineException("Engine zip has too many entries (>$MAX_ENTRIES).")
                }
                val name = entry.name
                if (name.isBlank() || name.contains('\\')) {
                    throw OmniInferEngineException("Engine zip contains an invalid entry name: '$name'.")
                }
                val outFile = File(destination, name)
                val canonical = outFile.canonicalPath
                if (canonical != rootPath && !canonical.startsWith(rootPath + File.separator)) {
                    throw OmniInferEngineException("Engine zip entry escapes the target directory: '$name'.")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(1 shl 16)
                        while (true) {
                            val count = input.read(buffer)
                            if (count <= 0) break
                            extracted += count
                            if (extracted > MAX_EXTRACTED_BYTES) {
                                throw OmniInferEngineException(
                                    "Engine zip expands beyond $MAX_EXTRACTED_BYTES bytes; refusing to continue.",
                                )
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                input.closeEntry()
            }
        }
        Log.i(TAG, "Extracted $entries zip entries into ${destination.absolutePath}")
    }
}
