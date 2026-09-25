package com.funchole.backend.gateway.server;

import java.util.Map;

/**
 * A small, fixed hostname -> internal-service lookup, separate from the
 * normal {@code AppDomain}/Flow routing in {@link com.funchole.backend.gateway.GatewayRegistry}.
 * Used for the admin web app and controlplane API, which are reverse-proxied
 * straight to their internal docker-network address rather than dispatched
 * through NATS to a Function/Flow - see {@link GatewayHttpHandler}. Both are
 * regular {@code Gateway}/{@code AppDomain} rows (with zero Flows) purely so
 * they get a real TLS certificate through the existing per-domain cert
 * pipeline; this class only carries where to forward the HTTP traffic once
 * TLS has already been terminated. A hostname may additionally carry one
 * {@link PathOverride} - e.g. the admin web host's own {@code /mcp} sends
 * traffic to controlplane instead of the web app, so an MCP client never
 * needs the separate controlplane API domain at all.
 *
 * <p>Built once in {@code GatewayMain} from env vars and empty when none are
 * configured - the self-hosted default, where this whole feature is inert.
 */
public final class FixedHostProxy {
    private final Map<String, ProxyTarget> targetsByHostname;
    private final Map<String, PathOverride> pathOverridesByHostname;

    public FixedHostProxy(Map<String, ProxyTarget> targetsByHostname, Map<String, PathOverride> pathOverridesByHostname) {
        this.targetsByHostname = Map.copyOf(targetsByHostname);
        this.pathOverridesByHostname = Map.copyOf(pathOverridesByHostname);
    }

    public static FixedHostProxy empty() {
        return new FixedHostProxy(Map.of(), Map.of());
    }

    /**
     * Resolves where to forward a request for {@code hostname}, and under
     * what URI - null if this hostname isn't a fixed-host-proxy target at
     * all. A hostname's {@link PathOverride}, when one is registered and
     * {@code path} matches it, wins over that hostname's own default target
     * (e.g. the admin web host's own {@code /mcp} shortcut to controlplane,
     * rewritten to its real {@code /api/mcp} route); every other path on
     * that hostname still falls through to the default target unchanged.
     */
    public Resolution resolve(String hostname, String path, String rawUri) {
        PathOverride override = pathOverridesByHostname.get(hostname);
        if (override != null && matchesPrefix(path, override.matchPrefix())) {
            String rewrittenUri = override.rewritePrefix() + rawUri.substring(override.matchPrefix().length());
            return new Resolution(override.target(), rewrittenUri);
        }
        ProxyTarget target = targetsByHostname.get(hostname);
        if (target == null) {
            return null;
        }
        return new Resolution(target, rawUri);
    }

    // Exact match or a real path segment boundary only - "/mcp" must not
    // also swallow an unrelated "/mcporeign" path on the same host.
    private static boolean matchesPrefix(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    public record Resolution(ProxyTarget target, String uri) {
    }

    public record PathOverride(String matchPrefix, String rewritePrefix, ProxyTarget target) {
    }

    public record ProxyTarget(String host, int port) {
        public static ProxyTarget parse(String hostColonPort) {
            int colonIndex = hostColonPort.lastIndexOf(':');
            if (colonIndex <= 0 || colonIndex == hostColonPort.length() - 1) {
                throw new IllegalArgumentException(
                        "Expected \"host:port\", got \"" + hostColonPort + "\"");
            }
            String host = hostColonPort.substring(0, colonIndex);
            int port = Integer.parseInt(hostColonPort.substring(colonIndex + 1));
            return new ProxyTarget(host, port);
        }
    }
}
