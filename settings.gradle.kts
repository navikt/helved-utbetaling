rootProject.name = "dp-oppdrag"

include(
    "apps:oppdrag",
    "apps:simulering",
    "apps:utsjekk"
)

include(
    "libs:auth",
    "libs:auth-test",
    "libs:http",
    "libs:mq",
    "libs:postgres",
    "libs:utils",
    "libs:ws",
    "libs:xml",
)
