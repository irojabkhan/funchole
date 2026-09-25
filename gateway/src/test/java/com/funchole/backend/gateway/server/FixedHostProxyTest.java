package com.funchole.backend.gateway.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.funchole.backend.gateway.server.FixedHostProxy.PathOverride;
import com.funchole.backend.gateway.server.FixedHostProxy.ProxyTarget;
import com.funchole.backend.gateway.server.FixedHostProxy.Resolution;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pure unit coverage for {@link FixedHostProxy#resolve} - the matching and
 * URI-rewrite rules a {@link PathOverride} applies, independent of the real
 * network forwarding exercised end to end by
 * {@link GatewayHttpHandlerFixedHostProxyTest}.
 */
class FixedHostProxyTest {

    private static final String HOST = "admin.test";
    private static final ProxyTarget DEFAULT_TARGET = new ProxyTarget("web", 3000);
    private static final ProxyTarget OVERRIDE_TARGET = new ProxyTarget("controlplane", 7080);

    private final FixedHostProxy proxy = new FixedHostProxy(
            Map.of(HOST, DEFAULT_TARGET),
            Map.of(HOST, new PathOverride("/mcp", "/api/mcp", OVERRIDE_TARGET)));

    @Test
    void unknownHostnameResolvesToNull() {
        assertNull(proxy.resolve("other.test", "/mcp", "/mcp"));
    }

    @Test
    void exactPrefixMatchRewritesToOverrideTarget() {
        Resolution resolution = proxy.resolve(HOST, "/mcp", "/mcp");
        assertEquals(OVERRIDE_TARGET, resolution.target());
        assertEquals("/api/mcp", resolution.uri());
    }

    @Test
    void subPathUnderThePrefixIsAlsoRewritten() {
        Resolution resolution = proxy.resolve(HOST, "/mcp/stream", "/mcp/stream");
        assertEquals(OVERRIDE_TARGET, resolution.target());
        assertEquals("/api/mcp/stream", resolution.uri());
    }

    @Test
    void queryStringSurvivesTheRewrite() {
        Resolution resolution = proxy.resolve(HOST, "/mcp", "/mcp?foo=bar");
        assertEquals("/api/mcp?foo=bar", resolution.uri());
    }

    @Test
    void similarButDistinctPathDoesNotMatchThePrefix() {
        // "/mcporeign" starts with "/mcp" as raw characters but is not the
        // same path segment - must fall through to the hostname's own
        // default target, unchanged, not the override.
        Resolution resolution = proxy.resolve(HOST, "/mcporeign", "/mcporeign");
        assertEquals(DEFAULT_TARGET, resolution.target());
        assertEquals("/mcporeign", resolution.uri());
    }

    @Test
    void unrelatedPathOnTheSameHostnameFallsThroughToTheDefaultTarget() {
        Resolution resolution = proxy.resolve(HOST, "/admin/login", "/admin/login");
        assertEquals(DEFAULT_TARGET, resolution.target());
        assertEquals("/admin/login", resolution.uri());
    }
}
