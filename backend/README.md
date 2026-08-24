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
