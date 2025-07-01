# OpenTelemetry

## Trace
Represents an end-to-end request across multiple services.

A *Trace ID* is an unique identifier for an entire trace.

A trace consists of multiple spans.

## Span
A single unit of work inside a trace:
    - Function execution
    - API call

A *Span ID* is an unique identifier for a single span inside a trace. 

## Parent-Child Relationship
Spans are linked in a hierarchy to show causality.

## Context Propagation
Ensures tracing works across:
    - Threads
    - Microservices
    - Network calls
