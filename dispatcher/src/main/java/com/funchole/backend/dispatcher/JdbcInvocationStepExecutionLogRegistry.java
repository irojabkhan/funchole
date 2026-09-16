package com.funchole.backend.dispatcher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcInvocationStepExecutionLogRegistry implements InvocationStepExecutionLogRegistry {

    private final DataSource dataSource;

    public JdbcInvocationStepExecutionLogRegistry(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void append(UUID invocationStepExecutionId, String stream, String message) {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        insert into invocation_step_execution_logs (id, invocation_step_execution_id, stream, message)
                        values (?, ?, ?, ?)
                        """)
        ) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, invocationStepExecutionId);
            statement.setString(3, stream);
            statement.setString(4, message);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to append step execution log for " + invocationStepExecutionId, exception);
        }
    }

    @Override
    public List<InvocationStepExecutionLog> findAllByStepExecutionId(UUID invocationStepExecutionId) {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        select id, invocation_step_execution_id, stream, message, created_at
                        from invocation_step_execution_logs
                        where invocation_step_execution_id = ?
                        order by created_at asc, id asc
                        """)
        ) {
            statement.setObject(1, invocationStepExecutionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<InvocationStepExecutionLog> logs = new ArrayList<>();
                while (resultSet.next()) {
                    logs.add(new InvocationStepExecutionLog(
                            resultSet.getObject("id", UUID.class),
                            resultSet.getObject("invocation_step_execution_id", UUID.class),
                            resultSet.getString("stream"),
                            resultSet.getString("message"),
                            resultSet.getObject("created_at", OffsetDateTime.class)
                    ));
                }
                return logs;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to find step execution logs for " + invocationStepExecutionId, exception);
        }
    }
}
