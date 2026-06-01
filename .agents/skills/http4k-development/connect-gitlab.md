---
license: Apache-2.0
module: http4k-connect-gitlab
---

# http4k-connect-gitlab Reference

GitLab client — connect actions for the GitLab REST API.

## Client

```kotlin
val gitlab = GitLab.Http(
    token = { GitLabToken.of(System.getenv("GITLAB_TOKEN")) },
    http = JavaHttpClient()   // optional
)
```

## Custom GitLab Instance

```kotlin
val gitlab = GitLab.Http(
    token = { GitLabToken.of("my-token") },
    baseUri = Uri.of("https://gitlab.mycompany.com"),
    http = JavaHttpClient()
)
```

## Project Operations

```kotlin
// List projects
val projects = gitlab.listProjects().successValue()

// Get project
gitlab.getProject(projectId = ProjectId.of("my-group/my-project")).successValue()
```

## Merge Requests

```kotlin
gitlab.listMergeRequests(projectId = ProjectId.of("my-group/my-repo")).successValue()
```

## Paged Actions

```kotlin
// Paged responses use X-Next-Page header
val allProjects = generateSequence(
    gitlab.listProjects(perPage = 100).successValue()
) { page ->
    page.nextPageToken?.let { gitlab.listProjects(page = it).successValue() }
}.flatMap { it.items }.toList()
```

## Gotchas

- Token is a lambda (`() -> GitLabToken`) — allows runtime token refresh
- No fake provided — use `MockHttpHandler` or recording for tests
- Project IDs can be numeric IDs or URL-encoded namespace/project paths
- GitLab pagination uses `X-Next-Page` header (not `Link` like GitHub)
- Self-hosted GitLab instances: override `baseUri` in client constructor
