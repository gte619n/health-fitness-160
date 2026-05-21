# IMPL-01: UI Design Specification

## Goal
Define the visual language and information architecture for the
health-fitness app across web, phone, foldable, and Wear OS. Drive UI
implementation in IMPL-02. No backend integration: all screens render from
fixture data so we can iterate on look and feel without dependencies.

## Design philosophy

**Warm precision, not consumer wellness.** This is a tool for someone who
manufactures medical devices. The reference points are precision
instruments and editorial design, not meditation apps. Data density is a
feature. Pastel gradients, mascots, and reassuring rounded illustrations
are the opposite of what we want.

**Light by default, warm and oat-toned.** The canvas is oatmeal cream, not
clinical white and not dark. The palette comes from the Tailwind Plus
"Oatmeal" kit with the "olive_instrument" theme: warm neutral backgrounds,
a single olive green accent, and white cards on top. Dark mode exists for
parity but design decisions assume light.

**Numbers are the hero.** Every screen leads with a number or a trend.
Decor is suppressed. Numerics are monospaced with tabular figures. Labels
are small caps in mono. Body text is sans. The reading experience is
closer to a Bloomberg terminal than a consumer app, with the courtesy of
modern typography and spacing.

**Same primitives, different density.** Phone, foldable, web, and Wear
share the same color tokens, type scale, and component vocabulary. They
differ in how much they show at once, not in what things look like.

**No italics, no display serif.** Editorial mood comes from layout and
restraint, not from typographic flourish.

---

## Foundations

### Color tokens

Single source of truth. Every screen references these by name, never raw
hex in component code.

**Light theme (default):**

| Token | Hex | Use |
|---|---|---|
| `bg.canvas` | `#F0EBE0` | App background (oatmeal) |
| `bg.canvas.muted` | `#EBE4D0` | Side nav, secondary surfaces |
| `bg.surface` | `#FFFFFF` | Cards, panels |
| `bg.surface.raised` | `#FFFFFF` | Modals, popovers (use shadow for elevation) |
| `bg.surface.sunken` | `#F0EBE0` | Inset wells, segment control bg, progress track |
| `border.subtle` | `#EFE7D2` | Inner dividers within cards |
| `border.default` | `#E6DFCF` | Card borders, input borders |
| `border.strong` | `#DDD3BB` | Hover, divider on muted surfaces |
| `border.focus` | `#5C7A2E` | Focused inputs (with 30% opacity ring) |
| `text.primary` | `#1F2419` | Body text, primary readings |
| `text.secondary` | `#6B6856` | Labels, metadata |
| `text.tertiary` | `#8A8770` | Caps mono labels, timestamps |
| `text.quaternary` | `#B5B09C` | Axis labels, decorative meta |
| `text.inverse` | `#F0EBE0` | Text on dark fills |
| `accent.signal` | `#5C7A2E` | Primary accent (olive) |
| `accent.signal.dim` | `#3D5A1E` | Accent on light fills, deeper olive |
| `accent.signal.bg` | `#E8EBD8` | Light olive tint for pills and active states |
| `data.good` | `#5C7A2E` | In-range, improving, hit target (same as accent) |
| `data.good.bg` | `#E8EBD8` | Good-state pill backgrounds |
| `data.good.alt` | `#8A9F5C` | Secondary good (e.g., second of two good series) |
| `data.warn` | `#A06A1F` | Approaching limit, borderline range |
| `data.warn.bg` | `#FBE9DA` | Warn pill background |
| `data.alert` | `#A8473A` | Out of range, missed dose, regression |
| `data.alert.bg` | `#F7E1DC` | Alert pill background |
| `data.neutral` | `#3B6B8E` | Informational, baselines |
| `data.muted` | `#B5B09C` | Reference ranges, prior periods, inactive |

**Dark theme:**

Defer until after light-theme implementation lands. Spec stub: invert the
canvas/surface relationship (canvas near-black, surfaces slightly raised),
keep olive accent but at slightly higher luminance for contrast. Full
dark-theme tokens to be added in a follow-up revision once the light
theme is in production.

All semantic data colors pass WCAG AA at 14pt on the paired surface in
light theme.

### Typography

Two families. Both free, both modern, both load via Fontsource on
jsdelivr.

- **UI / body**: **Instrument Sans** (Production Type, OFL). Weights 400,
  500.
- **Numerics / labels / data**: **JetBrains Mono** (JetBrains, OFL).
  Weights 400, 500. Tabular figures by default.
- **Fallback stack (UI)**: `'Inter', 'SF Pro Text', -apple-system,
  BlinkMacSystemFont, system-ui, sans-serif`
- **Fallback stack (mono)**: `'SF Mono', ui-monospace, 'Cascadia Code',
  monospace`

No italics, ever. No display serif. Editorial weight comes from sizing,
spacing, and the olive accent bar prefix on section titles.

**Type scale:**

| Token | Size / Line | Family | Weight | Use |
|---|---|---|---|---|
| `display.xl` | 36 / 40 | Mono | 500 | Hero numerics on dashboard cards |
| `display.lg` | 28 / 32 | Mono | 500 | Section hero numerics |
| `display.md` | 22 / 26 | Mono | 500 | Card hero numerics |
| `display.sm` | 18 / 22 | Mono | 500 | Inline large numerics |
| `heading.lg` | 20 / 28 | Sans | 500 | Page titles |
| `heading.md` | 14 / 20 | Sans | 500 | Card and section titles |
| `heading.sm` | 12 / 18 | Sans | 500 | Subsections |
| `body.lg` | 14 / 22 | Sans | 400 | Long-form text |
| `body.md` | 13 / 18 | Sans | 400 | Default body |
| `body.sm` | 11 / 16 | Sans | 400 | Table cells, compact body |
| `mono.lg` | 18 / 22 | Mono | 500 | Inline numerics in cards |
| `mono.md` | 12 / 16 | Mono | 500 | Dense data tables |
| `mono.sm` | 10 / 14 | Mono | 500 | Axis labels, log timestamps |
| `caps.md` | 10 / 14 | Mono | 500 | All caps labels (`tracking +0.08em`) |
| `caps.sm` | 9 / 12 | Mono | 500 | Compact caps labels (`tracking +0.08em`) |

Section titles always have a 3 px wide × 11 px tall olive vertical bar
prefix at `accent.signal`, 8 px gap, then the title at `heading.md`.

### Spacing scale

4 px base unit. Tokens: `space.0` 0, `space.1` 4, `space.2` 8, `space.3`
12, `space.4` 16, `space.5` 20, `space.6` 24, `space.8` 32, `space.10`
40, `space.12` 48.

Card padding: `space.4` (16 px) on phone, `space.4-5` (16–20 px) on web.
Gap between cards: `space.2-3` on phone and foldable, `space.3` on web.

### Radius

`radius.sm` 4 px (chips, small badges, log icon backgrounds), `radius.md`
7 px (buttons, nav items, segment controls), `radius.lg` 10 px (cards),
`radius.xl` 14 px (modals, sheets), `radius.pill` 999 px (avatars when
explicitly circular, not the default).

Default for everything else: square corners on data viz elements (range
indicator bars, macro progress bars). The instrument aesthetic prefers
precise rectangular elements where rounding isn't functional.

### Elevation

Two levels.

- **Resting**: 1 px `border.default` solid border. No shadow.
- **Raised** (modals, popovers, dragged cards): `border.default` border
  plus `0 12px 32px rgba(0,0,0,0.08)` shadow.

Hierarchy comes from surface tone and border weight, not from drop
shadows.

### Iconography

**Tabler Icons** (outline variant). 20 px and 24 px stroke icons for UI.
Single weight per screen. Load via `@tabler/icons-webfont` from jsdelivr,
referenced as `<i class="ti ti-name">`.

Icon backgrounds in log entries and feed items are 22–26 px squares with
`radius.sm`, filled with `accent.signal.bg` for good-state items and
`bg.canvas` for neutral items.

Phosphor and Lucide are alternatives; Tabler is chosen for the largest
glyph library that includes specific health icons (`barbell`, `bowl`,
`pill`, `scale`, `droplet`).

### Motion

Functional only. Standard easing `cubic-bezier(0.2, 0, 0, 1)`, durations
150 ms (micro), 250 ms (standard), 400 ms (large transitions). Charts
animate on mount only. Live values update without animation, just value
swaps with tabular figures so layout doesn't jitter.

---

## Component library

Defined once, used everywhere. Implementations differ by platform; the
visual contract is identical.

### `Card`
Default container. `bg.surface` background, 0.5 px `border.default`,
`radius.lg`, 13–18 px padding. Variants: `default`, `interactive` (hover
border becomes `border.strong`), `alert` (left border `data.alert` 2 px,
square corners on that edge).

Header pattern: olive accent bar (3 × 11 px) + title (`heading.md`) + right-aligned metadata or action.

### `Stat`
The atom of this app. Compact stat card content:
1. Label row: caps mono label (`caps.sm` in `text.tertiary`) on left,
   Tabler icon (12–14 px in `text.quaternary`) on right
2. Value: `display.md` mono in `text.primary`, with smaller `body.sm`
   unit suffix in `text.tertiary`
3. Footer row: delta (`mono.sm` in `data.good` or `data.alert`) on left,
   sparkline on right

Card padding 10–14 px, border `border.default`, radius `radius.md`.

### `Sparkline`
Standalone primitive. Pure SVG, no axes, no gridlines, no points. Single
1.25–1.5 px stroke in `accent.signal` or a `data.*` token. Optional
filled area at 6–8% opacity below the line. Optional anchor dot at the
most recent value (3.5 px, filled with line color, 2 px white border).

### `Chart`
Built on Recharts (web) / Vico (Android Compose). Conventions enforced:
- Background transparent (inherits card surface)
- Axes use `text.quaternary` for tick labels at `mono.sm`
- Gridlines `border.subtle`, dashed `2 3`, horizontal only by default
- Tooltips: `bg.surface` with `border.default`, `radius.md`, mono values
- Reference ranges shown as filled horizontal bands at 10% opacity in
  `data.muted`
- Series colors from `data.*` tokens only, never raw hex in components
- Legends shown only when more than one series exists; legend chips are
  10 px wide × 2 px tall colored rectangles in caps mono

### `RangeIndicator`
Horizontal bar showing where a value sits within a reference range.
- Background: `bg.surface.sunken`, 3–5 px tall, square corners, full
  width
- In-range fill: `data.good.bg`, spanning the in-range portion
- Out-of-range zones: implied by absence of fill
- Current value: 2 px wide vertical tick in `text.primary`, full height
- Below the bar: caps mono min, in-range threshold, max labels with the
  in-range threshold tinted `data.good`

Used heavily on blood test panels.

### `Pill`
Small label. `caps.sm` text, square corners (`radius.sm` max), 1–2 px
vertical / 5–7 px horizontal padding. Variants: `neutral`
(`bg.surface.sunken` / `text.secondary`), `good` (`data.good.bg` /
`data.good.dim`), `warn` (`data.warn.bg` / `data.warn`), `alert`
(`data.alert.bg` / `data.alert`).

### `Segment`
Segmented control. `bg.surface.sunken` track with 2 px padding,
`radius.md` outer corners, selected segment uses `bg.surface` with 0.5 px
`border.default`, inactive segments are transparent with `text.secondary`
labels. Labels in sans 500 at `body.sm`.

### `LogEntry`
Single row in a vertical feed. Three columns:
- Left: 22–26 px square `radius.sm` icon container with Tabler icon
  inside. Background `accent.signal.bg` for activity-good icons,
  `bg.canvas` for neutral
- Middle: primary content (sans 500 at `body.md` or `body.sm` depending
  on density), secondary metadata in caps mono at `caps.sm` on a second
  line (web) or appended inline with `·` separators (foldable, phone)
- Right: timestamp in `mono.sm`, right-aligned

Rows separated by 0.5 px `border.subtle`. No row separator on last item.

### `EmptyState`
Centered. Tabler icon at 32 px in `accent.signal.dim`. Title at
`heading.md`, description at `body.md` in `text.secondary` constrained to
320 px width. Optional primary button below at `space.4` distance.

### `Button`
Three variants:
- `primary`: `accent.signal` fill, `text.inverse`, sans 500
- `secondary`: `bg.surface` fill, `border.default` border,
  `text.primary`, sans 500
- `ghost`: transparent, `text.secondary`, hover `bg.canvas`

Sizes: `sm` (28 px tall, for compact toolbars), `md` (32 px, default web),
`lg` (40 px, mobile default for primary actions).

Tiles in the phone quick-log row are 80 px tall buttons with icon (18 px,
`accent.signal`) on top and caps mono label below. `bg.surface` fill,
`border.default`, `radius.lg`.

### `Input` / `Select` / `Textarea`
`bg.surface` background, `border.default` border, hover/focus `border.strong`,
focus adds a 2 px ring at `accent.signal` 30% opacity. Label `caps.sm`
`text.tertiary` above. Helper text `caps.sm` `text.quaternary` below.
Error replaces helper with `data.alert` text.

### `NavItem`
**Sidebar variant (web)**: icon (15 px) + label (sans 400 `body.md`) +
optional right-aligned badge. Default: transparent background,
`text.secondary` label, `text.tertiary` icon. Active: `accent.signal.bg`
background, `accent.signal.dim` label, `accent.signal.dim` icon, no
border. Hover: `bg.canvas.muted` (only on inactive items).

**Icon-only variant (foldable)**: 38 px × 36 px button, no background by
default, active state uses a 2 px wide `accent.signal` vertical bar on
the left edge with `accent.signal.dim` icon color.

**Bottom-nav variant (phone)**: icon (18 px) above caps mono label (9 px),
total target height 48 px. Active: olive icon + label, plus a 2 px tall
× 24 px wide olive underline pill 10 px above the icon.

---

## Information architecture

### Web

Single-page app with a persistent left nav. Nav is 178 px wide on `xl`
screens. Collapses to icons-only at `lg`. Hidden behind a hamburger on
`md` and below.

**Primary destinations:**

1. **Dashboard** — at-a-glance, vitals, hero chart, alerts
2. **Body** — weight, composition, measurements, photos
3. **Blood** — panels, longitudinal markers, annotations
4. **Workouts** — history, programs, volume analysis
5. **Nutrition** — daily intake, macro trends, food log
6. **Meds** — current regimen, adherence, history
7. **Insights** — LLM chat over the data, research workspace

**Secondary section in nav** (below a divider, with a `caps.sm` "Devices"
header):
- Connected device list with status dots (green=connected,
  gray=disconnected)

**Bottom of nav**:
- Settings (single nav item)
- User pill (avatar square, name, role caps, selector chevron)

Page top bar: page title (`heading.lg`) left, date in caps mono below,
controls right (date range pill, search button, bell with status dot).

### Phone

Bottom nav with four destinations. Phone is for capture and at-a-glance,
not deep analysis.

1. **Today** — what's happening right now (default)
2. **Log** — quick-entry hub (workout, food, weight, med, blood)
3. **Trends** — abbreviated trend cards, swipeable carousel
4. **More** — settings, devices, account

Phone never shows the full left nav. The Today screen header includes a
greeting, date in caps mono, notification bell, and avatar — no other
chrome.

### Foldable

**Folded (compact width)**: phone layout. Bottom nav with four
destinations.

**Unfolded (medium width, ≥ 600 dp)**: icon-only collapsed left nav (56 px
wide) plus full content area. The nav exposes all 7 destinations as
icons, a horizontal divider, then settings icon and avatar. Active state
uses a 2 px olive vertical bar on the left edge of the active icon. Tap
the icon to expand into labels temporarily; auto-collapses after
selection.

**Unfolded (expanded width, ≥ 840 dp)**: same icon-only nav, with content
area widening to accommodate. Charts can grow taller. Recent activity
feed can show more entries.

**Tabletop posture** (half-folded with the hinge horizontal): used for
active workout. Top half shows live stats and chart. Bottom half shows
controls (rep/weight pad for strength, lap button for cardio).
Detection via `WindowInfoTracker` posture events. Reverts to standard
unfolded layout when the device flattens or folds.

A subtle vertical hinge crease (1 px line at 7% black opacity, no border)
runs down the middle of any unfolded layout. Decorative only;
non-functional.

### Wear OS

Three surfaces.

1. **Main app**: tile-style vertically scrollable screens — Today,
   Active Workout (if any), Quick Log.
2. **Tiles**: at-a-glance from watch face swipe — "Today" tile and
   "Body" tile.
3. **Complications**: small data points — current HR, resting HR,
   today's calories, weight, readiness.

Active workout takes over the screen with always-on display variant.

---

## Screen specifications

Each screen defined in terms of components and fixture data. See the
mockups in `docs/mockups/` for visual reference. The mockups are
authoritative for the screens they cover; this section is authoritative
for everything they don't cover.

### Web · Dashboard

Defined visually in `docs/mockups/dashboard-desktop.html`. Structure:

**Page top bar**:
- Title "Dashboard" `heading.lg` + caps mono date below
- Right: date range pill ("LAST 90 DAYS" with calendar icon and
  chevron), search button, bell button with status dot

**Vitals strip** (4 columns, can grow to 6 at `2xl`):
- Weight, HRV, Resting HR, Readiness (Sleep Score and a sixth metric to
  be added when 6-up layout is implemented)
- Each card uses the `Stat` component

**Body composition hero card**:
- Section title with olive accent bar prefix
- Three-up inline numerics row: weight (`display.lg`), body fat %
  (`mono.lg`), lean mass (`mono.lg`), separated by 0.5 px dividers
- Date range segment control on the right (`30d / 90d / 1y / All`)
- 140 px tall chart with daily series, 7-day moving average overlay,
  filled area, current value anchor dot
- Y-axis labels left, x-axis labels bottom, both in caps mono
- Below chart: legend strip with colored swatches and caps mono labels

**Two-column row**:
- Left: "Blood panel" card with 4 markers, each using `RangeIndicator`
- Right: "Today" card with calories + donut, 3-up macros with progress
  bars, last workout summary at the bottom

**Recent activity feed**:
- Section title + "VIEW ALL" link in caps mono
- 4 `LogEntry` rows: workout, weighing, medication, sleep

### Web · Body

Header with title + range segment. Three-up hero numerics (weight, body
fat %, lean mass). Full-width chart with series toggleable (Weight /
Composition / Measurements). Annotations marker icons at intervention
dates.

Measurements panel: tabular layout, rows = measurement type (waist, hip,
chest, arm L/R, thigh L/R, neck), columns = last 4 entries with dates,
latest column shows delta.

Photos panel (opt-in): 4-up grid, "Add photo" tile. Local-only by
default.

### Web · Blood

Header + "New panel" primary button. Horizontal scroll of panel pills,
one per historical panel. Latest panel view by default, grouped by
category (Lipids, Glucose, Inflammation, Thyroid, Liver, Kidney,
Hormones, Vitamins, CBC, Other).

Each marker row: name, value (colored by in/warn/alert), units,
`RangeIndicator`, delta vs prior panel, sparkline of last 8 values.

Longitudinal view (toggle): picker for up to 4 markers to overlay,
full-width chart, reference range bands per marker, intervention
annotation lines.

Fixture data: 4 panels spanning Jan 2024, Aug 2024, Mar 2025, Oct 2025.
Realistic values with LDL trending up across the last two panels, ApoB
borderline, everything else in range. Annotations: "Started rosuvastatin
5 mg" on Mar 2025, "Increased to 10 mg" on Aug 2025.

### Web · Workouts

Header + "Log workout" button. 4-stat volume strip (This Week Tonnage,
This Week Sessions, Avg Session Duration, Avg HR Across Cardio). 12-week
calendar heat grid. Recent workouts table with expandable rows showing
exercise breakdown or HR/pace chart.

Fixture: 30 workouts across the last 90 days. Mix of push/pull/legs
strength, Zone 2 cardio (~3×/week), 1 heavy interval session per week.

### Web · Nutrition

Header + date display + "Log food" button. Today summary (4 stats:
calories, protein, carbs, fat). Macro distribution donut left, meal log
right (Breakfast / Lunch / Dinner / Snacks sections). 30-day trends
panel below.

Fixture: 14 days at ~2800 kcal/day, 200g protein.

### Web · Meds

Header + "Add medication" button. Current regimen sectioned by category
(Prescriptions, Supplements). Each item: name, dose, frequency, next
dose chip, 30-day adherence sparkline (tick marks), 30-day adherence
percentage.

Today's schedule timeline with tap-to-mark-taken. 90-day adherence
trends.

Fixture: Rosuvastatin 10 mg, Lisinopril 10 mg, Magnesium glycinate 400
mg PM, Vitamin D3 5000 IU AM, Creatine 5 g, Omega-3 2 g. 90 days at ~94%
overall.

### Web · Insights

LLM chat. Two-column on `lg+`: chat 60% left, context 40% right. Single
column with drawer below `lg`. Chat: assistant + user messages, SSE
streaming, markdown rendering, suggested follow-up pills below
assistant messages.

Context column: pinned data, conversation memory, sources.

Empty state: 6 suggested starter prompts.

This surface needs more thought before implementation. Default layout
above is provisional. Revisit before IMPL builds it.

### Phone · Today

Defined visually in `docs/mockups/dashboard-phone.html`. Structure:

- Status bar (iOS/Android conventions)
- Header: greeting + caps mono date / bell + avatar
- 2×2 vitals grid (Weight, HRV, RHR, Readiness)
- "Today" card: calories ring + macros + last workout summary
- 4-tile quick log row (Workout, Food, Weight, Med)
- "Recent" compact list (3 entries)
- Bottom nav (Today active)
- iOS home indicator

### Phone · Log

Hub screen. Sections:
- Quick log: Weight, Water, Mood, Sleep quality tiles
- Activity: Start Workout, Log Food (larger tiles)
- Medical: Log Medication, Log Blood Pressure, Log Symptom, Log
  Measurement
- More: photos, manual import

### Phone · Trends

Swipeable horizontal carousel. Each card full-screen minus bottom nav.
Page indicator dots above nav.

Card order: Weight, Body Composition, Sleep, HRV + RHR, Workout Volume,
Calories Balance, Latest Blood Markers.

Each card has date range segment top + "View on web" hint bottom.

### Phone · Active Workout (takeover)

Full-screen. Bottom nav hidden.
- Top half: time elapsed (`display.lg` mono), current HR (large, color
  by zone), zone bar with current position highlighted
- Middle: current exercise name, target reps × weight, set counter
- Bottom: numeric pad for reps/weight + "Log set" + "Skip"; for cardio,
  split table + lap button
- Controls strip: pause/resume, end workout, music shortcut

### Foldable · Unfolded Dashboard

Defined visually in `docs/mockups/dashboard-foldable.html`. Same as web
Dashboard but:
- Nav collapsed to icon-only (56 px wide)
- Content area takes the remainder
- Hinge crease visible vertically down the middle
- Slight density reduction vs desktop (smaller font sizes, tighter gaps)

### Foldable · Tabletop Active Workout

When the device is in tabletop posture during an active workout:
- Top screen (above hinge): live HR `display.lg`, time, zone bar,
  current set summary, next exercise preview
- Bottom screen (below hinge): logging controls, set list, large
  tap-friendly buttons for "Log set", "Next exercise", "Add note"

The split is detected via `WindowInfoTracker` posture events. Layout
collapses back to standard phone when the device returns to flat or
folded. To be mocked in a follow-up.

### Foldable · Unfolded Today

When in `OPEN` posture and width ≥ medium:
- Left pane (40%): Today summary (vitals card, today's log feed)
- Right pane (60%): detail of whichever item is selected; defaults to
  the active workout if any, else the most recent log entry, else empty
  state "select to view detail"

### Wear · Today tile

Single tile, swipe-accessible from watch face.
- Top half: HR (`display.lg` mono), HR zone indicator
- Bottom half: 2×2 mini grid — Steps, Calories, Active min, Sleep
- Tap to launch app

### Wear · Active Workout

Full-screen takeover.
- Top: time elapsed (`display.lg` mono)
- Middle: HR (`display.lg` mono, large, color by zone)
- Bottom: secondary stat (distance, pace, or current set), single line
- Side hardware button: lap / log set
- Long-press: end workout (with confirmation)

Always-on display variant: dim background, only time + HR visible.

---

## Fixture data

All screens render from a single fixture set committed to the repo.

**Location:**
- Web: `web/lib/fixtures/*.ts` (TypeScript modules exporting typed data)
- Android: `android/core-data/src/main/java/com/gte619n/healthfitness/data/fixtures/*.kt`

**Shape:** Same logical schema on both platforms, kept in sync via the
fixture generator script.

**Coverage** (single user, "Demo User" = Evan):
- 365 days of daily weight (slight downward trend with normal noise)
- 365 days of HRV and RHR
- 4 blood panels (Jan 2024, Aug 2024, Mar 2025, Oct 2025)
- 90 days of workouts (~30 sessions across types)
- 30 days of meals logged
- 90 days of medication adherence
- 60 days of sleep stages
- 12 progress photo placeholders (generated solid-color rectangles
  with date overlays)

**Generation:**
- `web/scripts/generate-fixtures.ts` produces all fixtures
  deterministically from a seed
- Run via `pnpm fixtures:generate`
- Android fixtures generated from the same script's JSON output via a
  Gradle task that copies and converts to Kotlin data classes

The 90-day weight trend in the mockups uses this exact pattern:
starting at 192.8 lb, ending at 189.2 lb, with realistic daily noise.

---

## Density and breakpoint reference

**Web breakpoints (Tailwind defaults):**
- `sm` 640 px — phone landscape (never the design target)
- `md` 768 px — tablet portrait
- `lg` 1024 px — desktop minimum (primary design target)
- `xl` 1280 px — desktop standard (canonical target for screen specs)
- `2xl` 1536 px — desktop wide

Layouts gracefully degrade. Below `lg` the persistent left nav
collapses. Below `md` it hides behind a hamburger.

**Android window size classes (Jetpack WindowSizeClass):**
- `Compact width` (< 600 dp) — phone portrait
- `Medium width` (600–840 dp) — foldable open, tablet portrait
- `Expanded width` (≥ 840 dp) — large tablet, foldable open landscape

Phone designs assume `Compact`. Foldable open assumes `Medium` or
`Expanded`.

**Wear** targets the 192–225 dp diameter circular range (Pixel Watch
class devices).

---

## Accessibility floor

Non-negotiable for v1:
- All text passes WCAG AA contrast in light theme
- All interactive targets ≥ 48×48 dp on mobile, ≥ 32×32 px on web
- Charts have a "view as table" alternative
- All non-decorative icons have accessible names (`aria-label` on
  buttons, `aria-hidden="true"` on purely decorative icons)
- Forms have associated labels
- Focus states are visible (2 px `accent.signal` ring, never remove
  default outline without replacement)

Out of scope for IMPL-01 (revisit later):
- Screen reader optimization beyond labels
- Reduced motion alternatives
- High-contrast theme variant
- Localization
- Dark theme (tokens stubbed above, full implementation later)

---

## Out of scope for IMPL-01

- Backend integration (all data is fixture)
- Authentication (no login screen; app is "already logged in" as Demo
  User)
- Permissions UX (Health Connect prompts, etc.)
- Onboarding flows
- Settings screens beyond a placeholder
- Push notifications design
- Email digest design
- Print / export views
- Marketing site
- Tabletop active workout mockup (to be added in follow-up)
- Dark theme implementation

---

## Acceptance criteria for IMPL-01

When the next implementation pass is done, an observer can:
- Open the web app at `lg`, `xl`, and `2xl` viewports and see every page
  defined above rendered with fixture data
- Open the phone app on a Pixel emulator and navigate all four
  bottom-nav destinations with fixture data
- Open the phone app on a foldable emulator (e.g., Pixel Fold), unfold
  it, and see the layout shift to the icon-only nav variant
- Open the watch app on a Wear OS emulator and see the Today, Quick
  Log, and Active Workout screens
- Confirm Instrument Sans and JetBrains Mono are loaded everywhere,
  with tabular figures on all numeric displays
- Confirm no screen uses a color outside the defined token palette
  (audit by grepping for hex codes in component files)
- Compare any web screen side-by-side with the corresponding mockup in
  `docs/mockups/` and find no meaningful visual divergence
