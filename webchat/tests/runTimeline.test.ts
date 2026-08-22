import assert from "node:assert/strict";
import test from "node:test";
import { buildRunTimeline } from "../src/runTimeline.ts";

interface TimelineMessage {
  id: string;
  sequence: number;
}

const compareSequence = (left: TimelineMessage, right: TimelineMessage) => (
  left.sequence - right.sequence
);

test("an active turn without a final message still forms one ordered timeline", () => {
  const thinking = { id: "thinking-1", sequence: 1 };
  const text = { id: "text-1", sequence: 2 };
  const tool = { id: "tool-1", sequence: 3 };
  const nextThinking = { id: "thinking-2", sequence: 4 };

  const timeline = buildRunTimeline(
    [text, nextThinking, tool, thinking],
    [],
    true,
    compareSequence,
  );

  assert.deepEqual(
    timeline?.processMessages.map((message) => message.id),
    ["thinking-1", "text-1", "tool-1", "thinking-2"],
  );
  assert.deepEqual(timeline?.visibleMessages, []);
});

test("a completed turn keeps only the terminal reply outside its ordered process", () => {
  const thinking = { id: "thinking-1", sequence: 1 };
  const text = { id: "text-1", sequence: 2 };
  const tool = { id: "tool-1", sequence: 3 };
  const finalReply = { id: "text-final", sequence: 4 };

  const timeline = buildRunTimeline(
    [tool, finalReply, text, thinking],
    [finalReply],
    false,
    compareSequence,
  );

  assert.deepEqual(
    timeline?.processMessages.map((message) => message.id),
    ["thinking-1", "text-1", "tool-1"],
  );
  assert.deepEqual(
    timeline?.visibleMessages.map((message) => message.id),
    ["text-final"],
  );
});
