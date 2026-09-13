import type { Conversation, ConversationMode } from "./types";

export const DRAFT_CONVERSATION_ID = 0;

export function createConversationDraft(
  mode: ConversationMode,
  updatedAt = Date.now(),
  agentId?: string,
): Conversation {
  return {
    id: DRAFT_CONVERSATION_ID,
    title: "新对话",
    mode,
    agentId: agentId?.trim() || undefined,
    messageCount: 0,
    updatedAt,
  };
}

export function isPersistedConversation(
  conversation: Conversation | null | undefined,
): boolean {
  return Number(conversation?.id ?? 0) > 0;
}
