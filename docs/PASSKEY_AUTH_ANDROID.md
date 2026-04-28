# Android Passkey Auth Path

Android should feel like a normal secure mobile sign-in, not a command-line setup.

The target production path is:

```text
Android UI
  -> Android backend
  -> NullBridge
  -> approved services
```

The Android app must not receive NullBridge service credentials or call privileged NullBridge routes directly.

## Standard User Setup

The in-app setup flow should guide the user through:

1. Choose sign-in method.
2. Enter or scan backend connection.
3. Sign in with a passkey.
4. Choose privacy level.
5. Choose resource profile.
6. Run health and greeting checks.
7. Save only mobile-safe settings.

No terminal commands should be required for normal phone setup.

## Preferred Auth

Use Android Credential Manager for passkeys.

Use Android Keystore-backed storage for session refresh material where a session must persist. Access tokens should be short-lived and revocable server-side.

OIDC Authorization Code with PKCE is allowed when the backend is configured for a team identity provider.

Password sign-in remains a development or migration fallback only. Admin accounts must not be password-only.

## Native Ceremony Contract

The app now has native ceremony plumbing:

- `Sign in with passkey` calls Android Credential Manager and sends the assertion to `/auth/passkey/complete`.
- Settings can list enrolled credentials from `/auth/passkey/credentials`.
- `Add passkey` calls Android Credential Manager and sends the registration response to `/auth/passkey/register/complete`.
- Removing a passkey revokes it through `/auth/passkey/credentials/{credential_id}`.
- `Continue with OIDC` starts Authorization Code with PKCE at `/auth/oidc/start`.
- The OIDC callback returns to `nullxoid://auth/oidc/callback`.
- The app verifies the OIDC state before exchanging the code at `/auth/oidc/complete`.

If the hosted backend has not been configured with a passkey or OIDC provider yet, these routes must return JSON `501 provider_not_configured` responses. They must never fall through to HTML, a local login page, or a NullBridge privileged route.

## Network Rules

Release builds should use HTTPS or an approved tunnel for remote backends.

Debug builds can keep scoped cleartext support for local development, but the app should warn when cleartext or LAN routes are active.

## AIBenchie Gates

AIBenchie should fail Android release gates if:

- the app receives NullBridge service credentials
- Android calls privileged NullBridge routes directly
- passkey/OIDC setup or native ceremony wiring is missing
- tokens are sent in URLs
- password-only admin sign-in is enabled
- release networking depends on local cleartext
