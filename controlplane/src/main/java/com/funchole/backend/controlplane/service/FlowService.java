package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.dto.FlowCreateRequest;
import com.funchole.backend.controlplane.dto.FlowUpdateRequest;
import com.funchole.backend.controlplane.entity.AppUser;
import com.funchole.backend.controlplane.entity.Flow;
import com.funchole.backend.controlplane.entity.Gateway;
import com.funchole.backend.controlplane.repository.FlowRepository;
import com.funchole.backend.controlplane.repository.GatewayRepository;
import com.funchole.backend.core.base.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlowService {
    private static final int DEFAULT_PRIORITY = 100;

    private final FlowRepository flowRepository;
    private final GatewayRepository gatewayRepository;

    public FlowService(FlowRepository flowRepository, GatewayRepository gatewayRepository) {
        this.flowRepository = flowRepository;
        this.gatewayRepository = gatewayRepository;
    }

    public Page<Flow> listFlows(UUID appUserId, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page - 1, 0),
                Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, Flow::getCreatedAt)
        );
        return flowRepository.findAllByAppUser_IdAndDeletedAtIsNull(appUserId, pageable);
    }

    public Flow getFlowById(UUID appUserId, UUID flowId) {
        return flowRepository.findByIdAndAppUser_IdAndDeletedAtIsNull(flowId, appUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Flow not found: " + flowId));
    }

    @Transactional
    public Flow createFlow(AppUser appUser, FlowCreateRequest request) {
        if (flowRepository.existsByFlowKey(request.flowKey())) {
            throw new IllegalArgumentException("Flow key already in use: " + request.flowKey());
        }
        validatePath(request.path());

        Gateway gateway = getOwnedGateway(appUser.getId(), request.gatewayId());

        Flow flow = Flow.create(
                appUser,
                gateway,
                request.flowKey(),
                request.name(),
                request.description(),
                request.httpMethod(),
                request.path(),
                resolvePriority(request.priority())
        );

        return flowRepository.save(flow);
    }

    @Transactional
    public Flow updateFlow(UUID appUserId, UUID flowId, FlowUpdateRequest request) {
        validatePath(request.path());
        Flow flow = getFlowById(appUserId, flowId);
        Gateway gateway = getOwnedGateway(appUserId, request.gatewayId());

        flow.update(
                gateway,
                request.name(),
                request.description(),
                request.httpMethod(),
                request.path(),
                resolvePriority(request.priority())
        );

        return flowRepository.save(flow);
    }

    @Transactional
    public void deleteFlow(UUID appUserId, UUID flowId) {
        Flow flow = getFlowById(appUserId, flowId);
        flow.softDelete();
        flowRepository.save(flow);
    }

    private Gateway getOwnedGateway(UUID appUserId, UUID gatewayId) {
        return gatewayRepository.findByIdAndAppUser_Id(gatewayId, appUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Gateway not found: " + gatewayId));
    }

    private int resolvePriority(Integer priority) {
        return priority != null ? priority : DEFAULT_PRIORITY;
    }

    /**
     * A path may be a literal exact route, or a wildcard route ending in
     * exactly "/*" - matching an entire subtree (a whole SPA/SSR frontend
     * app, or anything doing its own internal sub-routing) rather than one
     * URL. Only a single trailing "/*" is supported (see
     * {@code gateway.flow.PrefixRoute}) - a "*" anywhere else is rejected so
     * a malformed pattern fails at creation time, not silently at request
     * time in the Gateway.
     */
    private void validatePath(String path) {
        if (path == null) {
            return;
        }
        int starIndex = path.indexOf('*');
        if (starIndex == -1) {
            return;
        }
        if (!path.endsWith("/*") || path.indexOf('*') != path.length() - 1) {
            throw new IllegalArgumentException(
                    "Path may only use '*' as a single trailing wildcard segment, e.g. '/app/*': " + path);
        }
    }
}
