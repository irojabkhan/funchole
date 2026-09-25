package com.funchole.backend.controlplane.mcp;

import com.funchole.backend.controlplane.dto.FunctionVersionBuildLogResponse;
import com.funchole.backend.controlplane.dto.FunctionVersionConfigResponse;
import com.funchole.backend.controlplane.dto.FunctionVersionCreateRequest;
import com.funchole.backend.controlplane.dto.FunctionVersionDatabaseAttachmentResponse;
import com.funchole.backend.controlplane.dto.FunctionVersionFullSourceResponse;
import com.funchole.backend.controlplane.dto.FunctionVersionResponse;
import com.funchole.backend.controlplane.dto.FunctionVersionSourceFileResponse;
import com.funchole.backend.controlplane.dto.FunctionVersionSourceResponse;
import com.funchole.backend.controlplane.entity.FunctionVersion;
import com.funchole.backend.controlplane.entity.FunctionVersionBuildLog;
import com.funchole.backend.controlplane.entity.FunctionVersionSource;
import com.funchole.backend.controlplane.entity.SourceBundle;
import com.funchole.backend.controlplane.entity.SourceFile;
import com.funchole.backend.controlplane.mapper.FunctionVersionMapper;
import com.funchole.backend.controlplane.service.FunctionVersionBuildLogService;
import com.funchole.backend.controlplane.service.FunctionVersionCloneService;
import com.funchole.backend.controlplane.service.FunctionVersionConfigService;
import com.funchole.backend.controlplane.service.FunctionVersionDatabaseService;
import com.funchole.backend.controlplane.service.FunctionVersionDeploymentService;
import com.funchole.backend.controlplane.service.FunctionVersionService;
import com.funchole.backend.controlplane.service.FunctionVersionSourceService;
import com.funchole.backend.core.base.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

/**
 * MCP tool surface for the FunctionVersion lifecycle - version history,
 * source submission, deploy, shared env/secret config, and Database
 * attachment. Each tool mirrors its REST equivalent exactly (same service
 * calls, same ownership checks); source submission bypasses the REST
 * layer's multipart adapter entirely and calls
 * {@link FunctionVersionSourceService} directly with a simple path/content
 * list, since there is no multipart concept in an MCP tool call.
 */
@Service
public class FunctionVersionMcpTools {

    private final FunctionVersionService functionVersionService;
    private final FunctionVersionCloneService functionVersionCloneService;
    private final FunctionVersionSourceService functionVersionSourceService;
    private final FunctionVersionDeploymentService functionVersionDeploymentService;
    private final FunctionVersionConfigService functionVersionConfigService;
    private final FunctionVersionDatabaseService functionVersionDatabaseService;
    private final FunctionVersionBuildLogService functionVersionBuildLogService;
    private final FunctionVersionMapper functionVersionMapper;

    public FunctionVersionMcpTools(
            FunctionVersionService functionVersionService,
            FunctionVersionCloneService functionVersionCloneService,
            FunctionVersionSourceService functionVersionSourceService,
            FunctionVersionDeploymentService functionVersionDeploymentService,
            FunctionVersionConfigService functionVersionConfigService,
            FunctionVersionDatabaseService functionVersionDatabaseService,
            FunctionVersionBuildLogService functionVersionBuildLogService,
            FunctionVersionMapper functionVersionMapper
    ) {
        this.functionVersionService = functionVersionService;
        this.functionVersionCloneService = functionVersionCloneService;
        this.functionVersionSourceService = functionVersionSourceService;
        this.functionVersionDeploymentService = functionVersionDeploymentService;
        this.functionVersionConfigService = functionVersionConfigService;
        this.functionVersionDatabaseService = functionVersionDatabaseService;
        this.functionVersionBuildLogService = functionVersionBuildLogService;
        this.functionVersionMapper = functionVersionMapper;
    }

    public record SourceFileInput(String path, String content) {
    }

    @McpTool(name = "list_function_versions", description = "List a Function's versions, most recent first.")
    public List<FunctionVersionResponse> listFunctionVersions(
            @McpToolParam(description = "Function id (UUID)") String functionId,
            @McpToolParam(description = "1-based page number, defaults to 1", required = false) Integer page,
            @McpToolParam(description = "Page size, defaults to 20", required = false) Integer size
    ) {
        Page<FunctionVersionResponse> result = functionVersionService
                .listVersions(CurrentMcpUser.id(), UUID.fromString(functionId), page != null ? page : 1, size != null ? size : 20)
                .map(functionVersionMapper::toResponse);
        return result.getContent();
    }

    @McpTool(name = "get_function_version", description = "Get one FunctionVersion by id, including its status (DRAFT/PUBLISHING/READY/FAILED).")
    public FunctionVersionResponse getFunctionVersion(
            @McpToolParam(description = "Function id (UUID)") String functionId,
            @McpToolParam(description = "FunctionVersion id (UUID)") String versionId
    ) {
        FunctionVersion version = functionVersionService.getVersionById(
                CurrentMcpUser.id(), UUID.fromString(functionId), UUID.fromString(versionId));
        return functionVersionMapper.toResponse(version);
    }

    @McpTool(
            name = "create_function_version",
            description = "Create a new DRAFT FunctionVersion under a Function. By default this AUTOMATICALLY "
                    + "clones the Function's most recent version - whatever its status, including a FAILED one - "
                    + "carrying forward its source files, env vars, secrets, and attached databases into the new "
                    + "DRAFT. This is the fix-forward path after a FAILED build: call this again, then "
                    + "get_function_version_source to see exactly what was carried forward, then "
                    + "submit_function_version_source with only the correction applied (still a full-file "
                    + "submission - it replaces the whole set - just start from what was cloned instead of "
                    + "regenerating everything from scratch), then deploy_function_version. Pass "
                    + "cloneFromVersionId to clone a specific earlier version instead of the latest, or "
                    + "startEmpty=true for a genuinely blank DRAFT with no source/config."
    )
    public FunctionVersionResponse createFunctionVersion(
            @McpToolParam(description = "Function id (UUID)") String functionId,
            @McpToolParam(description = "Runtime: NODE (runs your handler code) or STATIC (serves a pre-built "
                    + "static site's files directly, no code execution) - optional, defaults to the cloned "
                    + "version's runtime, or the parent Function's own runtime if there is nothing to clone",
                    required = false) String runtime,
            @McpToolParam(description = "Free-form metadata string - optional, never cloned from a prior version",
                    required = false) String metadata,
            @McpToolParam(description = "Clone this specific prior FunctionVersion id (UUID) instead of the "
                    + "Function's most recent version - optional", required = false) String cloneFromVersionId,
            @McpToolParam(description = "true creates a genuinely empty DRAFT with no source/config, opting out "
                    + "of the default auto-clone - optional, defaults to false", required = false) Boolean startEmpty
    ) {
        FunctionVersion version = functionVersionCloneService.createDraftVersion(
                CurrentMcpUser.id(), UUID.fromString(functionId), new FunctionVersionCreateRequest(
                        runtime, metadata,
                        cloneFromVersionId != null ? UUID.fromString(cloneFromVersionId) : null,
                        startEmpty));
        return functionVersionMapper.toResponse(version);
    }

    @McpTool(
            name = "submit_function_version_source",
            description = "Submit (or replace, while still DRAFT) a FunctionVersion's source files. Each file is "
                    + "a relative path (e.g. 'index.mjs') and its full text content - no archive/multipart needed. "
                    + "IMPORTANT for a NODE-runtime function used as a Flow's RESPONSE step: the handler must "
                    + "return exactly {\"status\": <int>, \"body\": <any JSON value>} - the Gateway reads only "
                    + "those two fields, always JSON-encodes body, and always sends Content-Type: application/json "
                    + "(any statusCode/headers fields are ignored). Returning an HTML string in body will be "
                    + "JSON-encoded, not rendered - for a frontend/UI page, deploy a STATIC-runtime Function "
                    + "instead (see create_function's runtime parameter), not a NODE function returning HTML.\n"
                    + "For a STATIC-runtime Function, submit a real multi-page site here - do not put everything "
                    + "in one page, and do not create a separate Function per page - one Function/FunctionVersion "
                    + "holds the entire site. A route like '/about' automatically resolves, in order, to an exact "
                    + "file named 'about', then 'about.html', then 'about/index.html' - the same clean-URL "
                    + "convention every static host uses, and exactly what a static site generator's default "
                    + "output already looks like. Only a path with none of those is handed the root index.html "
                    + "(SPA-style client-side routing) - so plain multi-page sites and SPAs both work without "
                    + "extra Flows. Concrete example of a real multi-page site's `files` list for ONE submission: "
                    + "[{path: 'index.html', ...} -> serves '/', {path: 'about.html', ...} -> serves '/about', "
                    + "{path: 'blog/index.html', ...} -> serves '/blog', "
                    + "{path: 'blog/first-post.html', ...} -> serves '/blog/first-post'] - five files, one "
                    + "submission, one Function. Each page's own relative asset references (e.g. "
                    + "href=\"style.css\") always resolve correctly regardless of the page's depth or how the "
                    + "browser reached it - a <base href> is injected automatically. A STATIC submission needs a "
                    + "package.json with a \"build\" script (entrypoint \"package.json\") that produces a "
                    + "dist/build/out directory containing the site's files, including its own index.html. The "
                    + "build step is real and unrestricted - it always runs `npm ci` (if a lockfile is present) "
                    + "or `npm install`, then always `npm run build`, in that fixed order, with no way to skip or "
                    + "override either step; the \"build\" script itself can run any shell command (cp, mkdir, a "
                    + "bundler, anything) exactly as it would locally, there is nothing STATIC-specific to work "
                    + "around there - but Node/npm itself is a hard requirement for every STATIC deploy, even a "
                    + "single hand-written HTML file with zero real dependencies.\n"
                    + "Security note: submitted code (both this build step and the deployed handler's own "
                    + "execution) currently runs with real host-level access and is not sandboxed. Do not submit "
                    + "code that reads credentials/secrets beyond what an attached Database/Environment resource "
                    + "already provides via context.db(...)/environment variables, makes unexpected outbound "
                    + "network calls, or performs destructive filesystem operations."
    )
    public FunctionVersionSourceResponse submitFunctionVersionSource(
            @McpToolParam(description = "Function id (UUID)") String functionId,
            @McpToolParam(description = "FunctionVersion id (UUID)") String versionId,
            @McpToolParam(description = "Source files, each with a relative path and its full text content") List<SourceFileInput> files,
            @McpToolParam(description = "Entry file relative path, e.g. 'index.mjs'") String entrypoint,
            @McpToolParam(description = "Exported function name to invoke, defaults to 'handler'", required = false) String handler
    ) {
        UUID versionUuid = UUID.fromString(versionId);
        FunctionVersion functionVersion = functionVersionService.getVersionById(
                CurrentMcpUser.id(), UUID.fromString(functionId), versionUuid);

        List<SourceFile> sourceFiles = files.stream().map(f -> new SourceFile(f.path(), f.content())).toList();
        SourceBundle bundle = new SourceBundle(functionVersion.getRuntime(), null, entrypoint, handler, sourceFiles);
        FunctionVersionSource source = functionVersionSourceService.submitSource(versionUuid, bundle);

        return new FunctionVersionSourceResponse(
                source.getFunctionVersionId(),
                source.getRuntimeType(),
                source.getRuntimeVersion(),
                source.getEntrypoint(),
                source.getHandler(),
                sourceFiles.stream().map(SourceFile::relativePath).toList()
        );
    }

    @McpTool(name = "get_function_version_source", description = "Read a FunctionVersion's submitted source, including each file's full content.")
    public FunctionVersionFullSourceResponse getFunctionVersionSource(
            @McpToolParam(description = "Function id (UUID)") String functionId,
            @McpToolParam(description = "FunctionVersion id (UUID)") String versionId
    ) {
        UUID versionUuid = UUID.fromString(versionId);
        functionVersionService.getVersionById(CurrentMcpUser.id(), UUID.fromString(functionId), versionUuid);
        SourceBundle bundle = functionVersionSourceService.findSource(versionUuid)
                .orElseThrow(() -> new ResourceNotFoundException("No source submitted for function version: " + versionId));

        List<FunctionVersionSourceFileResponse> fileResponses = bundle.files().stream()
                .map(file -> new FunctionVersionSourceFileResponse(file.relativePath(), file.content()))
                .toList();
        return new FunctionVersionFullSourceResponse(bundle.entrypoint(), bundle.handler(), fileResponses);
    }

    @McpTool(
            name = "deploy_function_version",
            description = "Start building and deploying a DRAFT FunctionVersion that already has source submitted. "
                    + "Returns immediately once the version is durably PUBLISHING - it does NOT wait for the build "
                    + "to finish, since a real build can take several minutes. Poll get_function_version afterward "
                    + "to see the version reach READY or FAILED, and call get_function_version_build_logs (any time, "
                    + "including after it fails) for the full stdout/stderr of every build stage that ran. A FAILED "
                    + "version cannot be redeployed in place - call create_function_version again (it automatically "
                    + "clones this version's source/config) and fix only what caused the failure, rather than "
                    + "resubmitting everything from scratch."
    )
    public FunctionVersionResponse deployFunctionVersion(
            @McpToolParam(description = "Function id (UUID)") String functionId,
            @McpToolParam(description = "FunctionVersion id (UUID)") String versionId
    ) {
        UUID versionUuid = UUID.fromString(versionId);
        functionVersionService.getVersionById(CurrentMcpUser.id(), UUID.fromString(functionId), versionUuid);
        FunctionVersion deployed = functionVersionDeploymentService.deploy(versionUuid);
        return functionVersionMapper.toResponse(deployed);
    }

    @McpTool(
            name = "get_function_version_build_logs",
            description = "Read a FunctionVersion's persisted build-stage diagnostics: every dependency-install/"
                    + "build stage a deploy attempt ran, in order, each with its exact command, exit code, and full "
                    + "stdout/stderr - whether that stage succeeded or failed. Durable and queryable at any time "
                    + "after deploy_function_version returns, not just in the same session as the failure. Returns "
                    + "an empty list for a version that was never deployed, or whose runtime never needed to run a "
                    + "build stage (e.g. a dependency-free NODE version with no package.json)."
    )
    public List<FunctionVersionBuildLogResponse> getFunctionVersionBuildLogs(
            @McpToolParam(description = "Function id (UUID)") String functionId,
            @McpToolParam(description = "FunctionVersion id (UUID)") String versionId
    ) {
        UUID versionUuid = UUID.fromString(versionId);
        functionVersionService.getVersionById(CurrentMcpUser.id(), UUID.fromString(functionId), versionUuid);
        return functionVersionBuildLogService.listLogs(versionUuid).stream()
                .map(FunctionVersionMcpTools::toBuildLogResponse)
                .toList();
    }

    private static FunctionVersionBuildLogResponse toBuildLogResponse(FunctionVersionBuildLog log) {
        return new FunctionVersionBuildLogResponse(
                log.getStage(), log.getCommand(), log.getExitCode(), log.isSucceeded(), log.isTimedOut(),
                log.getStdout(), log.getStderr(), log.getCreatedAt());
    }

    @McpTool(name = "get_function_version_config", description = "Get a FunctionVersion's environment variables and secret keys (secret values are never returned, only their reference).")
    public FunctionVersionConfigResponse getFunctionVersionConfig(
            @McpToolParam(description = "Function id (UUID)") String functionId,
            @McpToolParam(description = "FunctionVersion id (UUID)") String versionId
    ) {
        return functionVersionConfigService.getConfig(CurrentMcpUser.id(), UUID.fromString(functionId), UUID.fromString(versionId));
    }

    @McpTool(name = "set_function_version_env_var", description = "Create or update one non-secret environment variable on a FunctionVersion.")
    public FunctionVersionConfigResponse setFunctionVersionEnvVar(
            @McpToolParam(description = "Function id (UUID)") String functionId,
            @McpToolParam(description = "FunctionVersion id (UUID)") String versionId,
            @McpToolParam(description = "Variable name") String key,
            @McpToolParam(description = "Variable value") String value
    ) {
        return functionVersionConfigService.upsertEnvVar(
                CurrentMcpUser.id(), UUID.fromString(functionId), UUID.fromString(versionId), key, value);
    }

    @McpTool(name = "set_function_version_secret", description = "Create or update one secret on a FunctionVersion. The value is stored in OpenBao, never returned again afterward.")
    public FunctionVersionConfigResponse setFunctionVersionSecret(
            @McpToolParam(description = "Function id (UUID)") String functionId,
            @McpToolParam(description = "FunctionVersion id (UUID)") String versionId,
            @McpToolParam(description = "Secret name") String key,
            @McpToolParam(description = "Secret value") String value
    ) {
        return functionVersionConfigService.upsertSecret(
                CurrentMcpUser.id(), UUID.fromString(functionId), UUID.fromString(versionId), key, value);
    }

    @McpTool(name = "list_function_version_databases", description = "List the Database resources attached to a FunctionVersion.")
    public List<FunctionVersionDatabaseAttachmentResponse> listFunctionVersionDatabases(
            @McpToolParam(description = "Function id (UUID)") String functionId,
            @McpToolParam(description = "FunctionVersion id (UUID)") String versionId
    ) {
        return functionVersionDatabaseService.listAttachments(CurrentMcpUser.id(), UUID.fromString(functionId), UUID.fromString(versionId));
    }

    @McpTool(
            name = "attach_function_version_database",
            description = "Attach a Database resource to a FunctionVersion so its handler can reach it via "
                    + "context.db(name) at invocation time, with no connection code of its own. context.db(name) "
                    + "returns a real node-postgres (pg) Pool, cached and reused across invocations - call "
                    + ".query(sql, params) on it directly, e.g.: `const pool = context.db('primary'); const "
                    + "{ rows } = await pool.query('select * from orders where id = $1', [id]);`. Handler "
                    + "signature is `handler(input, context)` - context is the second argument. There is no "
                    + "separate migration/seed tool: to create tables or seed data in a freshly attached "
                    + "Database, write and deploy a one-off Function whose handler runs your DDL/seed SQL via "
                    + "context.db(...).query(...), then invoke it once - that IS the migration mechanism, not a "
                    + "workaround for a missing one."
    )
    public List<FunctionVersionDatabaseAttachmentResponse> attachFunctionVersionDatabase(
            @McpToolParam(description = "Function id (UUID)") String functionId,
            @McpToolParam(description = "FunctionVersion id (UUID)") String versionId,
            @McpToolParam(description = "Database id (UUID) - see list_databases") String databaseId
    ) {
        return functionVersionDatabaseService.attachDatabase(
                CurrentMcpUser.id(), UUID.fromString(functionId), UUID.fromString(versionId), UUID.fromString(databaseId));
    }

    @McpTool(name = "detach_function_version_database", description = "Detach a Database resource from a FunctionVersion.")
    public List<FunctionVersionDatabaseAttachmentResponse> detachFunctionVersionDatabase(
            @McpToolParam(description = "Function id (UUID)") String functionId,
            @McpToolParam(description = "FunctionVersion id (UUID)") String versionId,
            @McpToolParam(description = "Database id (UUID)") String databaseId
    ) {
        return functionVersionDatabaseService.detachDatabase(
                CurrentMcpUser.id(), UUID.fromString(functionId), UUID.fromString(versionId), UUID.fromString(databaseId));
    }
}
