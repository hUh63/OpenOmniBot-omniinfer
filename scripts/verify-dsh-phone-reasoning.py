#!/usr/bin/env python3
"""Assert reasoning blocks in a synthetic DSH session's durable upstream journal.

Usage: verify-dsh-phone-reasoning.py SERIAL SESSION_FILE MARKER=off MARKER=high ...
Run after completing the matching UI sends and again after app restart.
No credentials, full messages or unrelated conversations are exported.
"""
import json
import re
import subprocess
import sys

serial, session_file, *cases = sys.argv[1:]
assert re.fullmatch(r"[A-Za-z0-9._:-]+", serial)
assert re.fullmatch(
    r"local/(ubuntu|alpine)/root/\.dsh/omnibot-acp/sessions/[^/]+/"
    r"[0-9a-f-]+/session\.jsonl\.zstd", session_file
), "Only an explicit DSH session journal is accepted"
assert cases, "At least one synthetic marker is required"

raw = subprocess.check_output([
    "adb", "-s", serial, "exec-out", "run-as", "cn.com.omnimind.bot", "cat", session_file
], timeout=30)
plain = subprocess.run(["zstd", "-d", "-c"], input=raw, check=True,
                       capture_output=True, timeout=30).stdout
rows = [json.loads(line) for line in plain.splitlines() if line.strip()]
results = []
for case in cases:
    marker, expected = case.rsplit("=", 1)
    assert re.fullmatch(r"OOB_DSH_[A-Z0-9_]+", marker)
    assert expected in ("off", "high")
    users = [r for r in rows if r["type"] == "user/message"
             and marker in json.dumps(r["data"], ensure_ascii=False)]
    replies = [r for r in rows if r["type"] == "assistant/message"
               and marker in json.dumps(r["data"]["message"], ensure_ascii=False)]
    assert len(users) == len(replies) == 1, f"{marker}: missing or duplicate user/reply"
    reply = replies[0]["data"]
    blocks = reply["message"]["content"]
    has_reasoning = any(b.get("type") == "reasoning" and b.get("text") for b in blocks)
    assert has_reasoning == (expected == "high"), f"{marker}: wrong reasoning behavior"
    results.append({"marker": marker, "selected": expected, "turn": reply["turn"],
                    "reasoningPresent": has_reasoning, "userCount": 1, "replyCount": 1})
print(json.dumps({"passed": True, "serial": serial,
                  "source": "DSH durable session journal", "cases": results}, indent=2))
