/// Canonical local-inference backend vocabulary for the `ui` package.
///
/// This is the **app level** vocabulary shared by the Flutter layer and the
/// `OmniInferLocalRuntime` native facade. It is a different layer from the
/// OmniInfer SDK's own selectors (see `BackendSelectors.kt`, which speaks
/// `llama.cpp-cpu` / `litert-lm-gpu` / `mnn-opencl` and accepts these names as
/// legacy aliases), so keep the two in sync when a backend is added.
///
/// This list used to be copy-pasted into four files. That duplication is exactly
/// how the onboarding page drifted to `litert` as the "recommended" backend, which
/// made GGUF model imports fail with "Please select a .litertlm model file".
/// Import this file instead of redeclaring the names.
library;

/// llama.cpp CPU text generation. The conservative default.
const String kBackendLlamaCpp = 'llama.cpp';

/// MNN backend served through the OmniInfer runtime.
const String kBackendOmniInferMnn = 'omniinfer-mnn';

/// ExecuTorch + Qualcomm QNN NPU backend.
const String kBackendExecutorchQnn = 'executorch-qnn';

/// LiteRT-LM on CPU.
const String kBackendLiteRt = 'litert';

/// LiteRT-LM on the NPU.
const String kBackendLiteRtNpu = 'litert-npu';

/// Every backend the local-model picker can offer, in display order.
const List<String> kLocalInferenceBackends = <String>[
  kBackendLlamaCpp,
  kBackendOmniInferMnn,
  kBackendExecutorchQnn,
  kBackendLiteRt,
];

/// Aliases the SDK and older app versions may hand back for LiteRT-LM.
const List<String> kLiteRtAliases = <String>[
  'litert',
  'litert-lm',
  'litertlm',
  'litert/cpu',
];

/// Maps any user- or native-supplied backend name onto its canonical value, or
/// `null` when the input is not recognised.
///
/// Use this when "unknown" must stay distinguishable (e.g. deciding whether to
/// apply a deep-link override). Use [normalizeInferenceBackend] when a safe
/// fallback is wanted instead.
String? tryNormalizeInferenceBackend(Object? raw) {
  final String value = (raw ?? '').toString().trim().toLowerCase();
  if (value.isEmpty) {
    return null;
  }
  switch (value) {
    case 'llama.cpp':
    case 'llama-cpp':
    case 'llamacpp':
    case 'llama.cpp/cpu':
    case 'llama.cpp-cpu':
      return kBackendLlamaCpp;
    case 'mnn':
    case 'mnn-cpu':
    case kBackendOmniInferMnn:
      return kBackendOmniInferMnn;
    case 'qnn':
    case 'executorch-qnn':
      return kBackendExecutorchQnn;
    case 'litert-npu':
    case 'litert-lm-npu':
    case 'litertlm-npu':
    case 'litert/npu':
      return kBackendLiteRtNpu;
    case 'litert':
    case 'litert-lm':
    case 'litertlm':
    case 'litert/cpu':
    case 'litert-lm/cpu':
      return kBackendLiteRt;
    default:
      return null;
  }
}

/// Like [tryNormalizeInferenceBackend] but falls back to [kBackendLlamaCpp].
///
/// llama.cpp is the conservative choice: it is the only backend that is always
/// present, and it accepts GGUF models.
String normalizeInferenceBackend(Object? raw) =>
    tryNormalizeInferenceBackend(raw) ?? kBackendLlamaCpp;

/// Label shown in the backend picker for [backend].
String inferenceBackendLabel(String backend) {
  switch (tryNormalizeInferenceBackend(backend)) {
    case kBackendOmniInferMnn:
      return 'omniinfer-mnn';
    case kBackendExecutorchQnn:
      return 'omniinfer-npu';
    case kBackendLiteRt:
      return 'LiteRT-LM';
    case kBackendLiteRtNpu:
      return 'LiteRT-LM-NPU';
    case kBackendLlamaCpp:
    default:
      return 'OmniInfer-llama';
  }
}

/// Backend identity the native `OmniInferLocalRuntime` facade expects.
String nativeBackendName(String backend) {
  switch (tryNormalizeInferenceBackend(backend)) {
    case kBackendOmniInferMnn:
      return 'mnn';
    case kBackendExecutorchQnn:
      return 'executorch-qnn';
    case kBackendLiteRt:
      return 'litert';
    case kBackendLiteRtNpu:
      return 'litert-npu';
    case kBackendLlamaCpp:
    default:
      return 'llama.cpp';
  }
}

/// File extensions each backend can run. Used to keep the import picker honest
/// instead of assuming a single format.
const Map<String, List<String>> kBackendModelExtensions = <String, List<String>>{
  kBackendLlamaCpp: <String>['.gguf'],
  kBackendOmniInferMnn: <String>['.mnn'],
  kBackendExecutorchQnn: <String>['.pte'],
  kBackendLiteRt: <String>['.litertlm', '.litert'],
  kBackendLiteRtNpu: <String>['.litertlm', '.litert'],
};

/// True when [backend] can run [filePath].
bool backendSupportsModelPath(String backend, String filePath) {
  final String lower = filePath.toLowerCase();
  final List<String>? extensions =
      kBackendModelExtensions[normalizeInferenceBackend(backend)];
  if (extensions == null) {
    return false;
  }
  return extensions.any(lower.endsWith);
}
