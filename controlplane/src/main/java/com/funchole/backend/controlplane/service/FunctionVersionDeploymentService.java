package com.funchole.backend.controlplane.service;

import com.funchole.backend.artifact.ArtifactPublisher;
import com.funchole.backend.artifact.PublishedArtifact;
import com.funchole.backend.controlplane.entity.FunctionVersion;
import com.funchole.backend.controlplane.functionbuild.BuildLogRecorder;
import com.funchole.backend.controlplane.functionbuild.BuildWorkspace;
import com.funchole.backend.controlplane.functionbuild.BuildWorkspaceService;
import com.funchole.backend.controlplane.functionbuild.FunctionBuildExecutor;
import com.funchole.backend.controlplane.functionbuild.PreparedArtifact;
import com.funchole.backend.controlplane.functionbuild.RuntimeBuilder;
import com.funchole.backend.controlplane.functionbuild.RuntimeBuilderRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the full source-to-deployed-artifact pipeline for a
 * FunctionVersion and drives its deployment lifecycle:
 *
 * <pre>
 * DRAFT -&gt; PUBLISHING -&gt; BuildWorkspaceService.prepareWorkspace(...)
 *                     -&gt; RuntimeBuilderRegistry.resolve(...) -&gt; RuntimeBuilder.build(...)
 *                     -&gt; ArtifactPublisher.publish(...)
 *                     -&gt; FunctionVersionArtifactRegistry.attachPublishedArtifact(...)
 *                     -&gt; READY
 *                     \-&gt; (any failure above) -&gt;----------------------------------&gt; FAILED
 * </pre>
 *
 * <p>Only the DRAFT -&gt; PUBLISHING transition (via {@link FunctionVersionLifecycleRegistry#beginPublishing})
 * runs synchronously inside {@link #deploy}; everything from workspace
 * preparation through finalization runs afterward on a small bounded
 * background executor ({@code FunctionBuildExecutorConfig}), since a real
 * build can take minutes. Callers get an immediate PUBLISHING response and
 * poll {@code get_function_version}/{@code get_function_version_build_logs}
 * for progress and outcome.
 *
 * This class holds no build logic itself - it only sequences its
 * collaborators and validates the hand-offs between them: source
 * materialization stays in {@link BuildWorkspaceService}, runtime-specific
 * building stays entirely behind {@link RuntimeBuilder} (selected via
 * {@link RuntimeBuilderRegistry} from the FunctionVersion's own declared
 * runtime - never hardcoded), packaging/checksum/upload stays in
 * {@link ArtifactPublisher}, and the final atomic deployment finalization
 * (artifact metadata persistence + PUBLISHING -&gt; READY transition) stays in
 * {@link FunctionVersionDeploymentFinalizer}.
 *
 * <p>{@link BuildWorkspace} and {@link PreparedArtifact} are both
 * {@code AutoCloseable}; nesting them in try-with-resources guarantees both
 * are cleaned up whether the build/publish sequence succeeds or fails.
 *
 * <p>Once {@link ArtifactPublisher#publish} has returned successfully, the
 * remote artifact exists whether or not the atomic finalization that follows
 * it commits. A failed finalization persists nothing locally, so the
 * published artifact is compensated (deleted, best-effort) with the original
 * finalization failure preserved as the primary cause and any compensation
 * failure added as suppressed. A failure in {@code publish} itself is
 * never compensated - there is no {@code PublishedArtifact} to reference,
 * and the existing orphan-on-write-failure gap documented for the artifact
 * registry remains an accepted limitation. A COMMITTED finalization is
 * terminal: compensation never runs for a successfully finalized READY
 * deployment. */
@Service
public class FunctionVersionDeploymentService {

    private static final Logger logger = LoggerFactory.getLogger(FunctionVersionDeploymentService.class);

    private final BuildWorkspaceService buildWorkspaceService;
    private final RuntimeBuilderRegistry runtimeBuilderRegistry;
    private final ArtifactPublisher artifactPublisher;
    private final FunctionVersionDeploymentFinalizer deploymentFinalizer;
    private final FunctionVersionArtifactRegistry artifactRegistry;
    private final FunctionVersionLifecycleRegistry lifecycleRegistry;
    private final FunctionVersionBuildLogService buildLogService;
    private final FunctionBuildExecutor buildExecutor;

    public FunctionVersionDeploymentService(
            BuildWorkspaceService buildWorkspaceService,
            RuntimeBuilderRegistry runtimeBuilderRegistry,
            ArtifactPublisher artifactPublisher,
            FunctionVersionDeploymentFinalizer deploymentFinalizer,
            FunctionVersionArtifactRegistry artifactRegistry,
            FunctionVersionLifecycleRegistry lifecycleRegistry,
            FunctionVersionBuildLogService buildLogService,
            FunctionBuildExecutor buildExecutor
    ) {
        this.buildWorkspaceService = buildWorkspaceService;
        this.runtimeBuilderRegistry = runtimeBuilderRegistry;
        this.artifactPublisher = artifactPublisher;
        this.deploymentFinalizer = deploymentFinalizer;
        this.artifactRegistry = artifactRegistry;
        this.lifecycleRegistry = lifecycleRegistry;
        this.buildLogService = buildLogService;
        this.buildExecutor = buildExecutor;
    }

    /**
     * Validates and starts deployment, returning as soon as the version is
     * durably PUBLISHING - not once the build has finished. The slow
     * build/publish pipeline ({@link #runBuildPipeline}) is handed to
     * {@link #buildExecutor} and runs in the background; callers must poll
     * {@code get_function_version}/{@code get_function_version_build_logs}
     * to observe its outcome.
     */
    public FunctionVersion deploy(UUID functionVersionId) {
        // Checked before the version ever enters PUBLISHING, so an
        // already-published version is rejected without touching status,
        // build, or publish at all. The finalizer re-checks this inside its
        // own transaction (artifact immutability is enforced at both ends).
        if (artifactRegistry.findArtifactMetadata(functionVersionId).isPresent()) {
            throw new IllegalStateException(
                    "Function version already has a published artifact and cannot be republished: " + functionVersionId);
        }

        // Persisted immediately, in its own transaction, before any build or
        // publish work starts - rejects deployment when already PUBLISHING,
        // READY, or FAILED (FAILED stays terminal: no retry path yet). This
        // is the only part of deployment the caller waits on.
        FunctionVersion functionVersion = lifecycleRegistry.beginPublishing(functionVersionId);

        buildExecutor.execute(() -> runBuildPipeline(functionVersionId, functionVersion));

        return functionVersion;
    }

    /**
     * Runs entirely on {@link #buildExecutor}, after {@link #deploy} has
     * already returned to its caller. Any failure here is caught, logged,
     * and turned into compensation + a FAILED status - never rethrown, since
     * there is no caller left in this call stack to receive it; the failure
     * detail lives in the build logs ({@link FunctionVersionBuildLogService})
     * and the FAILED status itself, both of which are pollable afterward.
     */
    private void runBuildPipeline(UUID functionVersionId, FunctionVersion functionVersion) {
        // Assigned only once publish() has actually returned a value - the
        // signal that a remote artifact now exists and, if anything later
        // fails, needs to be compensated for.
        PublishedArtifact published = null;
        try {
            try (BuildWorkspace workspace = buildWorkspaceService.prepareWorkspace(functionVersionId)) {
                RuntimeBuilder runtimeBuilder = runtimeBuilderRegistry.resolve(functionVersion.getRuntime());
                BuildLogRecorder logRecorder = buildLogService.recorderFor(functionVersionId);

                try (PreparedArtifact preparedArtifact = runtimeBuilder.build(workspace, logRecorder)) {
                    published = artifactPublisher.publish(functionVersionId, preparedArtifact.artifactDirectory());
                }
            }

            // Atomic local finalization: metadata + PUBLISHING -> READY in one
            // transaction. Any failure rolls back all local changes; the catch
            // block below then compensates for the already-published remote
            // artifact and marks the version FAILED.
            deploymentFinalizer.finalizeDeployment(functionVersionId, published);
        } catch (RuntimeException exception) {
            logger.warn("Function version build/publish pipeline failed: functionVersionId={}", functionVersionId, exception);
            if (published != null) {
                try {
                    artifactPublisher.delete(functionVersionId, published.objectKey());
                } catch (RuntimeException deleteException) {
                    // Same pattern as markFailed below: the original deployment
                    // failure is what matters for diagnosis, not a failure to
                    // clean up after it.
                    exception.addSuppressed(deleteException);
                }
            }
            try {
                lifecycleRegistry.markFailed(functionVersionId);
            } catch (RuntimeException markFailedException) {
                exception.addSuppressed(markFailedException);
            }
        }
    }
}
