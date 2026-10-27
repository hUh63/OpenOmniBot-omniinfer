class StorageKeys {
  StorageKeys._(); // 私有构造函数，防止实例化

  // ==================== 应用状态 Keys ====================
  /// 引导页是否已完成
  static const String welcomeCompleted = 'welcome_completed';

  /// 启动时未登录账号提示是否已选择不再提醒
  static const String startupAccountPromptDismissed =
      'startup_account_prompt_dismissed';

  /// 自启动权限是否已手动确认完成
  static const String autoStartPermissionGranted =
      'auto_start_permission_granted';

  /// 自启动权限手动确认完成的时间（毫秒时间戳）。
  /// 自启动白名单在系统更新/手机管家清理后会失效，这个「确认过」只是自报状态，
  /// 靠时间戳让它过期重新引导，不然会一直显示已授权、后台却被杀。
  static const String autoStartPermissionGrantedAt =
      'auto_start_permission_granted_at';

  /// Termux 终端能力是否已在引导页手动确认完成
  static const String termuxPermissionGranted = 'termux_permission_granted';
}
