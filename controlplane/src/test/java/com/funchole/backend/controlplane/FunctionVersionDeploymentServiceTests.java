package com.funchole.backend.controlplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.funchole.backend.artifact.ArtifactPublisher;
import com.funchole.backend.artifact.PublishedArtifact;
import com.funchole.backend.controlplane.constant.FunctionVersionStatus;
import com.funchole.backend.controlplane.entity.AppUser;
import com.funchole.backend.controlplane.entity.Function;
import com.funchole.backend.controlplane.entity.FunctionVersion;
import com.funchole.backend.controlplane.entity.SourceBundle;
import com.funchole.backend.controlplane.entity.SourceFile;
import com.funchole.backend.controlplane.functionbuild.BuildWorkspace;
import com.funchole.backend.controlplane.functionbuild.BuildWorkspaceService;
import com.funchole.backend.controlplane.functionbuild.FunctionBuildExecutor;
import com.funchole.backend.controlplane.functionbuild.PreparedArtifact;
import com.funchole.backend.controlplane.functionbuild.RuntimeBuilder;
import com.funchole.backend.controlplane.functionbuild.RuntimeBuilderRegistry;
import com.funchole.backend.controlplane.repository.AppUserRepository;
import com.funchole.backend.controlplane.repository.FunctionRepository;
import com.funchole.backend.controlplane.repository.FunctionVersionRepository;
import com.funchole.backend.controlplane.service.FunctionVersionArtifactRegistry;
import com.funchole.backend.controlplane.service.FunctionVersionBuildLogService;
import com.funchole.backend.controlplane.service.FunctionVersionDeploymentFinalizer;
import com.funchole.backend.controlplane.service.FunctionVersionDeploymentService;
import com.funchole.backend.controlplane.service.FunctionVersionLifecycleRegistry;
import com.funchole.backend.controlplane.service.FunctionVersionSourceService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class FunctionVersionDeploymentServiceTests {

    private static final String SHA256_A = "a".repeat(64);
    private static final long SIZE_A = 1024L;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withDatabaseName("funchole")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private FunctionRepository functionRepository;

    @Autowired
    private FunctionVersionRepository functionVersionRepository;

    @Autowired
    private FunctionVersionSourceService sourceService;

    @Autowired
    private BuildWorkspaceService buildWorkspaceService;

    @Autowired
    private FunctionVersionArtifactRegistry artifactRegistry;

    @Autowired
    private FunctionVersionDeploymentFinalizer deploymentFinalizer;

    @Autowired
    private FunctionVersionLifecycleRegistry lifecycleRegistry;

    @Autowired
    private FunctionVersionBuildLogService buildLogService;

    @Test
    void newFunctionVersionStartsDraft() {
        FunctionVersion functionVersion = createFunctionVersion();

        assertThat(functionVersion.getStatus()).isEqualTo(FunctionVersionStatus.DRAFT);
        assertThat(functionVersionRepository.findById(functionVersion.getId()).orElseThrow().getStatus())
                .isEqualTo(FunctionVersionStatus.DRAFT);
    }

    @Test
    void deploymentSetsPublishingBeforePublisherIsInvoked() {
        FunctionVersion functionVersion = createFunctionVersion();
        String objectKey = FunctionVersionArtifactRegistry.artifactObjectKey(functionVersion.getId());
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.capturingStatusAndReturning(
                () -> functionVersionRepository.findById(functionVersion.getId()).orElseThrow().getStatus(),
                new PublishedArtifact(functionVersion.getId(), objectKey, SHA256_A, SIZE_A));

        service(publisher).deploy(functionVersion.getId());

        assertThat(publisher.statusDuringPublish()).isEqualTo(FunctionVersionStatus.PUBLISHING);
    }

    @Test
    void successfulDeploymentEndsReadyAndPersistsMetadata() {
        FunctionVersion functionVersion = createFunctionVersion();
        String objectKey = FunctionVersionArtifactRegistry.artifactObjectKey(functionVersion.getId());
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.returning(
                new PublishedArtifact(functionVersion.getId(), objectKey, SHA256_A, SIZE_A));

        // Not asserting on deploy()'s own return value here: with the
        // direct/same-thread executor this suite uses, the pipeline shares
        // this test's single transaction, so Hibernate's identity map hands
        // back the very same managed entity later mutated to READY - an
        // artifact of that shared-transaction test setup, not something a
        // real (different-thread) caller could rely on. The persisted state
        // below is what actually matters.
        service(publisher).deploy(functionVersion.getId());

        assertThat(publisher.invocationCount()).isEqualTo(1);
        FunctionVersion retrieved = functionVersionRepository.findById(functionVersion.getId()).orElseThrow();
        assertThat(retrieved.getStatus()).isEqualTo(FunctionVersionStatus.READY);
        assertThat(retrieved.getArtifactMetadata()).hasValueSatisfying(metadata -> {
            assertThat(metadata.objectKey()).isEqualTo(objectKey);
            assertThat(metadata.sha256()).isEqualTo(SHA256_A);
            assertThat(metadata.sizeBytes()).isEqualTo(SIZE_A);
        });
    }

    @Test
    void persistedMetadataComesFromThePublishedArtifact() {
        FunctionVersion functionVersion = createFunctionVersion();
        String objectKey = FunctionVersionArtifactRegistry.artifactObjectKey(functionVersion.getId());
        String sha256 = "c".repeat(64);
        long size = 777L;
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.returning(
                new PublishedArtifact(functionVersion.getId(), objectKey, sha256, size));

        service(publisher).deploy(functionVersion.getId());

        FunctionVersion retrieved = functionVersionRepository.findById(functionVersion.getId()).orElseThrow();
        assertThat(retrieved.getArtifactObjectKey()).isEqualTo(objectKey);
        assertThat(retrieved.getArtifactSha256()).isEqualTo(sha256);
        assertThat(retrieved.getArtifactSizeBytes()).isEqualTo(size);
    }

    @Test
    void publisherFailureEndsFailedAndLeavesFunctionVersionWithoutArtifactMetadata() {
        FunctionVersion functionVersion = createFunctionVersion();
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.throwing(
                new IllegalStateException("simulated upload failure"));

        // The pipeline runs asynchronously now, so this failure is caught
        // and logged inside it rather than thrown back to this caller (see
        // FunctionVersionDeploymentService.runBuildPipeline) - only its
        // effect on persisted state is observable here.
        service(publisher).deploy(functionVersion.getId());

        FunctionVersion retrieved = functionVersionRepository.findById(functionVersion.getId()).orElseThrow();
        assertThat(retrieved.getStatus()).isEqualTo(FunctionVersionStatus.FAILED);
        assertThat(retrieved.getArtifactMetadata()).isEmpty();
        // publish() itself failed - there is no PublishedArtifact to compensate for.
        assertThat(publisher.deletionCount()).isZero();
    }

    @Test
    void artifactMetadataPersistenceFailureEndsFailed() {
        FunctionVersion functionVersion = createFunctionVersion();
        String objectKey = FunctionVersionArtifactRegistry.artifactObjectKey(functionVersion.getId());
        // An invalid sha256 makes FunctionVersionArtifactRegistry.attachPublishedArtifact
        // itself reject the write - a registration/persistence failure, not a publish failure.
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.returning(
                new PublishedArtifact(functionVersion.getId(), objectKey, "not-a-valid-sha256", SIZE_A));

        service(publisher).deploy(functionVersion.getId());

        FunctionVersion retrieved = functionVersionRepository.findById(functionVersion.getId()).orElseThrow();
        assertThat(retrieved.getStatus()).isEqualTo(FunctionVersionStatus.FAILED);
        assertThat(retrieved.getArtifactMetadata()).isEmpty();
        // publish() succeeded first - the now-orphaned remote artifact must be compensated for.
        assertThat(publisher.deletionCount()).isEqualTo(1);
        assertThat(publisher.lastDeletedObjectKey()).isEqualTo(objectKey);
    }

    @Test
    void successfulDeploymentDoesNotInvokeCompensation() {
        FunctionVersion functionVersion = createFunctionVersion();
        String objectKey = FunctionVersionArtifactRegistry.artifactObjectKey(functionVersion.getId());
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.returning(
                new PublishedArtifact(functionVersion.getId(), objectKey, SHA256_A, SIZE_A));

        service(publisher).deploy(functionVersion.getId());

        assertThat(publisher.deletionCount()).isZero();
    }

    @Test
    void readyTransitionFailureRemovesThePublishedArtifact() {
        FunctionVersion functionVersion = createFunctionVersion();
        String objectKey = FunctionVersionArtifactRegistry.artifactObjectKey(functionVersion.getId());
        // Forces the version out of PUBLISHING (via a genuine markReady call)
        // before publish() returns, so the deployment service's OWN later
        // markReady call - after a successful publish and attach - is the one
        // that fails, exactly reproducing a post-publish READY-transition failure.
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.returningAfterRunning(
                () -> lifecycleRegistry.markReady(functionVersion.getId()),
                new PublishedArtifact(functionVersion.getId(), objectKey, SHA256_A, SIZE_A));

        service(publisher).deploy(functionVersion.getId());

        assertThat(publisher.deletionCount()).isEqualTo(1);
        assertThat(publisher.lastDeletedObjectKey()).isEqualTo(objectKey);
        // Atomic finalization invariant: a failed READY transition rolls back
        // the metadata write in the same transaction, so the database never
        // points at the erased remote object.
        assertThat(functionVersionRepository.findById(functionVersion.getId()).orElseThrow().getArtifactMetadata())
                .isEmpty();
    }

    @Test
    void cleanupFailureDoesNotPreventFailedStatus() {
        FunctionVersion functionVersion = createFunctionVersion();
        String objectKey = FunctionVersionArtifactRegistry.artifactObjectKey(functionVersion.getId());
        RuntimeException deleteFailure = new IllegalStateException("simulated delete failure");
        // Same invalid-sha256 trick as artifactMetadataPersistenceFailureEndsFailed:
        // publish() succeeds, attachPublishedArtifact rejects the write.
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.returning(
                new PublishedArtifact(functionVersion.getId(), objectKey, "not-a-valid-sha256", SIZE_A))
                .withDeleteFailure(deleteFailure);

        // The original failure and this compensating delete failure are both
        // caught inside the async pipeline (the original with the delete
        // failure suppressed onto it, exactly as before) and only logged -
        // neither is thrown back to this caller anymore, so what remains
        // observable here is the resulting persisted state.
        service(publisher).deploy(functionVersion.getId());

        assertThat(publisher.deletionCount()).isEqualTo(1);
        assertThat(functionVersionRepository.findById(functionVersion.getId()).orElseThrow().getStatus())
                .isEqualTo(FunctionVersionStatus.FAILED);
    }

    @Test
    void publishFailureAfterForcedReadyLeavesVersionReady() {
        FunctionVersion functionVersion = createFunctionVersion();
        IllegalStateException originalFailure = new IllegalStateException("simulated publish failure");
        // Moves the version out of PUBLISHING before throwing, so the deployment
        // service's own markFailed(...) call (which requires PUBLISHING) fails too.
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.throwingAfter(
                () -> lifecycleRegistry.markReady(functionVersion.getId()), originalFailure);

        // Both the original publish failure and the subsequent markFailed
        // failure are caught and only logged by the async pipeline, so the
        // version is simply left at whatever the forced markReady set it to.
        service(publisher).deploy(functionVersion.getId());

        assertThat(functionVersionRepository.findById(functionVersion.getId()).orElseThrow().getStatus())
                .isEqualTo(FunctionVersionStatus.READY);
    }

    @Test
    void readyVersionCannotDeployAgain() {
        FunctionVersion functionVersion = createFunctionVersion();
        String objectKey = FunctionVersionArtifactRegistry.artifactObjectKey(functionVersion.getId());
        service(RecordingArtifactPublisher.returning(new PublishedArtifact(functionVersion.getId(), objectKey, SHA256_A, SIZE_A)))
                .deploy(functionVersion.getId());
        RecordingArtifactPublisher secondAttemptPublisher = RecordingArtifactPublisher.returning(null);
        RecordingRuntimeBuilder secondAttemptBuilder = RecordingRuntimeBuilder.supporting("NODE");

        assertThatThrownBy(() -> service(secondAttemptPublisher, secondAttemptBuilder).deploy(functionVersion.getId()))
                .isInstanceOf(IllegalStateException.class);

        // Rejected before build or publish is ever attempted.
        assertThat(secondAttemptBuilder.invocationCount()).isZero();
        assertThat(secondAttemptPublisher.invocationCount()).isZero();
        assertThat(functionVersionRepository.findById(functionVersion.getId()).orElseThrow().getStatus())
                .isEqualTo(FunctionVersionStatus.READY);
    }

    @Test
    void publishingVersionCannotDeployAgain() {
        FunctionVersion functionVersion = createFunctionVersion();
        lifecycleRegistry.beginPublishing(functionVersion.getId());
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.returning(null);

        assertThatThrownBy(() -> service(publisher).deploy(functionVersion.getId()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(publisher.invocationCount()).isZero();
        assertThat(functionVersionRepository.findById(functionVersion.getId()).orElseThrow().getStatus())
                .isEqualTo(FunctionVersionStatus.PUBLISHING);
    }

    @Test
    void artifactMetadataRemainsImmutableAcrossARejectedSecondAttempt() {
        FunctionVersion functionVersion = createFunctionVersion();
        String objectKey = FunctionVersionArtifactRegistry.artifactObjectKey(functionVersion.getId());
        service(RecordingArtifactPublisher.returning(new PublishedArtifact(functionVersion.getId(), objectKey, SHA256_A, SIZE_A)))
                .deploy(functionVersion.getId());

        assertThatThrownBy(() -> service(RecordingArtifactPublisher.returning(
                new PublishedArtifact(functionVersion.getId(), objectKey, "b".repeat(64), 2048L)))
                .deploy(functionVersion.getId()))
                .isInstanceOf(IllegalStateException.class);

        FunctionVersion retrieved = functionVersionRepository.findById(functionVersion.getId()).orElseThrow();
        assertThat(retrieved.getArtifactSha256()).isEqualTo(SHA256_A);
        assertThat(retrieved.getArtifactSizeBytes()).isEqualTo(SIZE_A);
    }

    @Test
    void mismatchedPublishedArtifactVersionIdIsRejectedAndEndsFailed() {
        FunctionVersion functionVersion = createFunctionVersion();
        UUID differentVersionId = UUID.randomUUID();
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.returning(new PublishedArtifact(
                differentVersionId,
                FunctionVersionArtifactRegistry.artifactObjectKey(differentVersionId),
                SHA256_A,
                SIZE_A
        ));

        service(publisher).deploy(functionVersion.getId());

        FunctionVersion retrieved = functionVersionRepository.findById(functionVersion.getId()).orElseThrow();
        assertThat(retrieved.getStatus()).isEqualTo(FunctionVersionStatus.FAILED);
        assertThat(retrieved.getArtifactMetadata()).isEmpty();
    }

    @Test
    void differentFunctionVersionsDeployAndMaintainIndependentStates() {
        FunctionVersion versionOne = createFunctionVersion();
        FunctionVersion versionTwo = createFunctionVersion();
        String objectKeyOne = FunctionVersionArtifactRegistry.artifactObjectKey(versionOne.getId());

        service(RecordingArtifactPublisher.returning(new PublishedArtifact(versionOne.getId(), objectKeyOne, SHA256_A, SIZE_A)))
                .deploy(versionOne.getId());
        service(RecordingArtifactPublisher.throwing(new IllegalStateException("boom")))
                .deploy(versionTwo.getId());

        assertThat(functionVersionRepository.findById(versionOne.getId()).orElseThrow().getStatus())
                .isEqualTo(FunctionVersionStatus.READY);
        assertThat(functionVersionRepository.findById(versionOne.getId()).orElseThrow().getArtifactSha256())
                .isEqualTo(SHA256_A);
        assertThat(functionVersionRepository.findById(versionTwo.getId()).orElseThrow().getStatus())
                .isEqualTo(FunctionVersionStatus.FAILED);
        assertThat(functionVersionRepository.findById(versionTwo.getId()).orElseThrow().getArtifactMetadata())
                .isEmpty();
    }

    @Test
    void buildWorkspaceIsCreatedFromTheExactFunctionVersionSource() {
        FunctionVersion functionVersion = createFunctionVersion();
        sourceService.submitSource(functionVersion.getId(), new SourceBundle("NODE", "20", "src/index.js", "handler", List.of(
                new SourceFile("src/index.js", "entry"),
                new SourceFile("lib/util.js", "util")
        )));
        RecordingRuntimeBuilder builder = RecordingRuntimeBuilder.supporting("NODE");
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.returning(new PublishedArtifact(
                functionVersion.getId(), FunctionVersionArtifactRegistry.artifactObjectKey(functionVersion.getId()), SHA256_A, SIZE_A));

        service(publisher, builder).deploy(functionVersion.getId());

        assertThat(builder.capturedFiles()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "src/index.js", "entry",
                "lib/util.js", "util"
        ));
        assertThat(builder.capturedEntrypoint()).isEqualTo("src/index.js");
        assertThat(builder.capturedRuntimeType()).isEqualTo("NODE");
        assertThat(builder.capturedRuntimeVersion()).isEqualTo("20");
    }

    @Test
    void correctRuntimeBuilderIsSelectedByFunctionVersionRuntime() {
        FunctionVersion functionVersion = createFunctionVersion();
        RecordingRuntimeBuilder nodeBuilder = RecordingRuntimeBuilder.supporting("NODE");
        RecordingRuntimeBuilder otherBuilder = RecordingRuntimeBuilder.supporting("OTHER");
        RuntimeBuilderRegistry registry = new RuntimeBuilderRegistry(List.of(otherBuilder, nodeBuilder));
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.returning(new PublishedArtifact(
                functionVersion.getId(), FunctionVersionArtifactRegistry.artifactObjectKey(functionVersion.getId()), SHA256_A, SIZE_A));
        FunctionVersionDeploymentService service = new FunctionVersionDeploymentService(
                buildWorkspaceService, registry, publisher, deploymentFinalizer, artifactRegistry, lifecycleRegistry,
                buildLogService, DIRECT_EXECUTOR);

        service.deploy(functionVersion.getId());

        assertThat(nodeBuilder.invocationCount()).isEqualTo(1);
        assertThat(otherBuilder.invocationCount()).isZero();
    }

    @Test
    void preparedArtifactIsPassedToArtifactPublisher() {
        FunctionVersion functionVersion = createFunctionVersion();
        RecordingRuntimeBuilder builder = RecordingRuntimeBuilder.supporting("NODE");
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.returning(new PublishedArtifact(
                functionVersion.getId(), FunctionVersionArtifactRegistry.artifactObjectKey(functionVersion.getId()), SHA256_A, SIZE_A));

        service(publisher, builder).deploy(functionVersion.getId());

        assertThat(publisher.capturedArtifactDirectory()).isEqualTo(builder.lastArtifactDirectory());
    }

    @Test
    void workspaceIsCleanedAfterSuccess() {
        FunctionVersion functionVersion = createFunctionVersion();
        RecordingRuntimeBuilder builder = RecordingRuntimeBuilder.supporting("NODE");
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.returning(new PublishedArtifact(
                functionVersion.getId(), FunctionVersionArtifactRegistry.artifactObjectKey(functionVersion.getId()), SHA256_A, SIZE_A));

        service(publisher, builder).deploy(functionVersion.getId());

        assertThat(Files.exists(builder.receivedWorkspaces().get(0).root())).isFalse();
    }

    @Test
    void preparedArtifactIsCleanedAfterSuccess() {
        FunctionVersion functionVersion = createFunctionVersion();
        RecordingRuntimeBuilder builder = RecordingRuntimeBuilder.supporting("NODE");
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.returning(new PublishedArtifact(
                functionVersion.getId(), FunctionVersionArtifactRegistry.artifactObjectKey(functionVersion.getId()), SHA256_A, SIZE_A));

        service(publisher, builder).deploy(functionVersion.getId());

        assertThat(Files.exists(builder.lastArtifactDirectory())).isFalse();
    }

    @Test
    void buildFailureResultsInFailedAndPublisherIsNeverCalled() {
        FunctionVersion functionVersion = createFunctionVersion();
        RecordingRuntimeBuilder failingBuilder =
                RecordingRuntimeBuilder.throwing("NODE", new IllegalStateException("simulated build failure"));
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.returning(null);

        service(publisher, failingBuilder).deploy(functionVersion.getId());

        assertThat(publisher.invocationCount()).isZero();
        assertThat(functionVersionRepository.findById(functionVersion.getId()).orElseThrow().getStatus())
                .isEqualTo(FunctionVersionStatus.FAILED);
        // The workspace materialized before the failing build call must still be cleaned up.
        assertThat(failingBuilder.receivedWorkspaces()).hasSize(1);
        assertThat(Files.exists(failingBuilder.receivedWorkspaces().get(0).root())).isFalse();
    }

    @Test
    void cleanupHappensWhenPublisherFailsAfterASuccessfulBuild() {
        FunctionVersion functionVersion = createFunctionVersion();
        RecordingRuntimeBuilder builder = RecordingRuntimeBuilder.supporting("NODE");
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.throwing(
                new IllegalStateException("simulated upload failure"));

        service(publisher, builder).deploy(functionVersion.getId());

        assertThat(Files.exists(builder.receivedWorkspaces().get(0).root())).isFalse();
        assertThat(Files.exists(builder.lastArtifactDirectory())).isFalse();
        assertThat(functionVersionRepository.findById(functionVersion.getId()).orElseThrow().getStatus())
                .isEqualTo(FunctionVersionStatus.FAILED);
    }

    @Test
    void unsupportedRuntimeResultsInFailed() {
        FunctionVersion functionVersion = createFunctionVersion("COBOL");
        RuntimeBuilderRegistry registry = new RuntimeBuilderRegistry(List.of(RecordingRuntimeBuilder.supporting("NODE")));
        RecordingArtifactPublisher publisher = RecordingArtifactPublisher.returning(null);
        FunctionVersionDeploymentService service = new FunctionVersionDeploymentService(
                buildWorkspaceService, registry, publisher, deploymentFinalizer, artifactRegistry, lifecycleRegistry,
                buildLogService, DIRECT_EXECUTOR);

        // Runtime resolution now happens inside the async pipeline, so the
        // failure is no longer thrown back to this caller (see
        // FunctionVersionDeploymentService.runBuildPipeline) - only its
        // effect (FAILED status, publisher never invoked) is observable.
        service.deploy(functionVersion.getId());

        assertThat(publisher.invocationCount()).isZero();
        assertThat(functionVersionRepository.findById(functionVersion.getId()).orElseThrow().getStatus())
                .isEqualTo(FunctionVersionStatus.FAILED);
    }

    // deploy() now hands the build/publish pipeline to an Executor and
    // returns immediately (see FunctionVersionDeploymentService); a direct,
    // same-thread Executor keeps the rest of this suite deterministic and
    // able to run inside the class's @Transactional test wrapper (a real
    // background thread would use its own DB connection/transaction and
    // never see this test method's uncommitted rows).
    private static final FunctionBuildExecutor DIRECT_EXECUTOR = Runnable::run;

    private FunctionVersionDeploymentService service(ArtifactPublisher publisher) {
        return service(publisher, RecordingRuntimeBuilder.supporting("NODE"));
    }

    private FunctionVersionDeploymentService service(ArtifactPublisher publisher, RuntimeBuilder runtimeBuilder) {
        return new FunctionVersionDeploymentService(
                buildWorkspaceService, new RuntimeBuilderRegistry(List.of(runtimeBuilder)), publisher,
                deploymentFinalizer, artifactRegistry, lifecycleRegistry, buildLogService, DIRECT_EXECUTOR);
    }

    private FunctionVersion createFunctionVersion() {
        return createFunctionVersion("NODE");
    }

    private FunctionVersion createFunctionVersion(String runtime) {
        AppUser admin = appUserRepository.findByUsername("admin").orElseThrow();
        Function function = functionRepository.save(Function.create(
                admin,
                "fn_test_" + UUID.randomUUID().toString().replace("-", ""),
                "Test Function",
                "created by FunctionVersionDeploymentServiceTests",
                runtime
        ));
        FunctionVersion functionVersion = functionVersionRepository.save(FunctionVersion.create(function, 1, runtime, null));
        sourceService.submitSource(functionVersion.getId(), new SourceBundle(
                runtime, "1", "index.js", "handler", List.of(new SourceFile("index.js", "console.log('hi')"))));
        return functionVersion;
    }

    private static final class RecordingArtifactPublisher implements ArtifactPublisher {
        private final PublishedArtifact result;
        private final RuntimeException failure;
        private final Supplier<FunctionVersionStatus> statusCapture;
        private final Runnable beforeReturnOrThrow;
        private final List<UUID> invocations = new ArrayList<>();
        private final List<UUID> deletions = new ArrayList<>();
        private FunctionVersionStatus capturedStatus;
        private Path capturedArtifactDirectory;
        private String lastDeletedObjectKey;
        private RuntimeException deleteFailure;

        private RecordingArtifactPublisher(
                PublishedArtifact result, RuntimeException failure, Supplier<FunctionVersionStatus> statusCapture, Runnable beforeReturnOrThrow) {
            this.result = result;
            this.failure = failure;
            this.statusCapture = statusCapture;
            this.beforeReturnOrThrow = beforeReturnOrThrow;
        }

        static RecordingArtifactPublisher returning(PublishedArtifact result) {
            return new RecordingArtifactPublisher(result, null, null, null);
        }

        static RecordingArtifactPublisher throwing(RuntimeException failure) {
            return new RecordingArtifactPublisher(null, failure, null, null);
        }

        static RecordingArtifactPublisher throwingAfter(Runnable beforeThrow, RuntimeException failure) {
            return new RecordingArtifactPublisher(null, failure, null, beforeThrow);
        }

        static RecordingArtifactPublisher capturingStatusAndReturning(Supplier<FunctionVersionStatus> statusCapture, PublishedArtifact result) {
            return new RecordingArtifactPublisher(result, null, statusCapture, null);
        }

        /**
         * Publish still succeeds and returns {@code result} normally, but only
         * after {@code sideEffect} has run - used to force a later lifecycle
         * step (e.g. markReady) to fail without publish() itself failing, so a
         * PublishedArtifact genuinely exists by the time that later step does.
         */
        static RecordingArtifactPublisher returningAfterRunning(Runnable sideEffect, PublishedArtifact result) {
            return new RecordingArtifactPublisher(result, null, null, sideEffect);
        }

        RecordingArtifactPublisher withDeleteFailure(RuntimeException failure) {
            this.deleteFailure = failure;
            return this;
        }

        @Override
        public PublishedArtifact publish(UUID componentVersionId, Path preparedArtifactDirectory) {
            invocations.add(componentVersionId);
            capturedArtifactDirectory = preparedArtifactDirectory;
            if (statusCapture != null) {
                capturedStatus = statusCapture.get();
            }
            if (beforeReturnOrThrow != null) {
                beforeReturnOrThrow.run();
            }
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        @Override
        public void delete(UUID componentVersionId, String objectKey) {
            deletions.add(componentVersionId);
            lastDeletedObjectKey = objectKey;
            if (deleteFailure != null) {
                throw deleteFailure;
            }
        }

        int invocationCount() {
            return invocations.size();
        }

        int deletionCount() {
            return deletions.size();
        }

        String lastDeletedObjectKey() {
            return lastDeletedObjectKey;
        }

        FunctionVersionStatus statusDuringPublish() {
            return capturedStatus;
        }

        Path capturedArtifactDirectory() {
            return capturedArtifactDirectory;
        }
    }

    /**
     * Records what {@link BuildWorkspace} it received and produces its own
     * fresh, real temporary artifact directory - distinct from the
     * workspace's - so cleanup-ownership tests can inspect both
     * independently after {@code deploy} returns.
     */
    private static final class RecordingRuntimeBuilder implements RuntimeBuilder {
        private final String runtimeType;
        private final RuntimeException failure;
        private final List<BuildWorkspace> receivedWorkspaces = new ArrayList<>();
        private Path artifactDirectory;
        private Map<String, String> capturedFiles;
        private String capturedEntrypoint;
        private String capturedRuntimeType;
        private String capturedRuntimeVersion;

        private RecordingRuntimeBuilder(String runtimeType, RuntimeException failure) {
            this.runtimeType = runtimeType;
            this.failure = failure;
        }

        static RecordingRuntimeBuilder supporting(String runtimeType) {
            return new RecordingRuntimeBuilder(runtimeType, null);
        }

        static RecordingRuntimeBuilder throwing(String runtimeType, RuntimeException failure) {
            return new RecordingRuntimeBuilder(runtimeType, failure);
        }

        @Override
        public boolean supports(String candidateRuntimeType) {
            return runtimeType.equalsIgnoreCase(candidateRuntimeType);
        }

        @Override
        public PreparedArtifact build(BuildWorkspace workspace, com.funchole.backend.controlplane.functionbuild.BuildLogRecorder logRecorder) {
            receivedWorkspaces.add(workspace);
            // Captured up front - the workspace is closed by the caller as
            // soon as this method returns (or throws), so its content is not
            // safe to inspect afterward.
            capturedEntrypoint = workspace.entrypoint();
            capturedRuntimeType = workspace.runtimeType();
            capturedRuntimeVersion = workspace.runtimeVersion();
            capturedFiles = snapshotFiles(workspace.root());

            if (failure != null) {
                throw failure;
            }

            try {
                artifactDirectory = Files.createTempDirectory("test-prepared-artifact-");
                Path entrypointPath = artifactDirectory.resolve(workspace.entrypoint());
                Files.createDirectories(entrypointPath.getParent());
                Files.writeString(entrypointPath, "prepared");
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return new PreparedArtifact(
                    workspace.functionVersionId(), artifactDirectory, workspace.entrypoint(), workspace.handler(),
                    workspace.runtimeType(), workspace.runtimeVersion());
        }

        private Map<String, String> snapshotFiles(Path root) {
            try (var paths = Files.walk(root)) {
                Map<String, String> snapshot = new LinkedHashMap<>();
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    snapshot.put(root.relativize(path).toString(), Files.readString(path));
                }
                return snapshot;
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        int invocationCount() {
            return receivedWorkspaces.size();
        }

        List<BuildWorkspace> receivedWorkspaces() {
            return receivedWorkspaces;
        }

        Path lastArtifactDirectory() {
            return artifactDirectory;
        }

        Map<String, String> capturedFiles() {
            return capturedFiles;
        }

        String capturedEntrypoint() {
            return capturedEntrypoint;
        }

        String capturedRuntimeType() {
            return capturedRuntimeType;
        }

        String capturedRuntimeVersion() {
            return capturedRuntimeVersion;
        }
    }
}
