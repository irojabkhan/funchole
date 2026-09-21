package com.funchole.backend.controlplane.mcp;

import com.funchole.backend.controlplane.dto.FunctionVersionConfigResponse;
import com.funchole.backend.controlplane.dto.FunctionVersionCreateRequest;
import com.funchole.backend.controlplane.dto.FunctionVersionDatabaseAttachmentResponse;
import com.funchole.backend.controlplane.dto.FunctionVersionFullSourceResponse;
import com.funchole.backend.controlplane.dto.FunctionVersionResponse;
import com.funchole.backend.controlplane.dto.FunctionVersionSourceFileResponse;
import com.funchole.backend.controlplane.dto.FunctionVersionSourceResponse;
import com.funchole.backend.controlplane.entity.FunctionVersion;
import com.funchole.backend.controlplane.entity.FunctionVersionSource;
import com.funchole.backend.controlplane.entity.SourceBundle;
import com.funchole.backend.controlplane.entity.SourceFile;
import com.funchole.backend.controlplane.mapper.FunctionVersionMapper;
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
    private final FunctionVersionSourceService functionVersionSourceService;
    private final FunctionVersionDeploymentService functionVersionDeploymentService;
    private final FunctionVersionConfigService functionVersionConfigService;
    private final FunctionVersionDatabaseService functionVersionDatabaseService;
    private final FunctionVersionMapper functionVersionMapper;

    public FunctionVersionMcpTools(
            FunctionVersionService functionVersionService,
            FunctionVersionSourceService functionVersionSourceService,
            FunctionVersionDeploymentService functionVersionDeploymentService,
            FunctionVersionConfigService functionVersionConfigService,
            FunctionVersionDatabaseService functionVersionDatabaseService,
            FunctionVersionMapper functionVersionMapper
    ) {
        this.functionVersionService = functionVersionService;
        this.functionVersionSourceService = functionVersionSourceService;
        this.functionVersionDeploymentService = functionVersionDeploymentService;
        this.functionVersionConfigService = functionVersionConfigService;
        this.functionVersionDatabaseService = functionVersionDatabaseService;
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
            description = "Create a new DRAFT FunctionVersion under a Function. Submit source next with "
                    + "submit_function_version_source, then deploy_function_version to make it READY."
    )
    public FunctionVersionResponse createFunctionVersion(
            @McpToolParam(description = "Function id (UUID)") String functionId,
            @McpToolParam(description = "Runtime: NODE (runs your handler code) or STATIC (serves a pre-built "
                    + "static site's files directly, no code execution) - optional, defaults to the parent "
                    + "Function's own runtime", required = false) String runtime,
            @McpToolParam(description = "Free-form metadata string - optional", required = false) String metadata
    ) {
        FunctionVersion version = functionVersionService.createDraftVersion(
                CurrentMcpUser.id(), UUID.fromString(functionId), new FunctionVersionCreateRequest(runtime, metadata));
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
            description = "Build and deploy a DRAFT FunctionVersion that already has source submitted, moving it to "
                    + "PUBLISHING then READY (or FAILED with build error detail)."
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
