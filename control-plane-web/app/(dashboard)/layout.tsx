"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { api } from "@/lib/api";
import { clearToken } from "@/lib/auth";
import type { ProfileResponse } from "@/lib/types";
import {
  GridIcon,
  FunctionIcon,
  WorkflowIcon,
  ServerIcon,
  GlobeIcon,
  DatabaseIcon,
  KeyIcon,
  TerminalIcon,
  LogOutIcon,
  UserIcon,
  SettingsIcon,
  PackageIcon,
} from "@/components/icons";
import { BrandMark } from "@/components/BrandMark";

const NAV_GROUPS = [
  {
    label: "Build",
    items: [
      { href: "/", label: "Overview", icon: GridIcon },
      { href: "/functions", label: "Actions", icon: FunctionIcon },
      { href: "/flows", label: "Workflows", icon: WorkflowIcon },
    ],
  },
  {
    label: "Operate",
    items: [
      { href: "/gateways", label: "Entry Points", icon: ServerIcon },
      { href: "/domains", label: "Custom Domains", icon: GlobeIcon },
    ],
  },
  {
    label: "Configure",
    items: [
      { href: "/environments", label: "Variables & Secrets", icon: KeyIcon },
      { href: "/databases", label: "Data Sources", icon: DatabaseIcon },
      { href: "/api-keys", label: "Agent Access", icon: TerminalIcon },
    ],
  },
  {
    label: "Account",
    items: [
      { href: "/profile", label: "User Profile", icon: UserIcon },
      { href: "/account", label: "Account", icon: UserIcon },
      { href: "/settings", label: "Settings", icon: SettingsIcon },
      { href: "/package", label: "Package", icon: PackageIcon },
    ],
  },
];

const NAV_ITEMS = NAV_GROUPS.flatMap((group) => group.items);

const ACCOUNT_MENU_ITEMS = [
  { href: "/profile", label: "User Profile", icon: UserIcon },
  { href: "/account", label: "Account", icon: UserIcon },
  { href: "/settings", label: "Settings", icon: SettingsIcon },
  { href: "/package", label: "Package", icon: PackageIcon },
];

export default function DashboardLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [userMenuOpen, setUserMenuOpen] = useState(false);

  useEffect(() => {
    let active = true;
    api
      .getProfile()
      .then((p) => {
        if (active) setProfile(p);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, []);

  function handleLogout() {
    clearToken();
    router.replace("/login");
  }

  function isActive(href: string) {
    return href === "/" ? pathname === "/" : pathname.startsWith(href);
  }

  const currentSection = NAV_ITEMS.find((item) => isActive(item.href))?.label ?? "FuncHole";

  return (
    <div className="flex min-h-screen bg-background">
      <aside className="fixed inset-y-0 left-0 z-20 hidden w-72 flex-col border-r border-border bg-[#0d0d10]/95 px-4 py-5 backdrop-blur-xl lg:flex">
        <BrandMark href="/" />
        <div className="mt-8 flex flex-1 flex-col gap-6">
          {NAV_GROUPS.map((group) => (
            <nav key={group.label} className="space-y-2">
              <p className="px-3 text-[11px] font-bold uppercase tracking-[0.2em] text-muted/70">{group.label}</p>
              <div className="space-y-1">
                {group.items.map((item) => {
                  const active = isActive(item.href);
                  const Icon = item.icon;
                  return (
                    <Link
                      key={item.href}
                      href={item.href}
                      aria-current={active ? "page" : undefined}
                      className={`group relative flex items-center gap-3 rounded-2xl px-3 py-2.5 text-sm font-semibold transition-all ${
                        active
                          ? "border border-accent-border bg-accent-soft text-accent shadow-[0_14px_34px_rgba(245,166,35,0.08)]"
                          : "text-muted hover:bg-surface-hover hover:text-foreground"
                      }`}
                    >
                      <Icon className="h-5 w-5" />
                      <span>{item.label}</span>
                      {active && <span className="ml-auto h-2 w-2 rounded-full bg-accent shadow-[0_0_16px_rgba(245,166,35,0.9)]" />}
                    </Link>
                  );
                })}
              </div>
            </nav>
          ))}
        </div>
        <div className="rounded-3xl border border-border bg-surface/70 p-4">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted">Agent setup</p>
          <p className="mt-2 text-sm font-semibold text-foreground">Connect your agent first</p>
          <p className="mt-1 text-xs leading-5 text-muted">Let coding agents handle building. Use the dashboard for access, secrets, logs, and manual checks.</p>
          <Link href="/api-keys" className="mt-3 inline-flex text-xs font-bold text-accent hover:text-accent-hover">
            Configure agent access
          </Link>
        </div>
      </aside>

      <div className="flex min-h-screen flex-1 flex-col lg:pl-72">
        <header className="sticky top-0 z-10 flex h-16 items-center justify-between border-b border-border bg-background/70 px-4 backdrop-blur-xl sm:px-6">
          <div className="flex items-center gap-2 text-sm">
            <span className="lg:hidden">
              <BrandMark href="/" showText={false} />
            </span>
            <span className="hidden font-semibold tracking-tight text-foreground lg:inline">FuncHole</span>
            <ChevronDivider />
            <span className="text-muted">{currentSection}</span>
          </div>
          <div className="relative flex items-center gap-3">
            <button
              type="button"
              onClick={() => setUserMenuOpen((open) => !open)}
              className={`flex h-10 items-center gap-2 rounded-full border px-2 pl-3 text-left transition-colors ${
                userMenuOpen
                  ? "border-accent-border bg-accent-soft text-accent"
                  : "border-border bg-surface/60 text-muted hover:border-border-strong hover:bg-surface-hover hover:text-foreground"
              }`}
              aria-expanded={userMenuOpen}
              aria-label="Open account menu"
            >
              <span className="hidden max-w-32 truncate text-sm font-semibold sm:block">
                {profile?.username || "Account"}
              </span>
              <span className="grid h-7 w-7 place-items-center rounded-full border border-accent-border bg-accent-soft text-accent">
                <span className="text-xs font-bold uppercase">
                  {(profile?.username || profile?.fullName || "U").slice(0, 1)}
                </span>
              </span>
            </button>

            {userMenuOpen && (
              <div className="absolute right-0 top-12 z-30 w-72 overflow-hidden rounded-3xl border border-border bg-[#0d0d10]/98 shadow-[0_24px_90px_rgba(0,0,0,0.45)] backdrop-blur-xl">
                <div className="border-b border-border p-4">
                  <div className="flex items-center gap-3">
                    <span className="grid h-10 w-10 place-items-center rounded-2xl border border-accent-border bg-accent-soft text-accent">
                      <UserIcon className="h-5 w-5" />
                    </span>
                    <span className="min-w-0">
                      <p className="truncate text-sm font-bold text-foreground">{profile?.fullName || profile?.username || "User"}</p>
                      <p className="mt-1 truncate text-xs text-muted">{profile?.email || "Manage your workspace account"}</p>
                    </span>
                  </div>
                </div>
                <div className="p-2">
                  {ACCOUNT_MENU_ITEMS.map((item) => {
                    const Icon = item.icon;
                    return (
                      <Link
                        key={item.href}
                        href={item.href}
                        onClick={() => setUserMenuOpen(false)}
                        className="flex items-center gap-3 rounded-2xl px-3 py-2 text-sm font-semibold text-muted transition-colors hover:bg-surface-hover hover:text-foreground"
                      >
                        <Icon className="h-4 w-4" />
                        {item.label}
                      </Link>
                    );
                  })}
                  <button
                    type="button"
                    onClick={handleLogout}
                    className="flex w-full cursor-pointer items-center gap-3 rounded-2xl px-3 py-2 text-sm font-semibold text-danger transition-colors hover:bg-danger/10"
                  >
                    <LogOutIcon className="h-4 w-4" />
                    Logout
                  </button>
                </div>
              </div>
            )}
          </div>
        </header>
        <nav className="sticky top-16 z-10 flex gap-2 overflow-x-auto border-b border-border bg-background/75 px-4 py-3 backdrop-blur-xl lg:hidden">
          {NAV_ITEMS.map((item) => {
            const active = isActive(item.href);
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={`flex shrink-0 items-center gap-2 rounded-full border px-3 py-2 text-xs font-semibold ${
                  active
                    ? "border-accent-border bg-accent-soft text-accent"
                    : "border-border bg-surface/70 text-muted"
                }`}
              >
                <Icon className="h-4 w-4" />
                {item.label}
              </Link>
            );
          })}
        </nav>
        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 sm:px-6">{children}</main>
      </div>
    </div>
  );
}

function ChevronDivider() {
  return <span className="text-border-strong">/</span>;
}
