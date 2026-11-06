import 'package:flutter/material.dart';
import 'package:ui/widgets/record_list_item.dart';

class RecordList extends StatelessWidget {
  final List<RecordListItemData> recordModels;
  final void Function(RecordListItemData, BuildContext, Offset) onMorePressed;
  final void Function(int recordId, bool targetStatus)? onRecommendPressed;

  const RecordList({
    Key? key,
    required this.recordModels,
    required this.onMorePressed,
    this.onRecommendPressed,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      itemCount: recordModels.length,
      itemBuilder: (context, index) {
        return Container(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
          decoration: const BoxDecoration(
            color: Colors.white,
            border: Border(
              bottom: BorderSide(color: Color(0x33000000), width: 0.5),
            ),
          ),
          child: RecordListItem(
            recordModel: recordModels[index],
            onMorePressed: (context, position) {
              onMorePressed(recordModels[index], context, position);
            },
            onRecommendPressed: onRecommendPressed,
          )
        );
      },
    );
  }
}
