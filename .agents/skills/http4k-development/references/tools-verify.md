---
module: http4k-tools-verify
license: http4k Commercial
---

# http4k-tools-verify Reference

A Gradle plugin that verifies cosign signatures on http4k artifacts downloaded from the http4k Enterprise Repository. Adds a `verifyHttp4kDependencies` task that runs automatically before compilation, ensuring supply chain integrity.

## Setup

Apply the plugin in your `build.gradle.kts`:

```kotlin
plugins {
    id("org.http4k.verify")
}
```

No further configuration is required. The plugin downloads the http4k public key from `https://http4k.org/cosign.pub` automatically.

## Configuration

```kotlin
http4kVerify {
    failOnError.set(true)                    // default: true — throw on verification failure
    publicKey.set(file("cosign.pub"))        // optional: use a local key file instead
}
```

- **`failOnError`** — when `true`, a failed signature check throws `GradleException` and stops the build
- **`publicKey`** — local PEM file path; if omitted, the public key is fetched from `https://http4k.org/cosign.pub`

## What It Verifies

For each `org.http4k` artifact on the `runtimeClasspath` and `testRuntimeClasspath`, the plugin checks up to four sigstore bundles:

| Check | Artifact Classifier | Bundle Classifier |
|-------|--------------------|--------------------|
| JAR signature | *(none)* | `jar-sigstore` |
| SBOM (CycloneDX) | `cyclonedx` | `cyclonedx-sigstore` |
| SLSA provenance | `provenance` | `provenance-sigstore` |
| License report | `license-report` | `license-report-sigstore` |

Bundles are only available from the http4k Enterprise Repository (`maven.http4k.org`). If no bundles are found, the plugin logs a notice and skips verification without failing.

## Tasks

### `verifyHttp4kDependencies`

```
./gradlew verifyHttp4kDependencies
```

Runs in the `verification` group. Wired as a dependency of `compileKotlin` / `compileJava`. After running, exports verification artifacts to `build/http4k-verify/`:

```
build/http4k-verify/
  cosign.pub                                         # copy of the public key used
  verification-report.json                           # structured JSON report
  org.http4k/http4k-core/<version>/
    http4k-core-<version>.jar.sha256
    http4k-core-<version>-jar-sigstore.json
    ...
```

### `clearHttp4kVerificationCache`

```
./gradlew clearHttp4kVerificationCache
```

Clears the local verification cache at `~/.gradle/caches/http4k-verify/verified.txt`. Run this to force re-verification of all artifacts on the next build.

## Caching

Verified artifacts are recorded in `~/.gradle/caches/http4k-verify/verified.txt` keyed by `label:sha256`. Already-verified artifacts are skipped on subsequent builds.

## Verification Report

After each run, a JSON report is written to `build/http4k-verify/verification-report.json`:

```json
{
  "timestamp": "2026-04-05T14:30:00Z",
  "public_key_fingerprint": "sha256:<hex>",
  "modules": [
    {
      "group": "org.http4k",
      "module": "http4k-core",
      "version": "6.40.1.0",
      "jar_sha256": "<hex>",
      "checks": {
        "jar":        { "bundle": "org.http4k/.../http4k-core-...-jar-sigstore.json", "verification": "passed" },
        "sbom":       { "verification": "not_available" },
        "provenance": { "verification": "not_available" },
        "license":    { "verification": "not_available" }
      }
    }
  ]
}
```

`verification` values: `"passed"`, `"failed"`, `"not_available"` (bundle not published).

## Console Output Example

```
Verifying 2 http4k module(s)...
  org.http4k:http4k-core:6.40.1.0           jar ✓   sbom ✓   provenance -   license -
  org.http4k:http4k-server-jetty:6.40.1.0   jar ✓   sbom ✓   provenance -   license -
Verified: 2 modules, 8 signatures
Verification artifacts exported to build/http4k-verify
```

A `-` means the artifact type was not available (not published with that classifier).

## Gotchas

- **Enterprise Repository required**: Sigstore bundles are only published to `maven.http4k.org`. Artifacts from Maven Central have no bundles — the plugin skips them silently.
- **`failOnError = false` for graceful degradation**: If you want warnings without breaking the build when bundles are unavailable, set `failOnError.set(false)`.
- **Public key is fetched on first run**: If the build environment has no internet access, supply a local `publicKey` file.
- **EC key format**: The PEM public key must be an EC key (`-----BEGIN PUBLIC KEY-----`). Verification uses `SHA256withECDSA`.
- **Output directory is recreated on each run**: `build/http4k-verify/` is deleted and recreated by `verifyHttp4kDependencies`. Do not store files there manually.
- **Digest check is optional**: If the sigstore bundle omits `messageDigest`, only the signature is checked against the artifact bytes. Bundles with a `messageDigest` field also have their digest validated before signature verification.
