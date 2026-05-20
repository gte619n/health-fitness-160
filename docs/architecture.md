# Architecture

The system has three deployable components: a native Android application
(phone plus Wear OS), a Next.js web application, and a Spring Boot backend.
All three live in this monorepo. The backend is the source of truth for all
persisted state and owns the only direct connection to Cloud Firestore (native
mode). Clients do not talk to Firestore directly — they call the backend's
REST API.

Health data flows in from a Fitbit Air device through the Google Health API.
A webhook receiver in the Spring backend (`integrations/webhook`) consumes
push notifications, hydrates the records via the Google Health API client
(`integrations/googlehealth`), and writes normalized records to Firestore.
Android and web clients read that normalized data through the backend's
REST surface. The Android phone app uses Health Connect as a secondary
ingestion path for sensor data captured on-device. The Wear OS app uses
Health Services Client for live workout sessions and syncs back through the
phone (or directly with the backend when connected).

Both the backend and the web app deploy to **Cloud Run** in `us-central1`.
Cloud Run was chosen for both because it gives identical CI/CD shape across
the two services, scales to zero between events, and avoids the operational
overhead of running Kubernetes or a long-lived VM. Going **native Android**
rather than Flutter buys us first-class Health Connect, Wear OS, and large-
screen / foldable support; going **Next.js** for the web rather than building
a PWA inside the Android app gives us server-side rendering for medical
research browsing and a clean home for an LLM chat surface. See
[ADR-0001](decisions/ADR-0001-three-component-architecture.md) for the full
rationale.
