import { fetchBackendHello } from "@/lib/backend";

export const dynamic = "force-dynamic";

export default async function HomePage() {
  const hello = await fetchBackendHello();
  return (
    <main className="min-h-screen flex items-center justify-center p-8 bg-zinc-50 dark:bg-zinc-950">
      <div className="max-w-md w-full space-y-4">
        <h1 className="text-4xl font-semibold tracking-tight">
          Health &amp; Fitness
        </h1>
        {hello.ok ? (
          <div className="rounded-lg border border-zinc-200 dark:border-zinc-800 p-4">
            <p className="text-sm text-zinc-500">Backend says:</p>
            <p className="text-lg">{hello.data.message}</p>
            <p className="text-xs text-zinc-400 mt-2">
              at {hello.data.timestamp}
            </p>
          </div>
        ) : (
          <div className="rounded-lg border border-amber-200 bg-amber-50 dark:bg-amber-950 dark:border-amber-900 p-4">
            <p className="text-sm">Backend unreachable: {hello.error}</p>
          </div>
        )}
      </div>
    </main>
  );
}
