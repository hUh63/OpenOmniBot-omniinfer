import 'package:flutter/widgets.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_svg/flutter_svg.dart';

/// A wrapper for [Icon] that inverts its color against the background.
///
/// This is useful for icons that need to be visible on both light and dark backgrounds
/// without manually changing the color. It works by painting the icon in white
/// and using [BlendMode.difference] to composite it onto the background.
class AutoInvertIcon extends StatelessWidget {
  const AutoInvertIcon(
    this.icon, {
    super.key,
    this.size,
    this.weight,
    this.fill,
    this.grade,
    this.opticalSize,
    this.shadows,
    this.semanticLabel,
    this.textDirection,
    this.blendMode = BlendMode.difference,
  });

  final IconData? icon;
  final double? size;
  final double? weight;
  final double? fill;
  final double? grade;
  final double? opticalSize;
  final List<Shadow>? shadows;
  final String? semanticLabel;
  final TextDirection? textDirection;

  /// Usually [BlendMode.difference] (invert) or [BlendMode.exclusion] (softer).
  final BlendMode blendMode;

  @override
  Widget build(BuildContext context) {
    return _BlendMask(
      blendMode: blendMode,
      child: Icon(
        icon,
        size: size,
        weight: weight,
        fill: fill,
        grade: grade,
        opticalSize: opticalSize,
        shadows: shadows,
        semanticLabel: semanticLabel,
        textDirection: textDirection,
        // Paint the icon itself in white; the blend mode does the "auto invert".
        color: const Color(0xFFFFFFFF),
      ),
    );
  }
}

/// A small helper widget that inverts whatever is behind it by blending
/// the icon layer with the background using [BlendMode.difference] (or others).
///
/// Why not `ColorFiltered` / `ShaderMask`?
/// - For some compositions, those may affect transparent pixels and create a
///   visible "square background".
/// - This widget uses `canvas.saveLayer` and applies the blend mode when the
///   offscreen layer is composited back, so transparent pixels remain transparent.
///
/// Notes:
/// - For best/strongest invert effect, the icon is painted in solid white
///   (then `difference` against background ≈ invert).
class AutoInvertSvgIcon extends StatelessWidget {
  const AutoInvertSvgIcon.asset(
    this.assetName, {
    super.key,
    this.size,
    this.width,
    this.height,
    this.fit = BoxFit.contain,
    this.alignment = Alignment.center,
    this.matchTextDirection = false,
    this.package,
    this.bundle,
    this.semanticsLabel,
    this.excludeFromSemantics = false,
    this.blendMode = BlendMode.difference,
  });

  final String assetName;

  /// If provided, sets both width & height.
  final double? size;
  final double? width;
  final double? height;

  final BoxFit fit;
  final AlignmentGeometry alignment;
  final bool matchTextDirection;

  final String? package;
  final AssetBundle? bundle;

  final String? semanticsLabel;
  final bool excludeFromSemantics;

  /// Usually [BlendMode.difference] (invert) or [BlendMode.exclusion] (softer).
  final BlendMode blendMode;

  @override
  Widget build(BuildContext context) {
    final w = width ?? size;
    final h = height ?? size;

    return _BlendMask(
      blendMode: blendMode,
      child: SvgPicture.asset(
        assetName,
        width: w,
        height: h,
        fit: fit,
        alignment: alignment,
        matchTextDirection: matchTextDirection,
        package: package,
        bundle: bundle,
        semanticsLabel: semanticsLabel,
        excludeFromSemantics: excludeFromSemantics,
        // Paint the icon itself in white; the blend mode does the "auto invert".
        colorFilter: const ColorFilter.mode(Color(0xFFFFFFFF), BlendMode.srcIn),
      ),
    );
  }
}

class _BlendMask extends SingleChildRenderObjectWidget {
  const _BlendMask({
    required this.blendMode,
    required super.child,
  });

  final BlendMode blendMode;

  @override
  RenderObject createRenderObject(BuildContext context) {
    return _RenderBlendMask(blendMode);
  }

  @override
  void updateRenderObject(BuildContext context, _RenderBlendMask renderObject) {
    renderObject.blendMode = blendMode;
  }
}

class _RenderBlendMask extends RenderProxyBox {
  _RenderBlendMask(this.blendMode);

  BlendMode blendMode;

  @override
  void paint(PaintingContext context, Offset offset) {
    final rect = offset & size;

    // Create an offscreen layer, paint the child into it,
    // then composite the layer back with the desired blend mode.
    context.canvas.saveLayer(rect, Paint()..blendMode = blendMode);
    super.paint(context, offset);
    context.canvas.restore();
  }
}