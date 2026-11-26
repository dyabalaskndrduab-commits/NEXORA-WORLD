# Changelog

## 0.5.0 — Server-authoritative private rooms
- Added an isolated room manager for the public plaza and private invite-code rooms.
- Added cryptographically random eight-character invite codes, 2–12 player room capacity, public-only room listing, host transfer, and empty-room cleanup.
- Replaced arbitrary Socket.IO room IDs with server-validated create/join/leave operations.
- Scoped player snapshots, movement broadcasts, and chat to the player's current room.
- Added server-side normalized-coordinate validation and movement-speed limits with authoritative correction events.
- Added per-IP authentication rate limiting and a production guard for the JWT signing secret.
- Added Node built-in unit tests and documented the current in-memory and Android-integration limitations.
