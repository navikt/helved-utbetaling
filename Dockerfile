FROM cgr.dev/chainguard/jre:latest

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"
ENV JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF8

COPY apps/*/build/libs/*-all.jar /app.jar
ADD apps/*/build/resources/main /migrations

ENTRYPOINT [                           \
    "java",                            \
    "-XX:MaxRAMPercentage=60.0",       \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/tmp",           \
    "-XX:ActiveProcessorCount=2",      \
    "-jar",                            \
    "/app.jar"                         \
]
