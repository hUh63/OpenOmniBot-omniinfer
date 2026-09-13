import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:ui/theme/app_text_styles.dart';
import 'package:ui/theme/theme_context.dart';
import 'package:ui/utils/image_util.dart';

class MemoryCard extends StatelessWidget {
  final String title;
  final String? description;
  final String time;
  final bool isFavorite;
  final String? appName;
  final ImageProvider? appIconProvider;
  final String? appSvgPath;
  final String? imagePath;
  final VoidCallback? onFavoriteToggle;
  final VoidCallback? onTap;
  final VoidCallback? onEdit;
  final VoidCallback? onDelete;
  final VoidCallback? onLongPress;

  // 选择模式相关
  final bool isSelectionMode;
  final bool isSelected;

  const MemoryCard({
    Key? key,
    required this.title,
    this.description,
    required this.time,
    this.isFavorite = false,
    this.appName,
    this.appIconProvider,
    this.appSvgPath,
    this.imagePath,
    this.onFavoriteToggle,
    this.onTap,
    this.onEdit,
    this.onDelete,
    this.onLongPress,
    this.isSelectionMode = false,
    this.isSelected = false,
  }) : super(key: key);

  /// 移除markdown语法标记，只保留纯文本
  String _stripMarkdown(String text) {
    String result = text;

    // 移除代码块标记 (```code```) - 需要先处理代码块，避免干扰其他规则
    result = result.replaceAll(RegExp(r'```[\s\S]*?```'), '');

    // 移除行内代码标记 (`code`) - 提取代码内容
    result = result.replaceAllMapped(
      RegExp(r'`([^`]+)`'),
      (match) => match.group(1) ?? '',
    );

    // 移除标题标记 (# ## ### 等)
    result = result.replaceAll(RegExp(r'^#{1,6}\s+', multiLine: true), '');

    // 移除粗体标记 (**text** 或 __text__)
    result = result.replaceAllMapped(
      RegExp(r'\*\*([^*]+)\*\*'),
      (match) => match.group(1) ?? '',
    );
    result = result.replaceAllMapped(
      RegExp(r'__([^_]+)__'),
      (match) => match.group(1) ?? '',
    );

    // 移除斜体标记 (*text* 或 _text_)
    result = result.replaceAllMapped(
      RegExp(r'\*([^*]+)\*'),
      (match) => match.group(1) ?? '',
    );
    result = result.replaceAllMapped(
      RegExp(r'_([^_]+)_'),
      (match) => match.group(1) ?? '',
    );

    // 移除删除线标记 (~~text~~)
    result = result.replaceAllMapped(
      RegExp(r'~~([^~]+)~~'),
      (match) => match.group(1) ?? '',
    );

    // 移除链接标记 [text](url)
    result = result.replaceAllMapped(
      RegExp(r'\[([^\]]+)\]\([^\)]+\)'),
      (match) => match.group(1) ?? '',
    );

    // 移除图片标记 ![alt](url)
    result = result.replaceAllMapped(
      RegExp(r'!\[([^\]]*)\]\([^\)]+\)'),
      (match) => match.group(1) ?? '',
    );

    // 移除引用标记 (> text)
    result = result.replaceAll(RegExp(r'^>\s+', multiLine: true), '');

    // 移除列表标记 (- item 或 * item 或 + item)
    result = result.replaceAll(RegExp(r'^[\-\*\+]\s+', multiLine: true), '');

    // 移除有序列表标记 (1. item)
    result = result.replaceAll(RegExp(r'^\d+\.\s+', multiLine: true), '');

    // 移除水平分割线 (--- 或 *** 或 ___)
    result = result.replaceAll(RegExp(r'^[\-\*_]{3,}$', multiLine: true), '');

    // 移除多余的空行（连续的换行符替换为单个换行符）
    result = result.replaceAll(RegExp(r'\n{2,}'), '\n');

    // 去除首尾空白
    result = result.trim();

    return result;
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      onLongPressStart: (details) {
        onLongPress?.call();
      },
      child: _buildCardContent(context),
    );
  }

  Widget _buildCardContent(BuildContext context) {
    final palette = context.omniPalette;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: palette.surfacePrimary,
        borderRadius: BorderRadius.circular(8),
        boxShadow: [
          BoxShadow(
            color: palette.shadowColor.withValues(
              alpha: context.isDarkTheme ? 0.34 : 0.10,
            ),
            blurRadius: 18,
            offset: const Offset(0, 8),
            spreadRadius: 0,
          ),
        ],
        border: Border.all(color: palette.borderSubtle),
      ),
      child:
          // Main content area with exact Figma layout
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              // Left content column
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Three dots menu
                    // GestureDetector(
                    //   onTapDown: (details) {
                    //     final RenderBox renderBox = context.findRenderObject() as RenderBox;
                    //     final Offset topRight = renderBox.localToGlobal(Offset(renderBox.size.width, 0));
                    //     onLongPress?.call(context, topRight);
                    //   },
                    // child: SizedBox(
                    //   width: 17,
                    //   height: 3,
                    //   child: SvgPicture.asset(
                    //     'assets/common/more.svg',
                    //     width: 17,
                    //     height: 3,
                    //     colorFilter: const ColorFilter.mode(
                    //       Color(0xFF1A1A1A), // icon_nav_secondary
                    //       BlendMode.srcIn,
                    //     ),
                    //   ),
                    // ),
                    // ),
                    // Title
                    Text(
                      title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: AppTextStyles.fontSizeH3,
                        fontWeight: AppTextStyles.fontWeightMedium,
                        height: 1.125,
                        letterSpacing: AppTextStyles.letterSpacingWide,
                      ),
                    ),

                    if (description != null) ...[
                      const SizedBox(height: 8),
                      Text(
                        _stripMarkdown(description!),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: AppTextStyles.fontSizeMain,
                          fontWeight: AppTextStyles.fontWeightRegular,
                          height: AppTextStyles.lineHeightH2,
                          letterSpacing: AppTextStyles.letterSpacingWide,
                        ),
                      ),
                    ],

                    const SizedBox(height: 12),
                    Row(
                      children: [
                        // app icon
                        if (appSvgPath != null) ...[
                          SizedBox(
                            width: 18,
                            height: 18,
                            child: SvgPicture.asset(
                              appSvgPath!,
                              width: 18,
                              height: 18,
                            ),
                          ),
                          const SizedBox(width: 8),
                        ] else if (appIconProvider != null) ...[
                          ClipRRect(
                            borderRadius: BorderRadius.circular(4),
                            child: Image(
                              image: appIconProvider!,
                              width: 18,
                              height: 18,
                              fit: BoxFit.cover,
                              errorBuilder: (context, error, stackTrace) {
                                return Container(
                                  width: 18,
                                  height: 18,
                                  decoration: BoxDecoration(
                                    color: Colors.grey[300],
                                    borderRadius: BorderRadius.circular(2),
                                  ),
                                  child: Icon(
                                    Icons.apps,
                                    size: 12,
                                    color: Colors.grey[600],
                                  ),
                                );
                              },
                            ),
                          ),
                          const SizedBox(width: 8),
                        ] else ...[
                          Icon(Icons.apps, size: 18, color: Colors.grey[600]),
                          const SizedBox(width: 8),
                        ],
                        // 时间
                        Text(
                          time,
                          style: TextStyle(
                            fontSize: AppTextStyles.fontSizeSmall,
                            fontWeight: AppTextStyles.fontWeightRegular,
                            color: palette.textSecondary,
                            height: AppTextStyles.lineHeightH2,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),

              // Right side image
              if (imagePath != null) ...[
                const SizedBox(width: 8),
                ClipRRect(
                  borderRadius: BorderRadius.circular(11),
                  child: Container(
                    width: 85,
                    height: 98,
                    decoration: BoxDecoration(
                      color: Colors.grey.withOpacity(0.5),
                    ),
                    child: ImageUtil.buildImage(imagePath!),
                  ),
                ),
              ] else if (onFavoriteToggle != null && !isSelectionMode) ...[
                const SizedBox(width: 8),
                GestureDetector(
                  onTap: onFavoriteToggle,
                  child: Icon(
                    isFavorite == true ? Icons.favorite : Icons.favorite_border,
                    size: 20,
                    color: isFavorite == true
                        ? Colors.red
                        : Colors.grey.shade400,
                  ),
                ),
              ],

              // 选择模式下显示复选框（垂直居中显示）
              if (isSelectionMode) ...[
                const SizedBox(width: 8),

                Container(
                  width: 20,
                  height: 20,
                  child: SvgPicture.asset(
                    isSelected
                        ? 'assets/common/card_selected.svg'
                        : 'assets/common/card_unselected.svg',
                    width: 20,
                    height: 20,
                  ),
                ),
              ],
            ],
          ),
    );
  }
}
