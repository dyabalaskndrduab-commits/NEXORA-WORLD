# NEXORA backend — private-room authority milestone

## Run locally

1. Install Node.js 20 or newer.
2. Run `npm install`.
3. Set `JWT_SECRET` to a random secret of at least 32 characters.
4. Run `npm test` to execute the dependency-free room-manager tests.
5. Run `npm start` to start the HTTP and Socket.IO server.

Health endpoint: `GET /health`.

Authentication endpoints:
- `POST /api/auth/register` with JSON `{"username":"player_1","password":"a-long-password"}`
- `POST /api/auth/login` with the same fields

Socket.IO events require a JWT in `handshake.auth.token`.

### Room events

- client -> server: `room:create` with `{ "name": "Friends", "maxPlayers": 8 }`
- client -> server: `room:join` with `{ "code": "ABCD2345" }`
- client -> server: `room:leave`
- client -> server: `rooms:list`
- server -> client: `room:created`, `room:joined`, `room:state`, `room:closed`, `room:host_changed`, `rooms:list`

Private rooms have cryptographically random eight-character invite codes and a capacity of 2–12 players. The public room directory never returns private rooms or their invite codes. Host ownership transfers when the host leaves; an empty private room is removed and its code becomes invalid.

### Movement and chat

- `player:move` accepts normalized coordinates in the 0..1 range. The server rejects non-finite, out-of-world, and implausibly fast movement. Rejected movement emits `player:corrected` with the authoritative position.
- `chat:message` is delivered only to the sender's current room and is rate-limited per socket.

## Verification

```bash
npm test
node --check src/server.js
```

The room manager tests use only Node's built-in test runner and do not need `npm install`.

## Important limitations

This is still a development milestone. Accounts, active players, and rooms are in memory and disappear when the process restarts. Android is not yet wired to this backend protocol. Before public deployment, add persistent database storage, HTTPS, shared session/socket coordination, monitoring, moderation, abuse reporting, and a production security review. The development JWT fallback is rejected in production; configure a strong `JWT_SECRET`.
