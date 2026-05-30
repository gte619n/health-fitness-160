# Android ↔ Web Feature Parity Roadmap

**Date:** 2026-05-26
**Status:** Discovery / planning — specs IMPL-AND-00..06 + IMPL-AND-12 (goals) drafted; reconciled with `main` (goals/IMPL-12 + PR #8 dosing/hours) on 2026-05-30

This document compares the current state of the **web** (Next.js 15) and **Android** (Compose) clients, identifies the gap, and proposes a phased roadmap to bring Android to feature parity. It is a living survey, not an authoritative spec — individual workstreams should be split into IMPL specs under `docs/specs/` before implementation.

---

## 1. Executive Summary

### Current state

| Surface | Status | Notes |
|---|---|---|
| **Web** | Feature-rich, production-grade | All major domains implemented end-to-end: dashboard, blood, body comp / DEXA, medications, gym + equipment catalog, plus full admin suite (equipment review, drug catalog). Backed by real backend endpoints, SSE streaming for PDF/AI work, Google Health OAuth. |
| **Android (phone)** | ~15–20% complete | Solid scaffolding (responsive Compose, theming, fold detection), Google sign-in working, phone↔wear token relay working. All dashboard widgets render **fixture data**. Feature modules (`feature-workouts`, `feature-medical`, `feature-chat`) are empty placeholders. |
| **Android (wear)** | ~30% complete | Token sync from phone working. UI is a placeholder ("Signed in" screen). Health Services / Tiles / Complications dependencies wired but inert. |

### Headline gap

Android has **no live backend integration**. Retrofit, Moshi, and Hilt are on the classpath but no API services, repositories, or ViewModels exist beyond auth. Every visible number in the app is hardcoded in `DashboardFixtures.kt`. Closing the gap requires both **foundation work** (network layer, DI, navigation, persistence) and **domain work** (per-feature screens, forms, and data flows).

### Recommended approach

1. **Foundation first** — land Hilt, NavHost, API client, Room, and a shared data layer before any feature work. Building features on top of the current ad-hoc architecture would create rework when these land.
2. **Domain priority** — match the web's most-used domains first: blood, body composition, medications, gym/workouts.
3. **Wear OS as a tail** — defer wear-specific surfaces (Tiles, Complications, Health Services) until phone domains are real. Token relay is already in place.

---

## 2. Domain-by-Domain Gap Analysis

### 2.1 Authentication

| Capability | Web | Android | Gap |
|---|---|---|---|
| Google OAuth sign-in | ✅ Auth.js + Google provider | ✅ Credential Manager + Web client ID | None |
| Session persistence | ✅ JWT cookie | ✅ DataStore (`IdTokenCache`) | None |
| Token refresh | ✅ Auto-refresh < 60s before expiry | ✅ `silentRefresh()` on bootstrap | None |
| Google Health scope (incremental) | ✅ `/me/profile` consent flow with `access_type=offline` | ❌ Not requested | Add incremental scope request; pass refresh_token to backend |
| Admin gating | ✅ `isAdmin()` check in layout | ❌ No admin surfaces at all | Add admin role check; gate admin destinations |
| Wear OS sign-in | n/a | ✅ Token relayed from phone | None |

**Verdict:** Sign-in is solid. Only gap is the **Google Health scope upgrade** flow on Settings/Profile, and admin role detection.

---

### 2.2 Dashboard / Home

| Capability | Web | Android | Gap |
|---|---|---|---|
| Layout | Server-rendered cards in 920px container | ✅ Responsive Compose (phone vs foldable) | Android layout is arguably nicer; no gap |
| Stat cards (weight, HRV, RHR, readiness) | ✅ UI + sparklines (HRV/RHR/readiness are **fixtures** on web too) | ⚠️ UI + sparklines, all fixtures | Both need real data sources for HRV / RHR / readiness; weight is real on web only |
| Weight chart (90d + 7d MA) | ✅ Real data | ⚠️ UI implemented, fixture data | Wire to `/api/me/body-composition` |
| Blood panel (top 4 markers) | ✅ Real data with reference ranges | ⚠️ UI implemented, fixture data | Wire to `/api/me/blood/markers` |
| Today's doses card | ✅ Real data, "take" action | ❌ Not present (fixture log entry only) | Build TodayDosesCard wired to medication API |
| Recent activity feed | ⚠️ Fixture on web too | ⚠️ Fixture on Android | Out of scope until backend aggregates events |
| Body composition hero | ✅ Real data | ⚠️ UI implemented, fixture data | Wire to backend |

**Verdict:** Android dashboard UI is **mostly built** — biggest unlock is wiring the existing widgets to real APIs.

---

### 2.3 Blood Testing

| Capability | Web | Android | Gap |
|---|---|---|---|
| List recent reports | ✅ | ❌ | New screen |
| Add manual marker reading | ✅ (`AddReadingButton`) | ❌ | New form |
| Upload lab PDF | ✅ SSE-streamed extraction | ❌ | File picker + multipart upload + SSE consumer |
| Marker history (sparklines) | ✅ Per-marker last 12mo | ❌ | New detail screen |
| Reference ranges | ✅ Visual bars | ⚠️ Component exists, fixture data | Reuse `BloodPanel.kt` + wire |
| PDF download / view | ✅ Proxied PDF | ❌ | In-app PDF viewer or system intent |
| Report detail (extracted markers list) | ✅ `ExpandableReport` | ❌ | New screen |

**Verdict:** Entire blood feature needs to be built. UI primitives (`BloodPanel`, `RangeBar`) can be reused.

---

### 2.4 Body Composition & DEXA

| Capability | Web | Android | Gap |
|---|---|---|---|
| Weight + body fat tracking | ✅ Google Health sync | ❌ | Need backend endpoint + screen |
| Manual weight entry | ⚠️ Indirect (via Google Health) | ❌ | Consider direct entry form (web lacks too) |
| DEXA scan list | ✅ Grid view | ❌ | New screen |
| DEXA scan upload (PDF) | ✅ SSE extraction | ❌ | File picker + SSE |
| DEXA detail (editable regions) | ✅ Click-to-edit per region | ❌ | New detail screen with `EditableNumber`-equivalent |
| DEXA delete | ✅ | ❌ | Delete action with confirm |
| 90d weight chart | ✅ | ⚠️ UI built, fixture data | Wire to API |
| 7d / 90d weight deltas | ✅ | ❌ | Computed display |

**Verdict:** Full feature build. DEXA upload + edit flow is the most complex piece (multipart upload + SSE + inline edit).

---

### 2.5 Medications

| Capability | Web | Android | Gap |
|---|---|---|---|
| Active medications grid | ✅ `MedicationGrid` | ❌ | New screen |
| Drug AI lookup (streaming) | ✅ SSE LLM lookup | ❌ | SSE consumer in Android |
| Add medication form | ✅ Drug search → dose/frequency/time slots | ❌ | Multi-step form |
| Frequency types (daily/weekly/monthly/cycle/PRN) | ✅ | ❌ | Domain model + form |
| Time slot scheduling (morning/afternoon/evening/bedtime) | ✅ | ❌ | Time slot picker |
| Adherence logging | ✅ Per-day taken/not-taken | ❌ | Quick-log tile already in mockup |
| Adherence sparkline (30d) | ✅ | ❌ | Reuse `Sparkline` |
| Discontinue with reason | ✅ | ❌ | Edit flow |
| Drug image (AI generated) | ✅ Displayed in card | ❌ | Coil image loader |
| Marker correlation linking | ✅ Optional | ❌ | Defer |
| Protocol linking | ✅ Optional | ❌ | Defer |

**Verdict:** Largest single feature in the app. Sequence as a dedicated IMPL spec — likely IMPL-MEDS-001 / -002 across two cycles (catalog/lookup, then adherence).

---

### 2.6 Workouts & Gym Tracking

| Capability | Web | Android | Gap |
|---|---|---|---|
| List user gyms | ✅ `LocationCard` grid | ❌ | New screen |
| Create gym | ✅ Name, address, hours, amenities | ❌ | New form |
| Edit gym | ✅ | ❌ | Edit form |
| Cover photo upload | ✅ | ❌ | Image picker + upload |
| Gym detail view | ✅ With equipment table | ❌ | New screen |
| Set default gym | ✅ | ❌ | Action button |
| Delete gym | ✅ | ❌ | Confirm dialog |
| Equipment catalog (browse) | ✅ Per gym | ❌ | New screen |
| Add equipment | ✅ With spec schema per category | ❌ | Multi-step form |
| Per-location equipment spec overrides | ✅ | ❌ | Override modal |
| Bulk equipment import (CSV preview) | ✅ | ❌ | Probably defer for mobile UX |
| Equipment image (AI generated) | ✅ Displayed in cards | ❌ | Coil image loader |
| **Active workout logging** | ❌ Not built on web either | ❌ | Greenfield — could ship on Android first |
| **Workout history** | ❌ Not built on web | ❌ | Greenfield |
| Wear OS workout session | ❌ n/a | ❌ Foreground service stub | Tied to greenfield workout logging |

**Verdict:** Gym + equipment management mirrors web. **Active workout tracking is undefined on both sides** — Android is the natural home for this (sensors, Health Connect, Wear). Worth a separate ADR before scoping.

---

### 2.7 Profile & Settings

| Capability | Web | Android | Gap |
|---|---|---|---|
| Display name / email | ✅ Read-only | ❌ | Profile screen |
| Height input | ✅ | ❌ | Form field |
| Google Health connection status | ✅ With disconnect/reconnect | ❌ | Settings screen |
| Sign out | ✅ via Auth.js | ⚠️ `signOut()` exists in repo, no UI | Add button |
| Theme / dark mode | ❌ Not yet | ❌ | Future |
| Notifications settings | ❌ | ❌ | Future |
| Unit preferences (lb/kg, in/cm) | ❌ Hard-coded in components | ❌ | Cross-cutting; both clients |

**Verdict:** Settings/profile is a small build — block out as a single screen with sub-sections.

---

### 2.7b Goals

| Capability | Web | Android | Gap |
|---|---|---|---|
| Goal list (cards + phase progress) | ✅ `/me/goals` | ❌ | New screen |
| Goal detail roadmap (phases/steps) | ✅ `GoalDeepResponse` | ❌ | New screen |
| Metric-bound steps + auto-evaluation | ✅ server-evaluated | ❌ | Display evaluated status |
| AI goal-chat (SSE) → editable proposal → commit | ✅ | ❌ | SSE consumer + proposal card |
| Manual goal create / edit / delete / reorder | ✅ | ❌ | Forms + reorder |

**Verdict:** Full feature build to web parity. Goals shipped on
web/backend (IMPL-12) but had **no Android spec** — now covered by
**IMPL-AND-12**. Goal-chat reuses the SSE consumer from IMPL-AND-03;
metric bindings reference the Meds/Blood/Body-comp read models, so
sequence Goals after those domains land.

---

### 2.8 Admin (Equipment + Drug Catalogs)

| Capability | Web | Android | Gap |
|---|---|---|---|
| Pending equipment review | ✅ Approve/reject/edit/merge | ❌ | Admin section |
| Active equipment catalog mgmt | ✅ | ❌ | Admin section |
| Drug catalog mgmt | ✅ Edit/regen image/merge/delete | ❌ | Admin section |
| Image regeneration with custom prompt | ✅ | ❌ | Modal flow |

**Verdict:** Admin features are **low priority for mobile**. Most admin work is faster on desktop with multi-pane editing and drag-drop. Recommend **deferring indefinitely** unless a clear admin-on-the-go use case emerges.

---

### 2.9 Wear OS

| Capability | Web | Android Wear | Gap |
|---|---|---|---|
| Sign-in handoff from phone | n/a | ✅ Working | None |
| "Today" glance screen | n/a | ❌ Placeholder | Build minimal vitals tile |
| Active workout session (HR, duration, sets) | n/a | ❌ | Greenfield — tie to workout logging |
| Tiles | n/a | ❌ Dep wired, no impl | Build after phone domains stable |
| Complications | n/a | ❌ Dep wired, no impl | Build last (lowest impact) |
| Health Services integration | n/a | ❌ Dep wired, no impl | Required for active workouts |

**Verdict:** Defer most wear work until at least medications + workouts ship on phone.

---

## 3. Foundational / Architectural Changes

These are prerequisites for almost any domain work. They should be sequenced **before** feature IMPLs, or at minimum the first feature IMPL should land them.

### 3.1 Dependency injection (Hilt)
- **Status:** Hilt 2.52 on classpath, **not integrated** (per `android/CLAUDE.md`)
- **Action:** Annotate `Application`, wire `@HiltViewModel`, replace manual graph wiring in `AppRoot`
- **Blocks:** All ViewModels, all repositories
- **Sizing:** ~1 cycle, mechanical refactor

### 3.2 Navigation graph
- **Status:** No `NavHost`; routing is hardcoded if/else in `MainActivity`
- **Action:** Build single `NavHost` with destinations for every top-level screen (today, body, blood, workouts, nutrition, meds, settings); migrate bottom-nav and foldable sidebar to dispatch into it
- **Blocks:** Every screen beyond the dashboard
- **Sizing:** ~1 cycle once Hilt lands

### 3.3 Network layer (Retrofit + OkHttp + Moshi)
- **Status:** All deps present in `core-data`, **no API service defined**
- **Action:**
  - Build `core-network` module (or extend `core-data`) with:
    - OkHttp interceptor that injects `Authorization: Bearer {idToken}` from `IdTokenCache`
    - Auto-refresh on 401 via `GoogleAuthRepository.silentRefresh()`
    - Moshi adapters for all DTOs
    - Generated Retrofit services per domain
  - Mirror web's `lib/*-api.ts` per-domain pattern
- **Blocks:** All live data
- **Sizing:** ~1–1.5 cycles; need to also figure out base URL configuration (BuildConfig per flavor for dev/staging/prod)

### 3.4 SSE / streaming consumer
- **Status:** Not present
- **Action:** OkHttp EventSource (or a small custom reader) for blood upload, DEXA upload, drug lookup
- **Blocks:** Lab PDF upload, DEXA upload, AI drug lookup
- **Sizing:** ~3 days inside the first IMPL that needs it

### 3.5 Local persistence (Room)
- **Status:** Dep on classpath, no DAOs / entities
- **Action:** Build entities + DAOs only as needed per domain (don't pre-build for unused features). Pattern: backend is source of truth, Room caches for offline read.
- **Blocks:** Offline support
- **Sizing:** Incremental per domain

### 3.6 Image loading (Coil)
- **Status:** Not present
- **Action:** Add Coil 2.x, wire to backend image URLs (drug images, equipment images, gym cover photos)
- **Blocks:** Medications grid, equipment cards, gym detail
- **Sizing:** ~1 day

### 3.7 File picker + multipart upload
- **Status:** Not present
- **Action:** `ActivityResultContracts.GetContent()` for PDFs, multipart Retrofit request
- **Blocks:** Blood upload, DEXA upload, gym cover photo upload
- **Sizing:** ~2 days

### 3.8 ViewModels for every screen
- **Status:** Zero ViewModels exist
- **Action:** Adopt convention as each domain lands. Pattern: `@HiltViewModel` exposes `StateFlow<UiState>` per screen.
- **Sizing:** Per-domain

### 3.9 Error handling / toasts / loading states
- **Status:** None
- **Action:** Build `Snackbar` host into `Scaffold`; standard loading / empty / error states for lists; surface auth errors centrally
- **Sizing:** ~3 days, ideally as part of the first real feature

### 3.10 In-app PDF viewing
- **Status:** Not present
- **Action:** Either embed (`androidx.pdf:pdf-viewer` if it's stable) or hand off to system viewer via `Intent`
- **Blocks:** Blood report viewing, DEXA PDF download
- **Decision needed:** ADR for in-app vs intent

---

## 4. Proposed Phased Roadmap

> Sizing: each "phase" is roughly one focused implementation cycle. Adjust as scope clarifies.

### Phase 0 — Foundations (blocking everything else)
1. Hilt integration (§3.1)
2. NavHost + route every existing screen + placeholder screens (§3.2)
3. Retrofit + auth interceptor + base URL config (§3.3)
4. Coil for image loading (§3.6)
5. Snackbar / loading / error state primitives (§3.9)

**Outcome:** Skeleton ready to grow. No new user-visible features yet.

### Phase 1 — Wire the existing dashboard to real data
1. `/api/me/profile` for identity, height, Google Health status
2. `/api/me/body-composition` for dashboard weight chart, hero card
3. `/api/me/blood/markers` for dashboard blood panel (top 4 markers + ranges)
4. Replace `DashboardFixtures.kt` usages

**Outcome:** First time the dashboard shows real data on Android.

### Phase 2 — Settings & Profile
1. Settings screen (More tab destination)
2. Profile sub-screen: name, email, height
3. Google Health connection: status, connect (incremental OAuth), disconnect
4. Sign out button

**Outcome:** Account management parity with web.

### Phase 3 — Medications (highest daily-use feature)
1. Medications list screen
2. Drug AI lookup (SSE) → add medication form
3. Frequency + time slot scheduling
4. Today's doses card on dashboard (replace `TodayCard` med fixture)
5. Adherence logging (quick-log tile already in mockup)
6. Adherence sparkline (30d)
7. Edit / discontinue medication

**Outcome:** Med tracking parity. Likely two IMPL specs.

### Phase 4 — Blood Testing
1. Blood reports list
2. Lab PDF upload via SSE
3. Manual marker reading form
4. Marker detail with history sparkline
5. In-app or system PDF viewer

**Outcome:** Blood parity.

### Phase 5 — Body Composition & DEXA
1. Body composition screen (weight/fat over time, full chart, deltas)
2. DEXA scan list
3. DEXA upload via SSE
4. DEXA detail with editable regions
5. DEXA delete

**Outcome:** Body comp parity.

### Phase 6 — Gym & Equipment
1. Gym list
2. Create / edit gym (form + cover photo upload)
3. Gym detail with equipment table
4. Add equipment (spec schema per category)
5. Per-location equipment spec overrides
6. (Defer: bulk import — desktop-friendlier)
7. (Defer: equipment image regeneration — admin task)

**Outcome:** Gym parity (minus admin-flavored features).

### Phase 6.5 — Goals (IMPL-AND-12)

Goals + phases + steps + metric bindings + AI goal-chat, to parity with
web's IMPL-12 (`/me/goals`). Numbered **IMPL-AND-12** to match the
backend/web spec (`IMPL-12-goals-module.md`) rather than the AND-0X
sequence. Sequence after Medications/Blood/Body-comp (its metric bindings
read those domains' models) and before/alongside Workouts.

1. Goal list + detail (roadmap timeline, metric chips, evaluated status)
2. Manual create/edit + reorder (optimistic)
3. AI goal-chat (SSE) → editable proposal card → commit
4. `feature-goals` module + nav destination

**Outcome:** Goals parity with web. Spec: `docs/specs/IMPL-AND-12-goals-module.md`.

### Phase 7 — Greenfield: Active workout logging
*Requires its own ADR before scoping. Likely Android-first.*
1. Define workout data model (exercises, sets, reps, weight, duration, HR)
2. Active workout foreground service (`WorkoutSessionService`)
3. Wear companion screen: HR, duration, current set
4. Health Connect integration for HR / steps
5. Workout history screen
6. Backend endpoints (new on backend too)

### Phase 8 — Wear OS surfaces
1. Wear "today glance" with vitals
2. Wear Tile (vitals at-a-glance)
3. Wear Complication (today's dose count, etc.)

### Phase 9 — Stretch / nice-to-have
1. Nutrition logging (greenfield — define data model + backend first)
2. Sleep tracking via Health Connect
3. Push notifications (dose reminders, etc.)
4. Theme / dark mode
5. Unit preferences (cross-cutting with web)

---

## 5. Cross-Cutting Changes

These apply across multiple phases but don't slot cleanly into one.

| Change | Why |
|---|---|
| **Establish ViewModel convention** | Currently zero VMs. Set the pattern in Phase 1 and apply consistently. |
| **Define DTO ↔ domain model boundary** | Web colocates types with API helpers. Android should mirror in `core-domain` + `core-data`. |
| **Standardize "edit in place" UX** | Web uses `EditableNumber` extensively (DEXA, equipment). Android needs an equivalent composable. |
| **Multipart upload helper** | Reused across blood / DEXA / gym photo. Build once in `core-network`. |
| **SSE consumer** | Reused across blood / DEXA / drug lookup. Build once. |
| **Reference-range bar component** | Already exists on Android (`BloodPanel.RangeBar`). Confirm it matches web's threshold visualization. |
| **Sparkline component** | Already exists. Reuse for adherence, marker history, dashboard vitals. |
| **Admin role detection** | Currently neither side has a UI affordance on Android. Decide whether admin surfaces ever come to mobile (likely no). |
| **Unit handling (kg/lb, cm/in)** | Web hard-codes kg→lb conversions inline. Centralize so both clients can share a unit preference later. |
| **Build flavors for env (dev/staging/prod)** | Backend URL must be configurable. Web reads `BACKEND_URL` from env; Android needs BuildConfig per flavor. |

---

## 6. Explicit Non-Goals

To prevent scope creep, the following are **out of scope** for parity (unless reprioritized):

- **Admin equipment review / catalog / drug management on mobile** — desktop is the right form factor.
- **Bulk CSV imports** — desktop-friendlier.
- **Image regeneration UI with custom prompts** — admin tool.
- **Multi-language support** — not present on web.
- **Tablet-specific layouts beyond what `FoldableDashboardScreen` already does** — out of scope.
- **Native iOS** — not in this monorepo.

---

## 7. Open Questions

1. **Active workout logging design.** This is greenfield on both sides. Where does the workout data model live? Does the backend store it, or does Android own it locally until uploaded? Needs an ADR.
2. **Offline support strategy.** Web is online-only (server components). Android users expect offline read, possibly offline write with sync. How aggressive should Room caching be?
3. **Push notifications.** Dose reminders are a likely high-value feature with no web equivalent. Worth scoping as part of Phase 3.
4. **PDF viewing approach.** In-app PDF view (`androidx.pdf`) or external intent? Affects blood + DEXA UX.
5. **Admin on mobile — truly never?** If a quick approve/reject for pending equipment would help on the go, it could be a small Phase 6+ addition.
6. **Wear standalone use cases.** Workout-in-progress is obvious. Are there others (logging a dose from wrist)?
7. **AI Coach / `feature-chat` module.** Empty placeholder — is this still in the roadmap, and if so, what's the design?

---

## 8. Suggested Next Steps

1. **Review and reprioritize** this document with the team.
2. **Carve Phase 0 into one or two IMPL specs** under `docs/specs/` (foundations + dashboard wiring).
3. **Open an ADR for active workout logging** before Phase 7 work begins.
4. **Decide PDF viewer approach** before Phase 4 (blood).
5. Update `android/CLAUDE.md` once Hilt + NavHost land (current doc notes Hilt is deferred).
6. Sequence **IMPL-AND-12 (Goals)** after the Meds/Blood/Body-comp domains (its metric bindings depend on them); goal-chat reuses the IMPL-AND-03 SSE consumer.
