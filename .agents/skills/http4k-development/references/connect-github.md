---
license: Apache-2.0
module: http4k-connect-github
---

# http4k-connect-github Reference

GitHub client — connect actions for the GitHub REST API.

## Client

```kotlin
val github = GitHub.Http(
    token = { GitHubToken.of(System.getenv("GITHUB_TOKEN")) },
    http = JavaHttpClient()   // optional
)
```

## GitHub App Auth

```kotlin
val github = GitHub.Http(
    token = { GitHubToken.of(generateAppInstallationToken(appId, privateKey, installationId)) },
    http = JavaHttpClient()
)
```

## Repository Operations

```kotlin
// List repos for authenticated user
val repos = github.listRepositories().successValue()

// Get repo details
github.getRepository(owner = Owner.of("my-org"), repo = RepoName.of("my-repo")).successValue()
```

## Issues & Pull Requests

```kotlin
// List issues (paged)
val issues = github.listIssues(Owner.of("my-org"), RepoName.of("my-repo")).successValue()

// Create issue
github.createIssue(
    owner = Owner.of("my-org"),
    repo = RepoName.of("my-repo"),
    title = "Bug report",
    body = "Description of the bug"
).successValue()
```

## Paged Actions

```kotlin
// Actions returning lists support automatic pagination
val allRepos = generateSequence(
    github.listRepositories(perPage = 100).successValue()
) { page ->
    page.nextPageToken?.let { github.listRepositories(page = it).successValue() }
}.flatMap { it.items }.toList()
```

## Gotchas

- Token is a lambda (`() -> GitHubToken`) — allows runtime token refresh
- No fake provided — use `MockHttpHandler` or recording for tests
- Paged responses include link headers — use the page token pattern for iteration
- Rate limiting: authenticated requests allow 5000/hour; check `X-RateLimit-Remaining`
- GitHub App tokens are short-lived (1 hour) — the lambda allows fresh token generation
