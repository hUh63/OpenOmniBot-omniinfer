# OmniFlow MCP Runtime

This runtime exposes OmniFlow through the official Model Context Protocol SDK. Any MCP host uses
the same `run_gui`, `run_function`, Function-management, and RunLog tools. A configured device MCP
endpoint exposes only `device_*` primitives; OmniFlow owns planning, replay, checker, OmniTransfer,
and VLM behavior.

The runtime ZIP intentionally does not contain the Python MCP SDK. The host installer maintains one
versioned SDK environment outside the runtime, so an OmniFlow hot update replaces only OmniFlow
source and assets instead of reinstalling a large dependency graph.

Required local configuration:

- `OMNIFLOW_DEVICE_MCP_URL`, the configured Streamable HTTP device MCP endpoint.
- `OMNIFLOW_DEVICE_MCP_TOKEN`, copied from OpenOmniBot's MCP settings.
- `OMNIFLOW_STORE_PATH`, optional local Function store path.

Payment confirmation remains a user-controlled action. The packaged coffee demo stops before order
submission or payment.
