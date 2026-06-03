---
license: Apache-2.0
module: http4k-testing-playwright
---

# http4k-testing-playwright Reference

Playwright browser testing backed by an http4k `HttpHandler`. Starts a real browser against a temporary in-process server.

## JUnit5 Extension

```kotlin
@RegisterExtension
val playwright = LaunchPlaywrightBrowser(myApp)

@Test
fun `renders homepage`(browser: Http4kBrowser) {
    with(browser.newPage()) {
        val response = navigateHome()
        assertThat(String(response.body()), equalTo("hello"))
    }
}

@Test
fun `navigates to path`(browser: Http4kBrowser) {
    with(browser.newPage()) {
        val response = navigate("/api/users")
        assertThat(response.status(), equalTo(200))
    }
}
```

## Using Standard Playwright Browser

```kotlin
@Test
fun `standard playwright`(browser: Browser) {
    with(browser.newPage()) {
        val response = navigate("${playwright.baseUri}/path")
        assertThat(String(response.body()), equalTo("content"))
    }
}
```

## Configuration

```kotlin
// From HttpHandler
LaunchPlaywrightBrowser(
    http = myApp,
    browserType = Playwright::chromium,        // or ::firefox, ::webkit
    launchOptions = LaunchOptions(),            // headless, slowMo, etc.
    serverFn = ::SunHttp                       // server backend for the temp server
)

// From pre-configured Http4kServer
LaunchPlaywrightBrowser(
    server = myApp.asServer(SunHttp(0)),
    browserType = Playwright::chromium,
    launchOptions = LaunchOptions()
)

// From PolyHandler (for WebSocket/SSE testing)
LaunchPlaywrightBrowser(
    poly = myPolyHandler,
    serverFn = ::SunHttp,                      // PolyServerConfig
    browserType = Playwright::chromium,
    launchOptions = LaunchOptions()
)
```

## Http4kBrowser and HttpPage

```kotlin
// Http4kBrowser wraps Playwright Browser with a baseUri
val page: HttpPage = browser.newPage()

// HttpPage wraps Playwright Page with convenience methods
page.navigateHome()           // navigate to baseUri
page.navigate("/path")        // navigate to baseUri + path
```

## Gotchas

- **Real browser**: Unlike `Http4kWebDriver`, this runs a real browser (Chromium/Firefox/WebKit) with full JavaScript support.
- **Temporary server**: A real HTTP server is started on a random port for the browser to connect to. Stopped after test.
- **Playwright install required**: Run `playwright install` to download browser binaries before first use.
- **Parameter injection**: The extension resolves `Http4kBrowser`, `Browser`, `Page`, and `Playwright` parameters in test methods.
- **PolyHandler support**: Use the `PolyHandler` constructor variant to test WebSocket and SSE handlers alongside HTTP.
