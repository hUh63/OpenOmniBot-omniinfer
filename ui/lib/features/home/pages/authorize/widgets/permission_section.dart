import 'package:flutter/material.dart';
import 'package:ui/features/home/pages/authorize/widgets/permiss_row.dart';

class PermissionSection extends StatelessWidget {
  final List<PermissionData> permissions;
  final double spacing;
  final VoidCallback? onPermissionChanged;

  const PermissionSection({
    Key? key,
    required this.permissions,
    this.spacing = 40,
    this.onPermissionChanged,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        SingleChildScrollView(
          child: Column(
            children: permissions.asMap().entries.map((entry) {
              final idx = entry.key;
              final item = entry.value;
              return Padding(
                padding: EdgeInsets.only(
                  bottom: idx == permissions.length - 1 ? 0 : spacing,
                ),
                child: PermissionRow(
                  iconPath: item.iconPath,
                  iconWidth: item.iconWidth,
                  iconHeight: item.iconHeight,
                  permissionName: item.name,
                  permissionDescription: item.description,
                  isAuthorized: item.notifier,
                  onAuthorize: () async {
                    await item.authorize();
                    onPermissionChanged?.call();
                  },
                  iconInfo: item.iconInfo,
                  iconClick: item.iconClick,
                ),
              );
            }).toList(),
          ),
        ),
        // ValueListenableBuilder<bool>(
        //   valueListenable: widget.allAuthorized,
        //   builder: (context, ok, child) {
        //     return Padding(
        //       padding: const EdgeInsets.fromLTRB(24.0, 10, 24.0, 0),
        //       child: SizedBox(
        //         width: 327,
        //         height: 44,
        //         child: ElevatedButton(
        //           style: ElevatedButton.styleFrom(
        //             backgroundColor: const Color(0xFF00AEFF),
        //             shape: RoundedRectangleBorder(
        //               borderRadius: BorderRadius.circular(50),
        //             ),
        //             padding: const EdgeInsets.symmetric(
        //               horizontal: 20,
        //               vertical: 10,
        //             ),
        //             elevation: 0,
        //           ),
        //           onPressed: ok
        //               ? () => _navigateToHomePage()
        //               : null,
        //           child: const Text(
        //             '开始体验',
        //             textAlign: TextAlign.center,
        //             style: TextStyle(
        //               color: Colors.white,
        //               fontSize: 16,
        //               fontFamily: 'PingFang SC',
        //               fontWeight: FontWeight.w600,
        //               height: 1.25,
        //               letterSpacing: 0.50,
        //             ),
        //           ),
        //         ),
        //       ),
        //     );
        //   },
        // ),
        // const SizedBox(height: 25),
      ],
    );
  }
}

class PermissionData {
  final String id;
  final String iconPath;
  final double iconWidth;
  final double iconHeight;
  final String name;
  final String description;
  final ValueNotifier<bool> notifier = ValueNotifier(false);
  final Future<void> Function() onAuthorize;
  final Future<bool> Function() checkAuthorization;
  final String? iconInfo; // info 文案
  final VoidCallback? iconClick; // info 点击事件

  PermissionData({
    this.id = '',
    required this.iconPath,
    required this.iconWidth,
    required this.iconHeight,
    required this.name,
    required this.description,
    required this.onAuthorize,
    required this.checkAuthorization,
    this.iconInfo,
    this.iconClick,
  });

  /// 调用授权逻辑并自动更新 notifier
  Future<void> authorize() async {
    await onAuthorize();
    notifier.value = await checkAuthorization();
  }
}
