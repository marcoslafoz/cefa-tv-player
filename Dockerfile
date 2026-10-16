# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml lombok.config ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -DskipTests clean package && mv target/*.jar target/app.jar

FROM eclipse-temurin:21-jre AS java

FROM debian:bookworm-slim AS runner
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=java /opt/java/openjdk /opt/java/openjdk

RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      chromium \
      xvfb \
      xserver-xorg-core \
      xserver-xorg-input-libinput \
      xserver-xorg-video-fbdev \
      xserver-xorg-video-vesa \
      xinit \
      x11-utils \
      x11-xserver-utils \
      unclutter \
      openbox \
      fonts-liberation \
      fonts-noto-core \
      fonts-noto-color-emoji \
      tzdata \
      ca-certificates \
      curl \
      bash \
      iproute2 \
      procps \
      util-linux \
      iputils-ping \
      nano \
      less \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /app/target/app.jar app.jar
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh && mkdir -p /data

ENV CEFA_TV_DATA_DIR=/data
ENV DISPLAY_MODE=headless
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV CEFA_TV_SHELL_ENABLED=true
ENV CEFA_TV_SHELL_MODE=container
ENV CEFA_TV_RESOLUTION=1920x1080x24
ENV JAVA_OPTS="-Xmx512m"
VOLUME /data

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8081/actuator/health || exit 1

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
