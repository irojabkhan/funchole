package com.funchole.backend.controlplane.mcp;

import com.funchole.backend.controlplane.dto.DomainCreateRequest;
import com.funchole.backend.controlplane.dto.DomainResponse;
import com.funchole.backend.controlplane.entity.AppDomain;
import com.funchole.backend.controlplane.entity.AppUser;
import com.funchole.backend.controlplane.mapper.DomainMapper;
import com.funchole.backend.controlplane.service.DomainService;
import com.funchole.backend.controlplane.service.ProfileService;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

/**
 * MCP tool surface for AppDomains - mirrors {@code DomainController}
 * exactly. A domain must be verified (DNS TXT challenge) before a Gateway
 * can bind to it.
 */
@Service
public class DomainMcpTools {

    private final DomainService domainService;
    private final ProfileService profileService;
    private final DomainMapper domainMapper;

    public DomainMcpTools(DomainService domainService, ProfileService profileService, DomainMapper domainMapper) {
        this.domainService = domainService;
        this.profileService = profileService;
        this.domainMapper = domainMapper;
    }

    @McpTool(name = "list_domains", description = "List the current user's AppDomains.")
    public List<DomainResponse> listDomains(
            @McpToolParam(description = "1-based page number, defaults to 1", required = false) Integer page,
            @McpToolParam(description = "Page size, defaults to 20", required = false) Integer size
    ) {
        Page<DomainResponse> result = domainService
                .listDomains(CurrentMcpUser.id(), page != null ? page : 1, size != null ? size : 20)
                .map(domainMapper::toResponse);
        return result.getContent();
    }

    @McpTool(name = "get_domain", description = "Get one AppDomain by id, including its verification status and code.")
    public DomainResponse getDomain(@McpToolParam(description = "AppDomain id (UUID)") String domainId) {
        return domainMapper.toResponse(domainService.getDomainById(CurrentMcpUser.id(), UUID.fromString(domainId)));
    }

    @McpTool(
            name = "create_domain",
            description = "Register a domain name for verification (PENDING status, with a DNS TXT verification "
                    + "code) - call initiate_domain_verification once the DNS record is in place."
    )
    public DomainResponse createDomain(@McpToolParam(description = "Domain name, e.g. example.com") String domainName) throws NotFoundException {
        AppUser appUser = profileService.loadUserById(CurrentMcpUser.id());
        AppDomain created = domainService.createDomain(appUser, new DomainCreateRequest(domainName));
        return domainMapper.toResponse(created);
    }

    @McpTool(name = "initiate_domain_verification", description = "Check a domain's DNS TXT record and mark it VERIFIED if it matches.")
    public DomainResponse initiateDomainVerification(@McpToolParam(description = "AppDomain id (UUID)") String domainId) {
        AppDomain domain = domainService.initiateDomainVerification(CurrentMcpUser.id(), UUID.fromString(domainId));
        return domainMapper.toResponse(domain);
    }
}
