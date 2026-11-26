# Changelog

## 0.5.0 — Android social client integration
- Added register/login UI with configurable backend URL.
- Added AES-GCM access-token storage using Android Keystore.
- Connected Android to the server-authoritative Socket.IO protocol.
- Added synchronized local movement, remote avatar interpolation, room controls and room-scoped chat.
- Added normalized city-coordinate conversion tests.


## 0.5.0 — Server-authoritative private rooms
- Added server-owned public/private rooms with cryptographically random invite codes and 2–12 player capacity.
- Added public-only room listing, host transfer and empty-room cleanup.
- Scoped player snapshots, movement broadcasts and chat to the current room.
- Added normalized-coordinate bounds and movement-speed validation with authoritative correction events.
- Added authentication attempt rate limiting, production JWT secret guard and Node built-in tests.

## 0.4.0 — Expanded city districts
- Expanded the playable ground and movement boundary to a 140 × 140 city area.
- Added wider avenues, residential towers, retail buildings, two pocket parks and two destination landmarks.
- Added collision-aware movement around main building footprints and extended the camera far plane.
- Reduced per-building window geometry for mobile GPU performance.
