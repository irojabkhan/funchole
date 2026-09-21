package com.funchole.backend.controlplane.service;

import com.funchole.backend.certificate.store.Http01ChallengeStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes ACME HTTP-01 challenge responses into Postgres so the Gateway's
 * own plain-HTTP port-80 listener (a separate process/container - see
 * {@code AcmeChallengeServer} in the gateway module) can serve them to
 * Let's Encrypt's validation requests. Controlplane is the only side that
 * writes; Gateway only ever reads this table directly.
 */
@Component
public class JdbcHttp01ChallengeStore implements Http01ChallengeStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcHttp01ChallengeStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void put(String token, String authorization) {
        jdbcTemplate.update(
                "INSERT INTO acme_challenges (token, key_authorization) VALUES (?, ?) "
                        + "ON CONFLICT (token) DO UPDATE SET key_authorization = EXCLUDED.key_authorization",
                token,
                authorization
        );
    }

    @Override
    public void remove(String token) {
        jdbcTemplate.update("DELETE FROM acme_challenges WHERE token = ?", token);
    }
}
