package com.funchole.backend.controlplane.security;

import com.funchole.backend.controlplane.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates machine clients (the MCP server today) carrying a long-lived
 * {@code fh_mcp_...} API key in the same {@code Authorization: Bearer} header
 * the browser JWT uses. Runs before {@link JwtAuthenticationFilter} in the
 * chain; only tokens with the API-key prefix are handled here, everything
 * else passes through untouched for the JWT filter to attempt.
 *
 * <p>The resolved {@link SecurityContext} is explicitly saved into
 * {@code securityContextRepository} (a request-attribute-backed store, see
 * {@code SecurityConfig}), not just left on the {@code SecurityContextHolder}
 * ThreadLocal - the MCP Streamable HTTP transport dispatches asynchronously,
 * possibly onto a different worker thread, and only the request-attribute
 * copy survives that; a ThreadLocal-only context left AuthorizationFilter
 * finding no authentication on the async continuation (a real
 * AuthorizationDeniedException seen live, not a hypothetical).
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;
    private final SecurityContextRepository securityContextRepository;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService, SecurityContextRepository securityContextRepository) {
        this.apiKeyService = apiKeyService;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(7);

        if (ApiKeyService.looksLikeApiKey(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            apiKeyService.resolve(token).ifPresent(appUser -> {
                AppUserPrincipal principal = new AppUserPrincipal(appUser);
                UsernamePasswordAuthenticationToken authenticationToken =
                        UsernamePasswordAuthenticationToken.authenticated(
                                principal,
                                null,
                                principal.getAuthorities()
                        );
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response);
            });
        }

        filterChain.doFilter(request, response);
    }
}
