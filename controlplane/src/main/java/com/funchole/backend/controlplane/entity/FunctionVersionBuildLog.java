package com.funchole.backend.controlplane.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One build stage's durable diagnostics (e.g. {@code dependency-install},
 * {@code build}) for an exact FunctionVersion's deploy attempt - the actual
 * command, exit code, and full stdout/stderr, written immediately as each
 * stage completes (success or failure) so a build failure remains
 * inspectable after the deploy response itself is long gone. Closes F248/F250:
 * before this, build output only ever existed transiently in the deploy
 * response body for whoever happened to be watching at the time.
 */
@Entity
@Table(name = "function_version_build_logs")
public class FunctionVersionBuildLog {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "function_version_id", nullable = false)
    private UUID functionVersionId;

    @Column(name = "stage", nullable = false, length = 64)
    private String stage;

    @Column(name = "command", nullable = false)
    private String command;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "succeeded", nullable = false)
    private boolean succeeded;

    @Column(name = "timed_out", nullable = false)
    private boolean timedOut;

    @Column(name = "stdout")
    private String stdout;

    @Column(name = "stderr")
    private String stderr;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public UUID getFunctionVersionId() {
        return functionVersionId;
    }

    public String getStage() {
        return stage;
    }

    public String getCommand() {
        return command;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public static FunctionVersionBuildLog create(
            UUID functionVersionId,
            String stage,
            List<String> command,
            Integer exitCode,
            boolean succeeded,
            boolean timedOut,
            String stdout,
            String stderr
    ) {
        FunctionVersionBuildLog log = new FunctionVersionBuildLog();
        log.id = UUID.randomUUID();
        log.functionVersionId = functionVersionId;
        log.stage = stage;
        log.command = String.join(" ", command);
        log.exitCode = exitCode;
        log.succeeded = succeeded;
        log.timedOut = timedOut;
        log.stdout = stdout;
        log.stderr = stderr;
        log.createdAt = OffsetDateTime.now();
        return log;
    }
}
