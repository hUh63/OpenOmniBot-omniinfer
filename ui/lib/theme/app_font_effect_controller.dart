import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:ui/services/app_font_effect_service.dart';
import 'package:ui/services/storage_service.dart';

final appFontEffectProvider =
    StateNotifierProvider<AppFontEffectController, AppFontEffectState>(
      (ref) => AppFontEffectController(),
    );

@immutable
class AppFontEffectState {
  const AppFontEffectState({
    required this.enabled,
    this.loading = false,
    this.errorMessage,
  });

  final bool enabled;
  final bool loading;
  final String? errorMessage;

  bool get useEnhancedFonts =>
      enabled && !loading && AppFontEffectService.isLoaded;

  AppFontEffectState copyWith({
    bool? enabled,
    bool? loading,
    Object? errorMessage = _unchanged,
  }) {
    return AppFontEffectState(
      enabled: enabled ?? this.enabled,
      loading: loading ?? this.loading,
      errorMessage: identical(errorMessage, _unchanged)
          ? this.errorMessage
          : errorMessage as String?,
    );
  }
}

class AppFontEffectController extends StateNotifier<AppFontEffectState> {
  AppFontEffectController()
    : super(
        AppFontEffectState(
          enabled:
              StorageService.isEnhancedFontEffectsEnabled() &&
              AppFontEffectService.isLoaded,
        ),
      ) {
    AppFontEffectService.setActive(state.enabled);
  }

  Future<bool> setEnabled(bool enabled) async {
    if (state.loading) {
      return false;
    }
    if (state.enabled == enabled) {
      return true;
    }

    if (!enabled) {
      await StorageService.setEnhancedFontEffectsEnabled(false);
      AppFontEffectService.setActive(false);
      state = state.copyWith(
        enabled: false,
        loading: false,
        errorMessage: null,
      );
      return true;
    }

    state = state.copyWith(loading: true, errorMessage: null);
    try {
      await AppFontEffectService.ensureLoaded();
      await StorageService.setEnhancedFontEffectsEnabled(true);
      AppFontEffectService.setActive(true);
      state = state.copyWith(enabled: true, loading: false, errorMessage: null);
      return true;
    } catch (error) {
      await StorageService.setEnhancedFontEffectsEnabled(false);
      AppFontEffectService.setActive(false);
      state = AppFontEffectState(
        enabled: false,
        loading: false,
        errorMessage: error.toString(),
      );
      return false;
    }
  }
}

const Object _unchanged = Object();
