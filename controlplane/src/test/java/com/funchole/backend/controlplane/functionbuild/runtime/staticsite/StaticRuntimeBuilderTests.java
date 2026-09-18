package com.funchole.backend.controlplane.functionbuild.runtime.staticsite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.funchole.backend.artifact.ArtifactManifest;
import com.funchole.backend.controlplane.functionbuild.BuildWorkspace;
import com.funchole.backend.controlplane.functionbuild.PreparedArtifact;
import com.funchole.backend.controlplane.functionbuild.process.ProcessExecutor;
import com.funchole.backend.controlplane.functionbuild.process.ProcessResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Plain unit tests, mirroring {@code NodeRuntimeBuilderTests}: depends only
 * on {@link ProcessExecutor}, so a hand-written fake is enough - no real
 * npm, no network access. The fake also simulates each command's real-world
 * side effect (writing build output to disk) since a canned
 * {@link ProcessResult} alone doesn't materialize any files.
 */
class StaticRuntimeBuilderTests {

    @TempDir
    Path tempDir;

    private final FakeProcessExecutor processExecutor = new FakeProcessExecutor();
    private final StaticRuntimeBuilder builder = new StaticRuntimeBuilder(processExecutor);

    @Test
    void missingPackageJsonThrowsBeforeInvokingNpm() {
        BuildWorkspace withoutPackageJson = workspaceWithFiles("README.md", "hello");

        assertThatThrownBy(() -> builder.build(withoutPackageJson))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("package.json");
        assertThat(processExecutor.invocationCount()).isZero();
    }

    @Test
    void installsThenRunsBuildScriptAndPromotesDistToArtifactRoot() {
        BuildWorkspace workspace = workspaceWithFiles(
                "package.json", "{\"scripts\":{\"build\":\"vite build\"}}",
                "package-lock.json", "{}",
                "src/main.tsx", "console.log('app')"
        );
        processExecutor.nextResult(new ProcessResult(0, "installed", "", false));
        processExecutor.nextResult(new ProcessResult(0, "built", "", false), workingDirectory -> writeFile(
                workingDirectory.resolve("dist/index.html"), "<html>built</html>"));

        try (PreparedArtifact artifact = builder.build(workspace)) {
            assertThat(processExecutor.commandAt(0)).isEqualTo(List.of("npm", "ci"));
            assertThat(processExecutor.commandAt(1)).isEqualTo(List.of("npm", "run", "build"));
            assertThat(artifact.entrypoint()).isEqualTo("index.html");
            assertThat(readString(artifact.artifactDirectory().resolve("index.html"))).isEqualTo("<html>built</html>");

            ArtifactManifest manifest = ArtifactManifest.readOrDefault(artifact.artifactDirectory());
            assertThat(manifest.entrypoint()).isEqualTo("index.html");
        }
    }

    @Test
    void noLockfileSelectsNpmInstall() {
        BuildWorkspace workspace = workspaceWithFiles("package.json", "{}");
        processExecutor.nextResult(new ProcessResult(0, "", "", false));
        processExecutor.nextResult(new ProcessResult(0, "", "", false), workingDirectory -> writeFile(
                workingDirectory.resolve("dist/index.html"), "<html/>"));

        try (PreparedArtifact artifact = builder.build(workspace)) {
            assertThat(processExecutor.commandAt(0)).isEqualTo(List.of("npm", "install"));
        }
    }

    @Test
    void fallsBackToBuildDirectoryWhenDistIsAbsent() {
        BuildWorkspace workspace = workspaceWithFiles("package.json", "{}");
        processExecutor.nextResult(new ProcessResult(0, "", "", false));
        processExecutor.nextResult(new ProcessResult(0, "", "", false), workingDirectory -> writeFile(
                workingDirectory.resolve("build/index.html"), "<html>cra</html>"));

        try (PreparedArtifact artifact = builder.build(workspace)) {
            assertThat(readString(artifact.artifactDirectory().resolve("index.html"))).isEqualTo("<html>cra</html>");
        }
    }

    @Test
    void throwsWhenNoOutputDirectoryIsProduced() {
        BuildWorkspace workspace = workspaceWithFiles("package.json", "{}");
        processExecutor.nextResult(new ProcessResult(0, "", "", false));
        processExecutor.nextResult(new ProcessResult(0, "", "", false)); // build "succeeds" but writes nothing

        assertThatThrownBy(() -> builder.build(workspace))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no output directory");
    }

    @Test
    void installFailureExposesStructuredDiagnostics() {
        UUID functionVersionId = UUID.randomUUID();
        BuildWorkspace workspace = workspaceWithFiles(functionVersionId, "package.json", "{}");
        processExecutor.nextResult(new ProcessResult(1, "install stdout", "install stderr", false));

        assertThatThrownBy(() -> builder.build(workspace))
                .isInstanceOf(StaticBuildException.class)
                .satisfies(exception -> {
                    StaticBuildException buildException = (StaticBuildException) exception;
                    assertThat(buildException.functionVersionId()).isEqualTo(functionVersionId);
                    assertThat(buildException.stage()).isEqualTo(StaticRuntimeBuilder.STAGE_DEPENDENCY_INSTALL);
                    assertThat(buildException.exitCode()).isEqualTo(1);
                });
        assertThat(processExecutor.invocationCount()).isEqualTo(1);
    }

    @Test
    void buildScriptFailureExposesStructuredDiagnostics() {
        UUID functionVersionId = UUID.randomUUID();
        BuildWorkspace workspace = workspaceWithFiles(functionVersionId, "package.json", "{}");
        processExecutor.nextResult(new ProcessResult(0, "installed", "", false));
        processExecutor.nextResult(new ProcessResult(2, "build stdout", "build stderr", false));

        assertThatThrownBy(() -> builder.build(workspace))
                .isInstanceOf(StaticBuildException.class)
                .satisfies(exception -> {
                    StaticBuildException buildException = (StaticBuildException) exception;
                    assertThat(buildException.stage()).isEqualTo(StaticRuntimeBuilder.STAGE_BUILD);
                    assertThat(buildException.command()).isEqualTo(List.of("npm", "run", "build"));
                    assertThat(buildException.exitCode()).isEqualTo(2);
                    assertThat(buildException.stderr()).isEqualTo("build stderr");
                });
    }

    @Test
    void buildTimeoutIsSurfacedClearly() {
        BuildWorkspace workspace = workspaceWithFiles("package.json", "{}");
        processExecutor.nextResult(new ProcessResult(0, "", "", false));
        processExecutor.nextResult(new ProcessResult(null, "partial", "partial", true));

        assertThatThrownBy(() -> builder.build(workspace))
                .isInstanceOf(StaticBuildException.class)
                .satisfies(exception -> {
                    StaticBuildException buildException = (StaticBuildException) exception;
                    assertThat(buildException.timedOut()).isTrue();
                    assertThat(buildException.stage()).isEqualTo(StaticRuntimeBuilder.STAGE_BUILD);
                });
    }

    @Test
    void artifactContainsOnlyBuildOutputNotSourceOrNodeModules() {
        BuildWorkspace workspace = workspaceWithFiles(
                "package.json", "{}",
                "src/App.tsx", "source code"
        );
        processExecutor.nextResult(new ProcessResult(0, "", "", false), workingDirectory -> writeFile(
                workingDirectory.resolve("node_modules/.bin/vite"), "fake binary"));
        processExecutor.nextResult(new ProcessResult(0, "", "", false), workingDirectory -> writeFile(
                workingDirectory.resolve("dist/index.html"), "<html/>"));

        try (PreparedArtifact artifact = builder.build(workspace)) {
            assertThat(Files.exists(artifact.artifactDirectory().resolve("src/App.tsx"))).isFalse();
            assertThat(Files.exists(artifact.artifactDirectory().resolve("node_modules"))).isFalse();
            assertThat(Files.exists(artifact.artifactDirectory().resolve("index.html"))).isTrue();
        }
    }

    @Test
    void buildWorkspaceIsNotMutated() {
        BuildWorkspace workspace = workspaceWithFiles("package.json", "{}");
        processExecutor.nextResult(new ProcessResult(0, "", "", false));
        processExecutor.nextResult(new ProcessResult(0, "", "", false), workingDirectory -> writeFile(
                workingDirectory.resolve("dist/index.html"), "<html/>"));

        try (PreparedArtifact artifact = builder.build(workspace)) {
            assertThat(Files.exists(workspace.root().resolve("dist"))).isFalse();
            assertThat(Files.exists(workspace.root().resolve("node_modules"))).isFalse();
        }
    }

    private BuildWorkspace workspaceWithFiles(String... pathsAndContents) {
        return workspaceWithFiles(UUID.randomUUID(), pathsAndContents);
    }

    private BuildWorkspace workspaceWithFiles(UUID functionVersionId, String... pathsAndContents) {
        Path root = workspaceRoot(pathsAndContents);
        // Submitters point "entrypoint" at package.json for STATIC versions -
        // see the class javadoc for why (BuildWorkspaceService's generic
        // existence precondition, which this builder never reads for
        // anything else).
        return new BuildWorkspace(functionVersionId, root, "package.json", "", "STATIC", null);
    }

    private Path workspaceRoot(String... pathsAndContents) {
        try {
            Path root = Files.createTempDirectory(tempDir, "workspace-");
            for (int i = 0; i < pathsAndContents.length; i += 2) {
                writeFile(root.resolve(pathsAndContents[i]), pathsAndContents[i + 1]);
            }
            return root;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void writeFile(Path target, String content) {
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static final class FakeProcessExecutor implements ProcessExecutor {
        private final List<ProcessResult> queuedResults = new ArrayList<>();
        private final List<Consumer<Path>> queuedSideEffects = new ArrayList<>();
        private final List<List<String>> capturedCommands = new ArrayList<>();

        void nextResult(ProcessResult result) {
            nextResult(result, workingDirectory -> { });
        }

        void nextResult(ProcessResult result, Consumer<Path> sideEffect) {
            queuedResults.add(result);
            queuedSideEffects.add(sideEffect);
        }

        @Override
        public ProcessResult execute(List<String> command, Path workingDirectory, Duration timeout) {
            int index = capturedCommands.size();
            capturedCommands.add(command);
            ProcessResult result = index < queuedResults.size() ? queuedResults.get(index) : new ProcessResult(0, "", "", false);
            Consumer<Path> sideEffect = index < queuedSideEffects.size() ? queuedSideEffects.get(index) : workingDirectory1 -> { };
            sideEffect.accept(workingDirectory);
            return result;
        }

        int invocationCount() {
            return capturedCommands.size();
        }

        List<String> commandAt(int index) {
            return capturedCommands.get(index);
        }
    }
}
