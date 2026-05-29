# ADR-0005: Goals chat uses gemini-3.5-pro (exception to the flash-only rule)

- Status: Accepted
- Date: 2026-05-29

## Context

The project convention (root `CLAUDE.md`, "AI Models") is that all general
AI work — text generation, parsing, extraction, lookup — runs on
**`gemini-3.5-flash`**, with `gemini-3.1-flash-image-preview` reserved for
image generation. Introducing any other model is explicitly disallowed
without an ADR. This record is that ADR for one carved-out feature.

[IMPL-12](../specs/IMPL-12-goals.md) ships the Goals chat: the user
describes a health objective and Gemini designs a complete Goal as a
structured roadmap of sequenced Phases and metric-bound Steps, returned via
the `propose_goal_structure` tool call for the user to review and edit.

Two properties of this feature distinguish it from the other Gemini jobs in
the codebase (DEXA extraction, blood-test extraction, drug lookup, equipment
parsing/imaging):

- **It is a reasoning task, not an extraction task.** The other jobs map a
  document to fields. Goals chat has to plan a *multi-phase* roadmap where
  Phases run in strict sequence (Phase N+1 cannot start until N completes),
  earlier Phases must build the foundation later Phases depend on, target
  date ranges must be ordered and non-overlapping, and each Step's
  comparator/kind must fit both the metric and the Step kind. This is
  genuine planning over constraints.
- **It now reasons over a full health snapshot.** The chat request appends a
  plain-text snapshot of the user's current medications, body composition /
  DEXA, blood panel, vitals, and the current value of every bindable
  registry metric (see `UserHealthSnapshotService`). The plan should be
  grounded in those real numbers — pick realistic targets, sequence Phases
  around the user's starting point — which rewards stronger reasoning.

On `gemini-3.5-flash`, plans tend to be plausible-looking but shallow:
weakly sequenced Phases, targets that ignore the snapshot, and occasional
constraint violations the validator then has to reject. The stronger model
produces materially better first-pass structures.

Cost is the usual reason to stay on flash. Here it is not a concern: Goals
planning is **low-volume and user-initiated** (a handful of design
conversations per user, not a per-document or per-webhook pipeline), so the
per-call premium of the Pro model is negligible against total spend.

## Decision

Goals chat uses **`gemini-3.5-pro`** as a deliberate, documented exception
to the `gemini-3.5-flash`-only convention.

- Scope is **goals chat ONLY**. DEXA extraction, blood-test extraction,
  medication/drug lookup, and equipment parsing all stay on
  `gemini-3.5-flash`. No other feature changes model.
- The model is configured on its **own** environment variable,
  `GOALS_GEMINI_MODEL`, independent of the shared `GEMINI_MODEL` the flash
  modules read:

  ```yaml
  app:
    goals:
      gemini-model: ${GOALS_GEMINI_MODEL:gemini-3.5-pro}
  ```

  This makes the exception explicit in config and lets us override the goals
  model (e.g. back to flash for a cost test, or forward to a newer Pro
  revision) without touching the other modules.

## Consequences

Positive:

- Better roadmap quality: well-sequenced Phases, snapshot-grounded targets,
  fewer validator rejections on the first proposal.
- The exception is contained and visible — one env var, one config line, one
  ADR — rather than a quiet model swap.
- Easy to revert: set `GOALS_GEMINI_MODEL=gemini-3.5-flash`.

Negative:

- Higher per-call cost and latency for goals chat than flash. Acceptable
  given the low, user-initiated volume.
- The codebase now runs two text models. Anyone reasoning about AI behavior
  or cost must remember goals chat is the outlier; this ADR and the
  `application.yml` comment are the breadcrumbs.

## Revisit when

- A future flash revision closes the planning-quality gap, at which point
  goals chat should fold back onto the shared model.
- Goals chat volume grows enough that the Pro premium becomes material.
- A second feature wants a non-flash model — that warrants its own ADR
  rather than widening this one.
