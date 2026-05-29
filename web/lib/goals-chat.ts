// Goals chat ↔ GoalProposalCard mapping + shared wire types.
//
// The backend streams a `GoalProposalDto` (see
// backend/api/.../goals/dto/GoalProposalDto.java) over SSE on the
// `proposal` event, and accepts that same DTO shape back on
// `POST /api/me/goals/chat/{threadId}/commit`. The editor component
// `<GoalProposalCard>` works in its own `GoalProposalDraft` model. This
// module is the single seam that converts between the two, both ways.
//
// It is import-safe from client and server (no `apiFetch`, no server env).

import type {
  GoalProposalDraft,
  PhaseDraft,
  StepDraft,
  MetricBindingDraft,
} from "@/components/goals/GoalProposalCard";
import type {
  GoalProposalDto,
  ProposalMetricDto,
  ProposalPhaseDto,
  ProposalStepDto,
} from "@/lib/types/goals-chat-wire";

// Re-export the wire types so chat consumers have a single import surface.
export type {
  GoalProposalDto,
  ProposalMetricDto,
  ProposalPhaseDto,
  ProposalStepDto,
  ChatTokenEvent,
  ChatErrorEvent,
  ChatDoneEvent,
  ChatThread,
} from "@/lib/types/goals-chat-wire";

// ── Local render-key generation ──────────────────────────────────────

let keySeq = 0;
function nextKey(prefix: string): string {
  keySeq += 1;
  return `${prefix}-${keySeq}-${Math.random().toString(36).slice(2, 7)}`;
}

// ── Backend DTO → editor draft (for the inline GoalProposalCard) ─────

export function proposalToDraft(dto: GoalProposalDto): GoalProposalDraft {
  const phases: PhaseDraft[] = (dto.phases ?? []).map((ph) => {
    const steps: StepDraft[] = (ph.steps ?? []).map((s) => {
      let metric: MetricBindingDraft | null = null;
      if (s.metric) {
        const m = s.metric;
        metric = {
          metricKey: m.metricKey,
          comparator: m.comparator ?? "LT",
          targetValue: m.targetValue ?? "",
          windowDays: m.windowDays ?? null,
          countFrom: m.countFrom ?? null,
        };
      }
      const step: StepDraft = {
        key: nextKey("step"),
        title: s.title ?? "",
        kind: s.kind ?? "MANUAL",
        metric,
        validationError: s.validationError ?? null,
        metricError: s.metric?.validationError ?? null,
      };
      return step;
    });
    const phase: PhaseDraft = {
      key: nextKey("phase"),
      title: ph.title ?? "",
      description: ph.description ?? "",
      targetStartDate: ph.targetStartDate ?? "",
      targetEndDate: ph.targetEndDate ?? "",
      steps,
      validationError: ph.validationError ?? null,
    };
    return phase;
  });

  return {
    title: dto.title ?? "",
    description: dto.description ?? "",
    domain: dto.domain ?? "CARDIOVASCULAR",
    targetDate: dto.targetDate ?? "",
    phases,
    validationError: dto.validationError ?? null,
  };
}

// ── Editor draft → backend DTO (for the commit endpoint) ─────────────

export function draftToProposal(draft: GoalProposalDraft): GoalProposalDto {
  const phases: ProposalPhaseDto[] = draft.phases.map((ph) => {
    const steps: ProposalStepDto[] = ph.steps.map((st) => {
      let metric: ProposalMetricDto | null = null;
      if (st.kind !== "MANUAL" && st.metric) {
        const m = st.metric;
        metric = {
          metricKey: m.metricKey,
          comparator: m.comparator,
          targetValue: m.targetValue === "" ? null : Number(m.targetValue),
          windowDays:
            st.kind === "SUSTAINED" && m.windowDays !== "" && m.windowDays != null
              ? Number(m.windowDays)
              : null,
          countFrom: st.kind === "COUNT" ? m.countFrom ?? null : null,
          validationError: null,
        };
      }
      return {
        title: st.title.trim(),
        kind: st.kind,
        metric,
        validationError: null,
      };
    });
    return {
      title: ph.title.trim(),
      description: ph.description.trim(),
      targetStartDate: ph.targetStartDate || null,
      targetEndDate: ph.targetEndDate || null,
      steps,
      validationError: null,
    };
  });

  return {
    title: draft.title.trim(),
    description: draft.description.trim(),
    domain: draft.domain,
    targetDate: draft.targetDate || null,
    phases,
    validationError: null,
  };
}
