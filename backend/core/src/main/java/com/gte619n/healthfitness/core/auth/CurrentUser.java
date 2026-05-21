package com.gte619n.healthfitness.core.auth;

// The identity of the caller for a single request, resolved from a validated
// Google ID token (or the dev-mode header in tests). `userId` is the JWT `sub`
// claim and is the canonical user ID across the system; `email` and
// `displayName` are best-effort and may be null for dev-mode requests.
public record CurrentUser(String userId, String email, String displayName) {}
