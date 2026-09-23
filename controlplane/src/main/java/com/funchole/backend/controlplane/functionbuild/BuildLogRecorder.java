package com.funchole.backend.controlplane.functionbuild;

import com.funchole.backend.controlplane.functionbuild.process.ProcessResult;
import java.util.List;

/**
 * Receives one durable log entry per build stage (e.g.
 * {@code dependency-install}, {@code build}) as a {@link RuntimeBuilder}
 * runs it, whether that stage succeeds or fails - called before any
 * failure is thrown, so a failing stage is always recorded too. Kept
 * separate from {@link BuildWorkspace} (which stays a runtime-neutral,
 * immutable description of what to build, not a mutable sink for what
 * happened while building it).
 */
public interface BuildLogRecorder {

    BuildLogRecorder NOOP = (stage, command, result) -> { };

    void record(String stage, List<String> command, ProcessResult result);
}
