package com.funchole.backend.controlplane.controller;

import com.funchole.backend.controlplane.dto.AuthRequest;
import com.funchole.backend.controlplane.dto.AuthTokenResponse;
import com.funchole.backend.controlplane.dto.GoogleAuthRequest;
import com.funchole.backend.controlplane.mapper.AuthTokenMapper;
import com.funchole.backend.controlplane.security.AppUserPrincipal;
import com.funchole.backend.controlplane.security.JwtService;
import com.funchole.backend.controlplane.security.JwtToken;
import com.funchole.backend.controlplane.service.GoogleAuthService;
import com.funchole.backend.core.base.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthTokenMapper authTokenMapper;
    private final GoogleAuthService googleAuthService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            AuthTokenMapper authTokenMapper,
            GoogleAuthService googleAuthService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.authTokenMapper = authTokenMapper;
        this.googleAuthService = googleAuthService;
    }

    @PostMapping("/token")
    public ApiResponse<AuthTokenResponse> issueToken(@Valid @RequestBody AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
        );

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        JwtToken jwtToken = jwtService.generateToken(principal.getId(), principal.getUsername(), principal.isPasswordChangeRequired());
        return ApiResponse.success(authTokenMapper.toResponse(jwtToken));
    }

    /**
     * "Sign in with Google" for the single bootstrap admin account - see
     * {@link GoogleAuthService}'s own javadoc. {@code idToken} is a signed
     * Google ID token the frontend already obtained via Google Identity
     * Services; this never itself talks to Google.
     */
    @PostMapping("/google")
    public ApiResponse<AuthTokenResponse> issueTokenViaGoogle(@Valid @RequestBody GoogleAuthRequest request) {
        JwtToken jwtToken = googleAuthService.authenticate(request.idToken());
        return ApiResponse.success(authTokenMapper.toResponse(jwtToken));
    }
}
