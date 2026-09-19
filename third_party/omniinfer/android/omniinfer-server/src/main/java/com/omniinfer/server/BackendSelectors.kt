package com.omniinfer.server

import java.util.Locale

/**
 * Canonical backend selectors understood by [OmniInferServer.loadModel].
 *
 * Names match upstream OmniInfer (`llama.cpp-cpu`, `llama.cpp-htp`, `litert-lm-cpu`,
 * `litert-lm-gpu`, `mnn-*`). Legacy spellings keep working: `llama.cpp/cpu`,
 * `litert/gpu`, and bare framework names such as `llama.cpp` or `litert` are all
 * aliases — see [BackendSelectors.normalizeExplicit].
 *
 * Bundle members (catalog `backend`, `extra_config`) also feed the resolver, so a
 * catalog default never has to be repeated in the app layer.
 */
object OmniInferBackend {
    const val AUTO = "auto"
    const val LLAMA_CPP_CPU = "llama.cpp-cpu"
    const val LLAMA_CPP_HTP = "llama.cpp-htp"
    const val LITERT_CPU = "litert-lm-cpu"
    const val LITERT_GPU = "litert-lm-gpu"
    const val MNN_CPU = "mnn-cpu"
    const val MNN_OPENCL = "mnn-opencl"
    const val MNN_VULKAN = "mnn-vulkan"
}

/**
 * Single source of truth for backend selector resolution and per-backend defaults.
 *
 * Every selector alias, bridge mapping, default thread/context count and default
 * `extraConfig` lives here. These used to be duplicated between the SDK facade and
 * the host app; that duplication is how the onboarding screen ended up selecting
 * LiteRT for GGUF models and failing the import.
 */
internal object BackendSelectors {
    const val UNSUPPORTED_AUTO_BACKEND = "__unsupported_auto_backend__"

    fun isAuto(selector: String): Boolean =
        selector.isBlank() || selector.trim().equals(OmniInferBackend.AUTO, ignoreCase = true)

    fun shouldApplyCatalogDefaults(preferCatalogDefaults: Boolean, selector: String): Boolean =
        preferCatalogDefaults && isAuto(selector)

    /** Map any accepted spelling onto a canonical selector. */
    fun normalizeExplicit(selector: String): String {
        val raw = selector.trim().lowercase(Locale.US).replace('_', '-')
        return when (raw) {
            "llama", "llamacpp", "llama-cpp", "llama.cpp", "llama.cpp/cpu",
            "llama.cpp-cpu", "llamacpp-cpu", "llama-cpp-cpu" -> OmniInferBackend.LLAMA_CPP_CPU

            "llama.cpp-htp", "llama.cpp-npu", "llama.cpp/htp", "llama.cpp/npu",
            "llamacpp-htp", "llama-cpp-htp", "llamacpp-npu", "llama-cpp-npu",
            "llama-htp", "llama-npu" -> OmniInferBackend.LLAMA_CPP_HTP

            "litert", "litert-lm", "litertlm", "litert-lm-cpu", "litert/cpu",
            "litert-lm/cpu" -> OmniInferBackend.LITERT_CPU

            "litert-lm-gpu", "litert/gpu", "litert-lm/gpu", "litertlm-gpu",
            "litert-gpu" -> OmniInferBackend.LITERT_GPU

            "mnn", "mnn-cpu", "mnn/cpu" -> OmniInferBackend.MNN_CPU
            "mnn-opencl", "mnn/opencl" -> OmniInferBackend.MNN_OPENCL
            "mnn-vulkan", "mnn/vulkan" -> OmniInferBackend.MNN_VULKAN

            else -> selector
        }
    }

    /** Escalate a CPU selector when the extra config asks for an accelerator. */
    fun refineSelectorWithExtra(
        selector: String,
        extraConfig: Map<String, String>,
    ): String {
        val accelerator = extraConfig["accelerator"]?.lowercase(Locale.US)
        val backendType = extraConfig["backend_type"]?.lowercase(Locale.US)
        val liteRtBackend = extraConfig["litert_backend"]?.lowercase(Locale.US)
        return when {
            selector == OmniInferBackend.LLAMA_CPP_CPU &&
                (accelerator == "htp" || accelerator == "npu" || backendType == "npu") ->
                OmniInferBackend.LLAMA_CPP_HTP

            selector == OmniInferBackend.LITERT_CPU &&
                (backendType == "gpu" || liteRtBackend == "gpu") ->
                OmniInferBackend.LITERT_GPU

            selector == OmniInferBackend.MNN_CPU && backendType == "opencl" ->
                OmniInferBackend.MNN_OPENCL

            selector == OmniInferBackend.MNN_CPU && backendType == "vulkan" ->
                OmniInferBackend.MNN_VULKAN

            else -> selector
        }
    }

    /** Resolve the selector from a catalog's `backend` + accelerator hint. */
    fun selectorFor(backend: String, accelerator: String?): String {
        val normalizedBackend = backend.lowercase(Locale.US)
        val normalizedAccelerator = accelerator?.lowercase(Locale.US)
        return when {
            normalizedBackend == "llama.cpp" && normalizedAccelerator == "htp" ->
                OmniInferBackend.LLAMA_CPP_HTP
            normalizedBackend == "llama.cpp" && normalizedAccelerator == "npu" ->
                OmniInferBackend.LLAMA_CPP_HTP
            normalizedBackend == "litert" && normalizedAccelerator == "gpu" ->
                OmniInferBackend.LITERT_GPU
            normalizedBackend == "litert" -> OmniInferBackend.LITERT_CPU
            normalizedBackend == "llama.cpp" -> OmniInferBackend.LLAMA_CPP_CPU
            else -> normalizeExplicit(backend)
        }
    }

    /**
     * Fall back to the model file extension when nothing else decides. `.gguf` is
     * llama.cpp, `.litertlm`/`.litert` is LiteRT — this is what keeps a GGUF import
     * from ever being routed to LiteRT.
     */
    fun inferBackendFromPath(modelPath: String): String {
        val lower = modelPath.lowercase(Locale.US)
        return when {
            lower.endsWith(".litertlm") || lower.endsWith(".litert") -> OmniInferBackend.LITERT_GPU
            lower.endsWith(".gguf") -> OmniInferBackend.LLAMA_CPP_CPU
            else -> UNSUPPORTED_AUTO_BACKEND
        }
    }

    /**
     * Full resolution for a load request: explicit selector, catalog default, or
     * model path inference.
     */
    fun normalizeSelector(
        selector: String,
        modelPath: String,
        catalogBackend: String?,
        catalogAccelerator: String?,
    ): String {
        val raw = selector.trim().lowercase(Locale.US).replace('_', '-')
        if (raw.isBlank() || raw == OmniInferBackend.AUTO) {
            val fromCatalog = catalogBackend?.let { selectorFor(it, catalogAccelerator) }
            return fromCatalog ?: inferBackendFromPath(modelPath)
        }
        return normalizeExplicit(selector)
    }

    /** Backend identity the JNI layer expects. */
    fun bridgeBackendFor(selector: String): String {
        return when (selector.lowercase(Locale.US)) {
            OmniInferBackend.LLAMA_CPP_CPU, OmniInferBackend.LLAMA_CPP_HTP -> "llama.cpp"
            OmniInferBackend.LITERT_CPU, OmniInferBackend.LITERT_GPU -> "litert"
            OmniInferBackend.MNN_CPU, OmniInferBackend.MNN_OPENCL, OmniInferBackend.MNN_VULKAN -> "mnn"
            else -> selector
        }
    }

    fun defaultThreadsFor(selector: String): Int {
        return when (selector.lowercase(Locale.US)) {
            OmniInferBackend.LLAMA_CPP_HTP -> 6
            OmniInferBackend.LITERT_CPU -> 4
            else -> 0
        }
    }

    fun defaultCtxFor(selector: String): Int {
        return when (selector.lowercase(Locale.US)) {
            else -> 8192
        }
    }

    fun defaultExtraConfig(selector: String): Map<String, String> {
        return when (selector.lowercase(Locale.US)) {
            OmniInferBackend.LLAMA_CPP_HTP -> mapOf(
                "accelerator" to "htp",
                "backend_type" to "npu",
                "llama_device" to "HTP0",
                "n_gpu_layers" to "99",
                "batch_size" to "1024",
                "ubatch_size" to "1024",
                "hexagon_opfilter" to "SSM_CONV",
            )
            OmniInferBackend.LITERT_GPU -> mapOf(
                "backend_type" to "gpu",
                "litert_backend" to "gpu",
            )
            OmniInferBackend.LITERT_CPU -> mapOf("backend_type" to "cpu")
            OmniInferBackend.MNN_OPENCL -> mapOf("backend_type" to "opencl")
            OmniInferBackend.MNN_VULKAN -> mapOf("backend_type" to "vulkan")
            else -> emptyMap()
        }
    }
}
