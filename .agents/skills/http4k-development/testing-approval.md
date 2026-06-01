---
license: Apache-2.0
module: http4k-testing-approval
---

# http4k-testing-approval Reference

Approval (snapshot) testing for HTTP messages. Compare responses against stored golden files with format-aware diffing.

## JUnit5 Extensions

```kotlin
// JSON approval testing
@ExtendWith(JsonApprovalTest::class)
class MyApiTest {
    @Test
    fun `returns user`(approver: Approver) {
        val response = app(Request(GET, "/user/1"))
        approver.assertApproved(response)
    }
}

// Other format extensions
@ExtendWith(HtmlApprovalTest::class)     // HTML comparison
@ExtendWith(XmlApprovalTest::class)      // XML comparison
@ExtendWith(YamlApprovalTest::class)     // YAML comparison
@ExtendWith(Html5ApprovalTest::class)    // HTML5 comparison
@ExtendWith(BinaryApprovalTest::class)   // Binary comparison
@ExtendWith(ApprovalTest::class)         // Plain text comparison
```

## Approver API

```kotlin
// Assert response matches approved content (creates file on first run)
approver.assertApproved(response)

// Assert with a named variant
approver.withNameSuffix("variant").assertApproved(response)

// Check if approved content exists
approver.hasApprovedContent()
```

## Approval Source

```kotlin
// Default: files stored next to test class
// Approved: src/test/resources/com/example/MyTest.testName.approved
// Actual:   src/test/resources/com/example/MyTest.testName.actual

// Custom storage
FileSystemApprovalSource(File("src/test/resources"))
```

## Gotchas

- **First run creates `.approved` file**: On first execution, the test fails and writes the `.actual` file. Rename to `.approved` to approve.
- **Format-aware**: `JsonApprovalTest` pretty-prints JSON before comparison; `XmlApprovalTest` normalizes XML, etc.
- **Full HTTP message**: By default compares the entire HTTP message (status, headers, body). Use `ApprovalContent` to customize.
