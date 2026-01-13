FROM eclipse-temurin:21-jdk-alpine AS jre
# jdeps --print-module-deps --ignore-missing-deps apps/abetal/build/libs/abetal-all.jar
# explicit add "--add-modules jdk.jcmd" a tool for creating heap dumps
RUN jlink \
    --verbose \
    --module-path $JAVA_HOME/bin/jmods/ \
    # --add-modules java.base \
    --add-modules java.base \
    --add-modules java.compiler \
    --add-modules java.desktop \
    --add-modules java.instrument \
    --add-modules java.management \
    --add-modules java.naming \
    --add-modules java.net.http \
    --add-modules java.security.jgss \
    --add-modules java.security.sasl \
    --add-modules java.sql \
    --add-modules java.xml \
    --add-modules java.xml.crypto \
    --add-modules jdk.charsets \
    --add-modules jdk.crypto.ec \
    --add-modules jdk.httpserver \
    --add-modules jdk.jcmd \ 
    --add-modules jdk.jfr \
    --add-modules jdk.management \
    --add-modules jdk.naming.dns \
    --add-modules jdk.unsupported \
    --add-modules jdk.xml.dom \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /customjre

FROM alpine:latest AS app
ENV LANG="nb_NO.UTF-8" LANGUAGE="nb_NO:nb" LC_ALL="nb:NO.UTF-8" TZ="Europe/Oslo"
ENV JAVA_HOME=/jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN apk add --no-cache gcompat libstdc++ snappy

COPY --from=jre /customjre $JAVA_HOME
ADD apps/abetal/build/resources/main /migrations
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
