import type { ReactNode } from "react";
import { Panel } from "@/components/Panel";

interface ResourceListProps {
  title: string;
  description?: string;
  children: ReactNode;
}

export function ResourceList({ title, description, children }: ResourceListProps) {
  return (
    <Panel className="overflow-hidden">
      <div className="border-b border-border px-5 py-4">
        <h2 className="text-lg font-bold tracking-tight text-foreground">{title}</h2>
        {description && <p className="mt-1 text-sm text-muted">{description}</p>}
      </div>
      <div className="divide-y divide-border">{children}</div>
    </Panel>
  );
}

export function ResourceListState({ children }: { children: ReactNode }) {
  return <div className="px-5 py-10 text-center text-sm text-muted">{children}</div>;
}
