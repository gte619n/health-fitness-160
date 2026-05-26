"use client";

import { useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import type { AdminEquipment, SpecSchema, EquipmentSpecs } from '@/lib/types/gym';
import { EditEquipmentModal } from './EditEquipmentModal';
import { RegenerateImageModal } from './RegenerateImageModal';
import { ImageLightbox } from './ImageLightbox';

interface Props {
  catalog: AdminEquipment[];
  update: (
    equipmentId: string,
    data: { name: string; category: string; subcategory: string; specSchema: SpecSchema; specs: EquipmentSpecs },
  ) => Promise<void>;
  regenerate: (equipmentId: string, prompt: string) => Promise<void>;
  getImageStatus: (equipmentId: string) => Promise<string | null>;
  getImagePrompt: (equipmentId: string) => Promise<string>;
}

export function AdminEquipmentCatalog({
  catalog,
  update,
  regenerate,
  getImageStatus,
  getImagePrompt,
}: Props) {
  const router = useRouter();
  const [query, setQuery] = useState('');
  const [editingEquipment, setEditingEquipment] = useState<AdminEquipment | null>(null);
  const [regeneratingEquipment, setRegeneratingEquipment] = useState<AdminEquipment | null>(null);
  const [lightboxSrc, setLightboxSrc] = useState<string | null>(null);
  const [lightboxAlt, setLightboxAlt] = useState<string>('');

  const filtered = useMemo(() => {
    const sorted = [...catalog].sort((a, b) => a.name.localeCompare(b.name));
    const q = query.trim().toLowerCase();
    if (!q) return sorted;
    return sorted.filter(e => e.name.toLowerCase().includes(q));
  }, [catalog, query]);

  return (
    <div>
      <div className="mb-4">
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search equipment…"
          className="w-full max-w-md rounded-md border border-border-default bg-surface px-3 py-2 text-sm text-primary focus:outline-none focus:ring-2 focus:ring-accent"
        />
      </div>

      {filtered.length === 0 ? (
        <div className="rounded-lg border border-border-default bg-surface px-6 py-12 text-center">
          <p className="text-sm text-secondary">
            {catalog.length === 0 ? 'No catalog items yet.' : 'No equipment matches your search.'}
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
          {filtered.map((eq) => {
            const isZoomable = !!eq.imageUrl && eq.imageStatus === 'GENERATED';
            return (
              <div
                key={eq.equipmentId}
                className="flex flex-col overflow-hidden rounded-lg border border-border-default bg-surface shadow-md"
              >
                {eq.imageUrl ? (
                  isZoomable ? (
                    <button
                      type="button"
                      onClick={() => {
                        if (eq.imageUrl) {
                          setLightboxSrc(eq.imageUrl);
                          setLightboxAlt(eq.name);
                        }
                      }}
                      className="block aspect-square w-full cursor-zoom-in border-b border-border-default p-0"
                      aria-label={`Zoom image for ${eq.name}`}
                    >
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img
                        src={eq.imageUrl}
                        alt={eq.name}
                        className="aspect-square w-full rounded-t-md object-cover"
                      />
                    </button>
                  ) : (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={eq.imageUrl}
                      alt={eq.name}
                      className="aspect-square w-full rounded-t-md border-b border-border-default object-cover"
                    />
                  )
                ) : (
                  <div className="flex aspect-square w-full items-center justify-center border-b border-dashed border-border-default bg-canvas">
                    <span className="text-xs text-tertiary">
                      {eq.imageStatus === 'PENDING' ? 'Generating…' : 'No image'}
                    </span>
                  </div>
                )}

                <div className="flex flex-1 flex-col gap-2 p-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold text-primary" title={eq.name}>
                      {eq.name}
                    </p>
                    <p className="truncate text-xs text-tertiary">
                      {eq.category} · {eq.subcategory}
                    </p>
                  </div>
                  <div className="mt-auto flex justify-end">
                    <button
                      type="button"
                      onClick={() => setEditingEquipment(eq)}
                      className="cursor-pointer rounded-md bg-accent px-3 py-1.5 text-xs font-medium text-inverse hover:bg-accent/90"
                    >
                      Edit
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {editingEquipment && (
        <EditEquipmentModal
          equipment={editingEquipment}
          isOpen={true}
          onClose={() => setEditingEquipment(null)}
          onSave={async () => {
            setEditingEquipment(null);
            router.refresh();
          }}
          update={update}
          onRegenerate={() => {
            setRegeneratingEquipment(editingEquipment);
            setEditingEquipment(null);
          }}
        />
      )}

      {regeneratingEquipment && (
        <RegenerateImageModal
          equipmentId={regeneratingEquipment.equipmentId}
          equipmentName={regeneratingEquipment.name}
          isOpen={true}
          onClose={() => setRegeneratingEquipment(null)}
          onStarted={() => {
            const id = regeneratingEquipment.equipmentId;
            setRegeneratingEquipment(null);
            // Poll image status in the background; refresh when it leaves
            // PENDING/GENERATING. Image generation is async on the backend
            // (~15-20s). Time out after 60s and refresh anyway so the admin
            // can manually retry.
            void (async () => {
              for (let i = 0; i < 20; i += 1) {
                await new Promise(r => setTimeout(r, 3000));
                try {
                  const status = await getImageStatus(id);
                  if (status && status !== 'PENDING' && status !== 'GENERATING') {
                    router.refresh();
                    return;
                  }
                } catch {
                  // swallow transient network errors and keep polling
                }
              }
              router.refresh();
            })();
          }}
          getPrompt={getImagePrompt}
          regenerate={regenerate}
        />
      )}

      <ImageLightbox
        src={lightboxSrc}
        alt={lightboxAlt}
        onClose={() => setLightboxSrc(null)}
      />
    </div>
  );
}
