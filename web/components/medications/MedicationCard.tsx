"use client";

import type { Medication } from "@/lib/types/medication";
import { formatFrequency, TIME_WINDOW_LABELS, CATEGORY_LABELS } from "@/lib/types/medication";
import { DrugImage } from "./DrugImage";
import { AdherenceSparkline } from "./AdherenceSparkline";

interface MedicationCardProps {
  medication: Medication;
  onClick?: () => void;
}

export function MedicationCard({ medication, onClick }: MedicationCardProps) {
  const { drug, dose, unit, frequency, timeSlots, adherence, status } = medication;
  const name = medication.customName ?? drug?.name ?? "Unknown";
  const category = drug?.category;
  const form = drug?.form ?? "TABLET";

  // Format dose display
  const doseDisplay = timeSlots.length > 1
    ? `${timeSlots.map(s => s.dose).join("/")} ${unit}`
    : `${dose} ${unit}`;

  // Format time slots
  const timesDisplay = timeSlots.length > 0
    ? timeSlots.map(s => TIME_WINDOW_LABELS[s.window]).join(", ")
    : null;

  return (
    <button
      type="button"
      onClick={onClick}
      className="group w-full rounded-xl border-[0.5px] border-border-default bg-surface p-4 text-left transition-all hover:border-border-hover hover:shadow-sm"
    >
      {/* Drug image */}
      <div className="relative mb-3 aspect-square w-full overflow-hidden rounded-lg bg-canvas-sunken">
        <DrugImage
          imageUrl={drug?.imageUrl ?? null}
          fallbackUrl={drug?.imageFallback ?? null}
          form={form}
          name={name}
          className="h-full w-full"
        />
        {status === "DISCONTINUED" && (
          <div className="absolute inset-0 flex items-center justify-center bg-surface/80">
            <span className="rounded-full bg-canvas-sunken px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-tertiary">
              Discontinued
            </span>
          </div>
        )}
      </div>

      {/* Drug info */}
      <div className="space-y-1.5">
        <div className="flex items-start justify-between gap-2">
          <h3 className="text-[14px] font-medium leading-tight text-primary">
            {name}
          </h3>
          {category && (
            <span className="flex-shrink-0 rounded-full bg-canvas-sunken px-1.5 py-0.5 text-[9px] font-medium uppercase tracking-wide text-tertiary">
              {CATEGORY_LABELS[category]}
            </span>
          )}
        </div>

        <div className="text-[12px] text-secondary">
          <span className="font-mono">{doseDisplay}</span>
          <span className="mx-1.5 text-tertiary">·</span>
          <span>{formatFrequency(frequency)}</span>
        </div>

        {timesDisplay && (
          <div className="text-[11px] text-tertiary">
            {timesDisplay}
          </div>
        )}

        {/* Adherence sparkline */}
        {adherence && status === "ACTIVE" && (
          <div className="pt-2">
            <AdherenceSparkline
              data={adherence.last30Days}
              percentage={adherence.percentage}
            />
          </div>
        )}
      </div>
    </button>
  );
}
