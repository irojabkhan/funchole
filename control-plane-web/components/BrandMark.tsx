import Link from "next/link";

interface BrandMarkProps {
  href?: string;
  showText?: boolean;
  className?: string;
}

export function BrandMark({ href, showText = true, className = "" }: BrandMarkProps) {
  const content = (
    <span className={`inline-flex items-center gap-3 ${className}`}>
      <span className="relative grid h-10 w-10 place-items-center rounded-2xl border border-accent-border bg-accent-soft text-accent shadow-[0_0_40px_rgba(245,166,35,0.18)]">
        <span className="absolute inset-1 rounded-xl border border-accent/20" />
        <svg viewBox="0 0 32 32" fill="none" aria-hidden="true" className="h-6 w-6">
          <path d="M8 17.5c0-5.2 3.7-9.5 8.5-9.5 3.3 0 6.1 1.9 7.5 4.7" stroke="currentColor" strokeWidth="2.3" strokeLinecap="round" />
          <path d="M24 14.5c0 5.2-3.7 9.5-8.5 9.5-3.3 0-6.1-1.9-7.5-4.7" stroke="currentColor" strokeWidth="2.3" strokeLinecap="round" />
          <path d="M10 10.5 8 17.5l6.8-2" stroke="currentColor" strokeWidth="2.3" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M22 21.5 24 14.5l-6.8 2" stroke="currentColor" strokeWidth="2.3" strokeLinecap="round" strokeLinejoin="round" />
          <circle cx="16" cy="16" r="2.5" fill="currentColor" />
        </svg>
      </span>
      {showText && (
        <span className="leading-tight">
          <span className="block text-sm font-bold tracking-tight text-foreground">FuncHole</span>
          <span className="block text-[11px] font-semibold uppercase tracking-[0.18em] text-muted">workspace</span>
        </span>
      )}
    </span>
  );

  if (!href) return content;

  return (
    <Link href={href} className="rounded-2xl outline-none focus-visible:ring-2 focus-visible:ring-accent">
      {content}
    </Link>
  );
}
