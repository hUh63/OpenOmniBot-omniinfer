import 'package:ui/features/local_model/local_model_feature.dart';
import 'package:ui/services/inference_backend.dart';
import 'package:ui/services/mnn_local_models_service.dart';

class _OmniinferLocalModelFeature extends LocalModelFeature {
  static const String _builtinProfileId = 'omniinfer-local';
  static const String _legacyBuiltinProfileId = 'mnn-local';
  // llama.cpp is the conservative default: always present and it accepts GGUF.
  // Keep this sourced from the shared vocabulary so the onboarding page, the
  // backend picker and this default can never drift apart again.
  static const String _recommendedBackend = kBackendLlamaCpp;
  static const String _recommendedModelId = 'Gemma-4-E2B';

  @override
  bool get enabled => true;

  @override
  bool isBuiltinLocalProvider(String? profileId) {
    final normalized = profileId?.trim();
    return normalized == _builtinProfileId ||
        normalized == _legacyBuiltinProfileId;
  }

  @override
  Future<String?> findInstalledRecommendedModelId() async {
    // 这里只是「查一下推荐模型装没装」这一只读探测：绝不能 setBackend。setBackend 会把
    // 用户正在跑的 MNN/QNN/LiteRT 引擎 stop 掉（还顺带停局域网代理）并静默改掉他的选择，
    // 而调用方以为只是读一下列表。listAllInstalledModels 跨后端汇总，正好够用。
    final installed = await MnnLocalModelsService.listAllInstalledModels();
    for (final model in installed) {
      final normalized = model.id.trim().toLowerCase();
      if (normalized == _recommendedModelId.toLowerCase()) {
        return model.id;
      }
    }
    return null;
  }

  @override
  Future<Map<String, dynamic>?> preloadModelIfNeeded({
    required String providerProfileId,
    required String modelId,
  }) async {
    if (!isBuiltinLocalProvider(providerProfileId)) {
      return null;
    }
    final result = await MnnLocalModelsService.preloadModel(modelId: modelId);
    return result?.cast<String, dynamic>();
  }
}

void configureOmniinferLocalModelFeature() {
  setLocalModelFeature(_OmniinferLocalModelFeature());
}
