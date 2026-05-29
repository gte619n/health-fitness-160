// Pure wire types for the Goals chat — mirror of the backend records
// (GoalProposalDto.java, ChatThreadResponse.java, GoalChatController SSE
// payloads). Type-only and dependency-free, so both the server-only
// `goals-api.ts` and the client-side mapping in `goals-chat.ts` can import
// them without dragging in client components or server env.

import type { Comparator, GoalDomain, StepKind } from "./goals";

export type ProposalMetricDto = {
  metricKey: string;
  comparator: Comparator | null;
  targetValue: number | null;
  windowDays: number | null;
  countFrom: string | null; // ISO Instant
  validationError: string | null;
};

export type ProposalStepDto = {
  title: string;
  kind: StepKind;
  metric: ProposalMetricDto | null;
  validationError: string | null;
};

export type ProposalPhaseDto = {
  title: string;
  description: string;
  targetStartDate: string | null; // ISO LocalDate (YYYY-MM-DD)
  targetEndDate: string | null;
  steps: ProposalStepDto[];
  validationError: string | null;
};

export type GoalProposalDto = {
  title: string;
  description: string;
  domain: GoalDomain | null;
  targetDate: string | null; // ISO LocalDate
  phases: ProposalPhaseDto[];
  validationError: string | null;
};

// SSE event payloads (GoalChatController.sendEvent).
export type ChatTokenEvent = { text: string };
export type ChatErrorEvent = { error: string };
export type ChatDoneEvent = { threadId: string };

// GET /api/me/goals/chat/threads element.
export type ChatThread = {
  threadId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
};
