rootProject.name = "utsjekk-tmp"

include(
    "apps:utsjekk",
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
