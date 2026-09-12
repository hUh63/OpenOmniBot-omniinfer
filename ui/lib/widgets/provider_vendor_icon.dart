import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:ui/services/model_vendor_catalog.dart';
import 'package:ui/theme/theme_context.dart';

/// 模型厂商图标。
///
/// 彩色品牌图标直接渲染；单色图标按主题文字色着色；[vendor] 为空（未识别厂商）
/// 时显示通用占位图标。[disabled] 时整体降低透明度。
class ProviderVendorIcon extends StatelessWidget {
  const ProviderVendorIcon({
    super.key,
    required this.vendor,
    this.size = 16,
    this.disabled = false,
    this.monochromeColor,
    this.forceMonochrome = false,
  });

  final ModelVendorInfo? vendor;
  final double size;
  final bool disabled;

  /// 单色图标与占位图标的着色；默认取主题次级文字色。
  final Color? monochromeColor;

  /// 设为 true 时,即使品牌图标本身是彩色,也强制按 [monochromeColor] 着色。
  /// 用于需要图标融入主题色的场景（如输入框内的模型选择按钮)。
  final bool forceMonochrome;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    final tint = monochromeColor ?? palette.textSecondary;
    final resolved = vendor;

    Widget icon;
    if (resolved == null) {
      icon = Icon(Icons.auto_awesome_rounded, size: size, color: tint);
    } else {
      final shouldTint = forceMonochrome || resolved.iconIsMonochrome;
      icon = SvgPicture.asset(
        resolved.iconAsset,
        width: size,
        height: size,
        colorFilter: shouldTint
            ? ColorFilter.mode(tint, BlendMode.srcIn)
            : null,
      );
    }
    if (disabled) {
      icon = Opacity(opacity: 0.45, child: icon);
    }
    return SizedBox(width: size, height: size, child: Center(child: icon));
  }
}
