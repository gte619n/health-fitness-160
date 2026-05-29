"use client";

import { useRouter } from "next/navigation";
import { useToast } from "@/components/ui/Toast";
import { GoalProposalCard } from "@/components/goals/GoalProposalCard";
import type { GoalProposalDraft } from "@/components/goals/GoalProposalCard";

// Client host for the blank manual editor. Bridges the presentation-
// agnostic GoalProposalCard to the server action that persists the whole
// structure, then navigates to the new roadmap. The chat host will reuse
// GoalProposalCard the same way against the commit endpoint.

export type SaveProposalAction = (
  draft: GoalProposalDraft,
) => Promise<{ goalId: string }>;

export function ManualGoalEditor({ save }: { save: SaveProposalAction }) {
  const router = useRouter();
  const toast = useToast();

  async function onSave(draft: GoalProposalDraft) {
    const { goalId } = await save(draft);
    toast.success("Goal created");
    router.push(`/me/goals/${goalId}` as never);
  }

  return (
    <GoalProposalCard
      heading="Create a goal"
      onSave={onSave}
      onDiscard={() => router.push("/me/goals" as never)}
    />
  );
}
