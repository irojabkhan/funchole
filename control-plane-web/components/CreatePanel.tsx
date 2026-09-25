import type { ReactNode } from "react";
import { panelClass } from "@/components/Panel";

interface CreatePanelProps {
  title: string;
  description: string;
  children: ReactNode;
}

export function CreatePanel({ title, description, children }: CreatePanelProps) {
  return (
    <div className={`${panelClass} overflow-hidden`}>
      <div className="border-b border-border bg-surface-2/45 px-5 py-4">
        <p className="text-xs font-bold uppercase tracking-[0.18em] text-accent">Create</p>
        <h2 className="mt-1 text-lg font-bold tracking-tight text-foreground">{title}</h2>
        <p className="mt-1 text-sm leading-6 text-muted">{description}</p>
      </div>
      <div className="p-5">{children}</div>
    </div>
  );
}
