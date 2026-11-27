# PostgreSQL account persistence CI verification

The PostgreSQL milestone is validated in GitHub Actions with a disposable PostgreSQL 16 service.

The job performs these checks in order:

1. Install the backend dependencies, including the PostgreSQL driver.
2. Run the backend unit tests.
3. Apply the versioned account-table migration.
4. Start the backend against PostgreSQL and register a test account.
5. Stop and restart the backend process.
6. Log in with the same credentials and verify the account UUID is unchanged.
7. Verify duplicate registration returns HTTP 409.
8. Continue with the Android unit tests and debug APK build.

The integration test uses a random temporary username and deletes that test row in its cleanup path. It does not use production credentials or a persistent external database.

This CI service is only a test database. It does not deploy the backend or configure a production DATABASE_URL, JWT_SECRET, HTTPS endpoint, or Android server URL.

Only account records are persisted in this milestone. Live presence, room membership, invite codes, and chat are still process-local.
