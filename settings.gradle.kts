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
    "libs:job",
    "libs:mq",
    "libs:postgres",
    "libs:task",
    "libs:utils",
    "libs:ws",
    "libs:xml",
)
