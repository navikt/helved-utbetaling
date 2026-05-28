FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25
ENV TZ="Europe/Oslo"
COPY --chown=1069:1069 apps/simulering2/build/split-jars/deps.jar /app/deps.jar
COPY --chown=1069:1069 apps/simulering2/build/split-jars/app.jar /app/app.jar
WORKDIR /app
CMD [                                     \
    "--enable-native-access=ALL-UNNAMED", \
    "-XX:MaxRAMPercentage=60.0",          \
    "-XX:ActiveProcessorCount=1",         \
    "-jar",                               \
    "app.jar"                             \
]
