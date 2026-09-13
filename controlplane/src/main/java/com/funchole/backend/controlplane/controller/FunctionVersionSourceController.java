package com.funchole.backend.controlplane.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funchole.backend.controlplane.dto.FunctionVersionSourceResponse;
import com.funchole.backend.controlplane.entity.FunctionVersion;
import com.funchole.backend.controlplane.entity.FunctionVersionSource;
import com.funchole.backend.controlplane.entity.SourceBundle;
import com.funchole.backend.controlplane.entity.SourceFile;
import com.funchole.backend.controlplane.security.AppUserPrincipal;
import com.funchole.backend.controlplane.service.FunctionVersionService;
import com.funchole.backend.controlplane.service.FunctionVersionSourceService;
import com.funchole.backend.core.base.exception.ResourceNotFoundException;
import com.funchole.backend.core.base.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Multipart REST adapter over the transport-neutral
 * {@link FunctionVersionSourceService}. The canonical source package format
 * (F019/F020) supports two multipart shapes side by side - a real submitter
 * may have either a packaged zip or just a handful of loose files, and both
 * end up as the exact same {@link SourceFile} list before reaching the
 * service:
 * <ul>
 *   <li>a single {@code file} part - a zip archive, unpacked by
 *       {@link SourceUploadReader#readZip};</li>
 *   <li>one or more {@code files} parts - individual files uploaded
 *       directly with no archive, each one's filename carrying its path
 *       relative to the source root (e.g. {@code src/index.mjs}), read by
 *       {@link SourceUploadReader#readFiles}.</li>
 * </ul>
 * Exactly one of the two must be present per request. {@code runtimeType} is
 * never taken from the request body either way - it always comes from the
 * owning FunctionVersion's own {@code runtime} field, so source submission
 * can never silently disagree with the version it is attached to.
 */
@RestController
@RequestMapping("/api/v1/functions/{functionId}/versions/{versionId}/source")
public class FunctionVersionSourceController {
    private final FunctionVersionService functionVersionService;
    private final FunctionVersionSourceService functionVersionSourceService;
    private final ObjectMapper objectMapper;

    public FunctionVersionSourceController(
            FunctionVersionService functionVersionService,
            FunctionVersionSourceService functionVersionSourceService
    ) {
        this.functionVersionService = functionVersionService;
        this.functionVersionSourceService = functionVersionSourceService;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping(consumes = "multipart/form-data")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<FunctionVersionSourceResponse> submitSource(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID functionId,
            @PathVariable UUID versionId,
            @RequestPart(value = "file", required = false) @Nullable MultipartFile archive,
            @RequestPart(value = "files", required = false) @Nullable List<MultipartFile> individualFiles,
            @RequestParam String entrypoint,
            @RequestParam(required = false) @Nullable String runtimeVersion
    ) {
        boolean hasArchive = archive != null && !archive.isEmpty();
        boolean hasIndividualFiles = individualFiles != null && !individualFiles.isEmpty();
        if (hasArchive == hasIndividualFiles) {
            throw new IllegalArgumentException(
                    "Submit source as exactly one of: a single zip archive part named 'file', "
                            + "or one or more individual file parts named 'files'");
        }

        FunctionVersion functionVersion = functionVersionService.getVersionById(appUserPrincipal.getId(), functionId, versionId);
        List<SourceFile> files = hasArchive
                ? SourceUploadReader.readZip(archive)
                : SourceUploadReader.readFiles(individualFiles);
        SourceBundle bundle = new SourceBundle(functionVersion.getRuntime(), runtimeVersion, entrypoint, files);

        FunctionVersionSource source = functionVersionSourceService.submitSource(versionId, bundle);
        return ApiResponse.success(toResponse(source));
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<FunctionVersionSourceResponse> getSource(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID functionId,
            @PathVariable UUID versionId
    ) {
        functionVersionService.getVersionById(appUserPrincipal.getId(), functionId, versionId);
        SourceBundle bundle = functionVersionSourceService.findSource(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("No source submitted for function version: " + versionId));

        List<String> relativePaths = bundle.files().stream().map(SourceFile::relativePath).toList();
        return ApiResponse.success(new FunctionVersionSourceResponse(
                versionId, bundle.runtimeType(), bundle.runtimeVersion(), bundle.entrypoint(), relativePaths));
    }

    private FunctionVersionSourceResponse toResponse(FunctionVersionSource source) {
        List<String> relativePaths = readPaths(source.getRelativePaths());
        return new FunctionVersionSourceResponse(
                source.getFunctionVersionId(), source.getRuntimeType(), source.getRuntimeVersion(),
                source.getEntrypoint(), relativePaths);
    }

    private List<String> readPaths(String relativePathsJson) {
        try {
            return objectMapper.readValue(relativePathsJson, new TypeReference<List<String>>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize source file paths", exception);
        }
    }
}
