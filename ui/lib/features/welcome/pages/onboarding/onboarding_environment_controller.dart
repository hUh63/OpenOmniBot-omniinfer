import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:ui/services/special_permission.dart';

import 'onboarding_definitions.dart';
import 'onboarding_l10n.dart';

/// Owns the local-environment side of onboarding: the Linux distribution,
/// the development preset, optional tools, and the install-progress
/// simulation shown on the progress page.
class OnboardingEnvironmentController extends ChangeNotifier {
  OnboardingEnvironmentController() {
    _progressSubscription = embeddedTerminalInitProgressStream.listen(
      (_) => unawaited(_reloadSnapshot()),
      onError: (_) {},
    );
  }

  StreamSubscription<EmbeddedTerminalInitProgress>? _progressSubscription;
  Timer? _progressTimer;
  bool _disposed = false;
  bool _snapshotLoading = false;

  EmbeddedTerminalDistribution _distribution =
      EmbeddedTerminalDistribution.alpine;
  String _presetId = 'general';
  Set<String> _optionalToolIds = <String>{};

  bool _isDistributionLoading = true;
  bool _isBusy = false;
  bool _ready = false;
  bool _failed = false;
  double _progress = 0;
  double _nativeProgress = 0;
  String _stage = '';

  EmbeddedTerminalDistribution get distribution => _distribution;
  String get presetId => _presetId;
  Set<String> get optionalToolIds => _optionalToolIds;
  bool get isDistributionLoading => _isDistributionLoading;
  bool get isBusy => _isBusy;
  bool get ready => _ready;
  bool get failed => _failed;
  double get progress => _progress;
  String get stage => _stage;

  Future<void> cancelSetup() async {
    if (!_isBusy) return;
    await cancelEmbeddedTerminalInit();
  }

  EnvironmentPreset get selectedPreset =>
      environmentPresets.firstWhere((item) => item.id == _presetId);

  String get distributionName =>
      _distribution == EmbeddedTerminalDistribution.ubuntu
      ? 'Ubuntu'
      : 'Alpine';

  List<String> get selectedPackageIds => <String>{
    ...selectedPreset.packageIds,
    ..._optionalToolIds,
  }.toList(growable: false);

  String get selectedToolLabels => optionalTools
      .where((tool) => _optionalToolIds.contains(tool.id))
      .map((tool) => tool.label)
      .join(' · ');

  @override
  void dispose() {
    _disposed = true;
    _progressSubscription?.cancel();
    _progressTimer?.cancel();
    super.dispose();
  }

  void _emit() {
    if (!_disposed) notifyListeners();
  }

  Future<void> loadDistribution() async {
    try {
      final distribution = await getEmbeddedTerminalDistribution();
      if (_disposed) return;
      _distribution = distribution;
      _emit();
    } catch (_) {
      // Alpine remains the safe visible default when the native channel is
      // unavailable in widget tests or unsupported builds.
    } finally {
      if (!_disposed) {
        _isDistributionLoading = false;
        _emit();
      }
    }
  }

  void selectDistribution(EmbeddedTerminalDistribution value) {
    if (_isBusy || _distribution == value) return;
    _distribution = value;
    _resetOutcome();
    _emit();
  }

  void selectPreset(String id) {
    if (_isBusy || _presetId == id) return;
    _presetId = id;
    _resetOutcome();
    _emit();
  }

  void toggleOptionalTool(String id) {
    if (_isBusy) return;
    if (_optionalToolIds.contains(id)) {
      _optionalToolIds = <String>{..._optionalToolIds}..remove(id);
    } else {
      _optionalToolIds = <String>{..._optionalToolIds, id};
    }
    _resetOutcome();
    _emit();
  }

  void _resetOutcome() {
    _ready = false;
    _failed = false;
    _progress = 0;
  }

  Future<void> _reloadSnapshot() async {
    if (_snapshotLoading) return;
    _snapshotLoading = true;
    try {
      final snapshot = await getEmbeddedTerminalInitSnapshot();
      if (_disposed || !_isBusy) return;
      if (!snapshot.running) return;
      _nativeProgress = snapshot.progress;
      if (snapshot.stage.trim().isNotEmpty) {
        _stage = snapshot.stage.trim();
      }
      _emit();
    } catch (_) {
    } finally {
      _snapshotLoading = false;
    }
  }

  void _startProgressTracking() {
    _progressTimer?.cancel();
    _progressTimer = Timer.periodic(const Duration(milliseconds: 350), (_) {
      if (_disposed || !_isBusy) {
        _progressTimer?.cancel();
        return;
      }
      unawaited(_reloadSnapshot());
      final stageFloor = _progressFloor(_stage);
      final stageCeiling = _progressCeiling(_stage).clamp(_progress, 0.99);
      final requiredTarget = [
        _nativeProgress,
        stageFloor,
      ].reduce((left, right) => left > right ? left : right);
      final current = _progress;
      double next;
      if (current + 0.001 < requiredTarget) {
        final distance = requiredTarget - current;
        next = current + (distance * 0.24).clamp(0.008, 0.035);
      } else {
        final remaining = stageCeiling - current;
        next = remaining <= 0
            ? current
            : current + (remaining * 0.018).clamp(0.0006, 0.004);
      }
      next = next.clamp(current, stageCeiling);
      if ((next - current).abs() >= 0.0001) {
        _progress = next;
        _emit();
      }
    });
  }

  double _progressFloor(String stage) {
    return switch (phaseIndex(stage)) {
      0 => 0.03,
      1 => 0.10,
      2 => 0.54,
      3 => 0.90,
      _ => 0.99,
    };
  }

  double _progressCeiling(String stage) {
    return switch (phaseIndex(stage)) {
      0 => 0.09,
      1 => 0.50,
      2 => 0.89,
      3 => 0.98,
      _ => 0.99,
    };
  }

  int phaseIndex(String stage) {
    final normalized = stage.trim();
    if (_ready ||
        normalized.contains('配置完成') ||
        normalized.contains('均已就绪') ||
        normalized.contains('所选开发工具已就绪')) {
      return 4;
    }
    var phase = 0;
    if (normalized.contains('验证') ||
        normalized.contains('安装完成') ||
        normalized.contains('校验完成')) {
      phase = 3;
    } else if (normalized.contains('所选开发工具') ||
        normalized.contains('Agent CLI 包')) {
      phase = 2;
    } else if (normalized.contains('workspace') ||
        normalized.contains('终端') ||
        normalized.contains('Linux') ||
        normalized.contains('Alpine') ||
        normalized.contains('Ubuntu') ||
        normalized.contains('运行资源')) {
      phase = 1;
    }
    final knownProgress = _nativeProgress > _progress
        ? _nativeProgress
        : _progress;
    final progressPhase = knownProgress >= 0.90
        ? 3
        : knownProgress >= 0.54
        ? 2
        : knownProgress >= 0.10
        ? 1
        : 0;
    return phase > progressPhase ? phase : progressPhase;
  }

  Future<void> _completeProgressAnimation(bool reduceMotion) async {
    _progressTimer?.cancel();
    if (reduceMotion) {
      _progress = 1;
      _emit();
      return;
    }
    final start = _progress;
    for (var frame = 1; frame <= 12; frame++) {
      await Future<void>.delayed(const Duration(milliseconds: 45));
      if (_disposed) return;
      final t = frame / 12;
      final eased = 1 - (1 - t) * (1 - t);
      _progress = start + (1 - start) * eased;
      _emit();
    }
  }

  /// Runs the full environment setup. Returns true on success.
  Future<bool> runSetup({
    required OnboardingTranslator t,
    required bool Function() reduceMotion,
  }) async {
    if (_isBusy || _isDistributionLoading) return _ready;
    _isBusy = true;
    _ready = false;
    _failed = false;
    _progress = 0.02;
    _nativeProgress = 0.02;
    _stage = t('正在保存你的选择…', 'Saving your choices…');
    _emit();
    _startProgressTracking();

    try {
      final saved = await setEmbeddedTerminalDistribution(_distribution);
      if (_disposed) return false;
      _distribution = saved;
      _stage = t(
        '正在准备 ${saved == EmbeddedTerminalDistribution.ubuntu ? 'Ubuntu' : 'Alpine'} 系统…',
        'Preparing ${saved == EmbeddedTerminalDistribution.ubuntu ? 'Ubuntu' : 'Alpine'}…',
      );
      _emit();
      final result = await prepareTermuxLiveWrapper(
        packageIds: selectedPackageIds,
      );
      if (_disposed) return false;
      await _reloadSnapshot();
      final success = result['success'] == true;
      if (success) {
        await _completeProgressAnimation(reduceMotion());
      } else {
        _progressTimer?.cancel();
      }
      if (_disposed) return false;
      _isBusy = false;
      _ready = success;
      _failed = !success;
      if (success) {
        _progress = 1;
        _stage = t(
          '系统与开发环境已准备完成',
          'System and development environment are ready',
        );
      } else {
        _stage = (result['message'] ?? '').toString().trim().isNotEmpty
            ? (result['message'] ?? '').toString().trim()
            : t('配置未完成，请重试', 'Setup did not finish. Please retry.');
      }
      _emit();
      return success;
    } on PlatformException catch (error) {
      if (_disposed) return false;
      _progressTimer?.cancel();
      _isBusy = false;
      _failed = true;
      _stage = error.message ?? t('配置失败，请重试', 'Setup failed. Please retry.');
      _emit();
      return false;
    } catch (error) {
      if (_disposed) return false;
      _progressTimer?.cancel();
      _isBusy = false;
      _failed = true;
      _stage = t('配置失败：$error', 'Setup failed: $error');
      _emit();
      return false;
    }
  }
}
