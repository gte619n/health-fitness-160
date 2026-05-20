const BASE = process.env.BACKEND_BASE_URL ?? "http://localhost:8080";

export type HelloResponse = { message: string; timestamp: string };

export type Result<T> =
  | { ok: true; data: T }
  | { ok: false; error: string };

export async function fetchBackendHello(): Promise<Result<HelloResponse>> {
  try {
    const res = await fetch(`${BASE}/api/hello`, { cache: "no-store" });
    if (!res.ok) {
      return { ok: false, error: `HTTP ${res.status}` };
    }
    const data = (await res.json()) as HelloResponse;
    return { ok: true, data };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : "Unknown" };
  }
}
