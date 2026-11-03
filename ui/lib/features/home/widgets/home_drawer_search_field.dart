import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:ui/l10n/l10n.dart';
import 'package:ui/theme/theme_context.dart';

class HomeDrawerSearchField extends StatelessWidget {
  const HomeDrawerSearchField({
    super.key,
    required this.controller,
    required this.focusNode,
    required this.isSearching,
    required this.textColor,
    this.hintText,
  });

  final TextEditingController controller;
  final FocusNode focusNode;
  final bool isSearching;
  final Color textColor;
  final String? hintText;

  bool get _hasQuery => controller.text.trim().isNotEmpty;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([focusNode, controller]),
      builder: _buildField,
    );
  }

  Widget _buildField(BuildContext context, Widget? _) {
    final palette = context.omniPalette;
    final hasFocus = focusNode.hasFocus;
    final iconColor = hasFocus ? palette.accentPrimary : palette.textSecondary;
    final isDark = context.isDarkTheme;
    final backgroundColor = isDark
        ? hasFocus
              ? palette.surfaceElevated
              : palette.surfacePrimary
        : Colors.white;
    final borderColor = hasFocus
        ? palette.accentPrimary.withValues(alpha: 0.65)
        : palette.borderSubtle.withValues(alpha: isDark ? 0.65 : 0.85);

    return AnimatedContainer(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOutCubic,
      height: 38,
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: borderColor, width: hasFocus ? 1.1 : 0.75),
      ),
      child: TextField(
        controller: controller,
        focusNode: focusNode,
        onTapOutside: (_) => focusNode.unfocus(),
        textInputAction: TextInputAction.search,
        style: TextStyle(
          fontSize: 13,
          color: textColor,
          fontWeight: FontWeight.w500,
          height: 1.2,
        ),
        cursorColor: palette.accentPrimary,
        decoration: InputDecoration(
          hintText: hintText ?? context.l10n.homeDrawerSearchHint,
          hintStyle: TextStyle(
            fontSize: 13,
            color: palette.textTertiary,
            fontWeight: FontWeight.w400,
          ),
          border: InputBorder.none,
          enabledBorder: InputBorder.none,
          focusedBorder: InputBorder.none,
          disabledBorder: InputBorder.none,
          errorBorder: InputBorder.none,
          focusedErrorBorder: InputBorder.none,
          filled: false,
          isDense: true,
          contentPadding: const EdgeInsets.symmetric(vertical: 10),
          prefixIconConstraints: const BoxConstraints(
            minWidth: 36,
            minHeight: 38,
          ),
          suffixIconConstraints: const BoxConstraints(
            minWidth: 34,
            minHeight: 38,
          ),
          prefixIcon: Icon(LucideIcons.search, size: 18, color: iconColor),
          suffixIcon: isSearching
              ? Padding(
                  padding: const EdgeInsets.all(10),
                  child: SizedBox(
                    width: 14,
                    height: 14,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      valueColor: AlwaysStoppedAnimation<Color>(
                        palette.accentPrimary,
                      ),
                    ),
                  ),
                )
              : _hasQuery
              ? IconButton(
                  key: const ValueKey('home-search-clear'),
                  tooltip: context.l10n.homeDrawerClearSearch,
                  splashRadius: 16,
                  onPressed: controller.clear,
                  icon: Icon(
                    LucideIcons.x,
                    size: 16,
                    color: palette.textSecondary,
                  ),
                )
              : null,
        ),
      ),
    );
  }
}
