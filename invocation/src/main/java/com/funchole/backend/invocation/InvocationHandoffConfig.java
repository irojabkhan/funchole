package com.funchole.backend.invocation;

import com.funchole.backend.invocationcontract.FlowInvocationHandoff;
import com.funchole.backend.invocationcontract.FunctionVersionInvocationHandoff;
import io.nats.client.Connection;
import io.nats.client.Nats;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Registers the concrete Invocation-side implementation of
 * {@link FunctionVersionInvocationHandoff} as a Spring bean, and separately
 * exposes {@link InvocationInspectionService} itself as a bean so the
 * dispatcher module's own inspection config (which adds step-level detail on
 * top) can inject it without either module compiling against the other's
 * internals. This class lives
 * in the {@code invocation} module - not controlplane - so controlplane
 * never needs to import {@link InvocationRegistry}, {@link JdbcInvocationRegistry},
 * or {@link Invocation} to obtain a working handoff: it only ever sees the
 * {@code invocation-contract} interface type, and Spring's classpath
 * component scan (controlplane depends on this module at {@code runtimeOnly},
 * not {@code implementation}) finds this configuration and wires the real
 * adapter automatically.
 *
 * <p>Wires a real {@link NatsJetStreamInvocationEventPublisher} (GAP-17) so
 * direct FunctionVersion invocations are actually picked up and executed by
 * the Dispatcher, exactly like Gateway-triggered Flow invocations. The
 * connection and the publisher are both {@link Lazy}: this configuration
 * loads into every controlplane Spring context (most of the test suite
 * included), but a real NATS connection is only opened the first time a
 * direct invocation is actually created - every other context never touches
 * NATS at all.
 */
@Configuration
public class InvocationHandoffConfig {

    @Bean
    @Lazy
    Connection invocationNatsConnection(@Value("${app.nats.url:nats://localhost:4222}") String natsUrl) throws Exception {
        return Nats.connect(natsUrl);
    }

    @Bean
    @Lazy
    InvocationEventPublisher invocationEventPublisher(Connection invocationNatsConnection) {
        return new NatsJetStreamInvocationEventPublisher(invocationNatsConnection);
    }

    @Bean
    InvocationRegistry invocationRegistry(DataSource dataSource, InvocationEventPublisher invocationEventPublisher) {
        return new JdbcInvocationRegistry(dataSource, invocationEventPublisher);
    }

    @Bean
    FunctionVersionInvocationHandoff functionVersionInvocationHandoff(InvocationRegistry invocationRegistry) {
        return new InvocationRegistryFunctionVersionInvocationHandoff(invocationRegistry);
    }

    @Bean
    FlowInvocationHandoff flowInvocationHandoff(InvocationRegistry invocationRegistry) {
        return new InvocationRegistryFlowInvocationHandoff(invocationRegistry);
    }

    @Bean
    InvocationInspectionService invocationInspectionService(InvocationRegistry invocationRegistry) {
        return new InvocationInspectionService(invocationRegistry);
    }
}
