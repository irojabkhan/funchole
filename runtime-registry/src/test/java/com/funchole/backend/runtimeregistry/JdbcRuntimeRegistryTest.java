package com.funchole.backend.runtimeregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JdbcRuntimeRegistryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withDatabaseName("funchole")
            .withUsername("test")
            .withPassword("test");

    private DataSource dataSource;
    private JdbcRuntimeRegistry registry;

    @BeforeEach
    void setUp() throws SQLException {
        PGSimpleDataSource postgresDataSource = new PGSimpleDataSource();
        postgresDataSource.setUrl(postgres.getJdbcUrl());
        postgresDataSource.setUser(postgres.getUsername());
        postgresDataSource.setPassword(postgres.getPassword());
        dataSource = postgresDataSource;
        createSchema();
        registry = new JdbcRuntimeRegistry(dataSource);
    }

    @Test
    void registeredRuntimeSurvivesNewRegistryInstance() {
        registry.register(runtime("runtime-a", 4, 0));

        JdbcRuntimeRegistry restartedRegistry = new JdbcRuntimeRegistry(dataSource);

        assertEquals("runtime-a", restartedRegistry.find("runtime-a").orElseThrow().runtimeInstanceId());
        assertEquals(4, restartedRegistry.find("runtime-a").orElseThrow().capacity());
    }

    @Test
    void selectsAndPersistsLeastInFlightReservation() {
        registry.register(runtime("runtime-a", 4, 2));
        registry.register(runtime("runtime-b", 4, 1));

        RuntimeTarget target = registry.selectAndReserve(new RuntimeRequirement("NODE"));

        assertEquals("runtime-b", target.runtimeInstanceId());
        assertEquals(2, registry.find("runtime-b").orElseThrow().inFlight());
    }

    @Test
    void releasePersistsCapacity() {
        registry.register(runtime("runtime-a", 4, 2));

        registry.release("runtime-a");

        assertEquals(1, registry.find("runtime-a").orElseThrow().inFlight());
    }

    @Test
    void failsClearlyWhenNoCompatibleRuntimeIsRegistered() {
        registry.register(new RuntimeInstance(
                "runtime-python", "PYTHON", RuntimeInstanceStatus.AVAILABLE, 4, 0, "/tmp/python.sock"));

        NoRuntimeCapacityException exception = assertThrows(
                NoRuntimeCapacityException.class,
                () -> registry.selectAndReserve(new RuntimeRequirement("NODE"))
        );

        assertTrue(exception.getMessage().contains("No compatible runtime"));
    }

    @Test
    void failsClearlyWhenCompatibleRuntimeHasNoCapacity() {
        registry.register(runtime("runtime-a", 1, 1));

        NoRuntimeCapacityException exception = assertThrows(
                NoRuntimeCapacityException.class,
                () -> registry.selectAndReserve(new RuntimeRequirement("NODE"))
        );

        assertTrue(exception.getMessage().contains("No available runtime capacity"));
    }

    @Test
    void concurrentReservationsDoNotOverbookRuntimeCapacity() throws Exception {
        registry.register(runtime("runtime-a", 1, 0));
        JdbcRuntimeRegistry secondRegistry = new JdbcRuntimeRegistry(dataSource);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> registry.selectAndReserve(new RuntimeRequirement("NODE")));
            var second = executor.submit(() -> secondRegistry.selectAndReserve(new RuntimeRequirement("NODE")));

            int successCount = 0;
            int failureCount = 0;
            for (var future : java.util.List.of(first, second)) {
                try {
                    future.get(5, TimeUnit.SECONDS);
                    successCount++;
                } catch (java.util.concurrent.ExecutionException exception) {
                    if (exception.getCause() instanceof NoRuntimeCapacityException) {
                        failureCount++;
                    } else {
                        throw exception;
                    }
                }
            }

            assertEquals(1, successCount);
            assertEquals(1, failureCount);
            assertEquals(1, registry.find("runtime-a").orElseThrow().inFlight());
        }
    }

    private RuntimeInstance runtime(String runtimeInstanceId, int capacity, int inFlight) {
        return new RuntimeInstance(
                runtimeInstanceId,
                "NODE",
                RuntimeInstanceStatus.AVAILABLE,
                capacity,
                inFlight,
                "/tmp/" + runtimeInstanceId + ".sock"
        );
    }

    private void createSchema() throws SQLException {
        try (
                var connection = dataSource.getConnection();
                var statement = connection.createStatement()
        ) {
            statement.execute("drop table if exists runtime_instances");
            statement.execute("""
                    create table runtime_instances (
                        runtime_instance_id varchar(150) primary key,
                        runtime_type varchar(100) not null,
                        status varchar(100) not null,
                        capacity integer not null,
                        in_flight integer not null default 0,
                        socket_path varchar(2048) not null,
                        registered_at timestamp with time zone not null default current_timestamp,
                        updated_at timestamp with time zone not null default current_timestamp
                    )
                    """);
        }
    }
}
