# Composer architecture

The shared `ChatInputArea` is a presentation subsystem used by normal chat,
OpenClaw, Agent, and the command overlay. Its public widget contract remains in
`widgets/chat_input_area.dart`; callers should not import implementation parts.

## State ownership

`state/chat_composer_state_machine.dart` is the only owner of transient
composer interaction state:

- text and focus presence;
- keyboard transition phase;
- hover state;
- owned popup opening/open/closed transitions;
- payload eligibility and the primary send/cancel/attachment action.

Overlay handles, animation controllers, focus nodes, text controllers, and
scroll controllers are lifecycle resources rather than state. They remain
owned and disposed by `_ChatInputAreaStateBase`.

The state machine intentionally does not disable typing while a task is
running. Processing changes the primary action to cancel, while the draft and
focus remain editable.

## Rendering responsibilities

- `chat_input_area_composer.dart`: composition and shell layout.
- `chat_input_actions.dart`: text field, terminal, OpenClaw, and primary action.
- `chat_input_attachments.dart`: attachment preview and removal.
- `chat_input_agent_controls.dart`: model, reasoning, and permission triggers.
- `chat_input_agent_menus.dart`: popup menu presentation and internal pages.
- `chat_input_context_usage.dart`: context ring and tooltip lifecycle.
- `chat_input_flow_border.dart`: animated border painter.
- `chat_input_area_popup.dart`: legacy popup compatibility surface.

All implementation files are parts of `chat_input_area.dart`, preserving the
existing public import path and private symbol behavior.

## Invariants

- A foreign keyboard must not expand an unfocused composer.
- Keyboard closing immediately collapses an empty composer.
- Attachments and external edit payloads make send eligible even with no text.
- Agent and permission popups prefer placement above the IME.
- Popup opening is idempotent and every early return closes its opening state.
- Model/permission controls remain inside `TextFieldTapRegion` so they do not
  steal composer focus.
