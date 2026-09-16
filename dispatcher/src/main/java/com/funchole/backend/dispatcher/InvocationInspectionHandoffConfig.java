package com.funchole.backend.dispatcher;

import com.funchole.backend.invocation.InvocationInspectionService;
import com.funchole.backend.invocationcontract.InvocationInspectionHandoff;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the concrete implementation of {@link InvocationInspectionHandoff}
 * as a Spring bean. Lives in the {@code dispatcher} module - not
 * controlplane - so controlplane never needs to import
 * {@link InvocationStepExecutionRegistry}, {@link JdbcInvocationStepExecutionRegistry},
 * or any Dispatcher/Runtime Registry type to read step-level execution
 * detail: it only ever sees the {@code invocation-contract} interface type,
 * and Spring's classpath component scan (controlplane depends on this
 * module at {@code runtimeOnly}, not {@code implementation}, mirroring
 * exactly how it already depends on the {@code invocation} module) finds
 * this configuration and wires the real adapter automatically.
 *
 * <p>{@code invocationInspectionService} is injected, not constructed here -
 * it is registered as a bean by the {@code invocation} module's own
 * {@code InvocationHandoffConfig}, which this class depends on to avoid
 * dispatcher owning invocation-module wiring.
 */
@Configuration
public class InvocationInspectionHandoffConfig {

    @Bean
    InvocationStepExecutionRegistry invocationStepExecutionRegistry(DataSource dataSource) {
        return new JdbcInvocationStepExecutionRegistry(dataSource);
    }

    @Bean
    InvocationStepExecutionLogRegistry invocationStepExecutionLogRegistry(DataSource dataSource) {
        return new JdbcInvocationStepExecutionLogRegistry(dataSource);
    }

    @Bean
    InvocationInspectionHandoff invocationInspectionHandoff(
            InvocationInspectionService invocationInspectionService,
            InvocationStepExecutionRegistry invocationStepExecutionRegistry,
            InvocationStepExecutionLogRegistry invocationStepExecutionLogRegistry
    ) {
        return new InvocationRegistryInvocationInspectionHandoff(
                invocationInspectionService, invocationStepExecutionRegistry, invocationStepExecutionLogRegistry);
    }
}
