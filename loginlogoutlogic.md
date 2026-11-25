# Persistent Login and Logout API

## Goal

Keep a user signed in across app restarts without making the one-hour access JWT live longer. Use:

- A short-lived access JWT for normal API and WebSocket authentication.
- A longer-lived, opaque refresh token to issue new access JWTs.
- A server-side session record so refresh tokens can be rotated and logout can revoke the session.

Recommended policy: expire a session after **14 days without a successful refresh**. Each successful refresh extends that inactivity window by another 14 days. Logging out revokes the current device session immediately.

> If the desired policy is instead a hard two-week limit from login, keep a fixed `expires_at` that refreshes do not extend.

## Current API contract

The existing API docs define:

- `POST /api/auth/register` and `POST /api/auth/login` accept `username` and `password`.
- Successful responses return `user` and `token`, where `token` is the one-hour JWT.
- `POST /api/auth/logout` currently requires a bearer JWT and has no request body.

The endpoints below preserve the existing `token` response field for compatibility and add refresh-session support. Update the API docs when the backend contract changes.

## 1. Register

### Request

```http
POST /api/auth/register
Content-Type: application/json
```

```json
{
  "username": "alice",
  "password": "a-password-at-least-12-characters"
}
```

### Success: `201 Created`

Registration should create a session and return the same token fields as login:

```json
{
  "user": {
    "id": "99e79d8f-1ae4-43c8-b657-4f9be44d57c9",
    "username": "alice",
    "profile_picture": null
  },
  "token": "<short-lived JWT access token>",
  "expires_in": 3600,
  "refresh_token": "<opaque refresh token>",
  "refresh_expires_at": "2026-12-09T12:00:00Z"
}
```

## 2. Login

### Request

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "username": "alice",
  "password": "a-password-at-least-12-characters"
}
```

### Success: `200 OK`

Create a new session for this app installation and return:

```json
{
  "user": {
    "id": "99e79d8f-1ae4-43c8-b657-4f9be44d57c9",
    "username": "alice",
    "profile_picture": null
  },
  "token": "<short-lived JWT access token>",
  "expires_in": 3600,
  "refresh_token": "<opaque refresh token>",
  "refresh_expires_at": "2026-12-09T12:00:00Z"
}
```

Keep the existing `401 INVALID_CREDENTIALS` response for incorrect credentials.

## 3. Refresh the session

The client calls this endpoint when its access JWT expires or is rejected as expired. The refresh token is sent in the request body; this endpoint does not require an access JWT.

### Request

```http
POST /api/auth/refresh
Content-Type: application/json
```

```json
{
  "refresh_token": "<current opaque refresh token>"
}
```

### Success: `200 OK`

Rotate the refresh token on every successful refresh. Revoke the submitted token and return a new access JWT and refresh token:

```json
{
  "user": {
    "id": "99e79d8f-1ae4-43c8-b657-4f9be44d57c9",
    "username": "alice",
    "profile_picture": null
  },
  "token": "<new short-lived JWT access token>",
  "expires_in": 3600,
  "refresh_token": "<new opaque refresh token>",
  "refresh_expires_at": "2026-12-09T12:00:00Z"
}
```

The new refresh expiry is 14 days from the successful refresh under the recommended inactivity policy.

### Failure

Return `401 INVALID_REFRESH_TOKEN` if the refresh token is missing, invalid, expired, revoked, or already used. Do not reveal which condition occurred.

If a previously rotated token is reused, revoke the active session/token family and require the user to sign in again. This detects refresh-token replay.

## 4. Logout the current session

Change the existing logout contract so it can revoke a session even when its one-hour access JWT has expired. The refresh token identifies the session to revoke.

### Request

```http
POST /api/auth/logout
Content-Type: application/json
```

```json
{
  "refresh_token": "<current refresh token>"
}
```

No access JWT is required for this endpoint. A valid refresh token is the credential for revoking its session.

### Success: `200 OK`

```json
{
  "logged_out": true
}
```

Make logout idempotent: an already revoked or expired session should still result in a successful logged-out response where possible.

Logout should revoke only the session represented by this refresh token. Other devices signed into the same account remain signed in.

## 5. Access JWT requirements

- Keep the access JWT lifetime at one hour.
- Include the session ID (for example, `sid`) in the JWT so logout can invalidate all access JWTs issued for that session.
- Protected REST routes and `/ws` must reject access JWTs whose session has been revoked or expired.
- Continue using the existing header for protected routes and the WebSocket handshake:

```http
Authorization: Bearer <access JWT>
```

## 6. Session storage and token handling

Create a server-side session table or equivalent with fields such as:

| Field | Purpose |
|---|---|
| `id` | Session ID included as the JWT `sid` |
| `user_id` | Account that owns this session |
| `refresh_token_hash` | Hash of the currently valid refresh token |
| `created_at` | Session creation time |
| `last_refreshed_at` | Last successful refresh |
| `expires_at` | Refresh-token inactivity expiry |
| `revoked_at` | Null until the session is revoked |
| `installation_id` | Optional app-installation identifier for device management |

Generate refresh tokens with a cryptographically secure random generator. Store only a hash of each refresh token, never the raw value. Compare hashes safely. Rotate the token atomically so two simultaneous refresh requests cannot both succeed with the same token.

Each login on a new device should create a separate session.

## 7. Android client behavior

1. On login or registration, securely store the access JWT, refresh token, access expiry, user ID, and username.
2. Attach the access JWT as `Authorization: Bearer <token>` to existing protected REST and WebSocket requests.
3. When an API request returns an expired-token response, call `POST /api/auth/refresh`.
4. Save both newly returned tokens, then retry the original request once.
5. If refresh fails, clear local authentication and send the user to login.
6. When opening the app, refresh first if the access token is expired or close to expiry. Then reconnect the WebSocket using the new access JWT.
7. On logout, remove this device's push registration first, while the session can still be refreshed if needed. Then call `POST /api/auth/logout` with the latest refresh token and clear local credentials.

Do not store the refresh token in plain `SharedPreferences`. Protect it with Android Keystore-backed storage. AndroidX `EncryptedSharedPreferences` is deprecated; choose a supported secure-storage approach.

## 8. Push-notification relationship

FCM delivery does not use the access JWT for each push. The backend sends to the FID associated with the account's app installation. On explicit logout, delete that installation's push registration before revoking the session. A JWT expiring on its own does not automatically remove the FCM registration.

## 9. Security and verification checklist

- Login and refresh endpoints use HTTPS.
- Refresh tokens are random, hashed at rest, rotated after each use, and revoked on logout.
- Expired, revoked, or reused refresh tokens cannot issue new access JWTs.
- Logging out invalidates all access JWTs for that session, not sessions on other devices.
- Password changes, account deletion, and security resets can revoke all account sessions.
- Concurrent refresh requests are handled atomically; the same old refresh token cannot be accepted twice.
- The app retries a failed protected request at most once after refreshing.
- The WebSocket reconnects with the latest access JWT after refresh.
- Update the hosted API docs to include the refresh and revised logout contracts.

References:

- [OAuth 2.0 Security Best Current Practice (RFC 9700)](https://www.rfc-editor.org/rfc/rfc9700.html)
- [Android Keystore system](https://developer.android.com/privacy-and-security/keystore)
- [EncryptedSharedPreferences API status](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
