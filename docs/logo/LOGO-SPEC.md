# Tesseta — Logo Specification

Brand assets for the Tesseta health tracking app. Drop this folder into
the repo at `docs/brand/`.

```
brand/
├── LOGO-SPEC.md                       this file
├── brand-marks.html                   visual asset sheet, open in a browser
├── mark-olive.svg                     mark, olive tiles, transparent ground
├── mark-cream.svg                     mark, cream tiles, transparent ground
├── mark-ink.svg                       mark, ink tiles, transparent ground
├── app-icon-light.svg                 squircle, olive tiles on oatmeal
├── app-icon-dark.svg                  squircle, cream tiles on ink
├── app-icon-olive.svg                 squircle, cream tiles on olive
├── wordmark-horizontal.svg            mark + "tesseta", ink text
├── wordmark-horizontal-reversed.svg   mark + "tesseta", cream on dark
├── wordmark-stacked.svg               mark over "tesseta" + descriptor
└── mockups/
    ├── dashboard-desktop.html         logo in the desktop side nav
    ├── dashboard-phone.html           logo in the phone Today header
    └── dashboard-foldable.html        logo in the foldable icon nav
```

## Concept

A tessera is a single tile of a mosaic. The mark is exactly that: a 3x3
grid of square tiles with the center tile absent. Eight tiles form a ring
around an empty middle.

The gap is the idea. A health record is hundreds of small tiles, each
weigh-in, each lab marker, each workout, and the picture only resolves at
distance. The absent center also keeps the mark from reading as a generic
grid or app-launcher cliche, and gives it a single quiet focal point.

## Mark geometry

The mark is defined on a 160-unit master grid.

| Property | Value |
|---|---|
| Master canvas | 160 x 160 units |
| Tiles | 8 (3x3 grid, center omitted) |
| Tile size | 36 x 36 units |
| Tile corner radius | 3 units (8.33% of tile size) |
| Gutter between tiles | 6 units |
| Grid block (3 tiles + 2 gutters) | 120 x 120 units |
| Outer margin (all sides) | 20 units |
| Tile pitch (tile + gutter) | 42 units |

Tile origin coordinates (x, y) on the 160 grid:

```
(20,20)   (62,20)   (104,20)
(20,62)      —       (104,62)
(20,104)  (62,104)  (104,104)
```

The center position (62, 62) is intentionally empty.

### Ratios that must hold at every size

- Tile corner radius : tile size = **1 : 12**
- Gutter : tile size = **1 : 6**
- Outer margin : tile size = **1 : 1.8** (margin equals roughly half a
  tile plus a gutter; this is the clear-space unit, see below)
- Grid block : full canvas = **3 : 4** (120 of 160)

When scaling, scale the whole master grid. Never adjust gutter or radius
independently; the ratios above are the identity of the mark.

## App icon geometry

The app icon places the mark on a rounded-square (squircle) field, sized
for platform icon safe zones.

| Property | Value |
|---|---|
| Icon canvas | 120 x 120 units |
| Squircle corner radius | 26 units (21.7% of canvas) |
| Tile size | 16.5 x 16.5 units |
| Tile corner radius | 2.5 units |
| Gutter between tiles | 1.25 units |
| Grid block | 53.5 x 53.5 units |
| Mark margin inside icon | 34 units all sides |
| Grid block : icon canvas | **~0.45 : 1** |

The mark occupies the center ~45% of the icon. This is deliberately
conservative: it keeps the entire mark, including the center gap, inside
the Android adaptive-icon safe zone (the inner 66 dp of the 108 dp
canvas) and inside the iOS icon grid keyline. The generous oatmeal field
also lets the mark breathe at small launcher sizes.

Android adaptive icon: use `app-icon-light.svg` artwork as the
foreground layer on a solid `#F0EBE0` background layer, or
`app-icon-dark.svg` artwork on `#1F2419`. Keep all eight tiles within the
inner safe circle; they already are at the spec above.

## Wordmark geometry

### Horizontal lockup

Mark sits left, wordmark right, vertically centered.

| Property | Value |
|---|---|
| Lockup canvas | 320 x 80 units |
| Mark grid block | 54 x 54 units |
| Mark tile size | 16 units |
| Mark tile radius | 2.4 units |
| Mark gutter | 3 units |
| Gap between mark and text | 24 units |
| Text baseline | y = 52 |
| Mark vertical center | aligned to text cap height, not baseline |

### Type

| Property | Value |
|---|---|
| Typeface | Instrument Sans |
| Weight | 500 (medium) |
| Case | lowercase always — never caps, never title case |
| Tracking | -0.02em |
| Horizontal lockup size | 40 units cap |
| Stacked lockup size | 36 units cap |
| Color (light ground) | `#1F2419` ink |
| Color (dark ground) | `#F0EBE0` oatmeal |

The brand name is always set lowercase: `tesseta`, never `Tesseta` or
`TESSETA`, in logo lockups. (Running prose may capitalize the sentence-
initial "Tesseta" normally; this rule is about the mark.)

### Stacked lockup

Mark centered on top, wordmark below, optional descriptor line under
that. Descriptor is JetBrains Mono, 10 units, 500 weight, letter-spacing
0.22em, all caps, color `#8A8770`. The descriptor ("HEALTH RECORD") is
optional and should be dropped at small sizes.

## Clear space

Minimum clear space around any lockup is **one tile unit** on all sides.
- Mark alone: one tile = the tile size at the current render scale.
- Horizontal wordmark: one mark-tile (16 units at lockup scale).

Nothing else (text, edges, other UI) enters this zone.

## Minimum sizes

| Asset | Minimum | Notes |
|---|---|---|
| Mark | 16 px | Below this the center gap muddies |
| App icon | 48 px | Launcher minimum |
| Horizontal wordmark | 96 px wide | Below this, use the mark alone |
| Stacked wordmark | 120 px wide | Drop the descriptor below 140 px |

Below the wordmark minimum, always fall back to the mark alone. Never
shrink the wordmark text past legibility to keep the lockup.

## Color

| Token | Hex | Role |
|---|---|---|
| Olive | `#5C7A2E` | Primary mark color |
| Olive deep | `#3D5A1E` | Mark on light olive tints only |
| Ink | `#1F2419` | Mark and wordmark on light grounds |
| Oatmeal | `#F0EBE0` | Mark and wordmark on dark grounds; icon field |
| Olive tint | `#E8EBD8` | Not used in the mark; brand-system accent |

### Approved combinations

| Ground | Mark / text color | Asset |
|---|---|---|
| Oatmeal `#F0EBE0` | Olive `#5C7A2E` | `mark-olive`, `app-icon-light` |
| Ink `#1F2419` | Oatmeal `#F0EBE0` | `mark-cream`, `app-icon-dark` |
| Olive `#5C7A2E` | Oatmeal `#F0EBE0` | `app-icon-olive` |
| White `#FFFFFF` | Olive or Ink | `mark-olive` / `mark-ink` |

Never place the olive mark on the ink ground (insufficient contrast) or
the ink mark on the olive ground. Use the cream variant on any dark
ground.

## Placement in the app

### Desktop (web)
Mark in the side-nav header at 32 px, ink-on-oatmeal squircle variant,
followed by the lowercase "tesseta" wordmark at 14 px and a version tag
beneath. See `mockups/dashboard-desktop.html`.

### Phone
Mark at 30 px, olive squircle variant, sits left of the "Good morning"
greeting on the Today screen header. The phone home screen leads with a
personal greeting, not a full wordmark; the mark alone is the brand
anchor. See `mockups/dashboard-phone.html`.

### Foldable
Mark at 38 px, ink squircle variant, at the top of the icon-only
collapsed nav rail. No wordmark; the rail is icon-only by design. See
`mockups/dashboard-foldable.html`.

### App launcher icon
`app-icon-light.svg` (or platform adaptive-icon layers per the App icon
section above).

## Misuse

Do not:
- Rotate, skew, or add perspective to the mark
- Fill the center tile (it must stay empty)
- Change the gutter-to-tile or radius-to-tile ratios
- Recolor individual tiles or apply a gradient across them
- Set the wordmark in any typeface other than Instrument Sans
- Set the wordmark in caps or title case
- Add a drop shadow, glow, or outline to mark or wordmark
- Place the mark on a busy photographic background; it needs a solid
  ground from the approved color list

## File format notes

All assets are SVG: resolution-independent, tiny, and editable. For
raster exports (Play Store listing, favicon, OG images), render the SVGs
at the required pixel sizes; do not hand-redraw. The mark is pure
rectangles, so raster export at any size is lossless in appearance.

For the web favicon, export `mark-olive.svg` (or the ink variant) at
32x32 and 16x16; verify the center gap still reads at 16x16, and if it
does not, ship only the 32x32.
