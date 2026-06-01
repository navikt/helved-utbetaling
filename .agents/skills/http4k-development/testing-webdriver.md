---
license: Apache-2.0
module: http4k-testing-webdriver
---

# http4k-testing-webdriver Reference

Selenium WebDriver backed by an http4k `HttpHandler`. Test web UIs without starting a real server or browser — requests go directly through the handler.

## Construction

```kotlin
val driver = Http4kWebDriver(myApp)
```

## Navigation

```kotlin
driver.get("/page")
driver.navigate().to("/other-page")
driver.navigate().back()
driver.navigate().forward()
driver.navigate().refresh()

driver.currentUrl    // current page URL
driver.title         // page title from <title>
driver.pageSource    // raw HTML source
```

## Finding Elements

```kotlin
val element = driver.findElement(By.id("myId"))
val elements = driver.findElements(By.className("item"))
val link = driver.findElement(By.tagName("a"))
val input = driver.findElement(By.name("email"))
val css = driver.findElement(By.cssSelector(".container > div"))
val linkByText = driver.findElement(By.linkText("Click Here"))

element.text         // text content
element.click()      // follow link or submit form
element.sendKeys("input text")
element.getAttribute("href")
element.isDisplayed
element.isEnabled
```

## Cookie Management

```kotlin
driver.manage().addCookie(Cookie("name", "value"))
driver.manage().getCookies()
driver.manage().getCookieNamed("session")
driver.manage().deleteCookieNamed("session")
driver.manage().deleteAllCookies()
```

## Form Submission

```kotlin
// Submit buttons can override form action and method
// <input type="submit" formaction="/other" formmethod="post">
element.click()  // uses formaction/formmethod if present, else form attributes

// Basic auth from URL
driver.get("http://user:pass@example.com/page")
// Subsequent form submissions include Authorization header
```

## Gotchas

- **No real browser**: Uses JSoup to parse HTML. No JavaScript execution.
- **No server needed**: Requests go directly to the `HttpHandler` — fast and in-process.
- **Cookie tracking**: Cookies are tracked automatically across requests via `ClientFilters.Cookies`.
- **Redirect following**: Redirects are followed automatically via `ClientFilters.FollowRedirects`.
- **Selenium API**: Implements the standard `WebDriver` interface — works with existing Selenium test patterns.
- **NoSuchElementException includes selector**: Error messages include which `By` selector failed, making failures easier to diagnose.
- **Basic auth from URL**: If the current page URL contains userinfo (`user:pass@`), the credentials are applied as Basic Auth headers on subsequent requests.
- **formaction/formmethod**: Submit buttons with `formaction` or `formmethod` attributes override the parent form's `action` and `method`.
