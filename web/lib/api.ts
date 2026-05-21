import { auth } from "@/auth";

// Server-only fetch wrapper that pulls the current ID token from the Auth.js
// session and attaches it as a bearer header. Throws if the session is
// missing or in an error state — callers should let the error propagate to
// trigger the middleware sign-in redirect on the next request.
//
// `BACKEND_URL` is the Spring Boot service base, e.g. http://localhost:8080
// during dev or the Cloud Run URL in production.

const BACKEND_URL = process.env.BACKEND_URL;

export class UnauthenticatedError extends Error {}
export class BackendError extends Error {
  constructor(message: string, public status: number) {
    super(message);
  }
}

export async function apiFetch(
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  if (!BACKEND_URL) {
    throw new Error("BACKEND_URL is not configured");
  }
  const session = await auth();
  if (!session || session.error || !session.idToken) {
    throw new UnauthenticatedError("no valid session");
  }
  const url = `${BACKEND_URL.replace(/\/$/, "")}${path}`;
  return fetch(url, {
    ...init,
    headers: {
      ...init.headers,
      Authorization: `Bearer ${session.idToken}`,
    },
    cache: "no-store",
  });
}

export async function apiJson<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await apiFetch(path, init);
  if (!res.ok) {
    throw new BackendError(`${path} returned ${res.status}`, res.status);
  }
  return res.json() as Promise<T>;
}
