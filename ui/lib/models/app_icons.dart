class AppIcons {
  final int id;
  final String appName;
  final String packageName;
  final String icon_base64;
  final String icon_path;
  final int createdAt;
  final int updatedAt;

  AppIcons({
    required this.id,
    required this.appName,
    required this.packageName,
    required this.icon_base64,
    this.icon_path = "",
    required this.createdAt,
    required this.updatedAt,
  });

  factory AppIcons.fromMap(Map<dynamic, dynamic> map) {
    return AppIcons(
      id: map['id'] as int,
      appName: map['appName'] as String,
      packageName: map['packageName'] as String,
      icon_base64: map['icon_base64'] as String,
      icon_path: map['icon_path'] as String? ?? "",
      createdAt: map['createdAt'] as int,
      updatedAt: map['updatedAt'] as int,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'appName': appName,
      'packageName': packageName,
      'icon_base64': icon_base64,
      'icon_path': icon_path,
      'createdAt': createdAt,
      'updatedAt': updatedAt,
    };
  }

  @override
  String toString() {
    return 'AppIcons(id: $id, appName: $appName, packageName: $packageName, icon_path: $icon_path, icon_base64: ${icon_base64.substring(0,10)}, createdAt: $createdAt, updatedAt: $updatedAt)';
  }
}