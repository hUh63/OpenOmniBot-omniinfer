package com.omniinfer.server

import org.json.JSONArray
import org.json.JSONObject

/**
 * Raised when an engine package fails manifest parsing, verification, or loading.
 * Host apps should catch this around [OmniInferEngineLoader.install] and offer the
 * user a clean re-download of the engine package.
 */
class OmniInferEngineException(message: String) : IllegalStateException(message)

data class OmniInferEngineLibEntry(
    val name: String,
    val sha256: String,
    val sizeBytes: Long,
)

/**
 * Parsed `manifest.json` of a downloadable OmniInfer native engine package.
 *
 * Field names, validation rules and the on-disk layout match upstream OmniInfer so
 * an engine zip built by `./gradlew :omniinfer-server:bundleEnginePackage` (or by
 * upstream itself) installs unchanged:
 *
 * ```
 * <engineDir>/manifest.json
 * <engineDir>/lib/arm64-v8a/<lib>.so
 * ```
 */
data class OmniInferEngineManifest(
    val formatVersion: Int,
    val engineVersion: String,
    val interfaceVersion: Int,
    val abi: String,
    val minSdk: Int,
    val backends: List<String>,
    /**
     * Native libs loaded with `System.load` in order before any backend init:
     * the DT_NEEDED chain ending in [JNI_BRIDGE_LIB].
     */
    val coreLibs: List<String>,
    /** Every packaged native lib, keyed by file name. */
    val libs: Map<String, OmniInferEngineLibEntry>,
) {
    /** Directory inside the engine package holding the native libs (mirrors APK jniLibs). */
    val libDirRelativePath: String get() = "lib/$abi"

    /**
     * Validate this manifest against the current device.
     * @param deviceAbis supported ABIs of the device (`Build.SUPPORTED_ABIS`)
     * @param sdkInt device SDK int (`Build.VERSION.SDK_INT`)
     */
    fun validate(
        deviceAbis: List<String>,
        sdkInt: Int,
        supportedFormatVersion: Int = CURRENT_FORMAT_VERSION,
        supportedInterfaceVersion: Int = CURRENT_INTERFACE_VERSION,
    ) {
        if (formatVersion != supportedFormatVersion) {
            throw OmniInferEngineException(
                "Engine manifest formatVersion $formatVersion is not supported " +
                    "(expected $supportedFormatVersion). Re-download a current engine package.",
            )
        }
        if (interfaceVersion != supportedInterfaceVersion) {
            throw OmniInferEngineException(
                "Engine interfaceVersion $interfaceVersion does not match this SDK " +
                    "(expected $supportedInterfaceVersion). Update the OmniInfer SDK and engine together.",
            )
        }
        if (engineVersion.isBlank()) {
            throw OmniInferEngineException("Engine manifest engineVersion must not be blank.")
        }
        if (abi !in deviceAbis) {
            throw OmniInferEngineException(
                "Engine package ABI '$abi' is not supported by this device (supported: $deviceAbis).",
            )
        }
        if (minSdk > sdkInt) {
            throw OmniInferEngineException(
                "Engine package requires minSdk $minSdk but this device reports $sdkInt.",
            )
        }
        if (backends.isEmpty()) {
            throw OmniInferEngineException("Engine manifest must list at least one backend.")
        }
        if (coreLibs.isEmpty()) {
            throw OmniInferEngineException("Engine manifest must list coreLibs in load order.")
        }
        if (JNI_BRIDGE_LIB !in coreLibs) {
            throw OmniInferEngineException(
                "Engine manifest coreLibs must contain the JNI bridge library $JNI_BRIDGE_LIB.",
            )
        }
        if (coreLibs.last() != JNI_BRIDGE_LIB) {
            throw OmniInferEngineException(
                "Engine manifest coreLibs must load the JNI bridge library $JNI_BRIDGE_LIB last.",
            )
        }
        for (lib in coreLibs) {
            if (lib !in libs) {
                throw OmniInferEngineException("Engine manifest coreLib '$lib' is missing from libs.")
            }
        }
        val invalid = libs.values.filter { !isValidLibEntry(it) }
        if (invalid.isNotEmpty()) {
            throw OmniInferEngineException(
                "Engine manifest has invalid lib entries: ${invalid.map { it.name }}.",
            )
        }
    }

    private fun isValidLibEntry(entry: OmniInferEngineLibEntry): Boolean {
        if (entry.name.isBlank() ||
            entry.name.contains('/') ||
            entry.name.contains('\\') ||
            entry.name.contains("..")
        ) {
            return false
        }
        if (entry.sizeBytes <= 0L) return false
        if (entry.sha256.length != 64) return false
        return entry.sha256.all { it.isDigit() || it in 'a'..'f' }
    }

    companion object {
        const val CURRENT_FORMAT_VERSION = 1
        const val CURRENT_INTERFACE_VERSION = 1
        const val JNI_BRIDGE_LIB = "libomniinfer-jni.so"

        /** Parse a `manifest.json` payload. Throws [OmniInferEngineException] on malformed input. */
        fun fromJson(json: String): OmniInferEngineManifest {
            val root = try {
                JSONObject(json)
            } catch (error: Exception) {
                throw OmniInferEngineException("Engine manifest is not valid JSON: ${error.message}")
            }

            val formatVersion = root.int("formatVersion")
            val engineVersion = root.string("engineVersion")
            val interfaceVersion = root.int("interfaceVersion")
            val abi = root.string("abi")
            val minSdk = root.int("minSdk")
            val backends = root.stringList("backends")
            val coreLibs = root.stringList("coreLibs")

            val libs = LinkedHashMap<String, OmniInferEngineLibEntry>()
            for (entry in root.libEntries()) {
                val name = entry.string("name")
                if (name in libs) {
                    throw OmniInferEngineException("Engine manifest lists lib '$name' twice.")
                }
                libs[name] = OmniInferEngineLibEntry(
                    name = name,
                    sha256 = entry.string("sha256"),
                    sizeBytes = entry.long("sizeBytes"),
                )
            }

            return OmniInferEngineManifest(
                formatVersion = formatVersion,
                engineVersion = engineVersion,
                interfaceVersion = interfaceVersion,
                abi = abi,
                minSdk = minSdk,
                backends = backends,
                coreLibs = coreLibs,
                libs = libs,
            )
        }

        private fun JSONObject.int(key: String): Int = if (has(key)) optInt(key) else {
            throw OmniInferEngineException("Engine manifest is missing $key.")
        }

        private fun JSONObject.long(key: String): Long = if (has(key)) optLong(key) else {
            throw OmniInferEngineException("Engine manifest is missing $key.")
        }

        private fun JSONObject.string(key: String): String = if (has(key)) optString(key) else {
            throw OmniInferEngineException("Engine manifest is missing $key.")
        }

        private fun JSONObject.stringList(key: String): List<String> {
            val array = optJSONArray(key)
                ?: throw OmniInferEngineException("Engine manifest is missing $key.")
            return buildList {
                for (index in 0 until array.length()) {
                    val element = array.opt(index)
                    if (element !is String) {
                        throw OmniInferEngineException(
                            "Engine manifest field '$key' must contain strings.",
                        )
                    }
                    add(element)
                }
            }
        }

        /**
         * `libs` is an array of objects in the canonical layout. A name-keyed object
         * map is also accepted so hand-written manifests keep working.
         */
        private fun JSONObject.libEntries(): List<JSONObject> {
            optJSONArray("libs")?.let { array: JSONArray ->
                return buildList {
                    for (index in 0 until array.length()) {
                        add(
                            array.optJSONObject(index)
                                ?: throw OmniInferEngineException(
                                    "Engine manifest field 'libs' must contain objects.",
                                ),
                        )
                    }
                }
            }
            optJSONObject("libs")?.let { map: JSONObject ->
                return buildList {
                    for (name in map.keys()) {
                        val entry = map.optJSONObject(name)
                            ?: throw OmniInferEngineException(
                                "Engine manifest field 'libs' must contain objects.",
                            )
                        add(
                            JSONObject()
                                .put("name", entry.optString("name", name))
                                .put("sha256", entry.optString("sha256"))
                                .put("sizeBytes", entry.optLong("sizeBytes")),
                        )
                    }
                }
            }
            throw OmniInferEngineException("Engine manifest is missing libs.")
        }
    }
}
