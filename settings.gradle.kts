plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "helved-utbetaling"

include(
    "apps:abetal",
    "apps:oppdrag",
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
    "libs:kafka",
    "libs:kafka-test",
)
