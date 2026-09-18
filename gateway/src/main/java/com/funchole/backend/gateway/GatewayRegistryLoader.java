package com.funchole.backend.gateway;

import com.funchole.backend.certificate.CertificateBundle;
import com.funchole.backend.certificate.CertificateReference;
import com.funchole.backend.certificate.store.CertificateLoader;
import com.funchole.backend.gateway.flow.FlowResolution;
import com.funchole.backend.gateway.flow.GatewayRoutingSnapshot;
import com.funchole.backend.gateway.flow.PrefixRoute;
import com.funchole.backend.gateway.flow.RouteKey;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.net.ssl.SSLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GatewayRegistryLoader {
    private static final Logger logger = LoggerFactory.getLogger(GatewayRegistryLoader.class);

    private final DataSource dataSource;
    private final CertificateLoader certificateLoader;

    public GatewayRegistryLoader(DataSource dataSource, CertificateLoader certificateLoader) {
        this.dataSource = dataSource;
        this.certificateLoader = certificateLoader;
    }

    public GatewayRegistrySnapshot load() {
        try {
            Map<String, GatewayRuntimeEntry> entries = loadEntries();
            SslContext defaultContext = entries.isEmpty()
                    ? createFallbackSslContext()
                    : entries.values().iterator().next().sslContext();
            Map<UUID, GatewayRoutingSnapshot> routingByGatewayId = loadRouting();

            if (entries.isEmpty()) {
                logger.warn("No active gateway certificates were found. Gateway will start with a fallback TLS context and return unknown-host responses until gateways are provisioned.");
            }

            return new GatewayRegistrySnapshot(Map.copyOf(entries), defaultContext, routingByGatewayId);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load gateway registry", exception);
        }
    }

    private Map<UUID, GatewayRoutingSnapshot> loadRouting() throws SQLException {
        Map<UUID, Map<RouteKey, FlowResolution>> routesByGatewayId = new LinkedHashMap<>();
        Map<UUID, List<PrefixRoute>> prefixRoutesByGatewayId = new LinkedHashMap<>();
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        select
                            f.gateway_id,
                            f.http_method,
                            f.path,
                            f.id as flow_id,
                            f.flow_key,
                            f.active_flow_version_id,
                            (
                                select fv.id
                                from flow_steps fs
                                join function_versions fv on fv.id = fs.component_version_id
                                where fs.flow_version_id = f.active_flow_version_id
                                  and fs.component_type = 'FUNCTION'
                                  and fv.runtime = 'STATIC'
                                order by fs.position asc
                                limit 1
                            ) as static_function_version_id
                        from flows f
                        join gateways g on g.id = f.gateway_id
                        where f.deleted_at is null
                          and f.active_flow_version_id is not null
                          and f.active_flow_version_status = 'ADOPTED'
                          and g.status = 'ACTIVE'
                        """)
        ) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID gatewayId = UUID.fromString(resultSet.getString("gateway_id"));
                    String httpMethod = resultSet.getString("http_method");
                    String path = resultSet.getString("path");
                    String staticFunctionVersionIdText = resultSet.getString("static_function_version_id");
                    UUID staticFunctionVersionId = staticFunctionVersionIdText == null
                            ? null : UUID.fromString(staticFunctionVersionIdText);

                    // A path ending in "/*" owns an entire subtree (a whole
                    // SPA/SSR frontend app, or anything that wants its own
                    // internal sub-routing) rather than one exact URL, so it
                    // goes into the prefix-matched fallback list instead of
                    // the exact-match map.
                    Optional<String> wildcardPrefix = PrefixRoute.wildcardPrefix(path);
                    FlowResolution resolution = new FlowResolution(
                            UUID.fromString(resultSet.getString("flow_id")),
                            resultSet.getString("flow_key"),
                            UUID.fromString(resultSet.getString("active_flow_version_id")),
                            wildcardPrefix.orElse(null),
                            staticFunctionVersionId
                    );

                    if (wildcardPrefix.isPresent()) {
                        prefixRoutesByGatewayId.computeIfAbsent(gatewayId, key -> new ArrayList<>())
                                .add(new PrefixRoute(httpMethod, wildcardPrefix.get(), resolution));
                    } else {
                        RouteKey routeKey = new RouteKey(httpMethod, path);
                        routesByGatewayId.computeIfAbsent(gatewayId, key -> new HashMap<>()).put(routeKey, resolution);
                    }
                }
            }
        }

        Map<UUID, GatewayRoutingSnapshot> routingByGatewayId = new LinkedHashMap<>();
        Set<UUID> gatewayIds = new LinkedHashSet<>();
        gatewayIds.addAll(routesByGatewayId.keySet());
        gatewayIds.addAll(prefixRoutesByGatewayId.keySet());
        for (UUID gatewayId : gatewayIds) {
            Map<RouteKey, FlowResolution> exactRoutes = routesByGatewayId.getOrDefault(gatewayId, Map.of());
            List<PrefixRoute> prefixRoutes = new ArrayList<>(prefixRoutesByGatewayId.getOrDefault(gatewayId, List.of()));
            // Longest prefix first, so "/app/admin/*" is tried before the
            // broader "/app/*" when both could match the same request.
            prefixRoutes.sort(Comparator.comparingInt((PrefixRoute route) -> route.prefix().length()).reversed());
            routingByGatewayId.put(gatewayId, new GatewayRoutingSnapshot(Map.copyOf(exactRoutes), prefixRoutes));
        }
        return Map.copyOf(routingByGatewayId);
    }

    private Map<String, GatewayRuntimeEntry> loadEntries() throws SQLException {
        Map<String, GatewayRuntimeEntry> entries = new LinkedHashMap<>();
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        select
                            g.id as gateway_id,
                            g.name as gateway_name,
                            g.unique_key,
                            d.domain_name,
                            c.secret_ref,
                            c.provider
                        from gateways g
                        join app_domains d on d.id = g.app_domain_id
                        join certificates c on c.gateway_id = g.id
                        where g.status = 'ACTIVE'
                          and d.status = 'VERIFIED'
                          and c.status = 'ACTIVE'
                          and c.secret_ref is not null
                        order by g.created_at desc
                        """)
        ) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String gatewayKey = resultSet.getString("unique_key");
                    String domainName = resultSet.getString("domain_name");
                    GatewayCertificateRecord record = new GatewayCertificateRecord(
                            UUID.fromString(resultSet.getString("gateway_id")),
                            resultSet.getString("gateway_name"),
                            gatewayKey,
                            domainName,
                            normalizeHostname(gatewayKey + "." + domainName),
                            resultSet.getString("secret_ref"),
                            Enum.valueOf(com.funchole.backend.certificate.CertificateProvider.class, resultSet.getString("provider"))
                    );
                    try {
                        GatewayRuntimeEntry entry = toRuntimeEntry(record);
                        entries.put(entry.hostname(), entry);
                    } catch (IllegalStateException exception) {
                        logger.warn("Skipping gateway {} for hostname {} because TLS material could not be loaded", record.gatewayId(), record.hostname(), exception);
                    }
                }
            }
        }
        return entries;
    }

    private GatewayRuntimeEntry toRuntimeEntry(GatewayCertificateRecord record) {
        CertificateBundle bundle = certificateLoader.load(new CertificateReference(record.secretRef()));
        try {
            SslContext sslContext = SslContextBuilder.forServer(
                    new ByteArrayInputStream(bundle.certificateChain()),
                    new ByteArrayInputStream(bundle.privateKey())
            ).build();
            logger.debug("Loaded gateway TLS material for {}", record.hostname());
            return new GatewayRuntimeEntry(
                    record.gatewayId(),
                    record.gatewayName(),
                    record.gatewayKey(),
                    record.domainName(),
                    record.hostname(),
                    record.certificateProvider(),
                    sslContext
            );
        } catch (SSLException exception) {
            throw new IllegalStateException("Failed to build SslContext for " + record.hostname(), exception);
        }
    }

    private SslContext createFallbackSslContext() {
        try {
            SelfSignedCertificate certificate = new SelfSignedCertificate("localhost");
            return SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey()).build();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build fallback TLS context", exception);
        }
    }

    private String normalizeHostname(String hostname) {
        return hostname == null ? "" : hostname.trim().toLowerCase();
    }
}
