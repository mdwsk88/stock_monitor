ARG APP_MODULE=stock-web
ARG APP_PORT=8888

FROM maven:3.9.13-eclipse-temurin-21 AS build

ARG APP_MODULE

WORKDIR /workspace

COPY pom.xml ./
COPY stock-common/pom.xml stock-common/pom.xml
COPY stock-web/pom.xml stock-web/pom.xml
COPY us-stock-mcp/pom.xml us-stock-mcp/pom.xml
COPY a-stock-mcp/pom.xml a-stock-mcp/pom.xml

COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl ${APP_MODULE} -am -DskipTests -Dmaven.test.skip=true package \
    && cp "$(find ${APP_MODULE}/target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' | head -n 1)" /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy

ARG APP_PORT

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring --create-home --home-dir /app spring

WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms256m -Xmx768m -Duser.timezone=Asia/Shanghai -XX:+ExitOnOutOfMemoryError"

COPY --from=build /workspace/app.jar /app/app.jar

RUN chown -R spring:spring /app

USER spring

EXPOSE ${APP_PORT}

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
