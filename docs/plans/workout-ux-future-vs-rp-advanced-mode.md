# Workout UX: Future.co (our approach) vs. RP Hypertrophy — and an "Advanced Mode"

> Status: discovery / design note. Companion to
> [`IMPL-WORKOUT-001`](IMPL-WORKOUT-001-guided-workout-experience.md). No
> implementation committed from this doc yet — it's the case for a future
> `IMPL-WORKOUT-002` (advanced/auto-regulation mode).

## Why this doc

We built the guided workout experience modeled on **Future.co** — a coached,
video-forward, "just tell me what to do" flow aimed at the general population.
**RP Hypertrophy** (Renaissance Periodization) sits at the other end: a
data-forward, auto-regulating tool for intermediate→advanced lifters who think
in sets, RIR, and mesocycles. This compares the two philosophies and proposes
how Tesseta can serve both without shipping two apps.

## The two philosophies in one line

- **Future.co (what we built):** *"Follow along."* The plan is fixed for today;
  the app's job is to coach you through it smoothly. Minimal input, maximal
  guidance, video is the hero.
- **RP Hypertrophy:** *"Train, report, and I'll adjust."* The plan is a living
  thing the lifter co-pilots through structured feedback; the app's job is to
  auto-regulate volume and intensity over a mesocycle. Numbers are the hero.

## How RP Hypertrophy works (research summary)

- **Mesocycle setup.** The lifter starts from one of **~45 templates**
  (filterable by gender, frequency 2–6 days/week, and focus) **or builds a
  custom** 4–6 week block: training days/week, muscle groups per day, and
  exercises per muscle group from a library (with technique videos and swappable
  alternatives), plus an experience level.
- **Per-set logging.** Every set captures **weight, reps, and RIR** (reps in
  reserve). The app shows a **target load + rep range** per set; you accept or
  edit the actuals. A **rest timer** auto-starts between sets.
- **Descending RIR across the block.** Intensity ramps by *lowering* the RIR
  target week over week — ~3–4 RIR early, down to 0–1 RIR (near failure) the
  week before a deload.
- **Structured feedback drives volume.** At the **start** of a muscle group's
  next session it asks about lingering **soreness**; after work it captures
  **pump**, **joint pain**, and **performance/difficulty**. The algorithm
  combines these to **add 0–2 sets**, hold, or cut volume next week, navigating
  the **MEV → MAV → MRV** volume landmarks (minimum-effective → maximum-adaptive
  → maximum-recoverable volume).
- **Automatic deloads.** A deload week is programmed at block end (volume +
  intensity drop) before the next mesocycle.
- **Aesthetic.** Utilitarian and data-dense — tables, targets, progression
  charts. Deliberately not flashy. Built for people who *want* the numbers.

Sources at the bottom.

## Side-by-side

| Dimension | Future.co (ours) | RP Hypertrophy |
|---|---|---|
| Target user | General / busy / beginner→intermediate | Intermediate→advanced hypertrophy lifters |
| Planning horizon | **Today's session** (authored upstream) | **Mesocycle** (4–6 wks) the user owns |
| Mental model | Follow the coach | Co-pilot the algorithm |
| Per-set input | Confirm reps, adjust weight | weight + reps + **RIR**, vs. a target |
| Intensity control | Implicit (coach picked it) | Explicit **descending RIR** target |
| Volume control | Fixed by the plan | **Auto-regulated** from feedback |
| Feedback captured | Optional rating/notes (post-workout) | **Soreness / pump / joint pain / performance** → volume |
| Progression | Comes from upstream re-planning | In-app algorithm, set-by-set + week-by-week |
| Deloads | Not modeled | First-class, auto-scheduled |
| Hero of the UI | **Looping demo video** | **Numbers & targets** |
| Failure mode if ignored | Still a fine workout | Plan drifts from the lifter |

## What each does better

**Future.co's strengths (keep these):**
- Frictionless: a beginner can press Start and just move.
- Video-forward immersion; form is taught continuously.
- Low cognitive load — no jargon, no decisions mid-set.

**RP's strengths (worth borrowing):**
- **RIR-based autoregulation** matches effort to readiness.
- **Feedback → volume** loop personalizes the plan over weeks.
- **Mesocycle + deload** structure prevents burnout and plateaus.
- **Volume landmarks** give principled stop/add rules.
- Respects advanced users by *showing* and *trusting* them with data.

**Where ours is weak for advanced lifters:** our log is "confirm reps / nudge
weight." There's no RIR, no per-muscle recovery signal, no week-over-week
volume logic, no deloads, no mesocycle view. An advanced lifter would feel the
app is "deciding for them" with no way to steer.

## Best of both worlds: a Tesseta "Advanced Mode"

The thesis: **keep the Future-style player as the chassis; layer RP-style
auto-regulation as an optional mode** that thickens the set logger and adds a
feedback + planning loop. The video, rest timers, and step engine we already
built stay identical — Advanced Mode changes *what we capture* and *what we do
with it*, not the core flow.

A single toggle (`trainingMode = GUIDED | ADVANCED`, per-program or per-user)
gates the additions below. Everything degrades gracefully to today's behavior.

### Idea 1 — RIR-aware set logger (the keystone, lowest effort)
Add an **RIR field** next to reps/weight in the player's `SetLogger`, and show a
**target RIR** for the current set (from the prescription). In Guided mode RIR
is hidden; in Advanced it's a third stepper. This alone unlocks most of RP's
value because RIR is the input the rest of the loop needs.

- Data: extend `PrescribedSet` with `targetRir: Int?` and `LoggedSet` with
  `actualRir: Int?` (both already nullable-friendly in our model).
- UI: the existing reps/weight steppers gain a sibling; `SessionEngine`
  untouched.

### Idea 2 — Load/rep suggestion from last time
Pre-fill each set's weight/reps from the **last performance of that exercise**
(plus the RIR delta), the way RP recommends a target you accept or edit. We
already store logged sets per session; a small `lastPerformance(exerciseId)`
lookup on the backend feeds the prefill. Guided mode keeps using the authored
target; Advanced prefers the data-driven one.

### Idea 3 — Post-set / post-muscle feedback prompts
After the last set of a muscle group (or at session end), show RP-style quick
chips: **pump (lo/med/hi)**, **joint pain (none/some/lots)**, **performance
(easy/right/hard)**. At the **start** of the next session that hits the same
muscle, ask **"still sore?"** These are 1-tap and optional — they feed Idea 4.

### Idea 4 — Auto-regulated volume between sessions (the RP engine)
A backend service turns feedback into next-session volume: combine
soreness + pump + joint pain + performance to **add 0–2 sets, hold, or cut**,
bounded by per-muscle **MEV/MAV/MRV** landmarks. This is where "the plan
becomes living." It writes the *next* `WorkoutSession`'s prescription — which
fits our existing "sessions are authored upstream" model: Advanced Mode simply
makes Tesseta itself one of those upstream authors.

### Idea 5 — Mesocycle scaffolding + auto-deload
Introduce a `Mesocycle` above sessions: length, **descending RIR scheme**
(e.g. 3→2→1→0 across weeks), and an auto-inserted **deload** week. The
dashboard card gains a subtle "Week 3/5 · RIR 1" line in Advanced mode; the
summary can show block progress. Guided mode never sees any of it.

### Idea 6 — A "numbers" skin for the player + summary
Advanced lifters want density. Offer an Advanced layout variant where the
player surfaces **target vs. actual**, **est. 1RM / e1RM trend**, and
**per-muscle weekly volume vs. landmarks**, and the summary shows
**tonnage by muscle** and **RIR adherence**. Same components, denser data —
the video can shrink to a glanceable corner loop instead of the hero.

### Idea 7 — Progressive disclosure, not a separate app
The mode is a spectrum, not a wall: a beginner on Guided who starts editing
weights and logging RIR can be nudged ("Want Tesseta to start auto-adjusting
your volume?") to opt into Advanced. One codebase, one player, graduated depth.

## Suggested phasing (if we pursue `IMPL-WORKOUT-002`)

1. **RIR logging + target** (Ideas 1–2) — additive fields; immediate value,
   no algorithm risk. Verifiable on backend + the existing player.
2. **Feedback capture** (Idea 3) — store soreness/pump/joint/performance on the
   session; no behavior change yet.
3. **Auto-regulation engine** (Idea 4) — backend turns feedback into the next
   session's volume within MEV/MAV/MRV. The highest-value, highest-care piece;
   pure-Java, unit-testable like the Phase-0 summary math.
4. **Mesocycle + deload** (Idea 5) — the planning container + RIR scheme.
5. **Advanced player/summary skin** (Idea 6) and **mode onboarding** (Idea 7).

## Open questions for product

- Is auto-regulation a **Tesseta-native** engine, or do we keep deferring plan
  generation "upstream" and only add RIR + feedback capture client-side?
- Do we expose **MEV/MAV/MRV** language to users, or hide the jargon behind
  plain prompts ("add a set?", "back off")?
- Should Advanced Mode be **per-program** (this block is a hypertrophy meso) or
  a **global user preference**?
- How much of RP's IP-adjacent methodology do we want to mirror vs. design our
  own auto-regulation rules (worth an ADR before building Idea 4)?

## Sources (RP Hypertrophy research)

- [RP Hypertrophy App — official](https://rpstrength.com/pages/hypertrophy-app)
- [Dr. Muscle — independent RP Hypertrophy review](https://dr-muscle.com/rp-hypertrophy-app-review/)
- [Dr. Muscle — 13-point training critique](https://dr-muscle.com/rp-hypertrophy-app-critique/)
- ["I used the RP Hypertrophy App for 6 months" (Medium)](https://medium.com/@justinsmith31491/i-used-the-rp-hypertrophy-app-for-6-months-f20e67378b20)
- [RP Hypertrophy — App Store listing](https://apps.apple.com/us/app/rp-hypertrophy/id1555614554)
</content>
