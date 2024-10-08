FROM eclipse-temurin:21-jdk-alpine AS jre
RUN jlink \
    --verbose \
    --module-path $JAVA_HOME/bin/jmods/ \
    --add-modules java.base \
    # --add-modules java.desktop \
    --add-modules java.management \
    --add-modules java.instrument \
    --add-modules java.naming \
    --add-modules java.net.http \
    --add-modules java.security.jgss \
    --add-modules java.security.sasl \
    --add-modules java.sql \
    --add-modules jdk.httpserver \
    --add-modules jdk.unsupported \
    --add-modules jdk.crypto.ec \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /customjre

FROM alpine:latest AS app
ENV LANG="nb_NO.UTF-8" LANGUAGE="nb_NO:nb" LC_ALL="nb:NO.UTF-8" TZ="Europe/Oslo"
ENV JAVA_HOME=/jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre /customjre $JAVA_HOME
COPY apps/*/build/libs/*-all.jar /app.jar
ADD apps/*/build/resources/main /migrations
ENTRYPOINT [                           \
    "java",                            \
    "-XX:MaxRAMPercentage=60.0",       \
    "-XX:ActiveProcessorCount=2",      \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/tmp",           \
    "-jar",                            \
    "app.jar"                          \
]
