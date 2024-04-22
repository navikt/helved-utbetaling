FROM cgr.dev/chainguard/jre-lts:latest

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"

COPY build/libs/utsjekk-all.jar /app.jar

ENTRYPOINT [ \
    "java",  \
    "-XX:MaxRAMPercentage=75.0",  \
    "-XX:MinRAMPercentage=25.0",  \
    "-XX:+HeapDumpOnOutOfMemoryError",  \
    "-XX:HeapDumpPath=/tmp",  \
    "-XX:ActiveProcessorCount=2",  \
    "-jar",  \
    "/app.jar"  \
    ]
