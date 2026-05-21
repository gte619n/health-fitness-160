import { auth } from "@/auth";
import { NextResponse } from "next/server";

// Gate every route except Auth.js routes, the sign-in page itself, and Next
// internals. Unauthenticated traffic redirects to /auth/signin.
export default auth((req) => {
  if (req.auth) return NextResponse.next();
  const url = new URL("/auth/signin", req.nextUrl);
  url.searchParams.set("callbackUrl", req.nextUrl.pathname + req.nextUrl.search);
  return NextResponse.redirect(url);
});

export const config = {
  matcher: [
    "/((?!api/auth|auth/signin|_next/static|_next/image|favicon.ico).*)",
  ],
};
