import 'package:flutter/material.dart';
import 'package:flutter_slidable/flutter_slidable.dart';
import 'package:intl/intl.dart';
import 'package:ui/features/memory/pages/memory_center/widgets/memory_card.dart';
import '../../../models/memory_model.dart';

/// 通用分组卡片列表（学习和收藏页都能复用）
class MemoryCardList extends StatelessWidget {
  final List<MemoryCardModel> cards;
  final void Function(int cardId, bool targetStatus)? onToggleFavorite;
  final void Function(String cardTitle, int cardId) onEdit;
  final void Function(int cardId) onDelete;
  final Function(MemoryCardModel)? onCardTap;
  final void Function(MemoryCardModel vm) onLongPress;
  final Widget? header;
  final Widget? emptyState;
  final Future<void> Function()? onRefresh;

  // 选择模式相关
  final bool isSelectionMode;
  final Set<int> selectedCardIds;
  final void Function(int cardId)? onToggleSelection;

  const MemoryCardList({
    super.key,
    required this.cards,
    this.onToggleFavorite,
    required this.onEdit,
    required this.onDelete,
    this.onCardTap,
    required this.onLongPress,
    this.header,
    this.emptyState,
    this.onRefresh,
    this.isSelectionMode = false,
    this.selectedCardIds = const {},
    this.onToggleSelection,
  });

  @override
  Widget build(BuildContext context) {
    final grouped = _groupBySection(cards);
    final scrollView = SingleChildScrollView(
      physics: const AlwaysScrollableScrollPhysics(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (header != null) header!,
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: cards.isEmpty
                ? (emptyState ?? const SizedBox.shrink())
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      ...grouped.entries.map((entry) {
                        final section = entry.key;
                        final sectionCards = entry.value;

                        return Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            ...sectionCards.map((card) {
                              final timeLabel = _getTimeLabel(
                                section,
                                card.createdAt,
                              );
                              final isSelected = selectedCardIds.contains(
                                card.id,
                              );
                              return Column(
                                children: [
                                  MemoryCard(
                                    title: card.title,
                                    description: card.description,
                                    time: timeLabel,
                                    isFavorite: card.isFavorite,
                                    appName: card.appName,
                                    appIconProvider: card.appIcon,
                                    appSvgPath: card.appSvgPath,
                                    imagePath: card.imagePath,
                                    isSelectionMode: isSelectionMode,
                                    isSelected: isSelected,
                                    onFavoriteToggle: onToggleFavorite == null
                                        ? null
                                        : () => onToggleFavorite!(
                                            card.id,
                                            !card.isFavorite,
                                          ),
                                    onEdit: () => onEdit(card.title, card.id),
                                    onDelete: () => onDelete(card.id),
                                    onLongPress: () {
                                      onLongPress(card);
                                    },
                                    onTap:
                                        isSelectionMode || onCardTap != null
                                        ? () {
                                            if (isSelectionMode) {
                                              onToggleSelection?.call(card.id);
                                            } else if (onCardTap != null) {
                                              onCardTap!(card);
                                            }
                                          }
                                        : null,
                                  ),
                                  const SizedBox(height: 8),
                                ],
                              );
                            }),
                          ],
                        );
                      }),
                    ],
                  ),
          ),
          const SizedBox(height: 20),
        ],
      ),
    );

    final content = SlidableAutoCloseBehavior(child: scrollView);
    if (onRefresh == null) {
      return content;
    }

    return RefreshIndicator(onRefresh: onRefresh!, child: content);
  }

  String _getTimeLabel(String section, int timestamp) {
    final date = DateTime.fromMillisecondsSinceEpoch(timestamp);

    if (section == '今天') {
      return '今天 ${DateFormat('HH:mm').format(date)}';
    } else if (section == '昨天') {
      return '昨天 ${DateFormat('HH:mm').format(date)}';
    } else {
      return DateFormat('yyyy/MM/dd HH:mm').format(date);
    }
  }

  // 按日期分组
  Map<String, List<MemoryCardModel>> _groupBySection(
    List<MemoryCardModel> cards,
  ) {
    final Map<String, List<MemoryCardModel>> grouped = {};
    for (final card in cards) {
      String section = '未知日期';
      final today = DateTime.now();
      final cardDate = DateTime.fromMillisecondsSinceEpoch(card.updatedAt);
      if (cardDate.year == today.year &&
          cardDate.month == today.month &&
          cardDate.day == today.day) {
        section = '今天';
      }
      // 如果时间是昨天，分为一组
      else if (cardDate.year == today.year &&
          cardDate.month == today.month &&
          cardDate.day == today.day - 1) {
        section = '昨天';
      } else {
        section = '三天前';
      }
      grouped.putIfAbsent(section, () => []).add(card);
    }
    return grouped;
  }
}
