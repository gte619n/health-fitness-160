import { apiFetch, apiJson, BackendError } from "./api";
import type {
  GoalResponse,
  GoalDeepResponse,
  GoalStatus,
  GoalDomain,
  GoalSource,
  PhaseResponse,
  StepResponse,
  StepKind,
  Comparator,
} from "./types/goals";

// Server-only HTTP helpers for the Goals module. Do not import from
// client components — apiFetch reads server env + the Auth.js session.
// Keep these thin: mutation orchestration (create-goal-then-phases-then-
// steps) lives in server actions, not here.

// ── Reads ────────────────────────────────────────────────────────────

export async function listGoals(status?: GoalStatus): Promise<GoalResponse[]> {
  const qs = status ? `?status=${status}` : "";
  return apiJson<GoalResponse[]>(`/api/me/goals${qs}`);
}

export async function getGoalDeep(goalId: string): Promise<GoalDeepResponse> {
  return apiJson<GoalDeepResponse>(`/api/me/goals/${goalId}`);
}

// ── Request body shapes (mirror backend dto records) ─────────────────

export type MetricBindingInput = {
  metricKey: string;
  comparator: Comparator;
  targetValue: number;
  windowDays?: number | null;
  countFrom?: string | null;
};

export type CreateGoalInput = {
  title: string;
  description: string;
  domain: GoalDomain;
  startDate: string;
  targetDate: string;
  source: GoalSource;
};

export type UpdateGoalInput = Partial<{
  title: string;
  description: string;
  domain: GoalDomain;
  status: GoalStatus;
  startDate: string;
  targetDate: string;
}>;

export type CreatePhaseInput = {
  title: string;
  description: string;
  targetStartDate: string;
  targetEndDate: string;
};

export type UpdatePhaseInput = Partial<CreatePhaseInput>;

export type CreateStepInput = {
  title: string;
  kind: StepKind;
  metric?: MetricBindingInput | null;
};

export type UpdateStepInput = Partial<{
  title: string;
  kind: StepKind;
  done: boolean;
  metric: MetricBindingInput | null;
  resetToAuto: boolean;
}>;

// ── Internal request helper ──────────────────────────────────────────

async function send<T>(
  path: string,
  method: "POST" | "PATCH" | "PUT" | "DELETE",
  body?: unknown,
): Promise<T> {
  const res = await apiFetch(path, {
    method,
    ...(body !== undefined
      ? {
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        }
      : {}),
  });
  if (!res.ok) {
    throw new BackendError(`${method} ${path} returned ${res.status}`, res.status);
  }
  // 204 / empty body responses (DELETE, reorder) have nothing to parse.
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

// ── Goal mutations ───────────────────────────────────────────────────

export function createGoal(input: CreateGoalInput): Promise<GoalResponse> {
  return send<GoalResponse>("/api/me/goals", "POST", input);
}

export function updateGoal(
  goalId: string,
  input: UpdateGoalInput,
): Promise<GoalResponse> {
  return send<GoalResponse>(`/api/me/goals/${goalId}`, "PATCH", input);
}

export function archiveGoal(goalId: string): Promise<void> {
  return send<void>(`/api/me/goals/${goalId}`, "DELETE");
}

export function reevaluateGoal(goalId: string): Promise<GoalDeepResponse> {
  return send<GoalDeepResponse>(`/api/me/goals/${goalId}/reevaluate`, "POST");
}

// ── Phase mutations ──────────────────────────────────────────────────

export function createPhase(
  goalId: string,
  input: CreatePhaseInput,
): Promise<PhaseResponse> {
  return send<PhaseResponse>(`/api/me/goals/${goalId}/phases`, "POST", input);
}

export function updatePhase(
  goalId: string,
  phaseId: string,
  input: UpdatePhaseInput,
): Promise<PhaseResponse> {
  return send<PhaseResponse>(
    `/api/me/goals/${goalId}/phases/${phaseId}`,
    "PATCH",
    input,
  );
}

export function deletePhase(goalId: string, phaseId: string): Promise<void> {
  return send<void>(`/api/me/goals/${goalId}/phases/${phaseId}`, "DELETE");
}

export function reorderPhases(goalId: string, ids: string[]): Promise<void> {
  return send<void>(`/api/me/goals/${goalId}/phases/order`, "PUT", { ids });
}

// ── Step mutations ───────────────────────────────────────────────────

export function createStep(
  goalId: string,
  phaseId: string,
  input: CreateStepInput,
): Promise<StepResponse> {
  return send<StepResponse>(
    `/api/me/goals/${goalId}/phases/${phaseId}/steps`,
    "POST",
    input,
  );
}

export function updateStep(
  goalId: string,
  phaseId: string,
  stepId: string,
  input: UpdateStepInput,
): Promise<StepResponse> {
  return send<StepResponse>(
    `/api/me/goals/${goalId}/phases/${phaseId}/steps/${stepId}`,
    "PATCH",
    input,
  );
}

export function deleteStep(
  goalId: string,
  phaseId: string,
  stepId: string,
): Promise<void> {
  return send<void>(
    `/api/me/goals/${goalId}/phases/${phaseId}/steps/${stepId}`,
    "DELETE",
  );
}

export function reorderSteps(
  goalId: string,
  phaseId: string,
  ids: string[],
): Promise<void> {
  return send<void>(
    `/api/me/goals/${goalId}/phases/${phaseId}/steps/order`,
    "PUT",
    { ids },
  );
}
