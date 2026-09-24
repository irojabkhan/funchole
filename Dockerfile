FROM eclipse-temurin:25-jdk AS build-base
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle gradle.properties /workspace/
COPY gradle /workspace/gradle
COPY certificate/build.gradle /workspace/certificate/build.gradle
COPY core/build.gradle /workspace/core/build.gradle
COPY controlplane/build.gradle /workspace/controlplane/build.gradle
COPY gateway/build.gradle /workspace/gateway/build.gradle
COPY invocation/build.gradle /workspace/invocation/build.gradle
COPY invocation-contract/build.gradle /workspace/invocation-contract/build.gradle
COPY artifact/build.gradle /workspace/artifact/build.gradle
COPY runtime-registry/build.gradle /workspace/runtime-registry/build.gradle
COPY dispatcher/build.gradle /workspace/dispatcher/build.gradle
COPY runtime/build.gradle /workspace/runtime/build.gradle
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :controlplane:dependencies :gateway:dependencies :dispatcher:dependencies :runtime:dependencies --no-daemon >/dev/null 2>&1 || true

COPY certificate/src /workspace/certificate/src
COPY core/src /workspace/core/src
COPY controlplane/src /workspace/controlplane/src
COPY gateway/src /workspace/gateway/src
COPY invocation/src /workspace/invocation/src
COPY invocation-contract/src /workspace/invocation-contract/src
COPY artifact/src /workspace/artifact/src
COPY runtime-registry/src /workspace/runtime-registry/src
COPY dispatcher/src /workspace/dispatcher/src
COPY runtime/src /workspace/runtime/src
COPY docker /workspace/docker

FROM build-base AS build-controlplane
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :controlplane:bootJar --no-daemon

FROM build-base AS build-gateway
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :gateway:fatJar --no-daemon

FROM build-base AS build-dispatcher
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :dispatcher:fatJar --no-daemon

FROM build-base AS build-runtime
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :runtime:fatJar --no-daemon

FROM eclipse-temurin:25-jre AS runtime-base
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl jq \
    && rm -rf /var/lib/apt/lists/*
COPY docker/openbao-common.sh /opt/funchole/openbao-common.sh
RUN chmod +x /opt/funchole/openbao-common.sh

FROM runtime-base AS runtime-node-base
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

FROM runtime-node-base AS controlplane
COPY --from=build-controlplane /workspace/controlplane/build/libs/funchole-controlplane.jar app.jar
COPY docker/controlplane-entrypoint.sh /opt/funchole/entrypoint.sh
RUN chmod +x /opt/funchole/entrypoint.sh
EXPOSE 7080
ENTRYPOINT ["/opt/funchole/entrypoint.sh"]

FROM runtime-base AS gateway
COPY --from=build-gateway /workspace/gateway/build/libs/funchole-gateway.jar app.jar
COPY docker/gateway-entrypoint.sh /opt/funchole/entrypoint.sh
RUN chmod +x /opt/funchole/entrypoint.sh
EXPOSE 443
ENTRYPOINT ["/opt/funchole/entrypoint.sh"]

FROM runtime-base AS dispatcher
COPY --from=build-dispatcher /workspace/dispatcher/build/libs/funchole-dispatcher.jar app.jar
COPY docker/dispatcher-entrypoint.sh /opt/funchole/entrypoint.sh
RUN chmod +x /opt/funchole/entrypoint.sh
ENTRYPOINT ["/opt/funchole/entrypoint.sh"]

FROM runtime-node-base AS runtime-worker
COPY --from=build-runtime /workspace/runtime/build/libs/funchole-runtime.jar app.jar
COPY runtime/node /app/node
RUN cd /app/node && npm ci --no-audit --no-fund --omit=dev
COPY runtime/artifacts /app/artifacts
ENV NODE_EXECUTOR_SCRIPT_PATH=/app/node/executor.mjs
ENV ARTIFACT_DIR=/app/artifacts/dev
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM debian:13-slim AS rustfs-init
RUN apt-get update \
    && apt-get install -y --no-install-recommends awscli ca-certificates gzip tar \
    && rm -rf /var/lib/apt/lists/*

FROM node:22-alpine AS build-web
WORKDIR /workspace/web
# NEXT_PUBLIC_* values are inlined into the client bundle at this build step,
# not read again at container-start time - a runtime `environment:` entry in
# docker-compose.yml has no effect on them, so this one has to arrive as a
# real build ARG instead. Empty by default: the login page's Google button
# simply doesn't render when this is unset (see app/login/page.tsx).
ARG NEXT_PUBLIC_GOOGLE_CLIENT_ID=""
ENV NEXT_PUBLIC_GOOGLE_CLIENT_ID=${NEXT_PUBLIC_GOOGLE_CLIENT_ID}
COPY control-plane-web/package.json control-plane-web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY control-plane-web/ ./
RUN npm run build

FROM node:22-alpine AS web
WORKDIR /app
ENV NODE_ENV=production HOSTNAME=0.0.0.0 PORT=3000
COPY --from=build-web /workspace/web/.next/standalone ./
COPY --from=build-web /workspace/web/.next/static ./.next/static
COPY --from=build-web /workspace/web/public ./public
EXPOSE 3000
CMD ["node", "server.js"]

FROM node:22-alpine AS dev-web
WORKDIR /workspace/web
CMD ["npm", "run", "dev"]

FROM eclipse-temurin:25-jdk AS dev-base-common
WORKDIR /workspace
ENV GRADLE_USER_HOME=/opt/gradle-home
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl jq \
    && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*
COPY gradlew gradlew.bat settings.gradle build.gradle gradle.properties /workspace/
COPY gradle /workspace/gradle
COPY core/build.gradle /workspace/core/build.gradle
COPY controlplane/build.gradle /workspace/controlplane/build.gradle
COPY gateway/build.gradle /workspace/gateway/build.gradle
COPY invocation/build.gradle /workspace/invocation/build.gradle
COPY runtime-registry/build.gradle /workspace/runtime-registry/build.gradle
COPY dispatcher/build.gradle /workspace/dispatcher/build.gradle
COPY runtime/build.gradle /workspace/runtime/build.gradle
COPY core/src /workspace/core/src
COPY controlplane/src /workspace/controlplane/src
COPY gateway/src /workspace/gateway/src
COPY invocation/src /workspace/invocation/src
COPY runtime-registry/src /workspace/runtime-registry/src
COPY dispatcher/src /workspace/dispatcher/src
COPY runtime/src /workspace/runtime/src
COPY docker /workspace/docker
COPY docker/openbao-common.sh /opt/funchole/openbao-common.sh
COPY docker/watch-and-run.sh /opt/funchole/watch-and-run.sh
RUN chmod +x /workspace/gradlew \
    /opt/funchole/openbao-common.sh \
    /opt/funchole/watch-and-run.sh \
    && mkdir -p /opt/gradle-home \
    && ./gradlew --version \
    && ./gradlew :controlplane:dependencies :gateway:dependencies :dispatcher:dependencies :runtime:dependencies --no-daemon >/dev/null 2>&1 || true

FROM dev-base-common AS dev-controlplane
COPY docker/controlplane-dev-entrypoint.sh /opt/funchole/controlplane-dev-entrypoint.sh
RUN chmod +x /opt/funchole/controlplane-dev-entrypoint.sh

FROM dev-base-common AS dev-gateway
COPY docker/gateway-dev-entrypoint.sh /opt/funchole/gateway-dev-entrypoint.sh
RUN chmod +x /opt/funchole/gateway-dev-entrypoint.sh

FROM dev-base-common AS dev-dispatcher
COPY docker/dispatcher-dev-entrypoint.sh /opt/funchole/dispatcher-dev-entrypoint.sh
RUN chmod +x /opt/funchole/dispatcher-dev-entrypoint.sh

FROM dev-base-common AS dev-runtime
COPY docker/runtime-dev-entrypoint.sh /opt/funchole/runtime-dev-entrypoint.sh
RUN chmod +x /opt/funchole/runtime-dev-entrypoint.sh

FROM rustfs-init AS dev-rustfs-init
