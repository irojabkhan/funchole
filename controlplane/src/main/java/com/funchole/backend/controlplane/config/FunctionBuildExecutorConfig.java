package com.funchole.backend.controlplane.config;

import com.funchole.backend.controlplane.functionbuild.FunctionBuildExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded background executor for the slow half of FunctionVersion
 * deployment - workspace preparation, {@code npm ci}/{@code npm run build},
 * and artifact publish - run by {@code FunctionVersionDeploymentService}
 * after the fast, synchronous {@code beginPublishing} transition. Deliberately
 * small and fixed rather than unbounded: the production host budgets 2
 * vCPU/4GB, and a single build (npm install + build) is itself CPU/memory
 * heavy, so unbounded concurrent builds would starve the rest of the
 * application instead of just queuing.
 */
@Configuration
public class FunctionBuildExecutorConfig {

    private static final int POOL_SIZE = 2;
    private static final String THREAD_NAME_PREFIX = "function-build-";

    @Bean
    FunctionBuildExecutor functionBuildExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(POOL_SIZE);
        executor.setMaxPoolSize(POOL_SIZE);
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.initialize();
        return executor::execute;
    }
}
