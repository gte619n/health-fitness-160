"use client";

import { useEffect, useRef, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { useConfirm } from "@/components/ui/ConfirmDialog";
import { useToast } from "@/components/ui/Toast";

// Overflow menu + re-evaluate control for the Goal roadmap detail header.
// Server-component page passes the mutations as server-action props.

type Props = {
  goalTitle: string;
  archiveGoal: () => Promise<void>;
  reevaluateGoal: () => Promise<void>;
};

export function GoalDetailActions({
  goalTitle,
  archiveGoal,
  reevaluateGoal,
}: Props) {
  const router = useRouter();
  const confirm = useConfirm();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [pending, startTransition] = useTransition();
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function onClick(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    window.addEventListener("mousedown", onClick);
    return () => window.removeEventListener("mousedown", onClick);
  }, [open]);

  function onReevaluate() {
    setOpen(false);
    startTransition(async () => {
      try {
        await reevaluateGoal();
        toast.success("Re-evaluated", {
          description: "Steps were checked against the latest data.",
        });
      } catch {
        toast.error("Couldn't re-evaluate");
      }
    });
  }

  async function onArchive() {
    setOpen(false);
    const ok = await confirm({
      title: "Archive this goal?",
      description: `"${goalTitle}" will move to Archived. You can still view it; this doesn't delete any data.`,
      confirmLabel: "Archive",
      tone: "danger",
    });
    if (!ok) return;
    startTransition(async () => {
      try {
        await archiveGoal();
        toast.success("Goal archived");
        router.push("/me/goals");
      } catch {
        toast.error("Couldn't archive goal");
      }
    });
  }

  return (
    <div className="flex items-center gap-2">
      <button
        type="button"
        onClick={onReevaluate}
        disabled={pending}
        className="caps-mono cursor-pointer rounded-md border-[0.5px] border-border-default bg-canvas px-3 py-1.5 text-[10px] tracking-[0.06em] text-secondary hover:text-primary disabled:opacity-60"
      >
        Re-evaluate
      </button>

      <div className="relative" ref={menuRef}>
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-haspopup="menu"
          aria-expanded={open}
          aria-label="Goal actions"
          className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-md border-[0.5px] border-border-default bg-canvas text-secondary hover:text-primary"
        >
          <span aria-hidden className="text-[16px] leading-none">⋯</span>
        </button>
        {open ? (
          <div
            role="menu"
            className="absolute right-0 z-50 mt-1 w-44 overflow-hidden rounded-md border-[0.5px] border-border-default bg-surface py-1 shadow-[0_12px_32px_rgba(0,0,0,0.12)]"
          >
            <MenuItem onClick={onArchive} danger>
              Archive (soft delete)
            </MenuItem>
          </div>
        ) : null}
      </div>
    </div>
  );
}

function MenuItem({
  onClick,
  danger,
  children,
}: {
  onClick: () => void;
  danger?: boolean;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onClick}
      className={`block w-full cursor-pointer px-3 py-1.5 text-left text-[12px] hover:bg-canvas-muted ${
        danger ? "text-alert" : "text-primary"
      }`}
    >
      {children}
    </button>
  );
}
