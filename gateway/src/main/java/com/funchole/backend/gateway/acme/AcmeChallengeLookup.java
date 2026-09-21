package com.funchole.backend.gateway.acme;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Reads the ACME HTTP-01 challenge responses Controlplane published while
 * requesting a Let's Encrypt certificate (see
 * {@code JdbcHttp01ChallengeStore} in the controlplane module) - a raw
 * direct-JDBC read against the shared {@code acme_challenges} table,
 * matching {@code GatewayRegistryLoader}'s own style of reading tables it
 * doesn't own via plain SQL rather than a cross-module Java dependency.
 */
public final class AcmeChallengeLookup {

    private final DataSource dataSource;

    public AcmeChallengeLookup(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<String> find(String token) {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select key_authorization from acme_challenges where token = ?")
        ) {
            statement.setString(1, token);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(resultSet.getString("key_authorization"));
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to look up ACME challenge token", exception);
        }
    }
}
