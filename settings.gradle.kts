rootProject.name = "dp-oppdrag"

include(
    "apps:oppdrag",
    "apps:simulering"
)

include(
    "libs:auth",
    "libs:auth-test",
    "libs:http",
    "libs:mq",
    "libs:utils",
    "libs:ws",
    "libs:xml",
)
