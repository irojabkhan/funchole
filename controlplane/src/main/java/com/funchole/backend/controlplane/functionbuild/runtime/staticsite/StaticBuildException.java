package com.funchole.backend.controlplane.functionbuild.runtime.staticsite;

import com.funchole.backend.controlplane.functionbuild.BuildFailureException;
import java.util.List;
import java.util.UUID;

/**
 * {@link BuildFailureException} for the STATIC runtime's dependency-install
 * or build-script stage - see the parent class for the shared diagnostic
 * contract every {@code RuntimeBuilder}'s build failures render through.
 */
public final class StaticBuildException extends BuildFailureException {

    public StaticBuildException(
            UUID functionVersionId,
            String stage,
            List<String> command,
            Integer exitCode,
            String stdout,
            String stderr,
            boolean timedOut
    ) {
        super(
                buildMessage("Static build", functionVersionId, stage, command, exitCode, timedOut),
                functionVersionId, stage, command, exitCode, stdout, stderr, timedOut
        );
    }
}
