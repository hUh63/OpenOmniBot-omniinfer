import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:ui/theme/theme_context.dart';

class UserGuideLinkRow extends StatelessWidget {
  const UserGuideLinkRow({
    super.key,
    required this.tapKey,
    required this.icon,
    required this.title,
    required this.description,
    required this.onTap,
  });

  final Key tapKey;
  final IconData icon;
  final String title;
  final String description;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        key: tapKey,
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 15),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: palette.accentPrimary.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(12),
                ),
                alignment: Alignment.center,
                child: Icon(icon, size: 20, color: palette.accentPrimary),
              ),
              const SizedBox(width: 13),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        color: palette.textPrimary,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      description,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: palette.textSecondary,
                        height: 1.45,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              Icon(
                LucideIcons.chevronRight,
                size: 18,
                color: palette.textTertiary,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class UserGuideRowDivider extends StatelessWidget {
  const UserGuideRowDivider({super.key});

  @override
  Widget build(BuildContext context) {
    return Divider(
      height: 1,
      thickness: 1,
      indent: 57,
      color: context.omniPalette.borderSubtle,
    );
  }
}
