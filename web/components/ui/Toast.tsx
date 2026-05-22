"use client";

import { createContext, useCallback, useContext, useMemo, useRef, useState } from "react";

// Toast system. One <Toaster /> at the root holds the list of active
// toasts and renders them in a fixed bottom-right stack. `useToast()`
// returns an imperative API any component can call:
//
//   const { toast } = useToast();
//   toast.success("Saved");
//   toast.error("Couldn't save", { description: "Try again." });
//
// Each toast auto-dismisses after `duration` ms (default 4000). Pass
// duration=0 to make it sticky (user must click ×).

type ToastKind = "success" | "error" | "info";

type ToastInput = {
  title: string;
  description?: string;
  duration?: number;
};

type ToastRecord = ToastInput & {
  id: number;
  kind: ToastKind;
};

type ToastApi = {
  success: (title: string, opts?: Omit<ToastInput, "title">) => void;
  error: (title: string, opts?: Omit<ToastInput, "title">) => void;
  info: (title: string, opts?: Omit<ToastInput, "title">) => void;
  dismiss: (id: number) => void;
};

const ToastContext = createContext<ToastApi | null>(null);

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used inside <Toaster>");
  return ctx;
}

export function Toaster({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastRecord[]>([]);
  const idRef = useRef(0);

  const dismiss = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const push = useCallback(
    (kind: ToastKind, input: ToastInput) => {
      const id = ++idRef.current;
      const duration = input.duration ?? 4000;
      setToasts((prev) => [...prev, { ...input, id, kind }]);
      if (duration > 0) {
        setTimeout(() => dismiss(id), duration);
      }
    },
    [dismiss],
  );

  const api = useMemo<ToastApi>(
    () => ({
      success: (title, opts) => push("success", { title, ...(opts ?? {}) }),
      error: (title, opts) => push("error", { title, ...(opts ?? {}) }),
      info: (title, opts) => push("info", { title, ...(opts ?? {}) }),
      dismiss,
    }),
    [push, dismiss],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="pointer-events-none fixed bottom-4 right-4 z-[100] flex w-[360px] max-w-[calc(100vw-2rem)] flex-col-reverse gap-2">
        {toasts.map((t) => (
          <ToastView key={t.id} toast={t} onDismiss={() => dismiss(t.id)} />
        ))}
      </div>
    </ToastContext.Provider>
  );
}

function ToastView({
  toast,
  onDismiss,
}: {
  toast: ToastRecord;
  onDismiss: () => void;
}) {
  const accent =
    toast.kind === "success"
      ? "border-accent/40"
      : toast.kind === "error"
      ? "border-red-600/40"
      : "border-border-default";
  const dot =
    toast.kind === "success"
      ? "bg-accent"
      : toast.kind === "error"
      ? "bg-red-600"
      : "bg-tertiary";

  return (
    <div
      role="status"
      className={`pointer-events-auto rounded-[12px] border-[0.5px] ${accent} bg-surface px-4 py-3 shadow-[0_12px_32px_rgba(0,0,0,0.12)]`}
    >
      <div className="flex items-start gap-3">
        <span
          className={`mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full ${dot}`}
          aria-hidden
        />
        <div className="min-w-0 flex-1">
          <div className="text-[13px] font-medium text-primary">
            {toast.title}
          </div>
          {toast.description && (
            <div className="mt-0.5 font-mono text-[11px] leading-[1.5] text-tertiary">
              {toast.description}
            </div>
          )}
        </div>
        <button
          type="button"
          onClick={onDismiss}
          className="-mr-1 -mt-0.5 cursor-pointer rounded-md px-1.5 py-0.5 font-mono text-[14px] leading-none text-tertiary hover:text-primary"
          aria-label="Dismiss"
        >
          ×
        </button>
      </div>
    </div>
  );
}
