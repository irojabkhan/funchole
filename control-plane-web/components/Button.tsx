import type { ButtonHTMLAttributes } from "react";

export type ButtonVariant = "primary" | "secondary" | "danger" | "ghost";
export type ButtonSize = "md" | "sm" | "icon";

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary:
    "bg-accent text-accent-ink shadow-[0_0_0_1px_rgba(245,166,35,0.18),0_10px_30px_rgba(245,166,35,0.16)] hover:bg-accent-hover",
  secondary:
    "border border-border bg-surface/80 text-foreground hover:border-accent-border hover:bg-accent-soft",
  danger:
    "border border-danger/35 bg-danger/10 text-danger hover:bg-danger/15",
  ghost: "text-muted hover:bg-surface-hover hover:text-foreground",
};

const SIZE_CLASSES: Record<ButtonSize, string> = {
  md: "h-9 px-4 text-sm",
  sm: "h-8 px-3 text-xs",
  icon: "h-8 w-8 p-0",
};

export function buttonClasses(variant: ButtonVariant = "secondary", size: ButtonSize = "md") {
  return `inline-flex cursor-pointer items-center justify-center gap-1.5 rounded-xl font-medium transition-all duration-200 hover:-translate-y-0.5 active:translate-y-0 disabled:pointer-events-none disabled:opacity-50 ${VARIANT_CLASSES[variant]} ${SIZE_CLASSES[size]}`;
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
}

export function Button({ variant = "secondary", size = "md", className = "", type = "button", ...props }: ButtonProps) {
  return (
    <button
      type={type}
      className={`${buttonClasses(variant, size)} ${className}`}
      {...props}
    />
  );
}
