---
license: Apache-2.0
module: http4k-connect-amazon-apprunner
---

# http4k-connect-amazon-apprunner Reference

AppRunner client — connect actions for AWS App Runner container services.

## Client

```kotlin
val appRunner = AppRunner.Http(
    region = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient()   // optional
)
```

## Service Management

```kotlin
// Create service from ECR image
val service = appRunner.createService(
    serviceName = ServiceName.of("my-service"),
    sourceConfiguration = SourceConfiguration(
        imageRepository = ImageRepository(
            imageIdentifier = "123456789.dkr.ecr.us-east-1.amazonaws.com/my-app:latest",
            imageRepositoryType = ImageRepositoryType.ECR
        )
    )
).successValue().Service

// List services
appRunner.listServices().successValue()

// Delete service
appRunner.deleteService(serviceArn = ServiceArn.of(service.ServiceArn)).successValue()
```

## Gotchas

- Uses JSON protocol with `X-Amz-Target` header
- Service creation is async — service enters `OPERATION_IN_PROGRESS` state initially
- Service ARN required for most operations (not name)
- ECR access requires an App Runner access role with ECR pull permissions
- Auto-scaling configuration can be attached at creation or updated separately
