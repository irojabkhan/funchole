package com.funchole.backend.runtimeregistry;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * PostgreSQL-backed Runtime Registry.
 *
 * This keeps the existing {@link RuntimeRegistry} contract but moves runtime
 * capacity state out of Dispatcher heap memory. Multiple Dispatcher processes
 * coordinate reservations through row-level locks.
 */
public final class JdbcRuntimeRegistry implements RuntimeRegistry {

    private final DataSource dataSource;

    public JdbcRuntimeRegistry(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void register(RuntimeInstance runtimeInstance) {
        try (
                Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        insert into runtime_instances (
                            runtime_instance_id, runtime_type, status, capacity, in_flight, socket_path, registered_at, updated_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (runtime_instance_id)
                        do update set
                            runtime_type = excluded.runtime_type,
                            status = excluded.status,
                            capacity = excluded.capacity,
                            in_flight = least(runtime_instances.in_flight, excluded.capacity),
                            socket_path = excluded.socket_path,
                            updated_at = excluded.updated_at
                        """)
        ) {
            OffsetDateTime now = OffsetDateTime.now();
            statement.setString(1, runtimeInstance.runtimeInstanceId());
            statement.setString(2, normalize(runtimeInstance.runtimeType()));
            statement.setString(3, runtimeInstance.status().name());
            statement.setInt(4, runtimeInstance.capacity());
            statement.setInt(5, runtimeInstance.inFlight());
            statement.setString(6, runtimeInstance.socketPath());
            statement.setTimestamp(7, Timestamp.from(now.toInstant()));
            statement.setTimestamp(8, Timestamp.from(now.toInstant()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to register runtime instance " + runtimeInstance.runtimeInstanceId(), exception);
        }
    }

    @Override
    public void unregister(String runtimeInstanceId) {
        try (
                Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement("delete from runtime_instances where runtime_instance_id = ?")
        ) {
            statement.setString(1, runtimeInstanceId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to unregister runtime instance " + runtimeInstanceId, exception);
        }
    }

    @Override
    public RuntimeTarget selectAndReserve(RuntimeRequirement requirement) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<RuntimeInstance> selected = selectAvailableRuntime(connection, normalize(requirement.runtimeType()));
                if (selected.isEmpty()) {
                    boolean anyCompatible = hasCompatibleRuntime(connection, normalize(requirement.runtimeType()));
                    connection.rollback();
                    if (!anyCompatible) {
                        throw new NoRuntimeCapacityException(
                                "No compatible runtime registered for runtime type: " + requirement.runtimeType());
                    }
                    throw new NoRuntimeCapacityException(
                            "No available runtime capacity for runtime type: " + requirement.runtimeType());
                }

                RuntimeInstance runtime = selected.get();
                reserve(connection, runtime.runtimeInstanceId());
                connection.commit();
                return new RuntimeTarget(runtime.runtimeInstanceId(), runtime.runtimeType(), runtime.socketPath());
            } catch (RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to select runtime capacity for " + requirement.runtimeType(), exception);
        }
    }

    @Override
    public void release(String runtimeInstanceId) {
        try (
                Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        update runtime_instances
                        set in_flight = greatest(in_flight - 1, 0),
                            updated_at = ?
                        where runtime_instance_id = ?
                        """)
        ) {
            statement.setTimestamp(1, Timestamp.from(OffsetDateTime.now().toInstant()));
            statement.setString(2, runtimeInstanceId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to release runtime capacity for " + runtimeInstanceId, exception);
        }
    }

    @Override
    public Optional<RuntimeInstance> find(String runtimeInstanceId) {
        try (
                Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select runtime_instance_id, runtime_type, status, capacity, in_flight, socket_path
                        from runtime_instances
                        where runtime_instance_id = ?
                        """)
        ) {
            statement.setString(1, runtimeInstanceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(toRuntimeInstance(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to find runtime instance " + runtimeInstanceId, exception);
        }
    }

    private Optional<RuntimeInstance> selectAvailableRuntime(Connection connection, String runtimeType) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select runtime_instance_id, runtime_type, status, capacity, in_flight, socket_path
                from runtime_instances
                where runtime_type = ?
                  and status = ?
                  and in_flight < capacity
                order by in_flight asc, runtime_instance_id asc
                limit 1
                for update skip locked
                """)) {
            statement.setString(1, runtimeType);
            statement.setString(2, RuntimeInstanceStatus.AVAILABLE.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(toRuntimeInstance(resultSet));
            }
        }
    }

    private boolean hasCompatibleRuntime(Connection connection, String runtimeType) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select 1
                from runtime_instances
                where runtime_type = ?
                limit 1
                """)) {
            statement.setString(1, runtimeType);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void reserve(Connection connection, String runtimeInstanceId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                update runtime_instances
                set in_flight = in_flight + 1,
                    updated_at = ?
                where runtime_instance_id = ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(OffsetDateTime.now().toInstant()));
            statement.setString(2, runtimeInstanceId);
            statement.executeUpdate();
        }
    }

    private RuntimeInstance toRuntimeInstance(ResultSet resultSet) throws SQLException {
        return new RuntimeInstance(
                resultSet.getString("runtime_instance_id"),
                resultSet.getString("runtime_type"),
                RuntimeInstanceStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("capacity"),
                resultSet.getInt("in_flight"),
                resultSet.getString("socket_path")
        );
    }

    private String normalize(String runtimeType) {
        return runtimeType.trim().toUpperCase(Locale.ROOT);
    }
}
