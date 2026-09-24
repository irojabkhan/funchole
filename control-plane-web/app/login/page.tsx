"use client";

import Script from "next/script";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { api, ApiError } from "@/lib/api";
import { setToken } from "@/lib/auth";
import { Panel } from "@/components/Panel";
import { Button } from "@/components/Button";
import { inputClass, labelClass, fieldClass } from "@/components/Input";

const GOOGLE_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [googleScriptLoaded, setGoogleScriptLoaded] = useState(false);
  const googleButtonRef = useRef<HTMLDivElement>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setPending(true);
    try {
      const token = await api.login(username, password);
      setToken(token.accessToken, token.expiresAt);
      router.replace("/");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Login failed");
      setPending(false);
    }
  }

  const handleGoogleCredential = useCallback(
    async (response: { credential: string }) => {
      setError(null);
      setPending(true);
      try {
        const token = await api.loginWithGoogle(response.credential);
        setToken(token.accessToken, token.expiresAt);
        router.replace("/");
      } catch (err) {
        setError(err instanceof ApiError ? err.message : "Google sign-in failed");
        setPending(false);
      }
    },
    [router]
  );

  // Google's own button only renders once its script has loaded and a real
  // container element exists - both happen asynchronously and independently
  // (the script tag firing onLoad, React committing the ref), so this waits
  // on whichever finishes last rather than assuming an order.
  useEffect(() => {
    if (!GOOGLE_CLIENT_ID || !googleScriptLoaded || !googleButtonRef.current || !window.google) {
      return;
    }
    window.google.accounts.id.initialize({
      client_id: GOOGLE_CLIENT_ID,
      callback: handleGoogleCredential,
    });
    window.google.accounts.id.renderButton(googleButtonRef.current, {
      theme: "outline",
      size: "large",
      width: 320,
      text: "signin_with",
    });
  }, [googleScriptLoaded, handleGoogleCredential]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4">
      <Panel className="w-full max-w-sm p-8 shadow-sm">
        <div className="mb-8 flex items-center gap-2.5">
          <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-cyan-600 text-sm font-bold text-white dark:bg-cyan-500 dark:text-slate-950">
            F
          </span>
          <div>
            <h1 className="text-lg font-semibold tracking-tight text-foreground">FuncHole</h1>
            <p className="text-xs text-muted">Control Plane</p>
          </div>
        </div>

        {GOOGLE_CLIENT_ID && (
          <>
            <Script
              src="https://accounts.google.com/gsi/client"
              async
              defer
              onLoad={() => setGoogleScriptLoaded(true)}
            />
            <div className="mb-6 flex justify-center" ref={googleButtonRef} />
            <div className="mb-6 flex items-center gap-3 text-xs text-muted">
              <span className="h-px flex-1 bg-border" />
              or sign in with a password
              <span className="h-px flex-1 bg-border" />
            </div>
          </>
        )}

        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <label className={fieldClass}>
            <span className={labelClass}>Username</span>
            <input
              type="text"
              required
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className={inputClass}
            />
          </label>

          <label className={fieldClass}>
            <span className={labelClass}>Password</span>
            <input
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className={inputClass}
            />
          </label>

          {error && (
            <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-600 dark:bg-rose-500/10 dark:text-rose-400">
              {error}
            </p>
          )}

          <Button type="submit" variant="primary" disabled={pending} className="mt-2 w-full">
            {pending ? "Signing in…" : "Sign in"}
          </Button>
        </form>
      </Panel>
    </div>
  );
}
