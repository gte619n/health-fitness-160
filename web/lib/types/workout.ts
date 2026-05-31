// Guided-workout wire types (IMPL-WORKOUT-001). Mirror the backend
// CompletedSessionResponse / SessionSummary records. The web client only
// consumes completed-session results (the dashboard results card); the player
// lives on Android.

export type WorkoutExerciseResult = {
  name: string;
  topSet: string;
  volume: number;
};

export type WorkoutSummary = {
  durationSeconds: number;
  totalVolume: number;
  setsCompleted: number;
  setsPrescribed: number;
  estimatedCalories: number;
  perExercise: WorkoutExerciseResult[];
  aiRecap: string | null;
};

export type CompletedWorkout = {
  sessionId: string;
  title: string;
  focus: string | null;
  scheduledDate: string;
  completedAt: string | null;
  summary: WorkoutSummary;
};
