import 'package:ui/services/device_service.dart';

class PermissionGuideStep {
  final String title;
  final String description;
  final String? imageAssetPath;

  const PermissionGuideStep({
    required this.title,
    required this.description,
    this.imageAssetPath,
  });
}

class PermissionGuideBrandInfo {
  final String id;
  final String name;
  final String osLabel;
  final List<String> aliases;

  const PermissionGuideBrandInfo({
    required this.id,
    required this.name,
    required this.osLabel,
    required this.aliases,
  });
}

class PermissionGuideTopicInfo {
  final String id;
  final String title;
  final String subtitle;
  final String iconPath;
  final String openMethod;
  final Set<String>? supportedBrands;
  final Map<String, List<PermissionGuideStep>> brandSteps;

  const PermissionGuideTopicInfo({
    required this.id,
    required this.title,
    required this.subtitle,
    required this.iconPath,
    required this.openMethod,
    required this.brandSteps,
    this.supportedBrands,
  });

  bool supportsBrand(String brandId) {
    return supportedBrands == null || supportedBrands!.contains(brandId);
  }

  List<PermissionGuideStep> stepsFor(String brandId) {
    return brandSteps[brandId] ?? brandSteps['other'] ?? const [];
  }
}

class PermissionGuideRepository {
  PermissionGuideRepository._();

  static const List<PermissionGuideBrandInfo> brands = [
    PermissionGuideBrandInfo(
      id: 'huawei',
      name: '华为',
      osLabel: 'HarmonyOS 4.0',
      aliases: ['huawei'],
    ),
    PermissionGuideBrandInfo(
      id: 'honor',
      name: '荣耀',
      osLabel: 'MagicOS 8.0',
      aliases: ['honor', 'honour'],
    ),
    PermissionGuideBrandInfo(
      id: 'xiaomi',
      name: '小米',
      osLabel: 'HyperOS 2.0',
      aliases: ['xiaomi', 'redmi'],
    ),
    PermissionGuideBrandInfo(
      id: 'oppo',
      name: 'OPPO / 一加 / realme',
      osLabel: 'ColorOS 16',
      aliases: ['oppo', 'oneplus', 'realme'],
    ),
    PermissionGuideBrandInfo(
      id: 'vivo',
      name: 'vivo / iQOO',
      osLabel: 'OriginOS 4',
      aliases: ['vivo', 'iqoo'],
    ),
    PermissionGuideBrandInfo(
      id: 'meizu',
      name: '魅族',
      osLabel: 'Flyme 10',
      aliases: ['meizu', '魅族'],
    ),
    PermissionGuideBrandInfo(
      id: 'other',
      name: '其他品牌',
      osLabel: 'Android',
      aliases: [],
    ),
  ];

  static const List<PermissionGuideTopicInfo> _topics = [
    PermissionGuideTopicInfo(
      id: 'overlay',
      title: '悬浮窗权限',
      subtitle: '允许小万显示在其他应用上层，便于随时唤起。',
      iconPath: 'assets/welcome/permission_overlay.svg',
      openMethod: 'openOverlaySettings',
      brandSteps: {
        'oppo': [
          PermissionGuideStep(
            title: '点击“悬浮窗权限”',
            description: '在小万的权限引导页点击该权限，系统会跳转到对应设置页。',
          ),
          PermissionGuideStep(
            title: '选择“小万”',
            description: '在“显示在其他应用的上层”列表中找到并点击小万。',
          ),
          PermissionGuideStep(
            title: '打开“在其他应用上方显示”开关',
            description: '开启后小万才能稳定显示在前台。',
          ),
        ],
        'huawei': [
          PermissionGuideStep(
            title: '点击“悬浮窗权限”',
            description: '在小万的权限引导页点击该权限，系统会跳转到对应设置页。',
          ),
          PermissionGuideStep(
            title: '找到“小万”',
            description: '在悬浮窗管理列表中找到小万并开启权限。',
          ),
        ],
        'honor': [
          PermissionGuideStep(
            title: '点击“悬浮窗权限”',
            description: '在小万的权限引导页点击该权限，系统会跳转到设置页。',
          ),
          PermissionGuideStep(title: '开启悬浮窗权限', description: '找到小万，打开悬浮窗开关。'),
        ],
        'xiaomi': [
          PermissionGuideStep(
            title: '点击“悬浮窗权限”',
            description: '在小万的权限引导页点击该权限，系统会跳转到设置页。',
          ),
          PermissionGuideStep(
            title: '开启悬浮窗权限',
            description: '找到小万，打开“显示悬浮窗”开关。',
          ),
        ],
        'vivo': [
          PermissionGuideStep(
            title: '点击“悬浮窗权限”',
            description: '在小万的权限引导页点击该权限，系统会跳转到设置页。',
          ),
          PermissionGuideStep(
            title: '允许显示在其他应用上层',
            description: '找到小万，开启悬浮窗权限。',
          ),
        ],
        'other': [
          PermissionGuideStep(title: '打开系统设置', description: '进入手机“设置”应用。'),
          PermissionGuideStep(title: '进入应用管理', description: '找到“应用”或“应用管理”入口。'),
          PermissionGuideStep(
            title: '开启悬浮窗权限',
            description: '找到小万，在权限设置中开启“悬浮窗”或“显示在其他应用上层”。',
          ),
        ],
      },
    ),
    PermissionGuideTopicInfo(
      id: 'battery',
      title: '后台运行 / 电池优化',
      subtitle: '防止系统过早回收小万，保证后台运行和唤起稳定性。',
      iconPath: 'assets/welcome/permission_battery.svg',
      openMethod: 'openBatteryOptimizationSettings',
      supportedBrands: {'oppo', 'xiaomi', 'vivo', 'meizu', 'other'},
      brandSteps: {
        'oppo': [
          PermissionGuideStep(title: '打开设置', description: '进入手机“设置”应用。'),
          PermissionGuideStep(
            title: '进入“电池”设置',
            description: '在设置页面中找到并点击“电池”选项。',
          ),
          PermissionGuideStep(
            title: '点击“更多电池设置”',
            description: '向下滑动，找到“更多电池设置”选项。',
          ),
          PermissionGuideStep(
            title: '进入“优化应用电量使用”',
            description: '点击“优化应用电量使用”进入应用列表。',
          ),
          PermissionGuideStep(
            title: '设置小万为“不优化”',
            description: '找到小万，将其设置为“不优化”，系统就不会轻易限制后台运行。',
          ),
        ],
        'xiaomi': [
          PermissionGuideStep(title: '打开设置', description: '进入手机“设置”应用。'),
          PermissionGuideStep(
            title: '进入“应用设置”',
            description: '点击“应用设置”并继续进入“应用管理”。',
          ),
          PermissionGuideStep(title: '找到小万', description: '在应用列表中找到并点击小万。'),
          PermissionGuideStep(
            title: '设置“省电策略”',
            description: '点击“省电策略”，选择“无限制”。',
          ),
        ],
        'vivo': [
          PermissionGuideStep(title: '打开设置', description: '进入手机“设置”应用。'),
          PermissionGuideStep(
            title: '进入“电池”设置',
            description: '点击“电池”，再进入“后台耗电管理”。',
          ),
          PermissionGuideStep(
            title: '设置小万为“允许高耗电”',
            description: '找到小万，选择“允许高耗电后台运行”。',
          ),
        ],
        'other': [
          PermissionGuideStep(title: '打开系统设置', description: '进入手机“设置”应用。'),
          PermissionGuideStep(
            title: '找到电池或电量管理',
            description: '查找“电池”“电量管理”或“后台管理”。',
          ),
          PermissionGuideStep(
            title: '允许后台运行',
            description: '找到小万，设置为后台允许运行、不优化或无限制，建议同时检查自启动权限。',
          ),
        ],
      },
    ),
    PermissionGuideTopicInfo(
      id: 'appLaunch',
      title: '应用启动管理',
      subtitle: '华为 / 荣耀机型常见的额外保活设置，建议和后台运行权限一起检查。',
      iconPath: 'assets/welcome/permission_autostart.svg',
      openMethod: 'openAutoStartSettings',
      supportedBrands: {'huawei', 'honor'},
      brandSteps: {
        'huawei': [
          PermissionGuideStep(
            title: '打开设置',
            description: '进入手机“设置”应用。',
            imageAssetPath: 'assets/welcome/auto_start_guide_1.png',
          ),
          PermissionGuideStep(
            title: '搜索“应用启动管理”',
            description: '通过搜索或在“应用和服务”里找到“应用启动管理”。',
            imageAssetPath: 'assets/welcome/auto_start_guide_2.png',
          ),
          PermissionGuideStep(
            title: '将小万设为手动管理',
            description: '关闭自动管理，并开启允许自启动、允许关联启动、允许后台活动。',
            imageAssetPath: 'assets/welcome/auto_start_guide_3.png',
          ),
        ],
        'honor': [
          PermissionGuideStep(
            title: '打开设置',
            description: '进入手机“设置”应用。',
            imageAssetPath: 'assets/welcome/auto_start_guide_1.png',
          ),
          PermissionGuideStep(
            title: '搜索“应用启动管理”',
            description: '通过搜索或在系统管理入口中找到“应用启动管理”。',
            imageAssetPath: 'assets/welcome/auto_start_guide_2.png',
          ),
          PermissionGuideStep(
            title: '关闭自动管理并全部放开',
            description: '找到小万后关闭自动管理，确保所有后台活动相关开关都已开启。',
            imageAssetPath: 'assets/welcome/auto_start_guide_3.png',
          ),
        ],
      },
    ),
  ];

  static PermissionGuideBrandInfo brandInfo(String brandId) {
    final normalized = normalizeBrandId(brandId);
    for (final brand in brands) {
      if (brand.id == normalized) {
        return brand;
      }
    }
    return brands.last;
  }

  static List<PermissionGuideBrandInfo> selectableBrands() {
    return brands;
  }

  static String normalizeBrandId(String? rawBrand) {
    final raw = (rawBrand ?? '').trim().toLowerCase();
    if (raw.isEmpty) {
      return 'other';
    }

    for (final brand in brands) {
      if (brand.id == raw) {
        return brand.id;
      }
      for (final alias in brand.aliases) {
        if (raw.contains(alias)) {
          return brand.id;
        }
      }
    }
    return 'other';
  }

  static String normalizeDeviceBrand(Map<String, dynamic>? deviceInfo) {
    if (deviceInfo == null) {
      return 'other';
    }
    final joined = [
      deviceInfo['manufacturer'],
      deviceInfo['brand'],
      deviceInfo['model'],
    ].whereType<String>().join(' ').toLowerCase();
    return normalizeBrandId(joined);
  }

  static Future<String> detectCurrentBrand() async {
    try {
      final info = await DeviceService.getDeviceInfo();
      return normalizeDeviceBrand(info);
    } catch (_) {
      return 'other';
    }
  }

  static List<PermissionGuideTopicInfo> topicsForBrand(String brandId) {
    final normalized = normalizeBrandId(brandId);
    final topics = <PermissionGuideTopicInfo>[];
    for (final topic in _topics) {
      if (topic.supportsBrand(normalized)) {
        topics.add(topic);
      }
    }
    return topics;
  }

  static PermissionGuideTopicInfo? topicById(String topicId) {
    for (final topic in _topics) {
      if (topic.id == topicId) {
        return topic;
      }
    }
    return null;
  }
}
