import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ui/widgets/flclash/navigation_dock.dart';

const _destinations = [
  FlClashNavigationDestination(icon: Icons.home, label: '首页'),
  FlClashNavigationDestination(icon: Icons.smart_toy, label: 'Agents'),
  FlClashNavigationDestination(icon: Icons.route, label: '轨迹'),
  FlClashNavigationDestination(icon: Icons.settings, label: '设置'),
];

Widget _testApp({
  required ValueChanged<int> onSelected,
  double textScale = 1,
  Brightness brightness = Brightness.light,
  TextDirection direction = TextDirection.ltr,
  double bottomPadding = 0,
}) => MaterialApp(
  theme: ThemeData(brightness: brightness),
  home: MediaQuery(
    data: MediaQueryData(
      textScaler: TextScaler.linear(textScale),
      padding: EdgeInsets.only(bottom: bottomPadding),
    ),
    child: Directionality(
      textDirection: direction,
      child: Scaffold(
        body: _DockHarness(onSelected: onSelected),
      ),
    ),
  ),
);

class _DockHarness extends StatefulWidget {
  const _DockHarness({required this.onSelected});
  final ValueChanged<int> onSelected;

  @override
  State<_DockHarness> createState() => _DockHarnessState();
}

class _DockHarnessState extends State<_DockHarness> {
  int selectedIndex = 0;

  @override
  Widget build(BuildContext context) => Align(
    alignment: Alignment.bottomCenter,
    child: FlClashNavigationDock(
      destinations: _destinations,
      selectedIndex: selectedIndex,
      onDestinationSelected: (index) {
        widget.onSelected(index);
        setState(() => selectedIndex = index);
      },
    ),
  );
}

void main() {
  testWidgets('tap selects once and lens settles without replacing the dock', (
    tester,
  ) async {
    final selected = <int>[];
    await tester.pumpWidget(_testApp(onSelected: selected.add));
    final dock = tester.element(find.byType(FlClashNavigationDock));
    await tester.tap(find.text('Agents'));
    await tester.pump();
    expect(selected, [1]);
    await tester.pumpAndSettle();
    expect(tester.element(find.byType(FlClashNavigationDock)), same(dock));
    expect(
      tester
          .widget<FlClashNavigationDock>(find.byType(FlClashNavigationDock))
          .selectedIndex,
      1,
    );
    await tester.tap(find.text('Agents'));
    await tester.pumpAndSettle();
    expect(selected, [1]);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'drag selects its release destination, cancellation retains selection',
    (tester) async {
      final selected = <int>[];
      await tester.pumpWidget(_testApp(onSelected: selected.add));
      final gesture = await tester.startGesture(
        tester.getCenter(find.text('首页')),
      );
      await tester.pump(const Duration(milliseconds: 80));
      await gesture.moveTo(tester.getCenter(find.text('设置')));
      await tester.pump(const Duration(milliseconds: 400));
      await gesture.up();
      await tester.pumpAndSettle();
      expect(selected, [3]);
      final cancel = await tester.startGesture(
        tester.getCenter(find.text('设置')),
      );
      await cancel.moveTo(tester.getCenter(find.text('Agents')));
      await tester.pump(const Duration(milliseconds: 120));
      await cancel.cancel();
      await tester.pumpAndSettle();
      expect(selected, [3]);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'dock keeps safe-area clearance and remains usable with large labels in RTL',
    (tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final selected = <int>[];
      await tester.pumpWidget(
        _testApp(
          onSelected: selected.add,
          textScale: 2,
          brightness: Brightness.dark,
          direction: TextDirection.rtl,
          bottomPadding: 34,
        ),
      );
      expect(tester.getSize(find.byType(FlClashNavigationDock)).height, 114);
      expect(tester.getRect(find.text('设置')).bottom, lessThan(640 - 34));
      await tester.tap(find.text('设置'));
      await tester.pumpAndSettle();
      expect(selected, [3]);
      expect(tester.takeException(), isNull);
    },
  );
}
