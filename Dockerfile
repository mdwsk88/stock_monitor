ARG APP_MODULE=stock-web
ARG APP_PORT=8888

FROM maven:3.9.13-eclipse-temurin-21 AS build

ARG APP_MODULE

WORKDIR /workspace

COPY . .

RUN mvn -pl ${APP_MODULE} -am -DskipTests package \
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
