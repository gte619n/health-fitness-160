# CLAUDE.md — web

- **App Router only**, no Pages Router.
- **Server Components by default.** Add `"use client"` only when the file
  needs state, browser APIs, or event handlers.
- Streaming + Suspense are first-class. Don't synthesize loading states the
  framework already provides.
- **Data fetching**: server-side `fetch` inside Server Components.
  Use **SSE** (`text/event-stream`) for LLM streaming, not WebSockets.
- **Styling**: Tailwind v4 utility classes only. No CSS modules, no
  `styled-components`.
- **No client-side state managers** (Redux, Zustand, MobX) until there's a
  real cross-component shared-state case. Server Components + URL state +
  React state cover most cases.
- **Components**: `components/ui/` follows shadcn/ui patterns — copy-in,
  owned in this repo, not imported from a dependency.
- TS strict + `noUncheckedIndexedAccess`. Treat type errors as build failures.
