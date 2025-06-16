plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "helved-utbetaling"

include(
    "apps:abetal",
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
    "libs:jdbc-test",
    "libs:kafka",
    "libs:kafka-test",
    "libs:ktor-test",
)
