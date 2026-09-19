package com.omniinfer.server

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * Resolves the native library directory that is handed to the native backend.
 *
 * Upstream OmniInfer exposes this as an injection point: `installedNativeLibDir`
 * wins when an engine payload has been downloaded, otherwise the host falls back
 * to `applicationInfo.nativeLibraryDir`. We reuse the same hook for two things.
 *
 * 1. **Engine payloads** — once a downloaded engine package is installed,
 *    [installedNativeLibDir] points at its `lib/<abi>` folder and that folder
 *    becomes the whole world as far as ggml is concerned. See
 *    [OmniInferEngineLoader].
 *
 * 2. **CPU variant policy** — when we fall back to the APK's own
 *    `nativeLibraryDir`, that directory is *not* handed over verbatim. Every CPU
 *    variant stays bundled in the APK (nothing is deleted at build time), but
 *    ggml must not be able to reach a tier that is known to SIGSEGV on this SoC.
 *    We therefore materialise a filtered directory of symlinks that mirrors
 *    everything except the excluded variants, and pass that one along.
 *
 * Filtering happens *before* ggml registers its backends, which is the only safe
 * moment: an already-registered ggml backend cannot be unloaded (upstream
 * OmniInfer documents the same constraint and requires an app restart to change
 * engine payloads). This replaces the earlier runtime deny-list / warm-up-probe
 * auto-downgrade logic.
 *
 * Safety net: before the filtered directory is used, the native side is asked to
 * `dlopen` the ggml libraries inside it exactly the way ggml itself would. If the
 * platform linker refuses the directory (namespace policy), we fall back to the
 * plain `nativeLibraryDir` so a model can always load.
 */
object OmniInferNativeLibs {
    private const val TAG = "OmniInferNativeLibs"
    private const val ROOT_DIR = "omniinfer-native"
    private const val COMPLETE_MARKER = ".complete"

    /**
     * CPU variant name substrings that must stay unreachable.
     *
     * Default is the armv9/SVE tier family: the SVE2 kernels
     * (`libggml-cpu-android_armv9.0_1.so`) SIGSEGV on several consumer armv9 SoCs
     * (verified on MediaTek MT6991 / Dimensity 9400, matching the ceilings used by
     * ChatterUI and PocketPal, which build at most armv8.2-a+dotprod+i8mm).
     */
    val DEFAULT_EXCLUDED_VARIANTS: List<String> = listOf("armv9")

    /**
     * An engine payload that has been installed at runtime. When non-null this
     * directory is used as-is; its contents are governed by the engine manifest.
     */
    @Volatile
    var installedNativeLibDir: String? = null
        set(value) {
            field = value
            cachedResolvedDir = null
        }

    /** Variant substrings filtered out of the scanned directory. */
    @Volatile
    var excludedVariantPrefixes: List<String> = DEFAULT_EXCLUDED_VARIANTS
        set(value) {
            field = value
            cachedResolvedDir = null
        }

    /** Set to false to hand every shipped variant over (diagnostics only). */
    @Volatile
    var filterEnabled: Boolean = true
        set(value) {
            field = value
            cachedResolvedDir = null
        }

    /** Memoised result of [resolve]; invalidated whenever the policy changes. */
    @Volatile
    private var cachedResolvedDir: String? = null

    /**
     * Native directory to pass to the backend for [context].
     * Never returns null; falls back to `applicationInfo.nativeLibraryDir`.
     */
    fun resolve(context: Context): String {
        cachedResolvedDir?.let { return it }
        val resolved = computeResolvedDir(context)
        cachedResolvedDir = resolved
        return resolved
    }

    private fun computeResolvedDir(context: Context): String {
        val engineDir = installedNativeLibDir?.takeIf { it.isNotBlank() }
        if (engineDir != null) {
            Log.i(TAG, "Using installed engine payload: $engineDir")
            return engineDir
        }

        val baseDir = context.applicationInfo.nativeLibraryDir
        if (!filterEnabled || excludedVariantPrefixes.isEmpty()) {
            Log.i(TAG, "Variant filtering disabled; using $baseDir")
            return baseDir
        }

        val filtered = runCatching { buildFilteredDir(context, baseDir) }
            .onFailure { Log.w(TAG, "Could not build filtered lib dir: ${it.message}") }
            .getOrNull()
            ?: return baseDir

        // Confirm the platform linker lets this process open ggml backends from
        // that directory. Same dlopen path ggml uses, so this is a true proxy.
        val visible = runCatching { OmniInferBridge.probeLibDir(filtered.absolutePath) }
            .getOrDefault(emptyList())
        if (visible.isEmpty()) {
            Log.w(
                TAG,
                "Linker refused $filtered; falling back to $baseDir (variant filtering unavailable)",
            )
            return baseDir
        }

        Log.i(
            TAG,
            "Using filtered native lib dir $filtered " +
                "(${visible.size} ggml libs visible, excluded=$excludedVariantPrefixes)",
        )
        return filtered.absolutePath
    }

    /** Human readable summary for diagnostics surfaces. */
    fun describe(context: Context): Map<String, String> {
        val baseDir = context.applicationInfo.nativeLibraryDir
        return mapOf(
            "nativeLibDir" to resolve(context),
            "applicationNativeLibraryDir" to baseDir,
            "installedEngineDir" to (installedNativeLibDir ?: ""),
            "excludedCpuVariants" to if (filterEnabled) excludedVariantPrefixes.joinToString(",") else "(disabled)",
            "abi" to Build.SUPPORTED_ABIS.joinToString(","),
        )
    }

    /** Drop cached filtered directories (call after changing the policy). */
    fun invalidate(context: Context) {
        cachedResolvedDir = null
        runCatching { File(context.filesDir, ROOT_DIR).deleteRecursively() }
        Log.i(TAG, "Filtered native lib cache cleared")
    }

    private fun isExcluded(name: String): Boolean {
        if (!name.startsWith("libggml-cpu-")) return false
        return excludedVariantPrefixes.any { it.isNotBlank() && name.contains(it) }
    }

    private fun buildFilteredDir(context: Context, baseDir: String): File? {
        val root = File(context.filesDir, ROOT_DIR)
        root.mkdirs()

        // Mirror everything the backend loader may need (ggml CPU variants, the
        // hexagon/opencl accelerators referenced through ADSP_LIBRARY_PATH, ...),
        // minus the excluded CPU variants.
        val kept = File(baseDir).listFiles().orEmpty()
            .filter { it.isFile && !isExcluded(it.name) }
        if (kept.isEmpty()) return null

        val fingerprint = fingerprintOf(kept)
        val target = File(root, fingerprint)
        if (File(target, COMPLETE_MARKER).isFile) return target

        val staging = File(root, "$fingerprint.tmp")
        staging.deleteRecursively()
        if (!staging.mkdirs()) return null

        for (source in kept) {
            val link = File(staging, source.name)
            if (link.exists()) link.delete()
            Os.symlink(source.absolutePath, link.absolutePath)
        }
        File(staging, COMPLETE_MARKER).writeText("${kept.size} libs\n")

        target.deleteRecursively()
        if (!staging.renameTo(target)) {
            staging.deleteRecursively()
            return null
        }

        // Only the current fingerprint needs to stay around.
        root.listFiles().orEmpty()
            .filter { it.isDirectory && it.name != target.name }
            .forEach { it.deleteRecursively() }

        Log.i(TAG, "Filtered ${kept.size} libs into $target (excluded=$excludedVariantPrefixes)")
        return target
    }

    private fun fingerprintOf(files: List<File>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(excludedVariantPrefixes.sorted().joinToString(",").toByteArray())
        digest.update(Build.SUPPORTED_ABIS.joinToString(",").toByteArray())
        files.sortedBy { it.name }.forEach { file ->
            digest.update(file.name.toByteArray())
            digest.update(":${file.length()}:${file.lastModified()}".toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.substring(0, 16)
    }
}
