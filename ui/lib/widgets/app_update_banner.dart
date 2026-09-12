import 'package:flutter/material.dart';
import 'package:ui/theme/app_colors.dart';
import 'package:ui/theme/app_text_styles.dart';

class AppUpdateBanner extends StatefulWidget {
  final String text;
  final VoidCallback onTap;
  final VoidCallback? onClose;

  const AppUpdateBanner({
    super.key,
    required this.text,
    required this.onTap,
    this.onClose,
  });

  @override
  State<AppUpdateBanner> createState() => _AppUpdateBannerState();
}

class _AppUpdateBannerState extends State<AppUpdateBanner>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2200),
    )..repeat();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      clipBehavior: Clip.none,
      children: [
        Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: widget.onTap,
            borderRadius: BorderRadius.circular(999),
            child: Ink(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: const Color(0xFFF4F8FF),
                borderRadius: BorderRadius.circular(999),
                border: Border.all(color: const Color(0xFFD9E6FB)),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x152D7AF0),
                    blurRadius: 14,
                    offset: Offset(0, 6),
                  ),
                ],
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(
                    Icons.auto_awesome_rounded,
                    size: 14,
                    color: AppColors.buttonPrimary,
                  ),
                  const SizedBox(width: 6),
                  Flexible(
                    child: _AnimatedShinyText(
                      controller: _controller,
                      text: widget.text,
                    ),
                  ),
                  const SizedBox(width: 4),
                  const Icon(
                    Icons.chevron_right_rounded,
                    size: 16,
                    color: AppColors.text50,
                  ),
                ],
              ),
            ),
          ),
        ),
        if (widget.onClose != null)
          Positioned(
            top: -6,
            right: -6,
            child: Material(
              color: Colors.transparent,
              child: InkWell(
                onTap: widget.onClose,
                customBorder: const CircleBorder(),
                child: Ink(
                  width: 20,
                  height: 20,
                  decoration: const BoxDecoration(
                    color: Colors.white,
                    shape: BoxShape.circle,
                    boxShadow: [
                      BoxShadow(
                        color: Color(0x12000000),
                        blurRadius: 6,
                        offset: Offset(0, 2),
                      ),
                    ],
                  ),
                  child: const Icon(
                    Icons.close_rounded,
                    size: 13,
                    color: AppColors.text50,
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }
}

class _AnimatedShinyText extends StatelessWidget {
  final Animation<double> controller;
  final String text;

  const _AnimatedShinyText({
    required this.controller,
    required this.text,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      child: Text(
        text,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: TextStyle(
          fontFamily: AppTextStyles.fontFamily,
          fontSize: 12,
          fontWeight: FontWeight.w500,
          color: AppColors.text70,
          height: 1.2,
        ),
      ),
      builder: (context, child) {
        final progress = controller.value;
        return ShaderMask(
          blendMode: BlendMode.srcIn,
          shaderCallback: (bounds) {
            final shift = (progress * 2.6) - 1.3;
            return LinearGradient(
              begin: Alignment(-1.4 + shift, 0),
              end: Alignment(-0.2 + shift, 0),
              colors: const [
                Color(0xFF7F8EA8),
                Color(0xFF1930D9),
                Color(0xFF8FA4C7),
              ],
              stops: const [0.18, 0.5, 0.82],
            ).createShader(bounds);
          },
          child: child,
        );
      },
    );
  }
}
