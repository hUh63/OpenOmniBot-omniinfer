import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'tag_chip.dart';
import 'package:ui/theme/app_colors.dart';

class AppTag {
  final String id;              // 唯一标识，例如 'all' / 包名 / 自定义 key
  final String label;           // 展示名称，例如 '全部'、'支付宝'
  final int count;              // 计数
  final IconData? icon;         // 可选：内置图标
  final String? svgPath;        // 可选：SVG 图标路径
  final Color? iconBgColor;     // 图标圆底颜色
  final Color? iconColor;       // 图标颜色（用于 IconData）
  final ImageProvider? appIconProvider; // 可选：应用图标的 ImageProvider

  const AppTag({
    required this.id,
    required this.label,
    this.count = 0,
    this.icon,
    this.svgPath,
    this.iconBgColor,
    this.iconColor,
    this.appIconProvider,
  });
  
  AppTag copyWith({
    String? id,
    String? label,
    int? count,
    IconData? icon,
    String? svgPath,
    Color? iconBgColor,
    Color? iconColor,
    ImageProvider? appIconProvider,
  }) {
    return AppTag(
      id: id ?? this.id,
      label: label ?? this.label,
      count: count ?? this.count,
      icon: icon ?? this.icon,
      svgPath: svgPath ?? this.svgPath,
      iconBgColor: iconBgColor ?? this.iconBgColor,
      iconColor: iconColor ?? this.iconColor,
      appIconProvider: appIconProvider ?? this.appIconProvider,
    );
  }
}

class TagSection extends StatefulWidget {
  final List<AppTag> items;
  final String? selectedId;
  final ValueChanged<String>? onSelected;
  // 多选模式：提供 selectedIds + onSelectionChanged 即可启用，保留单选兼容
  final Set<String>? selectedIds;
  final void Function(Set<String>, String)? onSelectionChanged;

  // 折叠模式下显示的最大行数
  final int maxCollapsedRows;

  // 单个胶囊高度（用于计算折叠高度）
  final double chipHeight;

  const TagSection({
    Key? key,
    required this.items,
    this.selectedId,
    this.onSelected,
    this.selectedIds,
    this.onSelectionChanged,
    this.maxCollapsedRows = 1,
    this.chipHeight = 24,
  }) : super(key: key);

  @override
  State<TagSection> createState() => _TagSectionState();
}

class _TagSectionState extends State<TagSection> with TickerProviderStateMixin {
  final GlobalKey _measuredWrapKey = GlobalKey(); // 用于测量
  bool _showToggle = false;                      // 是否需要展开按钮
  bool _expanded = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _measure());
  }

  @override
  void didUpdateWidget(covariant TagSection oldWidget) {
    super.didUpdateWidget(oldWidget);
    WidgetsBinding.instance.addPostFrameCallback((_) => _measure());
  }

  void _measure() {
    final render = _measuredWrapKey.currentContext?.findRenderObject() as RenderBox?;
    if (render != null) {
      const double runSpacing = 4;
      final fullH = render.size.height;
      final collapsedH = widget.chipHeight * widget.maxCollapsedRows
          + runSpacing * (widget.maxCollapsedRows - 1);
      final needsToggle = fullH > collapsedH + 2;// 2px容差
      if (needsToggle != _showToggle) {
        setState(() { _showToggle = needsToggle; });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    const double spacing = 5;
    const double runSpacing = 4;
    final collapsedHeight = widget.chipHeight * widget.maxCollapsedRows
        + runSpacing * (widget.maxCollapsedRows - 1);

    final chips = widget.items.map((t) {
      final bool selected = widget.selectedIds != null
          ? (widget.selectedIds!.contains(t.id))
          : t.id == widget.selectedId;
      return GestureDetector(
        onTap: () {
          // 多选优先，其次单选
          if (widget.selectedIds != null && widget.onSelectionChanged != null) {
            final next = Set<String>.from(widget.selectedIds!);
            if (next.contains(t.id)) {
              next.remove(t.id);
            } else {
              next.add(t.id);
            }
            widget.onSelectionChanged!(next, t.id);
          } else if (widget.onSelected != null) {
            widget.onSelected!(t.id);
          }
        },
        child: TagChip(
          title: '${t.label}' + (selected ? ' ${t.count}' : ''),
          iconPath: t.icon ?? Icons.label,
          svgPath: t.svgPath,
          appIconProvider: t.appIconProvider,
          selected: selected,
        ),
      );
    }).toList();

    return Stack(
      children: [
        // 离线测量完整 Wrap 高度
        Offstage(
          child: Wrap(
            key: _measuredWrapKey,
            spacing: spacing,
            runSpacing: runSpacing,
            children: chips,
          ),
        ),
        // 真正可见区域
        Container(
          padding: EdgeInsets.zero,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded( // 改为 Expanded 让标签区域占据剩余空间
                child: AnimatedSize(
                  duration: const Duration(milliseconds: 200),
                  curve: Curves.easeInOut,
                  alignment: Alignment.topCenter,
                  child: ClipRect(
                    child: ConstrainedBox(
                      constraints: _expanded
                          ? const BoxConstraints()
                          : BoxConstraints(maxHeight: collapsedHeight),
                      child: Wrap(
                        spacing: spacing,
                        runSpacing: runSpacing,
                        children: chips,
                      ),
                    ),
                  ),
                ),
              ),
              // 展开按钮固定在右侧
              if (_showToggle) ...[
                GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTap: () => setState(() => _expanded = !_expanded),
                  child: Container(
                    height: widget.chipHeight, // 与 chip 高度对齐
                    alignment: Alignment.center,
                    padding: const EdgeInsets.only(left: 2),
                    child: SizedBox(
                      height: 18,
                      width: 18,
                      child: SvgPicture.asset(
                        _expanded ? 'assets/common/chevron-up.svg' : 'assets/common/chevron-down.svg',
                        width: 18,
                        height: 18,
                        color: AppColors.iconPrimary,
                      ),
                    )
                  ),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}