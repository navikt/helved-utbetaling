FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21
ENV TZ="Europe/Oslo"
ADD --chown=1069:1069 apps/*/build/resources/main /app/migrations/
# Layer order matters for build cache reuse: deps.jar (~60 MB, third-party
# libraries) rarely changes, so it stays cached across docker builds whenever
# we only modify app code. app.jar (~2 MB, our code + libs/ + models/) changes
# every build but the layer is tiny. The Class-Path attribute in app.jar's
# MANIFEST points to deps.jar, so `java -jar app.jar` resolves both.
COPY --chown=1069:1069 apps/*/build/split-jars/deps.jar /app/deps.jar
COPY --chown=1069:1069 apps/*/build/split-jars/app.jar /app/app.jar
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
