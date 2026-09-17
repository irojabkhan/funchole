package com.funchole.backend.controlplane.mcp;

import com.funchole.backend.controlplane.dto.EnvironmentProfileConfigResponse;
import com.funchole.backend.controlplane.dto.EnvironmentProfileCreateRequest;
import com.funchole.backend.controlplane.dto.EnvironmentProfileResponse;
import com.funchole.backend.controlplane.dto.EnvironmentProfileUpdateRequest;
import com.funchole.backend.controlplane.entity.AppUser;
import com.funchole.backend.controlplane.entity.EnvironmentProfile;
import com.funchole.backend.controlplane.service.EnvironmentProfileService;
import com.funchole.backend.controlplane.service.ProfileService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

/**
 * MCP tool surface for EnvironmentProfiles - reusable, named bundles of
 * env vars/secrets a user attaches to one or more Flows so multiple Flows
 * (and, transitively, the Functions they call) can share the same
 * configuration instead of each FunctionVersion holding its own copy.
 * Mirrors {@code EnvironmentProfileController} exactly.
 */
@Service
public class EnvironmentProfileMcpTools {

    private final EnvironmentProfileService environmentProfileService;
    private final ProfileService profileService;

    public EnvironmentProfileMcpTools(EnvironmentProfileService environmentProfileService, ProfileService profileService) {
        this.environmentProfileService = environmentProfileService;
        this.profileService = profileService;
    }

    @McpTool(name = "list_environments", description = "List the current user's EnvironmentProfiles.")
    public List<EnvironmentProfileResponse> listEnvironments(
            @McpToolParam(description = "1-based page number, defaults to 1", required = false) Integer page,
            @McpToolParam(description = "Page size, defaults to 20", required = false) Integer size
    ) {
        Page<EnvironmentProfileResponse> result = environmentProfileService
                .listProfiles(CurrentMcpUser.id(), page != null ? page : 1, size != null ? size : 20)
                .map(EnvironmentProfileMcpTools::toResponse);
        return result.getContent();
    }

    @McpTool(name = "get_environment", description = "Get one EnvironmentProfile by id.")
    public EnvironmentProfileResponse getEnvironment(@McpToolParam(description = "EnvironmentProfile id (UUID)") String environmentId) {
        return toResponse(environmentProfileService.getProfileById(CurrentMcpUser.id(), UUID.fromString(environmentId)));
    }

    @McpTool(
            name = "create_environment",
            description = "Create a new EnvironmentProfile - attach it to one or more Flows with "
                    + "attach_flow_environment, then set values with set_environment_env_var/set_environment_secret."
    )
    public EnvironmentProfileResponse createEnvironment(
            @McpToolParam(description = "Unique key: letters, numbers, '_', '.' and '-' only, e.g. production") String environmentKey,
            @McpToolParam(description = "Display name") String name,
            @McpToolParam(description = "Description", required = false) String description
    ) throws NotFoundException {
        AppUser appUser = profileService.loadUserById(CurrentMcpUser.id());
        EnvironmentProfile created = environmentProfileService.createProfile(
                appUser, new EnvironmentProfileCreateRequest(environmentKey, name, description));
        return toResponse(created);
    }

    @McpTool(name = "update_environment", description = "Update an EnvironmentProfile's name or description.")
    public EnvironmentProfileResponse updateEnvironment(
            @McpToolParam(description = "EnvironmentProfile id (UUID)") String environmentId,
            @McpToolParam(description = "Display name") String name,
            @McpToolParam(description = "Description", required = false) String description
    ) {
        EnvironmentProfile updated = environmentProfileService.updateProfile(
                CurrentMcpUser.id(), UUID.fromString(environmentId), new EnvironmentProfileUpdateRequest(name, description));
        return toResponse(updated);
    }

    @McpTool(name = "delete_environment", description = "Delete (soft-delete) an EnvironmentProfile.")
    public Map<String, String> deleteEnvironment(@McpToolParam(description = "EnvironmentProfile id (UUID)") String environmentId) {
        environmentProfileService.deleteProfile(CurrentMcpUser.id(), UUID.fromString(environmentId));
        return Map.of("message", "Environment deleted successfully");
    }

    @McpTool(name = "get_environment_config", description = "Get an EnvironmentProfile's environment variables and secret keys (secret values are never returned, only their reference).")
    public EnvironmentProfileConfigResponse getEnvironmentConfig(@McpToolParam(description = "EnvironmentProfile id (UUID)") String environmentId) {
        return environmentProfileService.getConfig(CurrentMcpUser.id(), UUID.fromString(environmentId));
    }

    @McpTool(name = "set_environment_env_var", description = "Create or update one non-secret environment variable on an EnvironmentProfile.")
    public EnvironmentProfileConfigResponse setEnvironmentEnvVar(
            @McpToolParam(description = "EnvironmentProfile id (UUID)") String environmentId,
            @McpToolParam(description = "Variable name") String key,
            @McpToolParam(description = "Variable value") String value
    ) {
        return environmentProfileService.upsertEnvVar(CurrentMcpUser.id(), UUID.fromString(environmentId), key, value);
    }

    @McpTool(name = "set_environment_secret", description = "Create or update one secret on an EnvironmentProfile. The value is stored in OpenBao, never returned again afterward.")
    public EnvironmentProfileConfigResponse setEnvironmentSecret(
            @McpToolParam(description = "EnvironmentProfile id (UUID)") String environmentId,
            @McpToolParam(description = "Secret name") String key,
            @McpToolParam(description = "Secret value") String value
    ) {
        return environmentProfileService.upsertSecret(CurrentMcpUser.id(), UUID.fromString(environmentId), key, value);
    }

    private static EnvironmentProfileResponse toResponse(EnvironmentProfile profile) {
        return new EnvironmentProfileResponse(
                profile.getId(), profile.getEnvironmentKey(), profile.getName(),
                profile.getDescription(), profile.getCreatedAt(), profile.getUpdatedAt());
    }
}
