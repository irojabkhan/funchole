package com.funchole.backend.invocationcontract;

import java.util.UUID;

public record FlowInvocationResult(
        UUID invocationId,
        UUID flowVersionId,
        String initialStatus
) {
}
