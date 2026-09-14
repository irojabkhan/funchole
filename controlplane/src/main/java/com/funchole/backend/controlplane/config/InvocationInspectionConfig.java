package com.funchole.backend.controlplane.config;

import com.funchole.backend.controlplane.repository.FunctionVersionRepository;
import com.funchole.backend.controlplane.service.FlowService;
import com.funchole.backend.controlplane.service.InvocationInspectionAccessService;
import com.funchole.backend.invocationcontract.InvocationInspectionHandoff;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link InvocationInspectionAccessService} using only types
 * controlplane is allowed to depend on: its own {@link FlowService}/
 * {@link FunctionVersionRepository} and the {@code invocation-contract}
 * module's {@link InvocationInspectionHandoff}. The concrete handoff
 * implementation is registered elsewhere (the {@code dispatcher} module's
 * own configuration, present on this application's runtime classpath only)
 * and resolved here purely by interface type - mirroring
 * {@link FunctionVersionInvocationConfig}.
 */
@Configuration
public class InvocationInspectionConfig {

    @Bean
    InvocationInspectionAccessService invocationInspectionAccessService(
            InvocationInspectionHandoff invocationInspectionHandoff,
            FlowService flowService,
            FunctionVersionRepository functionVersionRepository
    ) {
        return new InvocationInspectionAccessService(invocationInspectionHandoff, flowService, functionVersionRepository);
    }
}
