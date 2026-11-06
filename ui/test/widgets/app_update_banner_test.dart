import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ui/widgets/app_update_banner.dart';

void main() {
  testWidgets('renders update banner text', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AppUpdateBanner(
            text: '发现新版本 v0.0.2，点击更新',
            onTap: () {},
          ),
        ),
      ),
    );

    expect(find.text('发现新版本 v0.0.2，点击更新'), findsOneWidget);
  });
}
