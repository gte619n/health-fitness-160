import { apiFetch } from "./api";
import type { CompletedWorkout } from "./types/workout";

// Server-only HTTP helper (see web/CLAUDE.md). Must not be imported from a
// "use client" component. Returns null when the user has no completed sessions
// yet (the backend answers 204).
export async function getLatestCompletedWorkout(): Promise<CompletedWorkout | null> {
  const res = await apiFetch("/api/me/workouts/sessions/latest-completed");
  if (res.status === 204) return null;
  if (!res.ok) {
    throw new Error(`latest-completed returned ${res.status}`);
  }
  return res.json() as Promise<CompletedWorkout>;
}
