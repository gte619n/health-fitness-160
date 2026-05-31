import Link from "next/link";
import type { CompletedWorkout } from "@/lib/types/workout";

// Read-only dashboard card showing the user's most recently completed workout
// (the guided experience itself lives on Android). Mirrors the look of the
// other dashboard cards.
export function WorkoutResultsCard({ workout }: { workout: CompletedWorkout | null }) {
  return (
    <div className="mb-3 rounded-[10px] border-[0.5px] border-border-default bg-surface px-5 py-[18px]">
      <CardTitle />
      {workout === null ? (
        <p className="mt-3 text-[13px] leading-[1.55] text-secondary">
          No workouts logged yet. Start today&rsquo;s session from the Tesseta
          app to see your results here.
        </p>
      ) : (
        <Body workout={workout} />
      )}
    </div>
  );
}

function Body({ workout }: { workout: CompletedWorkout }) {
  const s = workout.summary;
  return (
    <>
      <div className="mt-3 flex items-baseline justify-between">
        <div>
          <div className="text-[15px] font-medium tracking-[-0.01em] text-primary">
            {workout.title}
          </div>
          {workout.focus ? (
            <div className="mt-0.5 text-[12px] text-tertiary">{workout.focus}</div>
          ) : null}
        </div>
        <div className="caps-mono text-[10px] text-tertiary">
          {formatDate(workout.completedAt ?? workout.scheduledDate)}
        </div>
      </div>

      <div className="mt-3.5 grid grid-cols-4 gap-2.5 border-t-[0.5px] border-border-subtle pt-3.5">
        <Stat label="Duration" value={formatDuration(s.durationSeconds)} />
        <Stat label="Volume" value={`${trimNumber(s.totalVolume)} lb`} />
        <Stat label="Sets" value={`${s.setsCompleted}/${s.setsPrescribed}`} />
        <Stat label="Calories" value={String(s.estimatedCalories)} />
      </div>

      {s.aiRecap ? (
        <p className="mt-3.5 text-[12px] leading-[1.55] text-secondary">{s.aiRecap}</p>
      ) : null}

      {s.perExercise.length > 0 ? (
        <div className="mt-3.5 flex flex-col gap-2 border-t-[0.5px] border-border-subtle pt-3.5">
          {s.perExercise.map((ex, i) => (
            <div key={`${ex.name}-${i}`} className="flex items-baseline justify-between">
              <div className="text-[13px] font-medium text-primary">{ex.name}</div>
              <div className="font-mono text-[11px] tabular text-tertiary">
                {ex.topSet} · {trimNumber(ex.volume)} lb
              </div>
            </div>
          ))}
        </div>
      ) : null}
    </>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="caps-mono text-[9px] text-tertiary">{label}</div>
      <div className="mt-[3px] font-mono text-[16px] font-medium leading-none tracking-[-0.01em] text-primary tabular">
        {value}
      </div>
    </div>
  );
}

function CardTitle() {
  return (
    <Link
      href="/me/workouts"
      className="group inline-flex items-center gap-2.5 hover:text-accent-dim"
    >
      <span aria-hidden className="inline-block h-3.5 w-[3px] rounded-[2px] bg-accent" />
      <span className="text-[14px] font-medium tracking-[-0.01em] text-primary group-hover:text-accent-dim">
        Latest workout
      </span>
      <span
        aria-hidden
        className="font-mono text-[11px] text-tertiary opacity-0 transition-opacity group-hover:opacity-100"
      >
        →
      </span>
    </Link>
  );
}

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60);
  if (m >= 60) return `${Math.floor(m / 60)}h ${String(m % 60).padStart(2, "0")}m`;
  return `${m} min`;
}

function trimNumber(v: number): string {
  return Number.isInteger(v) ? String(v) : v.toFixed(1);
}

function formatDate(iso: string): string {
  // Accepts "2026-05-25" or an ISO instant; render "MAY 25".
  const d = new Date(iso.length <= 10 ? `${iso}T00:00:00Z` : iso);
  if (Number.isNaN(d.getTime())) return "";
  const month = d.toLocaleString("en-US", { month: "short", timeZone: "UTC" }).toUpperCase();
  const day = String(d.getUTCDate()).padStart(2, "0");
  return `${month} ${day}`;
}
