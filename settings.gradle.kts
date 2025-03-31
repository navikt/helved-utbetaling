plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "helved-utbetaling"

include(
    "apps:abetal",
    "apps:oppdrag",
    "apps:urskog",
    "apps:simulering",
    "apps:utsjekk",
    "apps:peisen",
    "models",
)

include(
    "libs:kafka",
    "libs:kafka-test",
)
include("apps:peisen")
findProject(":apps:peisen")?.name = "peisen"
