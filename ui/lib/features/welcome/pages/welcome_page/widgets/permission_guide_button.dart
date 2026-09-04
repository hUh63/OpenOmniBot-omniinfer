import 'package:flutter/material.dart';
import 'package:flutter_svg/svg.dart';

class PermissionGuideButton extends StatelessWidget {
  final VoidCallback onTap;

  const PermissionGuideButton({
    super.key,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
        decoration: ShapeDecoration(
          gradient: const LinearGradient(
            begin: Alignment(0.0, 0.5),
            end: Alignment(1.0, 0.5),
            colors: [
              Color(0xFFEDF2FE),
              Color(0xFFEBE8FD),
            ],
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(15),
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text(
              '授权遇到困难？看这',
              style: TextStyle(
                color: Color(0xFF5164E8),
                fontSize: 12,
                fontWeight: FontWeight.w500,
                height: 1.5,
              ),
            ),
            const SizedBox(width: 8),
            SvgPicture.asset(
              'assets/welcome/permission_guide_arrow.svg',
              width: 14,
              height: 14,
            ),
          ],
        ),
      ),
    );
  }
}
