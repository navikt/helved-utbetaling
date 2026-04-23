FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21
ENV TZ="Europe/Oslo"
ADD --chown=1069:1069 apps/urskog/build/resources/main /app/migrations/
COPY --chown=1069:1069 apps/urskog/build/libs/urskog-all.jar /app/app.jar
WORKDIR /app
CMD [                                     \
    "--enable-native-access=ALL-UNNAMED", \
    "-XX:+UseG1GC",                       \
    "-XX:MaxRAMPercentage=60.0",          \
    "-XX:ActiveProcessorCount=3",         \
    "-XX:+UseStringDeduplication",        \
    "-XX:+HeapDumpOnOutOfMemoryError",    \
    "-XX:HeapDumpPath=/tmp",              \
    "-jar",                               \
    "app.jar"                             \
]
