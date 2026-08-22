# OmniFlow Runtime Component

This GitHub Release ZIP is a self-contained, versioned OmniFlow plugin. OpenOmniBot downloads it
from the plugin market and runs it directly with the app's Alpine Python environment.

## Contents

- `component.json`: component identity, SemVer, runtime paths, and host MCP contract.
- `SKILL.md`: agent-facing usage and safety guidance.
- `vendor/site-packages/`: the small pure-Python dependency needed by the mobile runtime.
- `scripts/runtime/python/`: pinned OmniFlow sources plus OOB schemas.
- `scripts/runtime/.runtime/omnitransfer/`: canonical OmniTransfer v9 source and checkpoint.
- `INSTALL_DIR.json`: Android and shell installation paths.

## Installation

Install or update this complete ZIP through the OpenOmniBot plugin market. The Host verifies the
Release SHA-256 and extracts it atomically. NumPy comes from Alpine; the remaining pure-Python
dependency is already in the component, so mobile installation does not create a venv or run uv.
See `INSTALL_DIR.json` for the resolved directory contract.

## Quick Start

1. Install and enable **OmniFlow** from the OpenOmniBot plugin market.
2. Grant XiaoWan's accessibility and overlay permissions when prompted. OmniFlow uses the same
   native GUI-control path; it does not install a second accessibility service.
3. Ask XiaoWan to complete a GUI task. The first online run uses the configured VLM and writes a
   canonical RunLog containing the goal, actions, observations, final state, and diagnostics.
4. After a successful run, open **RunLog** and choose **Register as Function**. If the runtime has
   already registered it automatically, open **Functions** to inspect or run it directly.
5. Run the Function again with new semantic parameters. Replay uses the pinned OmniTransfer and
   falls back to the normal VLM path when current-screen recovery is needed.

The completion card reflects the real registration state: **Register as Function** means a
successful RunLog is available but has not been registered yet; **View Functions** means automatic
registration succeeded. Installing the plugin never enables XiaoWan's official built-in VLM model
automatically. Online execution uses the model/provider selected in OpenOmniBot.

### Coffee Example

The packaged catalog includes `order_beverage_meituan`, a parameterized Function that accepts a
beverage such as latte or Americano. It opens the app, searches for the requested drink, selects a
result, and stops before order submission or payment. Never use the example to submit or pay for a
real order without explicit user confirmation.

### Updating

Plugin-market updates replace the versioned OmniFlow Runtime as one unit. Prompts, planner logic,
RunLog conversion, Functions, checkers, replay policy, OmniTransfer source, checkpoint, and Python
dependencies stay pinned to the component manifest. OpenOmniBot remains the stable Android host for
permissions, screenshots, gestures, lifecycle, and the bridge.

### Standard MCP Hosts

Enable OpenOmniBot's built-in MCP service and register its Streamable HTTP `/mcp` URL and token in
any standard MCP host. The public OmniFlow surface contains only `run_gui`, `run_function`,
`list_functions`, and `register_function`. Observation, action, model, RunLog persistence, and
destructive Function management remain internal to OpenOmniBot. The phone does not install a
second Python MCP server or MCP SDK.

GitHub Release distributes the versioned plugin asset. The Skill explains how to use it. The MCP
endpoint exposes capabilities. Harness tests verify the published asset and MCP tool contract.
Runtime updates replace OmniFlow, OmniTransfer, prompts, Functions, checkers, and checkpoints
without replacing the APK or changing the standard MCP interface.

## Developer Override

Use these official Agent tools in order:

1. `get_omniflow_python_override` to inspect status or read one `omniflow/**/*.py` file.
2. `apply_omniflow_python_override` to validate, save, and hot reload a complete Python file.
3. `reload_omniflow_python_override` to restart the worker without changing source.
4. `clear_omniflow_python_override` with `confirm=true` to return to the pinned runtime.

An apply operation compiles the Python file before worker initialization and automatically restores
the previous content if reload fails. The override changes OmniFlow only. OmniTransfer remains the
canonical pinned implementation and missing mappings still fall back through the normal runtime.

Never change payment safety policy through the developer override. GUI automation may prepare an
order, but must not confirm or submit a real payment without explicit user confirmation.
