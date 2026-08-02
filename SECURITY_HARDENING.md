# Security hardening review

## Scope

Review based on the two supplied videos, the ten security cards in `D:/AIFix`, and a source audit of the React/Vite frontend, Spring Boot API, MySQL access, file storage, Docker, Vercel and Render configuration.

## High-risk issue fixed

Customer login previously trusted only a phone number. Anyone knowing a customer's number could create a customer session and attempt to access that customer's order history and private images. Login and registration now require an `ACCOUNT` OTP verification token. The token is hashed at rest, expires, is rate-limited and is consumed once.

Order lookup now requires a short-lived, one-time `TRACK` OTP verification token whose normalized phone matches the order owner. Customer order history requires an authenticated server session. CCCD and payment-proof reads perform ownership checks for customers and role checks for admins; responses are delivered inline with `no-store`.

## Early-access registration mode

The temporary public release does not use OTP, but it also does not authenticate the customer. `POST /api/customer/waitlist` only records name, normalized phone, consent and a waitlist slot. It does not create a trusted customer session and cannot be used to view orders or private files.

`BOOKING_ENABLED=false` blocks quote, hold, booking submission, CCCD upload and payment-proof upload at the API layer. `EARLY_ACCESS_REGISTRATION_ENABLED=false` is an emergency kill switch for public registration. The UI flag is informational; the API checks are authoritative.

## Controls applied

- Server-side sessions with fixation protection, 15-minute inactivity timeout, `HttpOnly`, `SameSite=Lax`, and `Secure` cookies in production.
- Logout invalidates the server session and clears browser cache/cookies/storage for the site.
- CSRF protection for state-changing requests and credentialed CORS restricted to configured origins.
- Rate limits for admin login, customer login/register, OTP request/verify, booking quote/hold/submit, identity uploads, payment proof and support requests.
- Parameterized JPA queries; no dynamic SQL concatenation found.
- Uploaded images are decoded, pixel-limited, re-encoded, randomly named, AES-GCM encrypted for identity/payment documents, owner-bound and retention-limited.
- Sensitive API responses use `Cache-Control: no-store`; TRACE and CONNECT are rejected.
- CSP, HSTS, frame denial, MIME sniffing protection, restrictive referrer and permissions policies are configured for Vercel and Caddy.
- Docker images run as a non-root user. `.env`, keys, private uploads, logs, build outputs and IDE files are excluded from Git and Docker contexts.
- Frontend dependencies were upgraded until `npm audit --omit=dev` reported zero vulnerabilities.
- Weekly Dependabot and CI security checks were added. The backend workflow also scans the built container for high/critical vulnerabilities.

## Notes from the supplied material

- Spec-driven development is appropriate for this project: requirements, plan, tasks, implementation and contradiction review should precede large feature changes.
- PostgreSQL/Supabase RLS is not applicable to this MySQL/JPA stack. Equivalent protection is implemented through repository/service ownership checks and authorization tests.
- CSP nonces are not needed while the production bundle contains no inline scripts. If inline scripts are introduced, add per-request nonces rather than `'unsafe-inline'` to `script-src`.
- DELETE and OPTIONS cannot be globally disabled: this application legitimately uses DELETE for admin CRUD and OPTIONS for CORS preflight. Unused TRACE and CONNECT are disabled instead.

## Required before production

1. Before enabling customer login or booking, configure a real eSMS provider on Render (`SMS_PROVIDER=esms` plus API key, secret, brand name and approved OTP template). Do not treat waitlist registration as authenticated login.
2. Rotate and store DB, admin, identity-encryption and R2 secrets only in Render/Vercel secret settings. Never commit them or paste them into prompts.
3. Use a private R2 bucket, least-privilege credentials and a retention/lifecycle rule matching the application retention period.
4. Keep `OTP_EXPOSE_DEMO_CODE=false`, `SESSION_COOKIE_SECURE=true`, `SEED_DEMO_DATA=false` and `BOOTSTRAP_ADMIN=false` after initial provisioning.
5. Review weekly Dependabot/Trivy results and test upgrades before merging.
6. Put a distributed rate limiter (Redis or gateway/WAF) in front of multiple backend instances; the current in-memory limiter is effective only per instance.
7. Back up MySQL with encrypted, access-controlled snapshots and regularly test restoration.
8. Keep `JPA_DDL_AUTO=validate`; use a separate Flyway migration account and a DML-only runtime account.
