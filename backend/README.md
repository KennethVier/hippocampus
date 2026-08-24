# Hippocampus backend

The base configuration names the application `hippocampus-backend` and binds it to
the loopback interface. No Spring profile is active by default.

## Profiles

- `local` inherits the loopback binding and listens on `SERVER_PORT`, defaulting
  to `8080`.
- `test` is available only to tests, inherits the loopback binding, and requests
  an ephemeral port.
- `pilot` binds to `0.0.0.0` and listens on `PORT`, defaulting to `8080`.

Select a runtime profile with `SPRING_PROFILES_ACTIVE`. Keep common, non-secret
properties in the base configuration and environment-specific properties in the
owning profile. Secrets must remain external to Git. Capability-specific
configuration will be documented by the future task that implements that
capability.

## Observability

The backend writes ECS JSON logs to the console. Every HTTP request receives an
opaque `X-Correlation-ID`; the same value is available to request-scoped logs as
the `correlationId` field. Request-completion events contain only the method,
request path without query values, response status, and duration.

Only status-limited Actuator health is exposed over HTTP:

- `/actuator/health` reports general application health and is not a readiness
  alias.
- `/actuator/health/liveness` contains only the application liveness state.
- `/actuator/health/readiness` contains only the application readiness state.

Deployment probes must use the dedicated liveness and readiness paths. Database
readiness is deferred until the owning persistence task introduces the
application datasource. The Actuator discovery page, metrics endpoint, other
management endpoints, and all JMX management exposure are disabled.
