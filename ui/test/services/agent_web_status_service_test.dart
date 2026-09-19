import 'package:flutter_test/flutter_test.dart';
import 'package:ui/services/agent_web_status_service.dart';
import 'package:ui/services/omni_plugin_service.dart';

void main() {
  OmniPluginActionItem action(Map<String, dynamic> presentation) {
    return OmniPluginActionItem(
      id: 'open_kimi_web',
      pluginId: 'com.omnimind.agent-web',
      displayName: 'Kimi Code Web',
      description: 'Open Kimi Code Web',
      presentation: presentation,
    );
  }

  test('lifecycle links come from the declarative presentation', () {
    final linked = action(<String, dynamic>{
      'statusAction': 'get_kimi_web_status',
      'stopAction': 'stop_kimi_web',
    });
    expect(AgentWebStatusService.supportsLifecycle(linked), isTrue);
    expect(
      AgentWebStatusService.statusActionId(linked),
      'get_kimi_web_status',
    );
    expect(AgentWebStatusService.stopActionId(linked), 'stop_kimi_web');
    expect(AgentWebStatusService.keyFor(linked),
        'com.omnimind.agent-web/open_kimi_web');

    final legacy = action(<String, dynamic>{'placement': 'agent_settings'});
    expect(AgentWebStatusService.supportsLifecycle(legacy), isFalse);
    expect(AgentWebStatusService.statusActionId(legacy), isNull);
    expect(AgentWebStatusService.stopActionId(legacy), isNull);
  });

  test('status responses map to the visible runtime states', () {
    expect(
      AgentWebStatusService.parseStatusResponse(<String, dynamic>{
        'code': 'RUNNING',
        'running': true,
      }),
      AgentWebStatus.running,
    );
    expect(
      AgentWebStatusService.parseStatusResponse(<String, dynamic>{
        'code': 'STARTING',
        'running': true,
      }),
      AgentWebStatus.starting,
    );
    expect(
      AgentWebStatusService.parseStatusResponse(<String, dynamic>{
        'code': 'NOT_RUNNING',
        'running': false,
      }),
      AgentWebStatus.notRunning,
    );
    // A process without a confirmed ready URL is still just starting.
    expect(
      AgentWebStatusService.parseStatusResponse(<String, dynamic>{
        'code': 'STOP_FAILED',
        'running': true,
      }),
      AgentWebStatus.starting,
    );
    expect(AgentWebStatus.running.active, isTrue);
    expect(AgentWebStatus.starting.active, isTrue);
    expect(AgentWebStatus.notRunning.active, isFalse);
    expect(AgentWebStatus.unknown.active, isFalse);
  });
}
