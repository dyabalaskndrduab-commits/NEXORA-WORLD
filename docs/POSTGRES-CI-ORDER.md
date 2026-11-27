# PostgreSQL CI execution order

The database migration must run before the full backend test command because the PostgreSQL restart test is intentionally enabled whenever DATABASE_URL is present.

CI therefore performs:
1. npm install
2. npm run migrate
3. npm test (unit tests only)
4. npm run test:integration (PostgreSQL restart/persistence test)
5. Android unit tests and debug APK build

This keeps local unit tests independent of a database while ensuring the hosted PostgreSQL integration test runs against a migrated schema.
