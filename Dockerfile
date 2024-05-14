FROM cgr.dev/chainguard/jre-lts:latest

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"

COPY apps/*/build/libs/*-all.jar /app.jar

ENTRYPOINT [                           \
    "java",                            \
    "-XX:MaxRAMPercentage=60.0",       \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/tmp",           \
    "-XX:ActiveProcessorCount=2",      \
    "-jar",                            \
    "/app.jar"                         \
]
