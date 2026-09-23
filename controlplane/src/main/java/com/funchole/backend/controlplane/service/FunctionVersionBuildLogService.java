package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.entity.FunctionVersionBuildLog;
import com.funchole.backend.controlplane.functionbuild.BuildLogRecorder;
import com.funchole.backend.controlplane.functionbuild.process.ProcessResult;
import com.funchole.backend.controlplane.repository.FunctionVersionBuildLogRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable storage for build-stage diagnostics (F248/F250) - each stage a
 * {@code RuntimeBuilder} runs (dependency install, bundling) is written here
 * as it completes, success or failure, so a build failure remains
 * inspectable after the deploy response itself is gone. {@link #recorderFor}
 * adapts this service to the transport-neutral {@link BuildLogRecorder}
 * {@code RuntimeBuilder} implementations already call, one row per stage,
 * committed independently of the overall deploy attempt's own outcome.
 */
@Service
public class FunctionVersionBuildLogService {

    private final FunctionVersionBuildLogRepository repository;

    public FunctionVersionBuildLogService(FunctionVersionBuildLogRepository repository) {
        this.repository = repository;
    }

    public BuildLogRecorder recorderFor(UUID functionVersionId) {
        return (stage, command, result) -> record(functionVersionId, stage, command, result);
    }

    @Transactional
    public void record(UUID functionVersionId, String stage, List<String> command, ProcessResult result) {
        repository.save(FunctionVersionBuildLog.create(
                functionVersionId, stage, command, result.exitCode(), result.succeeded(), result.timedOut(),
                result.stdout(), result.stderr()));
    }

    @Transactional(readOnly = true)
    public List<FunctionVersionBuildLog> listLogs(UUID functionVersionId) {
        return repository.findAllByFunctionVersionIdOrderByCreatedAtAsc(functionVersionId);
    }
}
