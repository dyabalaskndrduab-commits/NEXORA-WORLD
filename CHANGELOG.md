# Changelog

## 0.5.1 — PostgreSQL account persistence

- Added an account-store abstraction with an in-memory development adapter and a PostgreSQL production adapter.
- Added a transactional, versioned SQL migration for durable accounts.
- Production now refuses to start without \`DATABASE_URL\`, PostgreSQL TLS configuration, and the migrated account table.
- Registration and login now use the account-store adapter; PostgreSQL unique constraints handle concurrent duplicate registrations.
- Added account-store tests and a PostgreSQL integration test that verifies login after a backend process restart.
- CI now starts PostgreSQL, installs backend dependencies, runs the migration and persistence integration test before the Android build.
- Active players, rooms, and chat remain ephemeral; this release does not claim to persist them.
