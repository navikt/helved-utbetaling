---
license: http4k Commercial
module: http4k-ai-mcp-conformance
---

# http4k-ai-mcp-conformance Reference

MCP protocol conformance test suite — verify your MCP server implementation.

## Usage

Extend the conformance contract to test your MCP server against the protocol spec:

```kotlin
class MyMcpServerConformanceTest : McpConformanceContract() {
    override fun server(): HttpHandler = myMcpServer()
}
```

## What It Tests

The conformance suite verifies:
- Tool listing and invocation
- Resource listing, reading, and subscription
- Prompt listing and retrieval
- Initialization and capability negotiation
- Error handling (invalid tool names, missing args, etc.)
- Protocol version negotiation

## Gotchas

- This module is for **server** authors — use it to verify your MCP server is spec-compliant
- Conformance tests use the `http4k-ai-mcp-client` module internally
- Extend with your own transport-specific tests as needed
