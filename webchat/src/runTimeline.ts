export interface RunTimeline<T> {
  processMessages: T[];
  visibleMessages: T[];
}

/**
 * Build one ordered turn timeline even before a terminal assistant message
 * exists. Completed turns still keep their final/request messages outside the
 * collapsible process section.
 */
export function buildRunTimeline<T>(
  taskMessages: T[],
  visibleMessages: T[],
  active: boolean,
  compare: (left: T, right: T) => number,
): RunTimeline<T> | null {
  if (!active && visibleMessages.length === 0) return null;

  const visibleSet = new Set(visibleMessages);
  return {
    processMessages: taskMessages
      .filter((message) => !visibleSet.has(message))
      .sort(compare),
    visibleMessages: [...visibleMessages].sort(compare),
  };
}
