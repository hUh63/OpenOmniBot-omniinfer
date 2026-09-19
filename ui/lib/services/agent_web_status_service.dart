import 'package:ui/services/omni_plugin_service.dart';

/// Runtime state of a managed Agent Web process, reported by the native
/// AgentWebRuntimeManager through the plugin action boundary.
enum AgentWebStatus { unknown, notRunning, starting, running }

extension AgentWebStatusState on AgentWebStatus {
  /// A process that is up or on its way up; these are the states where a
  /// stop affordance makes sense.
  bool get active =>
      this == AgentWebStatus.starting || this == AgentWebStatus.running;
}

/// Reads and stops managed Agent Web processes through the lifecycle actions
/// linked from each open action's presentation (`statusAction`/`stopAction`).
/// Surfaces stay declarative: no hardcoded action ids outside the plugin.
class AgentWebStatusService {
  const AgentWebStatusService._();

  static String keyFor(OmniPluginActionItem action) =>
      '${action.pluginId}/${action.id}';

  static String? statusActionId(OmniPluginActionItem action) =>
      _linkedActionId(action, 'statusAction');

  static String? stopActionId(OmniPluginActionItem action) =>
      _linkedActionId(action, 'stopAction');

  static bool supportsLifecycle(OmniPluginActionItem action) =>
      statusActionId(action) != null && stopActionId(action) != null;

  static Future<AgentWebStatus> query(OmniPluginActionItem action) async {
    final actionId = statusActionId(action);
    if (actionId == null) return AgentWebStatus.unknown;
    try {
      final response = await OmniPluginService.invokeAction(
        action.pluginId,
        actionId,
      );
      return parseStatusResponse(response);
    } catch (_) {
      // A failed probe must not break the hosting surface; the tile simply
      // renders without a status badge until the next refresh.
      return AgentWebStatus.unknown;
    }
  }

  static Future<Map<String, AgentWebStatus>> queryAll(
    List<OmniPluginActionItem> actions,
  ) async {
    final entries = await Future.wait(
      actions.map(
        (action) async => MapEntry(keyFor(action), await query(action)),
      ),
    );
    return Map<String, AgentWebStatus>.fromEntries(entries);
  }

  static AgentWebStatus parseStatusResponse(Map<String, dynamic> response) {
    if (response['running'] != true) return AgentWebStatus.notRunning;
    return response['code']?.toString().trim() == 'RUNNING'
        ? AgentWebStatus.running
        : AgentWebStatus.starting;
  }

  /// Stops the managed process. Returns true when the runtime confirms the
  /// process is no longer running.
  static Future<bool> stop(OmniPluginActionItem action) async {
    final actionId = stopActionId(action);
    if (actionId == null) return false;
    final response = await OmniPluginService.invokeAction(
      action.pluginId,
      actionId,
    );
    return response['success'] == true && response['running'] != true;
  }

  static String? _linkedActionId(OmniPluginActionItem action, String key) {
    final value = action.presentation[key]?.toString().trim() ?? '';
    return value.isEmpty ? null : value;
  }
}
