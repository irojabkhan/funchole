package com.funchole.backend.controlplane.functionbuild.runtime.node;

import com.funchole.backend.controlplane.functionbuild.BuildFailureException;
import java.util.List;
import java.util.UUID;

/**
 * {@link BuildFailureException} for the Node runtime's dependency-install
 * stage - see the parent class for the shared diagnostic contract every
 * {@code RuntimeBuilder}'s build failures render through.
 */
public final class NodeBuildException extends BuildFailureException {

    public NodeBuildException(
            UUID functionVersionId,
            String stage,
            List<String> command,
            Integer exitCode,
            String stdout,
            String stderr,
            boolean timedOut
    ) {
        super(
                buildMessage("Node build", functionVersionId, stage, command, exitCode, timedOut),
                functionVersionId, stage, command, exitCode, stdout, stderr, timedOut
        );
    }
}
