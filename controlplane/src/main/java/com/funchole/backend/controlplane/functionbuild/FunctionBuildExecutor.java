package com.funchole.backend.controlplane.functionbuild;

import java.util.concurrent.Executor;

/**
 * Marker type for the executor {@code FunctionVersionDeploymentService} hands
 * its build/publish pipeline to, distinct from {@link java.util.concurrent.Executor}
 * itself so it can be injected unambiguously (Spring Boot already
 * auto-configures its own general-purpose {@code Executor} bean) and so
 * tests can swap in a same-thread implementation via {@code @Primary}
 * without any bean-name collision with the production bean - the same
 * pattern already used for {@code ProcessExecutor} in build-log tests.
 */
public interface FunctionBuildExecutor extends Executor {
}
