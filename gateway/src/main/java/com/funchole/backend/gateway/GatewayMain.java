package com.funchole.backend.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funchole.backend.artifact.RemoteArtifactStore;
import com.funchole.backend.artifact.S3ArtifactStore;
import com.funchole.backend.artifact.S3ArtifactStoreConfig;
import com.funchole.backend.gateway.acme.AcmeChallengeLookup;
import com.funchole.backend.gateway.acme.AcmeChallengeServer;
import com.funchole.backend.gateway.flow.FlowResolver;
import com.funchole.backend.gateway.flow.SnapshotFlowResolver;
import com.funchole.backend.gateway.server.FixedHostProxy;
import com.funchole.backend.gateway.server.FixedHostProxy.ProxyTarget;
import com.funchole.backend.gateway.server.GatewayHealthChecker;
import com.funchole.backend.gateway.server.GatewayHttpHandler;
import com.funchole.backend.gateway.server.GatewayInvocationCompletionListener;
import com.funchole.backend.gateway.server.GatewayServer;
import com.funchole.backend.gateway.server.PendingInvocationResponseRegistry;
import com.funchole.backend.gateway.staticsite.StaticSiteCache;
import com.funchole.backend.invocation.JdbcInvocationRegistry;
import com.funchole.backend.invocation.NatsJetStreamInvocationEventPublisher;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.nats.client.Connection;
import io.nats.client.Nats;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GatewayMain {
    private static final Logger logger = LoggerFactory.getLogger(GatewayMain.class);

    private GatewayMain() {
    }

    public static void main(String[] args) throws Exception {
        int port = readInt("GATEWAY_PORT", 443);
        DataSource dataSource = createDataSource();
        Connection natsConnection = Nats.connect(readString("NATS_URL", "nats://localhost:4222"));
        ObjectMapper objectMapper = new ObjectMapper();
        OpenBaoCertificateLoader certificateLoader = new OpenBaoCertificateLoader(
                readString("BAO_ADDR", "http://localhost:8200"),
                readString("BAO_TOKEN", "")
        );
        GatewayRegistryLoader gatewayRegistryLoader = new GatewayRegistryLoader(dataSource, certificateLoader);
        GatewayRegistry gatewayRegistry = new GatewayRegistry(loadGatewayRegistry(gatewayRegistryLoader));
        FlowResolver flowResolver = new SnapshotFlowResolver(gatewayRegistry);
        JdbcInvocationRegistry invocationRegistry =
                new JdbcInvocationRegistry(dataSource, new NatsJetStreamInvocationEventPublisher(natsConnection));
        ScheduledExecutorService invocationTimeoutExecutor = createInvocationTimeoutExecutor();
        PendingInvocationResponseRegistry pendingResponseRegistry = new PendingInvocationResponseRegistry(
                invocationTimeoutExecutor,
                Duration.ofMillis(readInt("GATEWAY_INVOCATION_TIMEOUT_MS", 15000))
        );
        GatewayInvocationCompletionListener completionListener =
                new GatewayInvocationCompletionListener(natsConnection, pendingResponseRegistry);
        ExecutorService invocationExecutor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "gateway-invocation-io");
            thread.setDaemon(true);
            return thread;
        });
        StaticSiteCache staticSiteCache = new StaticSiteCache(
                Path.of(readString("GATEWAY_STATIC_SITE_CACHE_DIR", "/tmp/funchole/gateway-static-site-cache")),
                createRemoteArtifactStore()
        );
        GatewayHttpHandler gatewayHttpHandler = new GatewayHttpHandler(
                objectMapper,
                gatewayRegistry,
                flowResolver,
                invocationRegistry,
                pendingResponseRegistry,
                invocationExecutor,
                staticSiteCache,
                new GatewayHealthChecker(dataSource, natsConnection),
                loadFixedHostProxy()
        );
        GatewayServer gatewayServer = new GatewayServer(port, gatewayRegistry, gatewayHttpHandler);
        AcmeChallengeServer acmeChallengeServer = new AcmeChallengeServer(
                readInt("ACME_HTTP01_PORT", 80),
                new AcmeChallengeLookup(dataSource)
        );
        ScheduledExecutorService registryRefreshExecutor = createRegistryRefreshExecutor();
        startRegistryPolling(gatewayRegistry, gatewayRegistryLoader, registryRefreshExecutor);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            gatewayServer.close();
            acmeChallengeServer.close();
            completionListener.close();
            gatewayHttpHandler.close();
            registryRefreshExecutor.shutdownNow();
            invocationTimeoutExecutor.shutdownNow();
            if (dataSource instanceof HikariDataSource hikariDataSource) {
                hikariDataSource.close();
            }
            try {
                natsConnection.close();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        }));

        startAcmeChallengeServer(acmeChallengeServer);
        gatewayServer.start();
        gatewayServer.await();
    }

    private static GatewayRegistrySnapshot loadGatewayRegistry(GatewayRegistryLoader gatewayRegistryLoader) {
        int attempts = readInt("GATEWAY_REGISTRY_LOAD_ATTEMPTS", 30);
        int delayMs = readInt("GATEWAY_REGISTRY_LOAD_DELAY_MS", 2000);
        IllegalStateException lastException = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return gatewayRegistryLoader.load();
            } catch (IllegalStateException exception) {
                lastException = exception;
                logger.warn(
                        "Gateway registry load attempt {}/{} failed: {}",
                        attempt,
                        attempts,
                        exception.getMessage()
                );
                if (attempt == attempts) {
                    break;
                }

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Gateway registry loading was interrupted", interruptedException);
                }
            }
        }

        throw new IllegalStateException("Unable to load gateway registry after " + attempts + " attempts", lastException);
    }

    /**
     * A bind failure here (port already in use, insufficient privileges,
     * etc.) is not allowed to take down real HTTPS traffic on 443 - most
     * deployments (the SELF_SIGNED default) never need this listener at
     * all, so it's logged and skipped rather than propagated.
     */
    private static void startAcmeChallengeServer(AcmeChallengeServer acmeChallengeServer) {
        try {
            acmeChallengeServer.start();
        } catch (RuntimeException exception) {
            logger.warn(
                    "ACME HTTP-01 challenge server failed to start; Let's Encrypt certificate "
                            + "issuance/renewal will fail until this is resolved. Gateway HTTPS traffic is unaffected.",
                    exception
            );
        }
    }

    private static ScheduledExecutorService createRegistryRefreshExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "gateway-registry-poller");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static ScheduledExecutorService createInvocationTimeoutExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "gateway-invocation-timeout");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void startRegistryPolling(
            GatewayRegistry gatewayRegistry,
            GatewayRegistryLoader gatewayRegistryLoader,
            ScheduledExecutorService registryRefreshExecutor
    ) {
        int intervalSeconds = readInt("GATEWAY_REGISTRY_POLL_INTERVAL_SECONDS", 5);
        registryRefreshExecutor.scheduleWithFixedDelay(
                () -> refreshGatewayRegistry(gatewayRegistry, gatewayRegistryLoader),
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
        );
    }

    private static void refreshGatewayRegistry(GatewayRegistry gatewayRegistry, GatewayRegistryLoader gatewayRegistryLoader) {
        try {
            GatewayRegistrySnapshot nextSnapshot = gatewayRegistryLoader.load();
            int previousSize = gatewayRegistry.size();
            int nextSize = nextSnapshot.entriesByHostname().size();
            gatewayRegistry.replace(nextSnapshot);
            if (previousSize != nextSize) {
                logger.info("Gateway registry refreshed. Registered host count changed from {} to {}", previousSize, nextSize);
            }
        } catch (Exception exception) {
            logger.warn("Gateway registry polling failed: {}", exception.getMessage());
        }
    }

    /**
     * Optional, cloud product only - reverse-proxies a fixed hostname (the
     * admin web app, the controlplane API, and/or the marketing landing
     * page) straight to its internal docker-network address instead of the
     * normal AppDomain/Flow dispatch. Each pair is independently optional;
     * an unset {@code *_HOST} excludes that entry, and with none set (the
     * self-hosted default) this returns {@link FixedHostProxy#empty()} and
     * the whole feature is inert. See docs/development.md before
     * configuring this.
     */
    private static FixedHostProxy loadFixedHostProxy() {
        Map<String, ProxyTarget> targetsByHostname = new HashMap<>();
        addFixedHostProxyEntry(targetsByHostname, "ADMIN_WEB_PROXY_HOST", "ADMIN_WEB_PROXY_TARGET", "web:3000");
        addFixedHostProxyEntry(targetsByHostname, "CONTROLPLANE_API_PROXY_HOST", "CONTROLPLANE_API_PROXY_TARGET", "controlplane:7080");
        addFixedHostProxyEntry(targetsByHostname, "LANDING_PROXY_HOST", "LANDING_PROXY_TARGET", "landing:80");

        Map<String, FixedHostProxy.PathOverride> pathOverridesByHostname = new HashMap<>();
        addMcpPathOverride(pathOverridesByHostname);

        return new FixedHostProxy(targetsByHostname, pathOverridesByHostname);
    }

    private static void addFixedHostProxyEntry(
            Map<String, ProxyTarget> targetsByHostname, String hostEnvVar, String targetEnvVar, String defaultTarget
    ) {
        String hostname = readString(hostEnvVar, "");
        if (hostname.isBlank()) {
            return;
        }
        targetsByHostname.put(hostname.trim().toLowerCase(), ProxyTarget.parse(readString(targetEnvVar, defaultTarget)));
    }

    /**
     * Lets an MCP client reach controlplane's MCP server at
     * {@code <admin-web-host>/mcp} instead of requiring the separate
     * controlplane API domain - a friendlier URL for the exact same
     * backend, rewritten to controlplane's real {@code /api/mcp} route
     * before forwarding (see {@code FixedHostProxy.PathOverride}). Only
     * registered when the admin web proxy itself is configured, since
     * there's no admin web host to attach this shortcut to otherwise; reuses
     * {@code CONTROLPLANE_API_PROXY_TARGET} so there is exactly one place
     * that says where controlplane actually lives.
     */
    private static void addMcpPathOverride(Map<String, FixedHostProxy.PathOverride> pathOverridesByHostname) {
        String adminWebHost = readString("ADMIN_WEB_PROXY_HOST", "");
        if (adminWebHost.isBlank()) {
            return;
        }
        ProxyTarget controlplaneTarget = ProxyTarget.parse(readString("CONTROLPLANE_API_PROXY_TARGET", "controlplane:7080"));
        pathOverridesByHostname.put(
                adminWebHost.trim().toLowerCase(),
                new FixedHostProxy.PathOverride("/mcp", "/api/mcp", controlplaneTarget));
    }

    /**
     * Same {@code S3_ARTIFACT_*} env vars {@code RuntimeWorkerMain} reads -
     * the Gateway fetches STATIC-runtime artifacts from the exact same
     * remote store Functions are published to, just for reading files
     * directly rather than executing anything.
     */
    private static RemoteArtifactStore createRemoteArtifactStore() {
        return new S3ArtifactStore("STATIC", new S3ArtifactStoreConfig(
                URI.create(readRequiredString("S3_ARTIFACT_ENDPOINT")),
                readRequiredString("S3_ARTIFACT_BUCKET"),
                readRequiredString("S3_ARTIFACT_ACCESS_KEY"),
                readRequiredString("S3_ARTIFACT_SECRET_KEY"),
                readString("S3_ARTIFACT_REGION", "us-east-1"),
                readBoolean("S3_ARTIFACT_PATH_STYLE_ACCESS", true)
        ));
    }

    private static DataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(readString("DB_URL", "jdbc:postgresql://localhost:5432/funchole"));
        config.setUsername(readString("DB_USERNAME", "funchole"));
        config.setPassword(readString("DB_PASSWORD", "funchole"));
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setPoolName("gateway-db-pool");
        return new HikariDataSource(config);
    }

    private static String readString(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int readInt(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(value);
    }

    private static String readRequiredString(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static boolean readBoolean(String name, boolean fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }
}
