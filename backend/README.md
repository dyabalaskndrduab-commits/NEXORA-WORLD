# NEXORA backend — authenticated social rooms

## Run locally (development)

1. Install Node.js 20 or newer.
2. Run `npm install`.
3. Set `JWT_SECRET` to a random secret of at least 32 characters.
4. Run `npm test` to execute the backend tests.
5. Run `npm start` to start the HTTP and Socket.IO server.

Without `DATABASE_URL`, local development uses an in-memory account store. Accounts created in this mode disappear when the process restarts. This mode is intentionally unavailable in production.

Health endpoint: `GET /health`. It reports whether accounts use `memory` or `postgres` storage.

## PostgreSQL setup

The production backend requires PostgreSQL and never silently falls back to memory storage.

1. Create a database and a least-privilege application user.
2. Configure `DATABASE_URL` as a secret in the hosting provider.
3. In production, set `DATABASE_SSL=require` so the connection uses TLS. Keep certificate verification enabled; only use `DATABASE_SSL_REJECT_UNAUTHORIZED=false` as a temporary provider-specific workaround.
4. Set `JWT_SECRET` to a cryptographically random value of at least 32 characters.
5. Set `NODE_ENV=production`, `CORS_ORIGIN` to the exact allowed web origin(s), and `PORT` if required by the host.
6. Run the schema migration once: `npm run migrate`.
7. Start the service: `npm start`.

The migration creates `nexora_accounts` with a UUID primary key, a unique normalized username, a password-hash column, and timestamps. Passwords continue to be hashed with bcrypt before they reach the database. SQL values are passed as parameters, not interpolated into query strings.

A production start fails fast if `DATABASE_URL`, TLS configuration, or the migrated account table is missing. Never commit `.env` files or database credentials. Use `.env.example` only as a variable-name template.

## Authentication endpoints

- `POST /api/auth/register` with JSON `{"username":"player_1","password":"a-long-password"}`
- `POST /api/auth/login` with the same fields

Socket.IO events require a JWT in `handshake.auth.token`.

## Room events

- client -> server: `room:create` with `{ "name": "Friends", "maxPlayers": 8 }`
- client -> server: `room:join` with `{ "code": "ABCD2345" }`
- client -> server: `room:leave`
- client -> server: `rooms:list`
- server -> client: `room:created`, `room:joined`, `room:state`, `room:closed`, `room:host_changed`, `rooms:list`

Private rooms have cryptographically random eight-character invite codes and a capacity of 2–12 players. The public room directory never returns private rooms or their invite codes. Host ownership transfers when the host leaves; an empty private room is removed and its code becomes invalid.

## Movement and chat

- `player:move` accepts normalized coordinates in the 0..1 range. The server rejects non-finite, out-of-world, and implausibly fast movement. Rejected movement emits `player:corrected` with the authoritative position.
- `chat:message` is delivered only to the sender's current room and is rate-limited per socket.

## Verification

Run `npm test`, then check `src/server.js`, `src/account-store.js`, and `src/migrate.js` with `node --check`. CI starts PostgreSQL, applies the migration before unit tests, then runs an integration test that registers an account, restarts the backend process, and logs into the same account. A real PostgreSQL instance is still required to verify deployment in the target hosting environment.

## Important limitations

This milestone persists account records only. Active player presence, private room membership, room invite codes, and chat history remain ephemeral and are intentionally cleared when the server restarts. Before public launch, also add shared Socket.IO coordination for multiple backend instances, persistent profile/progression data, moderation/reporting, monitoring, backups, and a production security review.
