package com.funchole.backend.controlplane.mcp;

import com.funchole.backend.controlplane.security.AppUserPrincipal;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * MCP tool methods have no {@code @AuthenticationPrincipal}-style injection
 * (that's a Spring MVC handler-method convenience, not something the MCP
 * annotation-scanning invoker provides) - but the same
 * {@code ApiKeyAuthenticationFilter}-populated {@link SecurityContextHolder}
 * is still in effect for the request, since Streamable HTTP tool calls are
 * handled synchronously on the same servlet thread. Every {@code @McpTool}
 * method resolves the caller through this instead of repeating the lookup.
 */
public final class CurrentMcpUser {

    private CurrentMcpUser() {
    }

    public static UUID id() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AppUserPrincipal appUserPrincipal) {
            return appUserPrincipal.getId();
        }
        throw new IllegalStateException("MCP request is not authenticated as a FuncHole user");
    }
}
