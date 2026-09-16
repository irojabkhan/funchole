package com.funchole.backend.dispatcher;

import com.funchole.backend.invocation.InvocationInspection;
import com.funchole.backend.invocation.InvocationInspectionService;
import com.funchole.backend.invocationcontract.InvocationInspectionHandoff;
import com.funchole.backend.invocationcontract.InvocationInspectionResult;
import com.funchole.backend.invocationcontract.InvocationStepInspectionResult;
import com.funchole.backend.invocationcontract.InvocationStepLogEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Concrete implementation of {@code InvocationInspectionHandoff}, combining
 * the invocation module's own {@link InvocationInspectionService} (durable
 * Invocation state) with this module's {@link InvocationStepExecutionRegistry}
 * (durable step-level execution state) into one read-only view. Lives here,
 * not in {@code invocation}, precisely because step-level detail is
 * dispatcher-owned data - the invocation module must never depend on
 * dispatcher (see its own forbidden-package-prefix test).
 *
 * <p>{@link InvocationInspectionService#inspect} throws when the Invocation
 * does not exist; this adapter translates that into {@link Optional#empty()}
 * at the boundary, since "not found" is a legitimate, expected outcome for a
 * read-only inspection call, not a boundary failure.
 */
final class InvocationRegistryInvocationInspectionHandoff implements InvocationInspectionHandoff {

    private final InvocationInspectionService invocationInspectionService;
    private final InvocationStepExecutionRegistry invocationStepExecutionRegistry;
    private final InvocationStepExecutionLogRegistry invocationStepExecutionLogRegistry;

    InvocationRegistryInvocationInspectionHandoff(
            InvocationInspectionService invocationInspectionService,
            InvocationStepExecutionRegistry invocationStepExecutionRegistry,
            InvocationStepExecutionLogRegistry invocationStepExecutionLogRegistry
    ) {
        this.invocationInspectionService = invocationInspectionService;
        this.invocationStepExecutionRegistry = invocationStepExecutionRegistry;
        this.invocationStepExecutionLogRegistry = invocationStepExecutionLogRegistry;
    }

    @Override
    public Optional<InvocationInspectionResult> inspect(UUID invocationId) {
        InvocationInspection inspection;
        try {
            inspection = invocationInspectionService.inspect(invocationId);
        } catch (IllegalStateException notFound) {
            return Optional.empty();
        }

        List<InvocationStepInspectionResult> steps = invocationStepExecutionRegistry
                .findAllByInvocationId(invocationId)
                .stream()
                .map(this::toStepResult)
                .toList();

        return Optional.of(new InvocationInspectionResult(
                inspection.invocationId(),
                inspection.status().name(),
                inspection.flowId(),
                inspection.flowKey(),
                inspection.flowVersionId(),
                inspection.functionVersionId(),
                inspection.inputPayload(),
                inspection.result(),
                inspection.error(),
                inspection.createdAt(),
                inspection.updatedAt(),
                inspection.completedAt(),
                steps
        ));
    }

    private InvocationStepInspectionResult toStepResult(InvocationStepExecution execution) {
        List<InvocationStepLogEntry> logs = invocationStepExecutionLogRegistry
                .findAllByStepExecutionId(execution.id())
                .stream()
                .map(log -> new InvocationStepLogEntry(log.stream(), log.message(), log.createdAt()))
                .toList();
        return new InvocationStepInspectionResult(
                execution.stepId(),
                execution.position(),
                execution.componentType(),
                execution.componentId(),
                execution.componentVersionId(),
                execution.status().name(),
                execution.attempt(),
                execution.result(),
                execution.error(),
                execution.createdAt(),
                execution.updatedAt(),
                execution.startedAt(),
                execution.completedAt(),
                logs
        );
    }
}
