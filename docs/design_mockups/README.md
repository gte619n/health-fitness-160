# Health & Fitness — UI Design Package

Drop this folder into the repo at `docs/`. Final layout:

```
docs/
├── specs/
│   └── IMPL-01-ui-design-spec.md
└── mockups/
    ├── index.html
    ├── dashboard-desktop.html
    ├── dashboard-phone.html
    └── dashboard-foldable.html
```

## What's here

**IMPL-01-ui-design-spec.md** — the design specification. Color tokens,
typography, component library, information architecture, screen specs.
Drives IMPL-02 (UI implementation) and beyond. Read this first.

**mockups/** — four HTML files that render the design language on real
surfaces with fixture data. Open `index.html` in any browser as the
gallery; open the individual files directly to inspect or share.

These are static HTML/CSS/SVG only. No build step, no dependencies beyond
two CDN-loaded fonts (Instrument Sans, JetBrains Mono) and Tabler icons.

## For Claude Code

The spec is the source of truth. The mockups are reference: they show
what the spec means in practice, but the spec text is what should be
implemented in code.

If the spec and the mockups disagree, the spec wins. Flag the discrepancy
so it can be resolved.

## What's not here yet

- Tabletop posture active workout mockup (foldable in half-fold, hinge
  horizontal; top screen for stats, bottom for log controls)
- Active workout takeover for phone and Wear OS
- Section detail screens (Blood detail, Workouts detail, Nutrition log,
  Meds schedule)
- Insights / LLM chat surface
- Onboarding, settings, auth screens
- Dark theme tokens (the spec defines them; mockups only render light)
