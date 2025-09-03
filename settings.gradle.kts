plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "helved-utbetaling"

include(
    "apps:abetal",
    "apps:branntaarn",
    "apps:peisschtappern",
    "apps:simulering",
    "apps:urskog",
    "apps:utsjekk",
    "apps:vedskiva",
)

include(
    "models",
)

include(
    "libs:auth",
    "libs:auth-test",
    "libs:cache",
    "libs:http",
    "libs:jdbc",
    "libs:jdbc-test",
    "libs:kafka",
    "libs:kafka-test",
    "libs:ktor",
    "libs:ktor-test",
    "libs:mq",
    "libs:mq-test",
    "libs:tracing",
    "libs:utils",
    "libs:ws",
)
