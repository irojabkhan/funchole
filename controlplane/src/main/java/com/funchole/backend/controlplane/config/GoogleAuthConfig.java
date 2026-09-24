package com.funchole.backend.controlplane.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.security.GeneralSecurityException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoogleAuthConfig {

    /**
     * Built even when {@code GOOGLE_OAUTH_CLIENT_ID} is unset, with an
     * audience that can never match a real token - {@code GoogleAuthService}
     * checks {@link GoogleAuthProperties#isConfigured()} itself before ever
     * calling this bean, but a Spring bean method still has to return
     * something at context-startup time regardless of runtime configuration.
     */
    @Bean
    GoogleIdTokenVerifier googleIdTokenVerifier(GoogleAuthProperties properties) throws GeneralSecurityException, java.io.IOException {
        String audience = properties.isConfigured() ? properties.clientId() : "unconfigured-google-oauth-client-id";
        return new GoogleIdTokenVerifier.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(audience))
                .build();
    }
}
