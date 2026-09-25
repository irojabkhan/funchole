package com.funchole.backend.controlplane.controller;

import com.funchole.backend.controlplane.dto.DatabaseCreateRequest;
import com.funchole.backend.controlplane.dto.DatabasePasswordResponse;
import com.funchole.backend.controlplane.dto.DatabaseResponse;
import com.funchole.backend.controlplane.dto.DatabaseUpdateRequest;
import com.funchole.backend.controlplane.entity.AppUser;
import com.funchole.backend.controlplane.entity.Database;
import com.funchole.backend.controlplane.mapper.DatabaseMapper;
import com.funchole.backend.controlplane.security.AppUserPrincipal;
import com.funchole.backend.controlplane.service.DatabaseService;
import com.funchole.backend.controlplane.service.ProfileService;
import com.funchole.backend.core.base.mapper.PaginationMapper;
import com.funchole.backend.core.base.response.ApiResponse;
import com.funchole.backend.core.base.response.PaginationResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/databases")
public class DatabaseController {
    private final DatabaseService databaseService;
    private final ProfileService profileService;
    private final DatabaseMapper databaseMapper;
    private final PaginationMapper paginationMapper;

    public DatabaseController(
            DatabaseService databaseService,
            ProfileService profileService,
            DatabaseMapper databaseMapper,
            PaginationMapper paginationMapper
    ) {
        this.databaseService = databaseService;
        this.profileService = profileService;
        this.databaseMapper = databaseMapper;
        this.paginationMapper = paginationMapper;
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<PaginationResponse<DatabaseResponse>> listDatabases(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<DatabaseResponse> databases = databaseService.listDatabases(appUserPrincipal.getId(), page, size)
                .map(databaseMapper::toResponse);
        return ApiResponse.success(paginationMapper.toResponse(databases));
    }

    @GetMapping("/{databaseId}")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<DatabaseResponse> getDatabaseById(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID databaseId
    ) {
        Database database = databaseService.getDatabaseById(appUserPrincipal.getId(), databaseId);
        return ApiResponse.success(databaseMapper.toResponse(database));
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<DatabaseResponse> createDatabase(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @Valid @RequestBody DatabaseCreateRequest request
    ) throws NotFoundException {
        AppUser appUser = profileService.loadUserById(appUserPrincipal.getId());
        Database database = databaseService.createDatabase(appUser, request);
        return ApiResponse.success(databaseMapper.toResponse(database));
    }

    @GetMapping("/{databaseId}/password")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<DatabasePasswordResponse> revealPassword(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID databaseId
    ) {
        String password = databaseService.revealPassword(appUserPrincipal.getId(), databaseId);
        return ApiResponse.success(new DatabasePasswordResponse(password));
    }

    @PutMapping("/{databaseId}")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<DatabaseResponse> updateDatabase(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID databaseId,
            @Valid @RequestBody DatabaseUpdateRequest request
    ) {
        Database database = databaseService.updateDatabase(appUserPrincipal.getId(), databaseId, request);
        return ApiResponse.success(databaseMapper.toResponse(database));
    }

    @DeleteMapping("/{databaseId}")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<Map<String, String>> deleteDatabase(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID databaseId
    ) {
        databaseService.deleteDatabase(appUserPrincipal.getId(), databaseId);
        return ApiResponse.success(Map.of("message", "Database deleted successfully"));
    }
}
