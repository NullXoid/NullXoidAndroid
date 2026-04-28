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

## Passkey Domain Association

Native Android passkeys also require a public domain association before a real phone can create credentials for the hosted backend. For the current production backend, publish Digital Asset Links at:

```text
https://api.echolabs.diy/.well-known/assetlinks.json
```

The statement must include:

- `relation`: `delegate_permission/common.get_login_creds`
- `target.namespace`: `android_app`
- `target.package_name`: `com.nullxoid.android`
- `target.sha256_cert_fingerprints`: the release signing SHA-256 fingerprint for the build users install

Do not publish a local debug signing fingerprint to the production domain unless the goal is a short-lived physical debug test. After the passkey provider is configured and `assetlinks.json` is live, a physical Android test is required to prove Credential Manager can enroll and sign in with a real passkey.

Generate the JSON from the fingerprint of the APK users will install:

```powershell
python scripts/generate_assetlinks.py `
  --apk app/build/outputs/apk/debug/app-debug.apk `
  --apksigner "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.1.0\apksigner.bat"
```

For production, prefer passing the release signing fingerprint directly:

```powershell
python scripts/generate_assetlinks.py `
  --fingerprint "AA:BB:CC:..." `
  --output assetlinks.json
```

Publish the generated file to the EchoLabs platform domain that owns the passkey RP ID. For the current hosted API route, that is `api.echolabs.diy`.

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
