package com.funchole.backend.controlplane.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.funchole.backend.controlplane.config.TenantDatabaseProperties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Unit-tests {@link TenantDatabaseProvisioningService} directly against a
 * real Postgres (Testcontainers standing in for the separate tenant-db
 * server) - no Spring context needed, this class has no other collaborators.
 */
@Testcontainers
class TenantDatabaseProvisioningServiceTests {

    @Container
    static PostgreSQLContainer<?> tenantDbPostgres = new PostgreSQLContainer<>("postgres:17.6")
            .withDatabaseName("postgres")
            .withUsername("tenant_admin")
            .withPassword("tenant_admin");

    private TenantDatabaseProvisioningService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.close();
        }
    }

    @Test
    void provisionsARealDatabaseConnectableWithTheGeneratedCredentials() throws SQLException {
        service = newService();
        String identifier = "fh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String password = "TestPassw0rd123456789012345678";

        service.provisionDatabase(identifier, identifier, password);

        try (Connection connection = connectAs(identifier, identifier, password)) {
            assertThat(connection.isValid(5)).isTrue();
        }
    }

    @Test
    void oneTenantsRoleCannotConnectToAnotherTenantsDatabase() throws SQLException {
        service = newService();
        String firstIdentifier = "fh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String firstPassword = "TestPassw0rd111111111111111111";
        String secondIdentifier = "fh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String secondPassword = "TestPassw0rd222222222222222222";

        service.provisionDatabase(firstIdentifier, firstIdentifier, firstPassword);
        service.provisionDatabase(secondIdentifier, secondIdentifier, secondPassword);

        // The second tenant's role, using its own genuine credentials, must
        // still be refused connecting to the first tenant's database - the
        // PUBLIC CONNECT revoke must actually block this, not just the
        // Database row/OpenBao layer above it.
        assertThatThrownBy(() -> {
            try (Connection connection = DriverManager.getConnection(
                    jdbcUrl(firstIdentifier), secondIdentifier, secondPassword)) {
                connection.isValid(5);
            }
        }).isInstanceOf(SQLException.class);
    }

    private TenantDatabaseProvisioningService newService() {
        TenantDatabaseProperties properties = new TenantDatabaseProperties(
                tenantDbPostgres.getHost(), tenantDbPostgres.getMappedPort(5432), "tenant_admin", "tenant_admin");
        return new TenantDatabaseProvisioningService(properties);
    }

    private Connection connectAs(String databaseName, String username, String password) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(databaseName), username, password);
    }

    private String jdbcUrl(String databaseName) {
        return "jdbc:postgresql://" + tenantDbPostgres.getHost() + ":" + tenantDbPostgres.getMappedPort(5432) + "/" + databaseName;
    }
}
