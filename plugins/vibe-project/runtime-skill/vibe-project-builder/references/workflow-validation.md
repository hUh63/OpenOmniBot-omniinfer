# Workflow Validation Contract

Write a compact acceptance matrix in working context before implementation. Do not add a test-results README to the project. Each row must name:

1. **Scenario** — a realistic user request, not a schema-only example.
2. **Provenance** — classify every input as user-entered, locally derived, fetched from a named URL, or AI-generated at runtime.
3. **Tool sequence** — list the exact Xiaowan-facing tools in expected order.
4. **Assertions** — state the returned, persisted, and visible outcomes that prove success.
5. **Failure behavior** — state what users see and what remains unchanged when the dependency fails.

## Required Workflows

Run every applicable workflow with non-placeholder inputs:

- **Create and read round trip** — create one record from realistic user input, query it through a separate read tool, and assert stable identifiers and matching fields.
- **Update or idempotent retry** — update the created record, or retry the same operation when duplicates would be harmful; assert no silent duplicate or stale value.
- **AI event lifecycle** — call a Xiaowan-backed generation tool with current inputs/state, assert non-empty runtime output plus model/latency/usage metadata, review it, save it only when appropriate, then read it back. The generated text must not already exist as a fixed production value in source files.
- **Capability-source alignment** — trace every dynamic output field to values present in tool arguments or returned by the selected Connector. Reject instructions that ask Xiaowan to pretend it observed runtime status, current time, stored rows, device state, or external facts it never received.
- **Public data provenance** — fetch through the declared `http_json` Connector, assert source URL and retrieval time, then exercise unavailable or malformed data. Never replace failure with invented records.
- **Empty and retry state** — start with no business records, verify a useful empty state, force one dependency failure, and verify explicit retry without data loss.
- **Tool and Dashboard consistency** — compare a tool result with the dashboard backed by the same record/source. Field names, counts, timestamps, and status must agree; the dashboard must not use a second data array.
- **Publish and reopen** — require successful `project_check` and `project_publish`, verify the plugin is installed and enabled, verify `dashboardRoute` when an entry exists, and confirm the plugin's tools remain discoverable after reopening.
- **Safety boundary** — verify that payments, messages, external writes, destructive actions, and sensitive-data disclosure stop for confirmation or remain unavailable.

Skip a workflow only when the capability is absent, and record the reason in the matrix. A UI-only visual check never substitutes for invoking the business tool. A static checker pass never substitutes for one realistic happy path and one real failure/retry path.

## No-Mock Audit

Before `project_check`, search project source for placeholder records, demo arrays, random business values, fixed timestamps, canned recommendations, hardcoded AI output, network-failure success fallbacks, and AI instructions that embed the answer they claim to discover. Remove them from production paths. If sample data is an explicit product feature, label it `Sample`, isolate it from real records, and provide one-action removal.

Do not reject fixed interface copy, enumerations, validation rules, CSS values, schemas, or deterministic formulas merely because they are constants. Reject only constants that impersonate user data, external facts, runtime AI output, or successful backend responses.
