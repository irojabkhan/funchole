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
 * TLS has already been terminated.
 *
 * <p>Built once in {@code GatewayMain} from env vars and empty when none are
 * configured - the self-hosted default, where this whole feature is inert.
 */
public final class FixedHostProxy {
    private final Map<String, ProxyTarget> targetsByHostname;

    public FixedHostProxy(Map<String, ProxyTarget> targetsByHostname) {
        this.targetsByHostname = Map.copyOf(targetsByHostname);
    }

    public static FixedHostProxy empty() {
        return new FixedHostProxy(Map.of());
    }

    public ProxyTarget targetFor(String hostname) {
        return targetsByHostname.get(hostname);
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
