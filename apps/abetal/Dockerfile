FROM eclipse-temurin:21-jdk-alpine AS jre
# jdeps --print-module-deps --ignore-missing-deps apps/abetal/build/libs/abetal-all.jar
RUN jlink \
    --verbose \
    --module-path $JAVA_HOME/bin/jmods/ \
    # --add-modules java.base \
    --add-modules java.desktop \
    --add-modules java.instrument \
    --add-modules java.naming \
    --add-modules java.security.jgss \
    --add-modules java.security.sasl \
    --add-modules java.sql \
    --add-modules java.compiler \
    --add-modules jdk.unsupported \
    --add-modules jdk.jfr \
    --add-modules jdk.management \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=zip-fast \
    --output /customjre

FROM alpine:latest AS app
ENV LANG="nb_NO.UTF-8" LANGUAGE="nb_NO:nb" LC_ALL="nb:NO.UTF-8" TZ="Europe/Oslo"
ENV JAVA_HOME=/jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN apk add --no-cache gcompat libstdc++ snappy

COPY --from=jre /customjre $JAVA_HOME
COPY apps/abetal/build/libs/abetal-all.jar /app.jar
ENTRYPOINT [                              \
    "java",                               \
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
