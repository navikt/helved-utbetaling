
### Create a user
> nais aiven create kafka <username> helved

Example: `nais aiven create kafka robin helved`

### Grant access to stream
> nais aiven grant-access stream <username> <appname>

Example: `nais aiven grant-access stream robin abetal`

### Grant access to vanilla topic
> nais aiven grant-access topic <username> <topicname>

Example: `nais aiven grant-access topic robin status.v1`

### Get kafka.properties
> nais aiven get kafka <user resource> <namespace>

Will be placed somewhere like `/private/var/folders/bh/xltypjf53350jvww3cr_f4g00000gn/T/aiven-secret-542195830`

Example: `nais aiven get kafka robin-helved-f8fda16d helved`


