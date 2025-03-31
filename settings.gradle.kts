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

