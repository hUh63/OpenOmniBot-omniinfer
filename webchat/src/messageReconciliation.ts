import type { ChatMessage } from "./types";

type UnknownRecord = Record<string, unknown>;

function asRecord(value: unknown): UnknownRecord {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as UnknownRecord
    : {};
}

function contentOf(message: ChatMessage): UnknownRecord {
  return asRecord(message.content);
}

function cardOf(message: ChatMessage): UnknownRecord {
  const content = contentOf(message);
  return asRecord(content.cardData);
}

function stableMessageId(message: ChatMessage): string {
  const content = contentOf(message);
  const card = cardOf(message);
  return String(
    message.id
      ?? message.contentId
      ?? content.id
      ?? card.cardId
      ?? "",
  ).trim();
}

function parentTaskId(message: ChatMessage): string {
  const card = cardOf(message);
  return String(
    message.streamMeta?.parentTaskId
      ?? card.taskID
      ?? card.taskId
      ?? "",
  ).trim();
}

function messageText(message: ChatMessage): string {
  return String(contentOf(message).text ?? "").trim();
}

function messageTime(message: ChatMessage): number {
  const raw = message.createAt;
  const parsed = typeof raw === "number" ? raw : Date.parse(String(raw ?? ""));
  return Number.isFinite(parsed) ? parsed : 0;
}

function streamSequence(message: ChatMessage): number {
  const value = message.streamMeta?.entrySeq ?? message.streamMeta?.seq;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : -1;
}

function statusRank(status: unknown): number {
  return ({
    running: 0,
    pending: 0,
    interrupted: 1,
    cancelled: 1,
    timeout: 2,
    error: 3,
    success: 4,
    completed: 4,
  } as Record<string, number>)[String(status ?? "").toLowerCase()] ?? 0;
}

function preferredText(previous: string, incoming: string, incomingIsFinal: boolean): string {
  if (!previous) return incoming;
  if (!incoming) return previous;
  if (incoming.startsWith(previous)) return incoming;
  if (previous.startsWith(incoming)) return previous;
  return incomingIsFinal || incoming.length >= previous.length ? incoming : previous;
}

function mergeCard(previous: UnknownRecord, incoming: UnknownRecord): UnknownRecord {
  const previousStatus = previous.status;
  const incomingStatus = incoming.status;
  const useIncomingStatus = statusRank(incomingStatus) >= statusRank(previousStatus);
  return {
    ...previous,
    ...incoming,
    status: useIncomingStatus ? incomingStatus : previousStatus,
  };
}

function mergeMessage(previous: ChatMessage, incoming: ChatMessage): ChatMessage {
  const previousContent = contentOf(previous);
  const incomingContent = contentOf(incoming);
  const previousCard = cardOf(previous);
  const incomingCard = cardOf(incoming);
  const incomingIsFinal = incoming.streamMeta?.isFinal === true;
  const mergedContent: UnknownRecord = {
    ...previousContent,
    ...incomingContent,
  };
  const text = preferredText(
    String(previousContent.text ?? ""),
    String(incomingContent.text ?? ""),
    incomingIsFinal,
  );
  if (text) mergedContent.text = text;
  if (Object.keys(previousCard).length || Object.keys(incomingCard).length) {
    mergedContent.cardData = mergeCard(previousCard, incomingCard);
  }

  const previousTime = messageTime(previous);
  const incomingTime = messageTime(incoming);
  return {
    ...previous,
    ...incoming,
    id: incoming.id ?? previous.id,
    contentId: incoming.contentId ?? previous.contentId,
    content: mergedContent,
    streamMeta: {
      ...(previous.streamMeta ?? {}),
      ...(incoming.streamMeta ?? {}),
    },
    createAt: previousTime > 0 && incomingTime > 0
      ? (previousTime <= incomingTime ? previous.createAt : incoming.createAt)
      : incoming.createAt ?? previous.createAt,
    reasoning_content: preferredText(
      String(previous.reasoning_content ?? previous.reasoningContent ?? ""),
      String(incoming.reasoning_content ?? incoming.reasoningContent ?? ""),
      incomingIsFinal,
    ) || undefined,
  };
}

function isCodexAssistant(message: ChatMessage): boolean {
  if (Number(message.type) !== 1 || Number(message.user) !== 2) return false;
  return stableMessageId(message).toLowerCase().includes("codex");
}

function isEquivalentCodexAssistant(left: ChatMessage, right: ChatMessage): boolean {
  if (!isCodexAssistant(left) || !isCodexAssistant(right)) return false;
  const leftId = stableMessageId(left).toLowerCase();
  const rightId = stableMessageId(right).toLowerCase();
  const legacyCanonicalPair = (
    leftId.endsWith("-codex-assistant") && rightId.endsWith("-codex-agent")
  ) || (
    rightId.endsWith("-codex-assistant") && leftId.endsWith("-codex-agent")
  );
  const leftText = messageText(left);
  const rightText = messageText(right);
  if (!leftText || leftText !== rightText) return false;
  const leftParent = parentTaskId(left);
  const rightParent = parentTaskId(right);
  const leftTime = messageTime(left);
  const rightTime = messageTime(right);
  if (legacyCanonicalPair) {
    if (leftParent && rightParent && leftParent === rightParent) return true;
    return leftTime > 0
      && rightTime > 0
      && Math.abs(leftTime - rightTime) <= 30_000;
  }
  const duplicateCanonicalEvent = leftId.endsWith("-codex-agent")
    && rightId.endsWith("-codex-agent")
    && leftParent
    && leftParent === rightParent;
  return Boolean(duplicateCanonicalEvent)
    && leftTime > 0
    && rightTime > 0
    && Math.abs(leftTime - rightTime) <= 1_500;
}

function canonicalAssistantScore(message: ChatMessage): number {
  const id = stableMessageId(message).toLowerCase();
  return (id.endsWith("-codex-agent") ? 4 : 0)
    + (parentTaskId(message) ? 2 : 0)
    + (message.streamMeta?.isFinal === true ? 1 : 0);
}

function compareChronologically(
  left: ChatMessage,
  right: ChatMessage,
  insertionOrder: Map<ChatMessage, number>,
): number {
  const leftTime = messageTime(left);
  const rightTime = messageTime(right);
  if (leftTime !== rightTime) return leftTime - rightTime;
  const leftParent = parentTaskId(left);
  const rightParent = parentTaskId(right);
  if (leftParent && leftParent === rightParent) {
    const sequenceDifference = streamSequence(left) - streamSequence(right);
    if (sequenceDifference) return sequenceDifference;
  }
  return (insertionOrder.get(left) ?? 0) - (insertionOrder.get(right) ?? 0);
}

/**
 * Codex may publish overlapping native and Flutter snapshots for one turn.
 * Treat those snapshots as monotonic updates: never let an older snapshot
 * remove an already-visible user/tool message, and collapse the two known
 * assistant representations into one stable entry.
 */
export function reconcileCodexMessages(
  current: ChatMessage[],
  incoming: ChatMessage[],
): ChatMessage[] {
  const messages: ChatMessage[] = [];
  const indexById = new Map<string, number>();

  const appendOrMerge = (candidate: ChatMessage) => {
    const id = stableMessageId(candidate);
    const exactIndex = id ? indexById.get(id) : undefined;
    if (exactIndex !== undefined) {
      messages[exactIndex] = mergeMessage(messages[exactIndex], candidate);
      return;
    }

    const equivalentIndex = messages.findIndex((existing) => (
      isEquivalentCodexAssistant(existing, candidate)
    ));
    if (equivalentIndex >= 0) {
      const existing = messages[equivalentIndex];
      const canonical = canonicalAssistantScore(candidate) >= canonicalAssistantScore(existing)
        ? mergeMessage(existing, candidate)
        : mergeMessage(candidate, existing);
      const previousId = stableMessageId(existing);
      if (previousId) indexById.delete(previousId);
      messages[equivalentIndex] = canonical;
      const canonicalId = stableMessageId(canonical);
      if (canonicalId) indexById.set(canonicalId, equivalentIndex);
      return;
    }

    const index = messages.length;
    messages.push(candidate);
    if (id) indexById.set(id, index);
  };

  current.forEach(appendOrMerge);
  incoming.forEach(appendOrMerge);

  const insertionOrder = new Map(messages.map((message, index) => [message, index]));
  return messages.sort((left, right) => (
    compareChronologically(left, right, insertionOrder)
  ));
}
