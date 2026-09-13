class ToolCardDetailGestureGate {
  ToolCardDetailGestureGate._();

  static final Set<int> _activePointerIds = <int>{};

  static bool get hasActivePointers => _activePointerIds.isNotEmpty;

  static bool containsPointer(int pointer) =>
      _activePointerIds.contains(pointer);

  static void holdPointer(int pointer) {
    _activePointerIds.add(pointer);
  }

  static void releasePointer(int pointer) {
    _activePointerIds.remove(pointer);
  }
}
