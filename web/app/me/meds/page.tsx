import Link from "next/link";
import { revalidatePath } from "next/cache";
import { apiFetch, apiJson } from "@/lib/api";
import type { Medication, Drug, FrequencyConfig, TimeSlot } from "@/lib/types/medication";
import { MedicationGrid } from "@/components/medications/MedicationGrid";
import { AddMedicationButton } from "@/components/medications/AddMedicationButton";

export const dynamic = "force-dynamic";

export default async function MedsPage() {
  const [medications, drugs] = await Promise.all([
    apiJson<Medication[]>("/api/me/medications"),
    apiJson<Drug[]>("/api/drugs"),
  ]);

  // Separate active and discontinued
  const activeMeds = medications.filter(m => m.status === "ACTIVE");
  const discontinuedMeds = medications.filter(m => m.status === "DISCONTINUED");

  // Server actions
  async function addMedication(formData: FormData) {
    "use server";
    const drugId = formData.get("drugId") as string;
    const dose = Number(formData.get("dose"));
    const unit = formData.get("unit") as string;
    const frequencyType = formData.get("frequencyType") as string;
    const timesPerPeriod = formData.get("timesPerPeriod")
      ? Number(formData.get("timesPerPeriod"))
      : undefined;
    const timeSlots = formData.get("timeSlots")
      ? JSON.parse(formData.get("timeSlots") as string) as TimeSlot[]
      : [];
    const correlatedMarkers = formData.get("correlatedMarkers")
      ? JSON.parse(formData.get("correlatedMarkers") as string) as string[]
      : [];
    const notes = formData.get("notes") as string | null;
    const prescribedBy = formData.get("prescribedBy") as string | null;

    const frequency: FrequencyConfig = {
      type: frequencyType as FrequencyConfig["type"],
      timesPerPeriod,
    };

    const res = await apiFetch("/api/me/medications", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        drugId,
        dose,
        unit,
        frequency,
        timeSlots,
        correlatedMarkers,
        notes: notes || null,
        prescribedBy: prescribedBy || null,
      }),
    });

    if (!res.ok) {
      const text = await res.text();
      throw new Error(`Failed to add medication: ${text}`);
    }

    revalidatePath("/me/meds");
  }

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  async function discontinueMedication(medicationId: string, reason: string, notes: string | null) {
    "use server";
    const res = await apiFetch(`/api/me/medications/${medicationId}/discontinue`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ reason, notes }),
    });

    if (!res.ok) {
      throw new Error(`Failed to discontinue: ${res.status}`);
    }

    revalidatePath("/me/meds");
  }

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  async function deleteMedication(medicationId: string) {
    "use server";
    const res = await apiFetch(`/api/me/medications/${medicationId}`, {
      method: "DELETE",
    });

    if (!res.ok) {
      throw new Error(`Failed to delete: ${res.status}`);
    }

    revalidatePath("/me/meds");
  }

  const hasActiveMeds = activeMeds.length > 0;
  const hasDiscontinuedMeds = discontinuedMeds.length > 0;

  return (
    <main className="min-h-screen bg-canvas p-8">
      <div className="mx-auto max-w-[1100px] space-y-6">
        <Link
          href="/"
          className="inline-flex items-center gap-1.5 font-mono text-[11px] uppercase tracking-[0.04em] text-tertiary hover:text-secondary"
        >
          ← Dashboard
        </Link>

        <header className="flex items-start justify-between">
          <div>
            <h1 className="m-0 text-[22px] font-medium tracking-[-0.015em] text-primary">
              Medications
            </h1>
            <p className="mt-1 text-[13px] text-secondary">
              Track your prescriptions, supplements, and protocols.
            </p>
          </div>
          <AddMedicationButton
            addMedication={addMedication}
            drugs={drugs}
          />
        </header>

        {/* Active Medications */}
        <section className="rounded-[14px] border-[0.5px] border-border-default bg-surface">
          <div className="border-b-[0.5px] border-border-subtle px-5 py-3">
            <h2 className="m-0 text-[14px] font-medium text-primary">
              Current ({activeMeds.length})
            </h2>
          </div>
          <div className="p-5">
            {hasActiveMeds ? (
              <MedicationGrid medications={activeMeds} />
            ) : (
              <EmptyState
                title="No active medications"
                description="Add your first medication to start tracking."
              />
            )}
          </div>
        </section>

        {/* Discontinued Medications */}
        {hasDiscontinuedMeds && (
          <section className="rounded-[14px] border-[0.5px] border-border-default bg-surface">
            <div className="border-b-[0.5px] border-border-subtle px-5 py-3">
              <h2 className="m-0 text-[14px] font-medium text-primary">
                History ({discontinuedMeds.length})
              </h2>
            </div>
            <div className="p-5">
              <MedicationGrid medications={discontinuedMeds} />
            </div>
          </section>
        )}
      </div>
    </main>
  );
}

function EmptyState({ title, description }: { title: string; description: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-12 text-center">
      <div className="mb-4 rounded-full bg-canvas-sunken p-4">
        <svg
          className="h-8 w-8 text-tertiary"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={1.5}
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M9.75 9.75l4.5 4.5m0-4.5l-4.5 4.5M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
          />
        </svg>
      </div>
      <h3 className="text-[14px] font-medium text-primary">{title}</h3>
      <p className="mt-1 text-[13px] text-secondary">{description}</p>
    </div>
  );
}
