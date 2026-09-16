package com.funchole.backend.dispatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JdbcInvocationStepExecutionLogRegistryTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("funchole")
            .withUsername("funchole")
            .withPassword("funchole");

    private JdbcInvocationStepExecutionLogRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = dataSource();
        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()
        ) {
            statement.execute("drop table if exists invocation_step_execution_logs");
            statement.execute("""
                    create table invocation_step_execution_logs (
                        id UUID primary key,
                        invocation_step_execution_id UUID not null,
                        stream VARCHAR(16) not null,
                        message TEXT not null,
                        created_at TIMESTAMP WITH TIME ZONE not null default CURRENT_TIMESTAMP
                    )
                    """);
        }
        registry = new JdbcInvocationStepExecutionLogRegistry(dataSource);
    }

    @Test
    void appendsAndFindsLogsInInsertionOrder() {
        UUID stepExecutionId = UUID.randomUUID();

        registry.append(stepExecutionId, "stdout", "first line");
        registry.append(stepExecutionId, "stderr", "second line");
        registry.append(stepExecutionId, "stdout", "third line");

        List<InvocationStepExecutionLog> logs = registry.findAllByStepExecutionId(stepExecutionId);

        assertEquals(3, logs.size());
        assertEquals("stdout", logs.get(0).stream());
        assertEquals("first line", logs.get(0).message());
        assertEquals("stderr", logs.get(1).stream());
        assertEquals("second line", logs.get(1).message());
        assertEquals("stdout", logs.get(2).stream());
        assertEquals("third line", logs.get(2).message());
        assertTrue(logs.stream().allMatch(log -> log.invocationStepExecutionId().equals(stepExecutionId)));
    }

    @Test
    void findingLogsForAStepExecutionWithNoneReturnsAnEmptyList() {
        List<InvocationStepExecutionLog> logs = registry.findAllByStepExecutionId(UUID.randomUUID());

        assertTrue(logs.isEmpty());
    }

    @Test
    void logsAreScopedToTheirOwnStepExecution() {
        UUID stepExecutionA = UUID.randomUUID();
        UUID stepExecutionB = UUID.randomUUID();
        registry.append(stepExecutionA, "stdout", "for A");
        registry.append(stepExecutionB, "stdout", "for B");

        List<InvocationStepExecutionLog> logsForA = registry.findAllByStepExecutionId(stepExecutionA);

        assertEquals(1, logsForA.size());
        assertEquals("for A", logsForA.get(0).message());
    }

    private DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }
}
