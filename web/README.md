# health-fitness-web

Next.js 15 App Router, TypeScript strict, Tailwind v4, pnpm. Deploys to
Cloud Run as `health-fitness-web` in `us-central1`.

## Develop
```bash
cp .env.example .env.local        # edit BACKEND_BASE_URL
pnpm install
pnpm dev
```

Open <http://localhost:3000>.

## Other scripts
- `pnpm typecheck`
- `pnpm lint`
- `pnpm build` (production build)
- `pnpm start` (run the production build)

## Deploy
Pushed via Cloud Build (`cloudbuild.yaml`). The backend Cloud Run URL is
passed in as `_BACKEND_URL` and surfaces inside the container as
`BACKEND_BASE_URL`.
