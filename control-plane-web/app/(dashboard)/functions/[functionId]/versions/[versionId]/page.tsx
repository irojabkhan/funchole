"use client";

import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useRef, useState, type ChangeEvent } from "react";
import CodeMirror, { EditorView } from "@uiw/react-codemirror";
import { syntaxHighlighting } from "@codemirror/language";
import { StatusBadge } from "@/components/StatusBadge";
import { panelClass, Panel } from "@/components/Panel";
import { Button } from "@/components/Button";
import { inputClass, labelClass, fieldClass } from "@/components/Input";
import {
  codeSyntaxColorsDark,
  codeSyntaxColorsLight,
  editorChrome,
  JsonEditor,
  languageForPath,
  useIsDarkMode,
} from "@/components/CodeEditor";
import { OutputLog } from "@/components/OutputLog";
import {
  ArrowLeftIcon,
  ChevronRightIcon,
  UploadIcon,
  PlayIcon,
  ZapIcon,
  CheckIcon,
  KeyIcon,
  PlusIcon,
  XIcon,
} from "@/components/icons";
import { api, ApiError } from "@/lib/api";
import type {
  FunctionResponse,
  FunctionVersionConfigResponse,
  FunctionVersionResponse,
  FunctionVersionSourceResponse,
  InvocationInspectionResponse,
} from "@/lib/types";

const DEFAULT_SOURCE = `export async function handler(input) {
  return { status: 200, body: { ok: true, input } };
}
`;

export default function FunctionVersionDetailPage() {
  const params = useParams<{ functionId: string; versionId: string }>();
  const { functionId, versionId } = params;
  const copyFrom = useSearchParams().get("copyFrom");

  const [fn, setFn] = useState<FunctionResponse | null>(null);
  const [version, setVersion] = useState<FunctionVersionResponse | null>(null);
  const [source, setSource] = useState<FunctionVersionSourceResponse | null>(null);
  const [sourceLoaded, setSourceLoaded] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [deployErrorDetails, setDeployErrorDetails] = useState<string[] | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setDeployErrorDetails(null);
      try {
        const fnData = await api.getFunction(functionId);
        if (!cancelled) setFn(fnData);
      } catch {
        if (!cancelled) setError("Failed to load function");
      }
      try {
        const versionData = await api.getFunctionVersion(functionId, versionId);
        if (!cancelled) setVersion(versionData);
      } catch {
        if (!cancelled) setError("Failed to load version");
      }
      try {
        const sourceData = await api.getFunctionVersionSource(functionId, versionId);
        if (!cancelled) setSource(sourceData);
      } catch {
        if (!cancelled) setSource(null);
      } finally {
        if (!cancelled) setSourceLoaded(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [functionId, versionId, reloadKey]);

  function refresh() {
    setReloadKey((key) => key + 1);
  }

  async function handleDeploy() {
    setError(null);
    setDeployErrorDetails(null);
    setBusy(true);
    try {
      await api.deployFunctionVersion(functionId, versionId);
      refresh();
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
        if (err.details.length > 0) setDeployErrorDetails(err.details);
      } else {
        setError("Failed to deploy");
      }
      refresh();
    } finally {
      setBusy(false);
    }
  }

  if (!fn || !version) {
    return <p className="p-6 text-sm text-muted">Loading…</p>;
  }

  const isDraft = version.status === "DRAFT";

  return (
    <div className="flex flex-col gap-6">
      <nav className="flex items-center gap-1.5 text-sm text-muted">
        <Link href="/functions" className="hover:text-foreground">
          Functions
        </Link>
        <ChevronRightIcon className="h-3.5 w-3.5" />
        <Link href={`/functions/${functionId}`} className="font-medium text-foreground hover:text-cyan-600 dark:hover:text-cyan-400">
          {fn.name}
        </Link>
        <ChevronRightIcon className="h-3.5 w-3.5" />
        <span className="font-mono text-foreground">v{version.version}</span>
      </nav>

      <div className="flex items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <Link
            href={`/functions/${functionId}`}
            className="mt-1 flex h-8 w-8 items-center justify-center rounded-lg border border-border text-muted hover:bg-surface-hover hover:text-foreground"
          >
            <ArrowLeftIcon className="h-4 w-4" />
          </Link>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="font-mono text-2xl font-semibold tracking-tight text-foreground">v{version.version}</h1>
              <StatusBadge status={version.status} />
            </div>
            <p className="mt-1 text-xs text-muted">{version.runtime} runtime</p>
          </div>
        </div>
        {isDraft && (
          <Button variant="primary" onClick={handleDeploy} disabled={busy || !source}>
            <PlayIcon className="h-4 w-4" />
            Deploy
          </Button>
        )}
      </div>

      {error && (
        <div className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-600 dark:bg-rose-500/10 dark:text-rose-400">
          <p role="alert">{error}</p>
          {deployErrorDetails && (
            <pre className="mt-2 max-h-64 overflow-auto whitespace-pre-wrap rounded-md bg-rose-100/60 p-2 font-mono text-xs dark:bg-black/30">
              {deployErrorDetails.join("\n")}
            </pre>
          )}
        </div>
      )}

      {version.status === "FAILED" && !deployErrorDetails && (
        <p className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-600 dark:bg-rose-500/10 dark:text-rose-400">
          This version failed to build. Build output isn&apos;t persisted, so it&apos;s only shown right after a deploy
          attempt in this session. Create a new draft version to try again.
        </p>
      )}

      <SourcePanel
        functionId={functionId}
        versionId={versionId}
        source={source}
        sourceLoaded={sourceLoaded}
        isDraft={isDraft}
        copyFrom={copyFrom}
        onSubmitted={refresh}
        onError={setError}
      />

      <ConfigPanel functionId={functionId} versionId={versionId} onError={setError} />

      {version.status === "READY" && (
        <TestInvokePanel functionId={functionId} versionId={versionId} onError={setError} />
      )}

      {version.artifactSha256 && (
        <Panel className="p-4">
          <p className="text-sm font-medium text-foreground">Artifact</p>
          <dl className="mt-2 grid gap-1.5 text-xs">
            <div className="flex gap-2">
              <dt className="w-28 shrink-0 text-muted">SHA-256</dt>
              <dd className="break-all font-mono text-foreground">{version.artifactSha256}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="w-28 shrink-0 text-muted">Size</dt>
              <dd className="text-foreground">{version.artifactSizeBytes} bytes</dd>
            </div>
            <div className="flex gap-2">
              <dt className="w-28 shrink-0 text-muted">Published</dt>
              <dd className="text-foreground">
                {version.artifactPublishedAt ? new Date(version.artifactPublishedAt).toLocaleString() : "—"}
              </dd>
            </div>
          </dl>
        </Panel>
      )}
    </div>
  );
}

interface SourcePanelProps {
  functionId: string;
  versionId: string;
  source: FunctionVersionSourceResponse | null;
  sourceLoaded: boolean;
  isDraft: boolean;
  copyFrom: string | null;
  onSubmitted: () => void;
  onError: (message: string) => void;
}

interface SourceFileDraft {
  path: string;
  content: string;
}

const DEFAULT_ENTRYPOINT = "index.mjs";
const DEFAULT_FILES: SourceFileDraft[] = [{ path: DEFAULT_ENTRYPOINT, content: DEFAULT_SOURCE }];

function SourcePanel({ functionId, versionId, source, sourceLoaded, isDraft, copyFrom, onSubmitted, onError }: SourcePanelProps) {
  const [editing, setEditing] = useState(false);
  const [files, setFiles] = useState<SourceFileDraft[]>(DEFAULT_FILES);
  const [activePath, setActivePath] = useState(DEFAULT_ENTRYPOINT);
  const [entrypoint, setEntrypoint] = useState(DEFAULT_ENTRYPOINT);
  const [handler, setHandler] = useState("handler");
  const [addingFile, setAddingFile] = useState(false);
  const [newFileName, setNewFileName] = useState("");
  const [busy, setBusy] = useState(false);
  const [copying, setCopying] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const isDark = useIsDarkMode();

  // Derive the initial editing mode - and, when replacing an existing
  // submission, seed the file list from its known paths/entrypoint/handler -
  // once the real fetch settles, adjusting state during render (React's
  // documented pattern for this) rather than in an effect. `initializedFor`
  // guards it to fire only on that one loading->loaded transition, so a
  // user's own "Replace source"/"Cancel" toggle is never overwritten by a
  // later refresh. Note the backend only ever returns file paths, never
  // content, so re-editing an EXISTING submission starts those files empty -
  // there is no way to read back what was previously submitted for THIS
  // version. A brand new version with nothing of its own yet and a
  // `copyFrom` id (set by "New draft version from this one") instead fetches
  // that other version's real file content below, via a separate effect.
  const [initializedFor, setInitializedFor] = useState(false);
  if (sourceLoaded && !initializedFor) {
    setInitializedFor(true);
    setEditing(!source && isDraft);
    if (source) {
      const seeded = source.relativePaths.map((path) => ({ path, content: "" }));
      setFiles(seeded.length > 0 ? seeded : DEFAULT_FILES);
      setActivePath(source.entrypoint || seeded[0]?.path || DEFAULT_ENTRYPOINT);
      setEntrypoint(source.entrypoint);
      setHandler(source.handler);
    }
  }

  useEffect(() => {
    if (!sourceLoaded || source || !copyFrom) return;
    let cancelled = false;
    (async () => {
      setCopying(true);
      try {
        const data = await api.getFunctionVersionSourceFiles(functionId, copyFrom);
        if (cancelled) return;
        const copied = data.files.map((f) => ({ path: f.path, content: f.content }));
        setFiles(copied.length > 0 ? copied : DEFAULT_FILES);
        setActivePath(data.entrypoint || copied[0]?.path || DEFAULT_ENTRYPOINT);
        setEntrypoint(data.entrypoint || copied[0]?.path || DEFAULT_ENTRYPOINT);
        setHandler(data.handler || "handler");
      } catch (err) {
        if (!cancelled) onError(err instanceof ApiError ? err.message : "Failed to copy source from the selected version");
      } finally {
        if (!cancelled) setCopying(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [sourceLoaded, source, copyFrom, functionId, onError]);

  if (!sourceLoaded) {
    return (
      <Panel className="p-4">
        <p className="text-sm text-muted">Loading source…</p>
      </Panel>
    );
  }

  const activeFile = files.find((f) => f.path === activePath) ?? files[0];

  function updateActiveContent(content: string) {
    setFiles((prev) => prev.map((f) => (f.path === activePath ? { ...f, content } : f)));
  }

  function handleAddFile() {
    const name = newFileName.trim();
    if (!name) {
      setAddingFile(false);
      return;
    }
    if (files.some((f) => f.path === name)) {
      onError(`A file named "${name}" already exists`);
      return;
    }
    setFiles((prev) => [...prev, { path: name, content: "" }]);
    setActivePath(name);
    setNewFileName("");
    setAddingFile(false);
  }

  function handleRemoveFile(path: string) {
    if (files.length <= 1) return;
    const remaining = files.filter((f) => f.path !== path);
    setFiles(remaining);
    if (activePath === path) setActivePath(remaining[0].path);
    if (entrypoint === path) setEntrypoint(remaining[0].path);
  }

  async function handleFilesPicked(event: ChangeEvent<HTMLInputElement>) {
    const picked = event.target.files;
    if (!picked || picked.length === 0) return;
    const entries = await Promise.all(
      Array.from(picked).map(async (file) => ({ path: file.name, content: await file.text() }))
    );
    setFiles((prev) => {
      const merged = [...prev];
      for (const entry of entries) {
        const existingIndex = merged.findIndex((f) => f.path === entry.path);
        if (existingIndex >= 0) merged[existingIndex] = entry;
        else merged.push(entry);
      }
      return merged;
    });
    setActivePath(entries[0].path);
    event.target.value = "";
  }

  async function handleSubmit() {
    onError("");
    setBusy(true);
    try {
      const fileObjects = files.map((f) => new File([f.content], f.path));
      await api.submitFunctionVersionSource(functionId, versionId, fileObjects, entrypoint, handler);
      setEditing(false);
      onSubmitted();
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Failed to submit source");
    } finally {
      setBusy(false);
    }
  }

  if (!editing) {
    return (
      <Panel className="p-4">
        <div className="flex items-center justify-between">
          <p className="text-sm font-medium text-foreground">Source</p>
          {isDraft && (
            <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
              <UploadIcon className="h-3.5 w-3.5" />
              Replace source
            </Button>
          )}
        </div>
        {source ? (
          <dl className="mt-2 grid gap-1.5 text-xs">
            <div className="flex gap-2">
              <dt className="w-24 shrink-0 text-muted">Entrypoint</dt>
              <dd className="font-mono text-foreground">{source.entrypoint}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="w-24 shrink-0 text-muted">Handler</dt>
              <dd className="font-mono text-foreground">{source.handler}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="w-24 shrink-0 text-muted">Files</dt>
              <dd className="font-mono text-foreground">{source.relativePaths.join(", ")}</dd>
            </div>
          </dl>
        ) : (
          <p className="mt-2 text-sm text-muted">No source submitted yet.</p>
        )}
      </Panel>
    );
  }

  return (
    <div className={`${panelClass} flex flex-col gap-4 p-4`}>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <p className="text-sm font-medium text-foreground">Source editor</p>
          {copying && <span className="text-xs text-muted">Copying files from the previous version…</span>}
        </div>
        <div className="flex gap-2">
          <input ref={fileInputRef} type="file" multiple className="hidden" onChange={handleFilesPicked} />
          <Button variant="secondary" size="sm" disabled={copying} onClick={() => fileInputRef.current?.click()}>
            <UploadIcon className="h-3.5 w-3.5" />
            Upload file(s)
          </Button>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <label className={fieldClass}>
          <span className={labelClass}>Entrypoint</span>
          <select value={entrypoint} onChange={(e) => setEntrypoint(e.target.value)} className={inputClass}>
            {files.map((f) => (
              <option key={f.path} value={f.path}>
                {f.path}
              </option>
            ))}
          </select>
        </label>
        <label className={fieldClass}>
          <span className={labelClass}>Handler (exported function name)</span>
          <input
            type="text"
            value={handler}
            onChange={(e) => setHandler(e.target.value)}
            className={`${inputClass} font-mono`}
          />
        </label>
      </div>

      <div className={fieldClass}>
        <span className={labelClass}>Files</span>
        <div className="flex flex-wrap items-center gap-1.5">
          {files.map((f) => (
            <div
              key={f.path}
              onClick={() => setActivePath(f.path)}
              className={`flex cursor-pointer items-center gap-1.5 rounded-md border px-2.5 py-1 text-xs font-mono transition-colors ${
                f.path === activePath
                  ? "border-cyan-500 bg-cyan-50 text-cyan-700 dark:border-cyan-400 dark:bg-cyan-500/10 dark:text-cyan-400"
                  : "border-border text-muted hover:border-border-strong hover:text-foreground"
              }`}
            >
              <span>{f.path}</span>
              {f.path === entrypoint && <span className="text-[9px] uppercase tracking-wide text-muted">entry</span>}
              {files.length > 1 && (
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleRemoveFile(f.path);
                  }}
                  aria-label={`Remove ${f.path}`}
                  className="cursor-pointer text-muted hover:text-rose-600 dark:hover:text-rose-400"
                >
                  <XIcon className="h-3 w-3" />
                </button>
              )}
            </div>
          ))}
          {addingFile ? (
            <div className="flex items-center gap-1">
              <input
                autoFocus
                type="text"
                value={newFileName}
                onChange={(e) => setNewFileName(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") handleAddFile();
                  if (e.key === "Escape") {
                    setAddingFile(false);
                    setNewFileName("");
                  }
                }}
                placeholder="package.json"
                className="h-7 w-32 rounded-md border border-border bg-surface px-2 font-mono text-xs text-foreground outline-none focus:border-cyan-500 dark:focus:border-cyan-400"
              />
              <Button variant="secondary" size="sm" onClick={handleAddFile}>
                Add
              </Button>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => setAddingFile(true)}
              className="flex cursor-pointer items-center gap-1 rounded-md border border-dashed border-border px-2.5 py-1 text-xs text-muted hover:border-cyan-500/60 hover:text-cyan-600 dark:hover:text-cyan-400"
            >
              <PlusIcon className="h-3 w-3" />
              New file
            </button>
          )}
        </div>
      </div>

      <div className={fieldClass}>
        <span className={labelClass}>{activeFile?.path ?? "Code"}</span>
        <div className="overflow-hidden rounded-lg border border-border bg-surface focus-within:border-cyan-500 dark:focus-within:border-cyan-400">
          <CodeMirror
            value={activeFile?.content ?? ""}
            onChange={updateActiveContent}
            extensions={[
              languageForPath(activeFile?.path ?? ""),
              syntaxHighlighting(isDark ? codeSyntaxColorsDark : codeSyntaxColorsLight),
              editorChrome,
              EditorView.lineWrapping,
            ]}
            theme="none"
            basicSetup={{ highlightActiveLine: true }}
            minHeight="14rem"
          />
        </div>
      </div>

      <div className="flex gap-2">
        <Button variant="primary" size="sm" disabled={busy || !entrypoint} onClick={handleSubmit}>
          Submit source
        </Button>
        {source && (
          <Button variant="secondary" size="sm" onClick={() => setEditing(false)}>
            Cancel
          </Button>
        )}
      </div>
    </div>
  );
}

interface ConfigPanelProps {
  functionId: string;
  versionId: string;
  onError: (message: string) => void;
}

function ConfigPanel({ functionId, versionId, onError }: ConfigPanelProps) {
  const [config, setConfig] = useState<FunctionVersionConfigResponse | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await api.getFunctionVersionConfig(functionId, versionId);
        if (!cancelled) setConfig(data);
      } catch (err) {
        if (!cancelled) onError(err instanceof ApiError ? err.message : "Failed to load configuration");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [functionId, versionId, reloadKey, onError]);

  function refresh() {
    setReloadKey((key) => key + 1);
  }

  return (
    <Panel className="flex flex-col gap-4 p-4">
      <div className="flex items-center gap-2">
        <KeyIcon className="h-4 w-4 text-cyan-600 dark:text-cyan-400" />
        <p className="text-sm font-medium text-foreground">Environment &amp; secrets</p>
      </div>
      <p className="text-xs text-muted">
        Injected into <code className="font-mono">process.env</code> whenever this exact version runs. Secret values
        are stored encrypted and never shown again after saving - only their reference key is displayed.
      </p>

      <ConfigList
        title="Environment variables"
        entries={config?.envVars.map((v) => ({ key: v.key, display: v.value })) ?? null}
        placeholderValue="production"
        onSave={async (key, value) => {
          await api.upsertFunctionVersionEnvVar(functionId, versionId, key, value);
          refresh();
        }}
        onError={onError}
      />

      <ConfigList
        title="Secrets"
        entries={config?.secrets.map((s) => ({ key: s.key, display: s.secretRef })) ?? null}
        placeholderValue="super-secret-value"
        secret
        onSave={async (key, value) => {
          await api.upsertFunctionVersionSecret(functionId, versionId, key, value);
          refresh();
        }}
        onError={onError}
      />
    </Panel>
  );
}

interface ConfigListProps {
  title: string;
  entries: { key: string; display: string }[] | null;
  placeholderValue: string;
  secret?: boolean;
  onSave: (key: string, value: string) => Promise<void>;
  onError: (message: string) => void;
}

function ConfigList({ title, entries, placeholderValue, secret, onSave, onError }: ConfigListProps) {
  const [adding, setAdding] = useState(false);
  const [key, setKey] = useState("");
  const [value, setValue] = useState("");
  const [busy, setBusy] = useState(false);

  async function handleSave() {
    onError("");
    setBusy(true);
    try {
      await onSave(key.trim(), value);
      setKey("");
      setValue("");
      setAdding(false);
    } catch (err) {
      onError(err instanceof ApiError ? err.message : `Failed to save ${title.toLowerCase()}`);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="rounded-lg border border-border">
      <div className="flex items-center justify-between border-b border-border px-3 py-2">
        <p className="text-xs font-medium text-foreground">{title}</p>
        {!adding && (
          <Button variant="secondary" size="sm" onClick={() => setAdding(true)}>
            <PlusIcon className="h-3.5 w-3.5" />
            Add
          </Button>
        )}
      </div>

      {entries === null ? (
        <p className="px-3 py-3 text-xs text-muted">Loading…</p>
      ) : entries.length === 0 && !adding ? (
        <p className="px-3 py-3 text-xs text-muted">None set.</p>
      ) : (
        <ul className="divide-y divide-border">
          {entries.map((entry) => (
            <li key={entry.key} className="flex items-center gap-3 px-3 py-2 text-xs">
              <span className="w-40 shrink-0 truncate font-mono font-medium text-foreground">{entry.key}</span>
              <span className="truncate font-mono text-muted">{secret ? `configured (${entry.display})` : entry.display}</span>
            </li>
          ))}
        </ul>
      )}

      {adding && (
        <div className="flex flex-wrap items-end gap-2 border-t border-border px-3 py-2.5">
          <label className={fieldClass}>
            <span className={labelClass}>Key</span>
            <input
              type="text"
              value={key}
              onChange={(e) => setKey(e.target.value)}
              placeholder="API_KEY"
              className={`${inputClass} w-40 font-mono text-xs`}
            />
          </label>
          <label className={fieldClass}>
            <span className={labelClass}>Value</span>
            <input
              type={secret ? "password" : "text"}
              value={value}
              onChange={(e) => setValue(e.target.value)}
              placeholder={placeholderValue}
              className={`${inputClass} w-48 font-mono text-xs`}
            />
          </label>
          <Button variant="primary" size="sm" disabled={busy || !key.trim()} onClick={handleSave}>
            Save
          </Button>
          <Button variant="secondary" size="sm" onClick={() => setAdding(false)}>
            Cancel
          </Button>
        </div>
      )}
    </div>
  );
}

interface TestInvokePanelProps {
  functionId: string;
  versionId: string;
  onError: (message: string) => void;
}

function TestInvokePanel({ functionId, versionId, onError }: TestInvokePanelProps) {
  const [input, setInput] = useState("{}");
  const [busy, setBusy] = useState(false);
  const [invocationId, setInvocationId] = useState<string | null>(null);
  const [initialStatus, setInitialStatus] = useState<string | null>(null);
  const [inspection, setInspection] = useState<InvocationInspectionResponse | null>(null);
  const [inspecting, setInspecting] = useState(false);
  const currentStatus = inspection?.status ?? initialStatus ?? "PENDING";

  const inputError = useMemo(() => {
    if (input.trim() === "") return null;
    try {
      JSON.parse(input);
      return null;
    } catch (err) {
      return err instanceof Error ? err.message : "Invalid JSON";
    }
  }, [input]);

  // Execution is asynchronous (the Dispatcher picks the invocation up off
  // NATS), so right after Run it's still PENDING. Poll a few times so the
  // user sees it actually complete instead of assuming it's stuck.
  useEffect(() => {
    if (!invocationId) return;
    let cancelled = false;
    let attempts = 0;
    const timer = setInterval(async () => {
      attempts += 1;
      try {
        const data = await api.getInvocation(invocationId);
        if (cancelled) return;
        setInspection(data);
        if (data.status !== "PENDING" || attempts >= 10) {
          clearInterval(timer);
        }
      } catch {
        clearInterval(timer);
      }
    }, 1000);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [invocationId]);

  async function handleRun() {
    onError("");
    setInspection(null);
    setBusy(true);
    try {
      const result = await api.invokeFunctionVersion(functionId, versionId, input);
      setInvocationId(result.invocationId);
      setInitialStatus(result.initialStatus);
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Failed to invoke");
    } finally {
      setBusy(false);
    }
  }

  async function handleInspect() {
    if (!invocationId) return;
    setInspecting(true);
    try {
      const data = await api.getInvocation(invocationId);
      setInspection(data);
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Failed to inspect invocation");
    } finally {
      setInspecting(false);
    }
  }

  return (
    <Panel className="flex flex-col gap-4 p-4">
      <div className="flex items-center gap-2">
        <ZapIcon className="h-4 w-4 text-amber-600 dark:text-amber-400" />
        <p className="text-sm font-medium text-foreground">Test invoke</p>
      </div>
      <p className="text-xs text-muted">
        Runs this exact version directly, with no Flow or Gateway involved. Execution happens asynchronously through
        the same Dispatcher a real request uses - status updates automatically below once it completes.
      </p>

      <JsonEditor value={input} onChange={setInput} error={inputError} />

      <div>
        <Button variant="primary" size="sm" disabled={busy || !!inputError} onClick={handleRun}>
          <PlayIcon className="h-3.5 w-3.5" />
          Run
        </Button>
      </div>

      {invocationId && (
        <div className="rounded-lg border border-border bg-background p-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-xs">
              <CheckIcon className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" />
              <span className="text-muted">Invocation status</span>
              <StatusBadge status={currentStatus} />
            </div>
            <Button variant="secondary" size="sm" disabled={inspecting} onClick={handleInspect}>
              Inspect
            </Button>
          </div>
          <p className="mt-1.5 font-mono text-xs text-muted">{invocationId}</p>

          {inspection && (
            <div className="mt-3 border-t border-border pt-3">
              <dl className="grid gap-1.5 text-xs">
                <div className="flex gap-2">
                  <dt className="w-20 shrink-0 text-muted">Status</dt>
                  <dd>
                    <StatusBadge status={inspection.status} />
                  </dd>
                </div>
                <div className="flex gap-2">
                  <dt className="w-20 shrink-0 text-muted">Input</dt>
                  <dd className="break-all font-mono text-foreground">{inspection.inputPayload}</dd>
                </div>
                {inspection.result && (
                  <div className="flex gap-2">
                    <dt className="w-20 shrink-0 text-muted">Result</dt>
                    <dd className="break-all font-mono text-foreground">{inspection.result}</dd>
                  </div>
                )}
                {inspection.error && (
                  <div className="flex gap-2">
                    <dt className="w-20 shrink-0 text-muted">Error</dt>
                    <dd className="break-all font-mono text-rose-600 dark:text-rose-400">{inspection.error}</dd>
                  </div>
                )}
              </dl>

              <OutputLog steps={inspection.steps} />
            </div>
          )}
        </div>
      )}
    </Panel>
  );
}
