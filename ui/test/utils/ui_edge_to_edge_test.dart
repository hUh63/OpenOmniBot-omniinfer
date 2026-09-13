import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ui/utils/ui.dart';

void main() {
  testWidgets('edge-to-edge scroll padding keeps the final item gesture-safe', (
    tester,
  ) async {
    EdgeInsets? resolvedPadding;

    await tester.pumpWidget(
      MediaQuery(
        data: const MediaQueryData(viewPadding: EdgeInsets.only(bottom: 24)),
        child: Builder(
          builder: (context) {
            resolvedPadding = edgeToEdgeScrollPadding(
              context,
              const EdgeInsets.fromLTRB(18, 10, 18, 28),
            );
            return const SizedBox();
          },
        ),
      ),
    );

    expect(resolvedPadding, const EdgeInsets.fromLTRB(18, 10, 18, 52));
  });
}
