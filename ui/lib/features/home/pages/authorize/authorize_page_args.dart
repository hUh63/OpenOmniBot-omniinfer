const String kAccessibilityPermissionId = 'accessibility';
const String kOverlayPermissionId = 'overlay';
const String kInstalledAppsPermissionId = 'installed_apps';
const String kShizukuPermissionId = 'shizuku';
const String kWorkspaceStoragePermissionId = 'workspace_storage';
const String kPublicStoragePermissionId = 'public_storage';

const List<String> kTaskExecutionRequiredPermissionIds = <String>[
  kAccessibilityPermissionId,
  kOverlayPermissionId,
];

const Set<String> _executionPermissionIds = <String>{
  kAccessibilityPermissionId,
  kOverlayPermissionId,
  kInstalledAppsPermissionId,
  kShizukuPermissionId,
  kWorkspaceStoragePermissionId,
  kPublicStoragePermissionId,
};

const Map<String, String> _executionPermissionAliases = <String, String>{
  '无障碍权限': kAccessibilityPermissionId,
  'Android GUI 无障碍权限': kAccessibilityPermissionId,
  'Accessibility': kAccessibilityPermissionId,
  'Accessibility Permission': kAccessibilityPermissionId,
  '悬浮窗权限': kOverlayPermissionId,
  'Overlay': kOverlayPermissionId,
  '应用列表读取权限': kInstalledAppsPermissionId,
  'Installed Apps Access': kInstalledAppsPermissionId,
  'Shizuku 权限': kShizukuPermissionId,
  'Shizuku Permission': kShizukuPermissionId,
  '内置 workspace': kWorkspaceStoragePermissionId,
  'Built-in workspace': kWorkspaceStoragePermissionId,
  '公共文件访问': kPublicStoragePermissionId,
  'Public Storage Access': kPublicStoragePermissionId,
};

List<String> normalizeRequiredPermissionIds(Iterable<dynamic>? rawIds) {
  if (rawIds == null) return const <String>[];
  return rawIds
      .map((item) => item.toString().trim())
      .where((item) => item.isNotEmpty)
      .toSet()
      .toList(growable: false);
}

List<String> resolveExecutionPermissionIds(Iterable<dynamic>? rawValues) {
  if (rawValues == null) return const <String>[];
  return rawValues
      .map((item) => item.toString().trim())
      .map((item) => _executionPermissionAliases[item] ?? item)
      .where(_executionPermissionIds.contains)
      .toSet()
      .toList(growable: false);
}

class AuthorizePageArgs {
  final List<String> requiredPermissionIds;

  const AuthorizePageArgs({this.requiredPermissionIds = const <String>[]});

  static const AuthorizePageArgs taskExecution = AuthorizePageArgs(
    requiredPermissionIds: kTaskExecutionRequiredPermissionIds,
  );
}
