# NEXORA backend starter

## Run locally

1. Install Node.js 20 or newer.
2. Run `npm install`.
3. Set a strong `JWT_SECRET` environment variable.
4. Run `npm start`.

Health endpoint: `GET /health`.

Authentication endpoints:
- `POST /api/auth/register` with JSON `{"username":"player_1","password":"a-long-password"}`
- `POST /api/auth/login` with the same fields

Socket.IO events require a JWT in `handshake.auth.token`:
- client -> server: `player:move`, `room:join`, `chat:message`
- server -> client: `world:snapshot`, `player:joined`, `player:moved`, `player:left`, `room:joined`, `chat:message`

## Important limitations

This is a development-only starter. User records and online players are in memory and disappear when the process restarts. Use a database, rate limiting, input moderation, HTTPS, secure secret management, origin allowlists, abuse reporting, and server-side authorization before public deployment. Do not use the fallback JWT secret in production.
