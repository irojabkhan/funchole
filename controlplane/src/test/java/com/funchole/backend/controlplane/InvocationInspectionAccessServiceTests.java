package com.funchole.backend.controlplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.funchole.backend.controlplane.constant.DomainStatus;
import com.funchole.backend.controlplane.constant.GatewayStatus;
import com.funchole.backend.controlplane.dto.FlowCreateRequest;
import com.funchole.backend.controlplane.entity.AppDomain;
import com.funchole.backend.controlplane.entity.AppUser;
import com.funchole.backend.controlplane.entity.Flow;
import com.funchole.backend.controlplane.entity.Function;
import com.funchole.backend.controlplane.entity.FunctionVersion;
import com.funchole.backend.controlplane.entity.Gateway;
import com.funchole.backend.controlplane.repository.AppDomainRepository;
import com.funchole.backend.controlplane.repository.AppUserRepository;
import com.funchole.backend.controlplane.repository.FunctionRepository;
import com.funchole.backend.controlplane.repository.FunctionVersionRepository;
import com.funchole.backend.controlplane.repository.GatewayRepository;
import com.funchole.backend.controlplane.service.FlowService;
import com.funchole.backend.controlplane.service.FunctionService;
import com.funchole.backend.controlplane.service.InvocationInspectionAccessService;
import com.funchole.backend.core.base.exception.ResourceNotFoundException;
import com.funchole.backend.invocationcontract.InvocationInspectionHandoff;
import com.funchole.backend.invocationcontract.InvocationInspectionResult;
import com.funchole.backend.invocationcontract.InvocationStepInspectionResult;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class InvocationInspectionAccessServiceTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withDatabaseName("funchole")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AppDomainRepository appDomainRepository;

    @Autowired
    private GatewayRepository gatewayRepository;

    @Autowired
    private FlowService flowService;

    @Autowired
    private FunctionRepository functionRepository;

    @Autowired
    private FunctionVersionRepository functionVersionRepository;

    @Autowired
    private FunctionService functionService;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private InvocationInspectionAccessService realService;

    private AppUser admin;
    private UUID gatewayId;

    @BeforeEach
    void setUp() {
        admin = appUserRepository.findByUsername("admin").orElseThrow();
        AppDomain domain = appDomainRepository.save(
                AppDomain.create(admin, "inspect-test-" + UUID.randomUUID() + ".example.com", "verify-me", DomainStatus.VERIFIED));
        Gateway gateway = gatewayRepository.save(
                Gateway.create(admin, domain, "Inspection Test Gateway", "itg" + System.nanoTime() % 100000, "test gateway", GatewayStatus.ACTIVE));
        gatewayId = gateway.getId();
    }

    @Test
    void flowInvocationOwnedByCallerIsReturnedUnchanged() {
        Flow flow = createFlow();
        InvocationInspectionResult canned = flowResult(flow.getId(), flow.getFlowKey());

        InvocationInspectionResult result = service(new InMemoryHandoff(canned)).inspect(admin.getId(), canned.invocationId());

        assertThat(result).isEqualTo(canned);
    }

    @Test
    void flowInvocationForANonexistentFlowIsNotFound() {
        InvocationInspectionResult canned = flowResult(UUID.randomUUID(), "flw_does_not_exist");

        assertThatThrownBy(() -> service(new InMemoryHandoff(canned)).inspect(admin.getId(), canned.invocationId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void directFunctionInvocationOwnedByCallerIsReturnedUnchanged() {
        FunctionVersion functionVersion = createFunctionVersion();
        InvocationInspectionResult canned = directFunctionResult(functionVersion.getId());

        InvocationInspectionResult result = service(new InMemoryHandoff(canned)).inspect(admin.getId(), canned.invocationId());

        assertThat(result).isEqualTo(canned);
    }

    @Test
    void directFunctionInvocationForANonexistentFunctionVersionIsNotFound() {
        InvocationInspectionResult canned = directFunctionResult(UUID.randomUUID());

        assertThatThrownBy(() -> service(new InMemoryHandoff(canned)).inspect(admin.getId(), canned.invocationId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void directFunctionInvocationForASoftDeletedFunctionIsNotFound() {
        FunctionVersion functionVersion = createFunctionVersion();
        InvocationInspectionResult canned = directFunctionResult(functionVersion.getId());
        functionService.deleteFunction(admin.getId(), functionVersion.getFunction().getId());

        assertThatThrownBy(() -> service(new InMemoryHandoff(canned)).inspect(admin.getId(), canned.invocationId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aTrulyNonexistentInvocationIsNotFound() {
        UUID invocationId = UUID.randomUUID();

        assertThatThrownBy(() -> service(new NotFoundHandoff()).inspect(admin.getId(), invocationId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(invocationId.toString());
    }

    @Test
    void springResolvesExactlyOneInvocationInspectionHandoffBean() {
        assertThat(applicationContext.getBeansOfType(InvocationInspectionHandoff.class)).hasSize(1);
    }

    @Test
    void invocationInspectionAccessServiceIsConstructedThroughNormalApplicationWiring() {
        assertThat(applicationContext.getBeansOfType(InvocationInspectionAccessService.class)).hasSize(1);
        assertThat(realService).isNotNull();
    }

    @Test
    void serviceOnlyUsesContractBoundaryTypesNeverInvocationImplementationOrExecutionClasses() {
        // "com.funchole.backend.invocation." (trailing dot) intentionally
        // excludes "com.funchole.backend.invocationcontract" - the contract
        // package - which is exactly what this service is allowed to depend on.
        List<String> forbiddenPackagePrefixes = List.of(
                "com.funchole.backend.invocation.",
                "com.funchole.backend.dispatcher",
                "com.funchole.backend.runtimeregistry",
                "com.funchole.backend.runtime"
        );
        for (Field field : InvocationInspectionAccessService.class.getDeclaredFields()) {
            assertTypeIsAllowed(field.getType(), forbiddenPackagePrefixes);
        }
        for (Constructor<?> constructor : InvocationInspectionAccessService.class.getDeclaredConstructors()) {
            for (Class<?> parameterType : constructor.getParameterTypes()) {
                assertTypeIsAllowed(parameterType, forbiddenPackagePrefixes);
            }
        }
        for (Method method : InvocationInspectionAccessService.class.getDeclaredMethods()) {
            assertTypeIsAllowed(method.getReturnType(), forbiddenPackagePrefixes);
            for (Class<?> parameterType : method.getParameterTypes()) {
                assertTypeIsAllowed(parameterType, forbiddenPackagePrefixes);
            }
        }
    }

    private void assertTypeIsAllowed(Class<?> type, List<String> forbiddenPackagePrefixes) {
        for (String forbidden : forbiddenPackagePrefixes) {
            assertThat(type.getPackageName().startsWith(forbidden))
                    .as("type %s must not belong to package %s", type.getName(), forbidden)
                    .isFalse();
        }
    }

    private InvocationInspectionAccessService service(InvocationInspectionHandoff handoff) {
        return new InvocationInspectionAccessService(handoff, flowService, functionVersionRepository);
    }

    private InvocationInspectionResult flowResult(UUID flowId, String flowKey) {
        return new InvocationInspectionResult(
                UUID.randomUUID(), "COMPLETED", flowId, flowKey, UUID.randomUUID(), null,
                "{}", "{\"ok\":true}", null,
                OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(),
                List.of(stepResult()));
    }

    private InvocationInspectionResult directFunctionResult(UUID functionVersionId) {
        return new InvocationInspectionResult(
                UUID.randomUUID(), "PENDING", null, null, null, functionVersionId,
                "{}", null, null,
                OffsetDateTime.now(), OffsetDateTime.now(), null,
                List.of());
    }

    private InvocationStepInspectionResult stepResult() {
        return new InvocationStepInspectionResult(
                UUID.randomUUID(), 1, "FUNCTION", UUID.randomUUID(), UUID.randomUUID(),
                "COMPLETED", 1, "{\"ok\":true}", null,
                OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now());
    }

    private Flow createFlow() {
        String flowKey = "flw_inspect_" + UUID.randomUUID().toString().replace("-", "");
        return flowService.createFlow(admin, new FlowCreateRequest(
                flowKey, "Test Flow", "created by InvocationInspectionAccessServiceTests",
                gatewayId, "GET", "/inspect-" + flowKey, null));
    }

    private FunctionVersion createFunctionVersion() {
        Function function = functionRepository.save(Function.create(
                admin,
                "fn_inspect_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                "Test Function",
                "created by InvocationInspectionAccessServiceTests",
                "NODE"
        ));
        return functionVersionRepository.save(FunctionVersion.create(function, 1, "NODE", null));
    }

    private static final class InMemoryHandoff implements InvocationInspectionHandoff {
        private final InvocationInspectionResult result;

        private InMemoryHandoff(InvocationInspectionResult result) {
            this.result = result;
        }

        @Override
        public Optional<InvocationInspectionResult> inspect(UUID invocationId) {
            return Optional.of(result);
        }
    }

    private static final class NotFoundHandoff implements InvocationInspectionHandoff {
        @Override
        public Optional<InvocationInspectionResult> inspect(UUID invocationId) {
            return Optional.empty();
        }
    }
}
