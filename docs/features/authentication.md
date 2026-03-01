# Authentication & Authorization

**Status:** ✅ Implementiert
**Last Updated:** 2026-03-01

## Overview

JWT-basierte Authentifizierung mit Email-Verifizierung. OAuth2 SSO Infrastructure ready (Google, Facebook, Apple).

## Components

### Backend
- **AuthService.java** - Registration, Login, Email Verification
- **JwtService.java** - JWT Token Generation & Validation
- **SecurityConfig.java** - Spring Security Configuration
- **JwtAuthenticationFilter.java** - JWT Token Interceptor
- **CustomUserDetailsService.java** - User Loading für Spring Security

### Frontend
- **LoginView.vue** - Login + "Email nicht verifiziert" Hinweis + Resend Button
- **RegisterView.vue** - Registration + "Check deine Emails" Screen
- **VerifyEmailView.vue** - Email Verification Handler (loading/success/expired/invalid states)
- **stores/auth.ts** - Pinia Auth Store (token, user state)
- **router/index.ts** - Route Guards (requiresAuth, guestOnly)

## Auth Flow

### Registration Flow
1. User: `POST /api/auth/register` mit `{ email, username, password }`
2. Backend:
   - Erstellt User mit `email_verified = false`
   - Generiert 256-bit SecureRandom Token
   - Speichert in `email_verification_tokens` (24h TTL)
   - Sendet HTML-Email via `EmailService`
3. Frontend: Zeigt "Check deine E-Mails" Screen
4. User klickt Link: `GET /api/auth/verify-email?token={token}`
5. Backend:
   - Validiert Token (expired? invalid?)
   - Setzt `email_verified = true`
   - Löscht Token
   - Returns JWT Token
6. Frontend: Speichert JWT, redirected zu `/dashboard`

### Login Flow
1. User: `POST /api/auth/login` mit `{ email, password }`
2. Backend:
   - Validiert Credentials (BCrypt)
   - Prüft `email_verified == true`
   - Falls nicht verifiziert: `403 EMAIL_NOT_VERIFIED` Error
   - Generiert JWT (7 Tage Expiration)
3. Frontend: Speichert JWT in localStorage, redirected zu `/dashboard`

### JWT Structure
```json
{
  "sub": "user-uuid",
  "email": "max@ev-monitor.net",
  "exp": 1709876543,
  "iat": 1709271743
}
```

**Signing:** HS512 mit `JWT_SECRET` (min. 64 chars)

## API Endpoints

### POST /api/auth/register
**Request:**
```json
{
  "email": "max@ev-monitor.net",
  "username": "max_e_driver",
  "password": "SecurePass123!"
}
```

**Response (200 OK):**
```json
{
  "status": "PENDING_VERIFICATION",
  "email": "max@ev-monitor.net"
}
```

**WICHTIG:** Kein JWT Token! User muss erst Email verifizieren.

### POST /api/auth/login
**Request:**
```json
{
  "email": "max@ev-monitor.net",
  "password": "SecurePass123!"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "userId": "uuid",
  "email": "max@ev-monitor.net",
  "username": "max_e_driver"
}
```

**Error (403 Forbidden - EMAIL_NOT_VERIFIED):**
```json
{
  "error": "EMAIL_NOT_VERIFIED",
  "message": "Please verify your email before logging in"
}
```

### GET /api/auth/verify-email?token={token}
**Response (200 OK):** JWT Token (direkt als AuthResponse, wie Login)

**Error (410 Gone - TOKEN_EXPIRED):**
```json
{
  "error": "TOKEN_EXPIRED",
  "message": "Verification token has expired"
}
```

**Error (400 Bad Request - INVALID_TOKEN):**
```json
{
  "error": "INVALID_TOKEN",
  "message": "Invalid verification token"
}
```

### POST /api/auth/resend-verification
**Request:**
```json
{
  "email": "max@ev-monitor.net"
}
```

**Response (200 OK):**
```json
{
  "message": "Verification email sent"
}
```

**Rate Limiting:** 1 Request pro Minute pro Email (via `RateLimiter` in `AuthService`)

### GET /api/auth/me
**Headers:** `Authorization: Bearer {token}`

**Response (200 OK):**
```json
{
  "userId": "uuid",
  "email": "max@ev-monitor.net",
  "username": "max_e_driver",
  "emailVerified": true
}
```

## Email Verification Tokens

**Tabelle:** `email_verification_tokens`

**Schema:**
- `id` (UUID, PK)
- `user_id` (UUID, FK → app_user ON DELETE CASCADE)
- `token` (VARCHAR(64), UNIQUE) - 256-bit Base64url-encoded SecureRandom
- `expires_at` (TIMESTAMP) - 24h nach Erstellung
- `created_at` (TIMESTAMP)

**Cleanup:** Tokens werden automatisch bei Verifikation gelöscht. Expired Tokens bleiben in DB (TODO: Scheduled Cleanup Job).

## Security Features

### Password Hashing
- **BCrypt** mit Strength 10
- Kein Plaintext, kein reversibles Hashing

### JWT Secret
- Min. 64 Zeichen für HS512
- Environment Variable: `JWT_SECRET`
- **Dev:** `dev-secret-key-CHANGE-IN-PRODUCTION-THIS-MUST-BE-AT-LEAST-64-CHARS-LONG-FOR-HS512-ALGORITHM-12345678901234567890`
- **Prod:** In `.env` auf Server (Hetzner)

### CORS
- Whitelist: `localhost:5173` (Dev), `https://ev-monitor.net` (Prod)
- Credentials: `true` (für JWT Cookies/Headers)

### OAuth2 SSO (Infrastructure Ready)
**Provider:** Google, Facebook, Apple

**Flow:**
1. Frontend: `GET /oauth2/authorization/{provider}` (Spring Security Redirect)
2. User authentifiziert bei Provider
3. Callback: `GET /login/oauth2/code/{provider}`
4. Backend: Erstellt/Updated User, generiert JWT
5. Frontend: `OAuth2RedirectHandler.vue` parsed Token aus URL Fragment

**Status:** Infrastructure vorhanden, aber **deaktiviert** (kein `oauth` Profil aktiv).

## Frontend Auth State

**Pinia Store:** `stores/auth.ts`

```typescript
interface AuthState {
  token: string | null
  user: { id: string, email: string, username: string } | null
}
```

**Persistence:** `localStorage.setItem('token', jwt)`

**Axios Interceptor:** (`api/axios.ts`)
- Request: Fügt `Authorization: Bearer {token}` Header hinzu
- Response 401: Logout + Redirect zu `/login`

## Route Guards

**requiresAuth:** Prüft ob Token existiert, sonst redirect zu `/login`

**guestOnly:** Redirect zu `/dashboard` wenn bereits eingeloggt

**WICHTIG:** `/verify-email` Route hat **keinen Guard** (muss ohne Login erreichbar sein).

## Known Issues

### Email-Konfiguration auf Server fehlt
**Status:** Mail-Sending funktioniert lokal (Mailpit), aber **nicht auf Prod** (Hetzner).

**TODO:**
- `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` in `.env` auf Server eintragen
- Empfohlen: Mailgun, Brevo, SendGrid

### Expired Tokens nicht automatisch gelöscht
**TODO:** Scheduled Job (`@Scheduled`) der täglich alte Tokens aus DB entfernt.

## Related Features
- [User Management](./user-management.md) - User CRUD (TODO)
- [Car Management](./car-management.md) - Requires Auth
