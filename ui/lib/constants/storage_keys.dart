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

  /// Termux 终端能力是否已在引导页手动确认完成
  static const String termuxPermissionGranted = 'termux_permission_granted';
}
