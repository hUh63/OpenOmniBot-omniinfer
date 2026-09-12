import 'package:flutter_test/flutter_test.dart';
import 'package:ui/features/home/pages/command_overlay/state/chat_composer_state_machine.dart';

void main() {
  group('ChatComposerStateMachine', () {
    test('drives expansion from text, focus and keyboard phase', () {
      final machine = ChatComposerStateMachine(hasText: false, hasFocus: false);

      expect(machine.value.expandsTextField, isFalse);

      machine.keyboardInsetChanged(300);
      expect(machine.value.keyboardPhase, ChatComposerKeyboardPhase.opening);
      expect(
        machine.value.expandsTextField,
        isFalse,
        reason: 'a keyboard owned by another field must not expand composer',
      );

      machine.focusChanged(true);
      expect(machine.value.expandsTextField, isTrue);

      machine.keyboardInsetChanged(220);
      expect(machine.value.keyboardPhase, ChatComposerKeyboardPhase.closing);
      expect(machine.value.expandsTextField, isFalse);

      machine.textChanged(true);
      expect(machine.value.expandsTextField, isTrue);
    });

    test('moves owned popup through opening, open and closed states', () {
      final machine = ChatComposerStateMachine(hasText: false, hasFocus: true);

      expect(
        machine.beginPopupOpening(ChatComposerPopup.agentRunSettings),
        isTrue,
      );
      expect(
        machine.value.isPopupOpening(ChatComposerPopup.agentRunSettings),
        isTrue,
      );
      expect(
        machine.beginPopupOpening(ChatComposerPopup.agentRunSettings),
        isFalse,
        reason: 'opening the same overlay must be idempotent',
      );

      machine.popupOpened(ChatComposerPopup.agentRunSettings);
      expect(
        machine.value.isPopupOpen(ChatComposerPopup.agentRunSettings),
        isTrue,
      );

      machine.popupClosed(ChatComposerPopup.agentRunSettings);
      expect(
        machine.value.isPopupActive(ChatComposerPopup.agentRunSettings),
        isFalse,
      );
    });

    test('derives primary action without disabling draft editing', () {
      final machine = ChatComposerStateMachine(hasText: false, hasFocus: true);

      expect(
        machine.value.primaryAction(
          isProcessing: true,
          hasAttachments: false,
          hasExternalPayload: false,
        ),
        ChatComposerPrimaryAction.cancel,
      );
      expect(machine.value.hasFocus, isTrue);

      expect(
        machine.value.primaryAction(
          isProcessing: false,
          hasAttachments: false,
          hasExternalPayload: true,
        ),
        ChatComposerPrimaryAction.send,
        reason: 'an empty composer can still submit an external edit payload',
      );
      expect(
        machine.value.primaryAction(
          isProcessing: false,
          hasAttachments: false,
          hasExternalPayload: false,
          supportsAttachmentFallback: true,
        ),
        ChatComposerPrimaryAction.addAttachment,
      );
    });
  });
}
