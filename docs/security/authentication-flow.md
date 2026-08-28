# P1-02 Authentication Flow

This implementation applies accepted [ADR-0002](../adr/ADR-0002-v1-student-credential-mechanism.md).

`POST /api/auth/login` accepts only JSON containing `email` and `password`. Success returns `204 No Content`; it returns neither a token nor a session identifier. Unknown email, wrong password, absent credentials, and `DISABLED` or `DELETED` users all receive the same `401 AUTHENTICATION_FAILED` response. Only `ACTIVE` users authenticate, and the privacy-minimal principal is mapped from persisted `users.id`.

Password credentials are stored separately in `user_password_credentials` as Spring Security delegating encoded hashes. Login accepts at most 72 UTF-8 password bytes before adaptive verification. Missing credentials and unknown users are checked against a startup-generated dummy adaptive hash to avoid an obvious account-enumeration shortcut.

P1-02 uses an ordinary servlet `HttpSession`, an `HttpSessionSecurityContextRepository`, and change-session-ID fixation protection. P1-03 backs that `HttpSession` with Spring Session JDBC; PostgreSQL stores the server-side session state and Flyway owns the session schema. The idle timeout is configurable through `HIPPOCAMPUS_SESSION_IDLE_TIMEOUT` and defaults to 30 minutes. An authenticated session survives an application restart while PostgreSQL remains available, while an expired session requires reauthentication. The browser stores only an opaque session cookie: local/test use HttpOnly, SameSite=Lax, and Path=/api without forcing Secure on HTTP; the initial cross-site Vercel-to-Render pilot uses HttpOnly, Secure, SameSite=None, and Path=/api. CSRF remains enabled (P1-04 owns browser token acquisition), and P1-05 owns CORS. There are no JWT, bearer, refresh, or access tokens and no role model.

Login submissions are limited by remote address with a configurable, bounded, in-memory fixed window. The default is 60 attempts per minute and at most 1024 tracked addresses. Expired entries are reclaimed; a new address fails closed at capacity. This pilot control is single-process, resets on restart, and neither trusts forwarded-address headers nor provides distributed enforcement.

Initial pilot provisioning remains operator-only and out-of-band. Operators must generate hashes through the application-approved `PasswordEncoder`. P1-02 provides no public signup, password reset, password change, or administrator account API.
