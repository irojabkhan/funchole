package com.funchole.backend.controlplane.config;

import com.funchole.backend.controlplane.repository.FlowVersionRepository;
import com.funchole.backend.controlplane.service.FlowVersionInvocationService;
import com.funchole.backend.invocationcontract.FlowInvocationHandoff;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link FlowVersionInvocationService} using only types controlplane
 * is allowed to depend on: its own {@link FlowVersionRepository} and the
 * {@code invocation-contract} module's {@link FlowInvocationHandoff}. The
 * concrete handoff implementation is registered elsewhere (the
 * {@code invocation} module's own configuration, present on this
 * application's runtime classpath only) and resolved here purely by
 * interface type.
 */
@Configuration
public class FlowVersionInvocationConfig {

    @Bean
    FlowVersionInvocationService flowVersionInvocationService(
            FlowVersionRepository flowVersionRepository,
            FlowInvocationHandoff flowInvocationHandoff
    ) {
        return new FlowVersionInvocationService(flowVersionRepository, flowInvocationHandoff);
    }
}
