"use client";

import { useMemo, useSyncExternalStore } from "react";
import CodeMirror, { EditorView } from "@uiw/react-codemirror";
import { json, jsonParseLinter } from "@codemirror/lang-json";
import { javascript } from "@codemirror/lang-javascript";
import { linter, lintGutter } from "@codemirror/lint";
import { HighlightStyle, syntaxHighlighting } from "@codemirror/language";
import { tags } from "@lezer/highlight";
import { fieldClass, labelClass } from "@/components/Input";

export const codeSyntaxColorsLight = HighlightStyle.define([
  { tag: [tags.propertyName, tags.attributeName], color: "#0e7490" },
  { tag: tags.string, color: "#047857" },
  { tag: tags.number, color: "#b45309" },
  { tag: [tags.bool, tags.null, tags.keyword, tags.controlKeyword], color: "#7c3aed" },
  { tag: [tags.function(tags.variableName), tags.function(tags.definition(tags.variableName))], color: "#0e7490" },
  { tag: tags.comment, color: "var(--muted)", fontStyle: "italic" },
  { tag: [tags.separator, tags.squareBracket, tags.brace, tags.paren, tags.punctuation, tags.operator], color: "#64748b" },
]);

export const codeSyntaxColorsDark = HighlightStyle.define([
  { tag: [tags.propertyName, tags.attributeName], color: "#22d3ee" },
  { tag: tags.string, color: "#34d399" },
  { tag: tags.number, color: "#fbbf24" },
  { tag: [tags.bool, tags.null, tags.keyword, tags.controlKeyword], color: "#c4b5fd" },
  { tag: [tags.function(tags.variableName), tags.function(tags.definition(tags.variableName))], color: "#22d3ee" },
  { tag: tags.comment, color: "var(--muted)", fontStyle: "italic" },
  { tag: [tags.separator, tags.squareBracket, tags.brace, tags.paren, tags.punctuation, tags.operator], color: "#7c8ba1" },
]);

export const editorChrome = EditorView.theme({
  "&": { backgroundColor: "transparent", fontSize: "0.75rem" },
  ".cm-content": { fontFamily: "var(--font-geist-mono), monospace", caretColor: "var(--foreground)" },
  ".cm-gutters": { backgroundColor: "transparent", border: "none", color: "var(--muted)" },
  ".cm-activeLine": { backgroundColor: "var(--surface-hover)" },
  ".cm-activeLineGutter": { backgroundColor: "var(--surface-hover)" },
  "&.cm-focused": { outline: "none" },
  ".cm-lintRange-error": { backgroundImage: "none", textDecoration: "underline wavy #e11d48" },
});

function subscribeToColorScheme(callback: () => void) {
  const query = window.matchMedia("(prefers-color-scheme: dark)");
  query.addEventListener("change", callback);
  return () => query.removeEventListener("change", callback);
}

function getIsDarkModeSnapshot() {
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

export function useIsDarkMode() {
  return useSyncExternalStore(subscribeToColorScheme, getIsDarkModeSnapshot, () => false);
}

export function languageForPath(path: string) {
  return path.endsWith(".json") ? json() : javascript();
}

interface JsonEditorProps {
  value: string;
  onChange: (value: string) => void;
  error: string | null;
  minHeight?: string;
  label?: string;
}

export function JsonEditor({ value, onChange, error, minHeight = "8rem", label = "Input payload (JSON)" }: JsonEditorProps) {
  const isDark = useIsDarkMode();
  const extensions = useMemo(
    () => [
      json(),
      linter(jsonParseLinter()),
      lintGutter(),
      syntaxHighlighting(isDark ? codeSyntaxColorsDark : codeSyntaxColorsLight),
      editorChrome,
      EditorView.lineWrapping,
    ],
    [isDark]
  );

  return (
    <div className={fieldClass}>
      <span className={labelClass}>{label}</span>
      <div
        className={`overflow-hidden rounded-lg border bg-surface transition-colors ${
          error ? "border-rose-400 dark:border-rose-500/60" : "border-border focus-within:border-cyan-500 dark:focus-within:border-cyan-400"
        }`}
      >
        <CodeMirror
          value={value}
          onChange={onChange}
          extensions={extensions}
          theme="none"
          basicSetup={{ foldGutter: false, highlightActiveLine: true }}
          minHeight={minHeight}
        />
      </div>
      {error && <p className="text-[11px] text-rose-600 dark:text-rose-400">{error}</p>}
    </div>
  );
}
