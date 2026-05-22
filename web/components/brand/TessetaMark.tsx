// Tesseta mark — 3x3 grid of tiles with the center omitted. Specs at
// docs/logo/LOGO-SPEC.md. The squircle field is optional; the bare
// mark on transparent ground is also a valid variant.
//
// Variants follow the spec's approved color combinations:
//   light  — olive tiles on oatmeal squircle  (app-icon-light)
//   dark   — cream tiles on ink squircle      (app-icon-dark)
//   olive  — cream tiles on olive squircle    (app-icon-olive)
//   bare-* — same tile color, no squircle ground

type Variant = "light" | "dark" | "olive" | "bare-olive" | "bare-ink" | "bare-cream";

const TILE = 16.5;
const PITCH = 17.75; // tile + gutter (1.25)
const ORIGIN = 34;
const RADIUS = 2.5;

const TILE_POSITIONS: [number, number][] = [
  [0, 0],
  [1, 0],
  [2, 0],
  [0, 1],
  /* center omitted */
  [2, 1],
  [0, 2],
  [1, 2],
  [2, 2],
];

function colorsFor(variant: Variant): { tile: string; ground: string | null } {
  switch (variant) {
    case "light":
      return { tile: "#5C7A2E", ground: "#F0EBE0" };
    case "dark":
      return { tile: "#F0EBE0", ground: "#1F2419" };
    case "olive":
      return { tile: "#F0EBE0", ground: "#5C7A2E" };
    case "bare-olive":
      return { tile: "#5C7A2E", ground: null };
    case "bare-ink":
      return { tile: "#1F2419", ground: null };
    case "bare-cream":
      return { tile: "#F0EBE0", ground: null };
  }
}

export function TessetaMark({
  variant = "light",
  size = 32,
  className,
}: {
  variant?: Variant;
  size?: number;
  className?: string;
}) {
  const { tile, ground } = colorsFor(variant);
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 120 120"
      role="img"
      aria-label="Tesseta"
      className={className}
    >
      {ground !== null && (
        <rect width="120" height="120" rx="26" fill={ground} />
      )}
      {TILE_POSITIONS.map(([col, row]) => (
        <rect
          key={`${col}-${row}`}
          x={ORIGIN + col * PITCH}
          y={ORIGIN + row * PITCH}
          width={TILE}
          height={TILE}
          rx={RADIUS}
          fill={tile}
        />
      ))}
    </svg>
  );
}
