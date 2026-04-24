FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21
ENV TZ="Europe/Oslo"
COPY --chown=1069:1069 apps/branntaarn/build/split-jars/deps.jar /app/deps.jar
COPY --chown=1069:1069 apps/branntaarn/build/split-jars/app.jar /app/app.jar
WORKDIR /app
CMD [                                     \
    "--enable-native-access=ALL-UNNAMED", \
    "-XX:+UseG1GC",                       \
    "-XX:MaxRAMPercentage=70.0",          \
    "-XX:ActiveProcessorCount=2",         \
    "-XX:+UseStringDeduplication",        \
    "-XX:+HeapDumpOnOutOfMemoryError",    \
    "-XX:HeapDumpPath=/tmp",              \
    "-jar",                               \
    "app.jar"                             \
]
