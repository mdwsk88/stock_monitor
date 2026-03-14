FROM maven:3.9.13-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY . .

RUN mvn -pl stock-web -am -DskipTests package \
    && cp "$(find stock-web/target -maxdepth 1 -type f -name 'stock-web-*.jar' ! -name '*.original' | head -n 1)" /workspace/stock-web-app.jar

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring --create-home --home-dir /app spring

WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms256m -Xmx768m -Duser.timezone=Asia/Shanghai -XX:+ExitOnOutOfMemoryError"

COPY --from=build /workspace/stock-web-app.jar /app/app.jar

RUN chown -R spring:spring /app

USER spring

EXPOSE 8888

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
