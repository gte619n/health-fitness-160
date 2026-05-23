"use client";

import { useState } from "react";
import type { DrugForm } from "@/lib/types/medication";

interface DrugImageProps {
  imageUrl: string | null;
  fallbackUrl: string | null;
  form: DrugForm;
  name: string;
  className?: string;
}

// Default fallback images by form type
const FALLBACK_IMAGES: Record<DrugForm, string> = {
  INJECTABLE_VIAL: "/fallbacks/injectable-vial.png",
  TABLET: "/fallbacks/tablet.png",
  CAPSULE: "/fallbacks/capsule.png",
  SOFTGEL: "/fallbacks/softgel.png",
  CREAM: "/fallbacks/cream.png",
  PATCH: "/fallbacks/patch.png",
  LIQUID: "/fallbacks/liquid.png",
  POWDER: "/fallbacks/powder.png",
};

// Placeholder SVG for when no image is available
function PlaceholderIcon({ form }: { form: DrugForm }) {
  const iconPath = {
    INJECTABLE_VIAL: "M12 3v2m0 14v2M5 12H3m18 0h-2M7.05 7.05L5.636 5.636m12.728 12.728l-1.414-1.414M7.05 16.95l-1.414 1.414m12.728-12.728l-1.414 1.414M12 8a4 4 0 100 8 4 4 0 000-8z",
    TABLET: "M9 12a3 3 0 106 0 3 3 0 00-6 0zm0 0l-3 3m6-6l3-3",
    CAPSULE: "M12 4v16m-4-4l4 4 4-4M8 8l4-4 4 4",
    SOFTGEL: "M12 6a6 6 0 100 12 6 6 0 000-12z",
    CREAM: "M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10",
    PATCH: "M4 4h16v16H4V4z",
    LIQUID: "M12 3v18m-6-6l6 6 6-6",
    POWDER: "M12 3v2m0 14v2M5 12H3m18 0h-2",
  };

  return (
    <svg
      className="h-8 w-8 text-tertiary"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.5}
    >
      <path strokeLinecap="round" strokeLinejoin="round" d={iconPath[form]} />
    </svg>
  );
}

export function DrugImage({ imageUrl, fallbackUrl, form, name, className = "" }: DrugImageProps) {
  const [error, setError] = useState(false);
  const [fallbackError, setFallbackError] = useState(false);

  const primarySrc = imageUrl;
  const secondarySrc = fallbackUrl ?? FALLBACK_IMAGES[form];

  // If both fail, show placeholder
  if ((error && fallbackError) || (!primarySrc && !secondarySrc)) {
    return (
      <div className={`flex items-center justify-center bg-canvas-sunken ${className}`}>
        <PlaceholderIcon form={form} />
      </div>
    );
  }

  // If primary failed, show secondary
  if (error || !primarySrc) {
    return (
      <img
        src={secondarySrc}
        alt={name}
        className={`object-cover ${className}`}
        onError={() => setFallbackError(true)}
      />
    );
  }

  // Show primary image
  return (
    <img
      src={primarySrc}
      alt={name}
      className={`object-cover ${className}`}
      onError={() => setError(true)}
    />
  );
}
