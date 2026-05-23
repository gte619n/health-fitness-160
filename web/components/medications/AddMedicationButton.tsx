"use client";

import { useState, useTransition } from "react";
import type { Drug, FrequencyType, TimeWindow, TimeSlot } from "@/lib/types/medication";
import {
  CATEGORY_LABELS,
  FORM_LABELS,
  FREQUENCY_LABELS,
  TIME_WINDOW_LABELS,
} from "@/lib/types/medication";

interface AddMedicationButtonProps {
  addMedication: (formData: FormData) => Promise<void>;
  drugs: Drug[];
}

export function AddMedicationButton({ addMedication, drugs }: AddMedicationButtonProps) {
  const [open, setOpen] = useState(false);
  const [isPending, startTransition] = useTransition();
  const [step, setStep] = useState<"search" | "form">("search");
  const [selectedDrug, setSelectedDrug] = useState<Drug | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [error, setError] = useState<string | null>(null);

  // Form state
  const [dose, setDose] = useState("");
  const [unit, setUnit] = useState("");
  const [frequencyType, setFrequencyType] = useState<FrequencyType>("DAILY");
  const [timesPerPeriod, setTimesPerPeriod] = useState("1");
  const [selectedWindows, setSelectedWindows] = useState<TimeWindow[]>(["MORNING"]);
  const [notes, setNotes] = useState("");
  const [prescribedBy, setPrescribedBy] = useState("");

  // Filter drugs based on search
  const filteredDrugs = searchQuery.trim()
    ? drugs.filter(
        (d) =>
          d.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
          d.aliases.some((a) => a.toLowerCase().includes(searchQuery.toLowerCase()))
      )
    : drugs;

  function handleSelectDrug(drug: Drug) {
    setSelectedDrug(drug);
    setUnit(drug.defaultUnit);
    setStep("form");
  }

  function handleBack() {
    setStep("search");
    setSelectedDrug(null);
    setError(null);
  }

  function handleClose() {
    setOpen(false);
    setStep("search");
    setSelectedDrug(null);
    setSearchQuery("");
    setDose("");
    setUnit("");
    setFrequencyType("DAILY");
    setTimesPerPeriod("1");
    setSelectedWindows(["MORNING"]);
    setNotes("");
    setPrescribedBy("");
    setError(null);
  }

  function toggleWindow(window: TimeWindow) {
    setSelectedWindows((prev) =>
      prev.includes(window)
        ? prev.filter((w) => w !== window)
        : [...prev, window]
    );
  }

  function handleSubmit() {
    if (!selectedDrug) return;
    if (!dose || Number(dose) <= 0) {
      setError("Dose is required");
      return;
    }

    const formData = new FormData();
    formData.set("drugId", selectedDrug.drugId);
    formData.set("dose", dose);
    formData.set("unit", unit || selectedDrug.defaultUnit);
    formData.set("frequencyType", frequencyType);
    formData.set("timesPerPeriod", timesPerPeriod);

    // Build time slots from selected windows
    const timeSlots: TimeSlot[] = selectedWindows.map((window) => ({
      window,
      dose: Number(dose) / selectedWindows.length,
    }));
    formData.set("timeSlots", JSON.stringify(timeSlots));
    formData.set("correlatedMarkers", JSON.stringify(selectedDrug.suggestedMarkers));
    if (notes) formData.set("notes", notes);
    if (prescribedBy) formData.set("prescribedBy", prescribedBy);

    startTransition(async () => {
      try {
        await addMedication(formData);
        handleClose();
      } catch (e) {
        setError(e instanceof Error ? e.message : "Failed to add medication");
      }
    });
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-2 text-[13px] font-medium text-accent-contrast transition-colors hover:bg-accent-hover"
      >
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
        </svg>
        Add medication
      </button>

      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-md rounded-xl bg-surface shadow-xl">
            {/* Header */}
            <div className="flex items-center justify-between border-b border-border-subtle px-5 py-4">
              <h2 className="text-[16px] font-medium text-primary">
                {step === "search" ? "Add medication" : selectedDrug?.name}
              </h2>
              <button
                type="button"
                onClick={handleClose}
                className="rounded-lg p-1.5 text-tertiary hover:bg-canvas-sunken hover:text-secondary"
              >
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* Content */}
            <div className="max-h-[60vh] overflow-y-auto p-5">
              {step === "search" ? (
                <div className="space-y-4">
                  {/* Search input */}
                  <div>
                    <input
                      type="text"
                      placeholder="Search medications..."
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      className="w-full rounded-lg border border-border-default bg-canvas px-3 py-2 text-[14px] text-primary placeholder:text-tertiary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                      autoFocus
                    />
                  </div>

                  {/* Drug list */}
                  <div className="space-y-1">
                    {filteredDrugs.length === 0 ? (
                      <p className="py-4 text-center text-[13px] text-secondary">
                        No medications found. Try a different search term.
                      </p>
                    ) : (
                      filteredDrugs.slice(0, 10).map((drug) => (
                        <button
                          key={drug.drugId}
                          type="button"
                          onClick={() => handleSelectDrug(drug)}
                          className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left hover:bg-canvas-sunken"
                        >
                          <div className="flex-1">
                            <div className="text-[14px] font-medium text-primary">
                              {drug.name}
                            </div>
                            <div className="text-[12px] text-tertiary">
                              {CATEGORY_LABELS[drug.category]} · {FORM_LABELS[drug.form]}
                            </div>
                          </div>
                          <svg className="h-4 w-4 text-tertiary" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                          </svg>
                        </button>
                      ))
                    )}
                  </div>
                </div>
              ) : (
                <div className="space-y-4">
                  {error && (
                    <div className="rounded-lg bg-alert/10 px-3 py-2 text-[13px] text-alert">
                      {error}
                    </div>
                  )}

                  {/* Dose */}
                  <div>
                    <label className="mb-1.5 block text-[12px] font-medium text-secondary">
                      Dose
                    </label>
                    <div className="flex gap-2">
                      <input
                        type="number"
                        value={dose}
                        onChange={(e) => setDose(e.target.value)}
                        placeholder="200"
                        className="flex-1 rounded-lg border border-border-default bg-canvas px-3 py-2 text-[14px] text-primary placeholder:text-tertiary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                      />
                      <input
                        type="text"
                        value={unit}
                        onChange={(e) => setUnit(e.target.value)}
                        placeholder={selectedDrug?.defaultUnit ?? "mg"}
                        className="w-20 rounded-lg border border-border-default bg-canvas px-3 py-2 text-[14px] text-primary placeholder:text-tertiary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                      />
                    </div>
                  </div>

                  {/* Frequency */}
                  <div>
                    <label className="mb-1.5 block text-[12px] font-medium text-secondary">
                      Frequency
                    </label>
                    <div className="flex gap-2">
                      <select
                        value={frequencyType}
                        onChange={(e) => setFrequencyType(e.target.value as FrequencyType)}
                        className="flex-1 rounded-lg border border-border-default bg-canvas px-3 py-2 text-[14px] text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                      >
                        {Object.entries(FREQUENCY_LABELS).map(([key, label]) => (
                          <option key={key} value={key}>
                            {label}
                          </option>
                        ))}
                      </select>
                      {(frequencyType === "DAILY" || frequencyType === "WEEKLY") && (
                        <input
                          type="number"
                          value={timesPerPeriod}
                          onChange={(e) => setTimesPerPeriod(e.target.value)}
                          min="1"
                          max="10"
                          className="w-16 rounded-lg border border-border-default bg-canvas px-3 py-2 text-[14px] text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                        />
                      )}
                    </div>
                  </div>

                  {/* Time windows */}
                  {frequencyType !== "PRN" && (
                    <div>
                      <label className="mb-1.5 block text-[12px] font-medium text-secondary">
                        Time of day
                      </label>
                      <div className="flex flex-wrap gap-2">
                        {(Object.keys(TIME_WINDOW_LABELS) as TimeWindow[]).map((window) => (
                          <button
                            key={window}
                            type="button"
                            onClick={() => toggleWindow(window)}
                            className={`rounded-lg px-3 py-1.5 text-[13px] font-medium transition-colors ${
                              selectedWindows.includes(window)
                                ? "bg-accent text-accent-contrast"
                                : "bg-canvas-sunken text-secondary hover:bg-canvas hover:text-primary"
                            }`}
                          >
                            {TIME_WINDOW_LABELS[window]}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Prescribed by */}
                  <div>
                    <label className="mb-1.5 block text-[12px] font-medium text-secondary">
                      Prescribed by (optional)
                    </label>
                    <input
                      type="text"
                      value={prescribedBy}
                      onChange={(e) => setPrescribedBy(e.target.value)}
                      placeholder="Dr. Smith"
                      className="w-full rounded-lg border border-border-default bg-canvas px-3 py-2 text-[14px] text-primary placeholder:text-tertiary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                    />
                  </div>

                  {/* Notes */}
                  <div>
                    <label className="mb-1.5 block text-[12px] font-medium text-secondary">
                      Notes (optional)
                    </label>
                    <textarea
                      value={notes}
                      onChange={(e) => setNotes(e.target.value)}
                      placeholder="Any additional notes..."
                      rows={2}
                      className="w-full rounded-lg border border-border-default bg-canvas px-3 py-2 text-[14px] text-primary placeholder:text-tertiary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                    />
                  </div>

                  {/* Correlated markers */}
                  {selectedDrug?.suggestedMarkers && selectedDrug.suggestedMarkers.length > 0 && (
                    <div>
                      <label className="mb-1.5 block text-[12px] font-medium text-secondary">
                        Blood markers to track
                      </label>
                      <div className="flex flex-wrap gap-1.5">
                        {selectedDrug.suggestedMarkers.map((marker) => (
                          <span
                            key={marker}
                            className="rounded-full bg-canvas-sunken px-2 py-0.5 text-[11px] font-medium text-tertiary"
                          >
                            {marker}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* Footer */}
            <div className="flex items-center justify-between border-t border-border-subtle px-5 py-4">
              {step === "form" ? (
                <>
                  <button
                    type="button"
                    onClick={handleBack}
                    className="text-[13px] font-medium text-secondary hover:text-primary"
                  >
                    ← Back
                  </button>
                  <button
                    type="button"
                    onClick={handleSubmit}
                    disabled={isPending}
                    className="rounded-lg bg-accent px-4 py-2 text-[13px] font-medium text-accent-contrast transition-colors hover:bg-accent-hover disabled:opacity-50"
                  >
                    {isPending ? "Adding..." : "Add medication"}
                  </button>
                </>
              ) : (
                <div className="ml-auto">
                  <button
                    type="button"
                    onClick={handleClose}
                    className="text-[13px] font-medium text-secondary hover:text-primary"
                  >
                    Cancel
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
