---
license: Apache-2.0
module: http4k-api-graphql
---

# http4k-api-graphql Reference

GraphQL server and client support bridging graphql-java to http4k's function model. `GraphQLHandler` is `(GraphQLRequest) -> GraphQLResponse`.

## Server — Basic Handler

```kotlin
val graphQLHandler: GraphQLHandler = { request ->
    val result = graphQL.execute(request.query)
    GraphQLResponse.from(result)
}

val app = routes(
    "/graphql" bind graphQL(graphQLHandler)
)
```

## Server — With Context

```kotlin
val app = routes(
    "/graphql" bind graphQL(
        handler = { request, user ->
            val result = graphQL.execute(
                ExecutionInput.newExecutionInput()
                    .query(request.query)
                    .operationName(request.operationName)
                    .variables(request.variables ?: emptyMap())
                    .context(user)
                    .build()
            )
            GraphQLResponse.from(result)
        },
        getContext = { request -> authenticateUser(request) }
    )
)
```

## GraphQL Playground

```kotlin
val app = routes(
    "/graphql" bind graphQL(handler),
    "/playground" bind graphQLPlayground(Uri.of("/graphql"), "My API Playground")
)
```

Serves an interactive GraphQL IDE pointing at the given endpoint.

## Client

```kotlin
// Convert any HttpHandler into a GraphQLHandler
val client: GraphQLHandler = httpClient.asGraphQLHandler("/graphql")
val client: GraphQLHandler = httpClient.asGraphQLHandler(Uri.of("https://api.example.com/graphql"))

// Use it
val response = client(GraphQLRequest(
    query = "query { user(id: 1) { name email } }",
    operationName = "GetUser",
    variables = mapOf("id" to 1)
))
val data = response.data       // Any?
val errors = response.errors   // List<Map<String, Any>>?
```

## Request/Response Types

```kotlin
data class GraphQLRequest(
    val query: String = "",
    val operationName: String? = null,
    val variables: Map<String, Any>? = emptyMap()
)

data class GraphQLResponse(
    val data: Any?,
    val errors: List<Map<String, Any>>?,
    val extensions: Map<String, Any>? = null
) {
    companion object {
        fun from(executionResult: ExecutionResult): GraphQLResponse
    }
}
```

## Gotchas

- **POST only**: The `graphQL()` handler only accepts POST requests.
- **Jackson required**: This module depends on `http4k-format-jackson` for serialization of request/response types.
- **graphql-java bridge**: `GraphQLResponse.from(ExecutionResult)` converts graphql-java results, deduplicating `ExceptionWhileDataFetching` errors.
- **Testable without HTTP**: Since `GraphQLHandler` is just a function, test it directly without starting a server.
